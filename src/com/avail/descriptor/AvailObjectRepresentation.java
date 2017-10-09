/**
 * AvailObjectRepresentation.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.descriptor;

import com.avail.descriptor.FiberDescriptor.ObjectSlots;
import com.avail.utility.visitor.MarkUnreachableSubobjectVisitor;
import sun.misc.Unsafe;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.avail.descriptor.NilDescriptor.nil;

/**
 * {@code AvailObjectRepresentation} is the representation used for all Avail
 * objects.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class AvailObjectRepresentation
extends AbstractAvailObject
implements A_BasicObject
{
	/**
	 * This static switch enables paranoid checks to ensure objects are only
	 * being accessed via slot definitions appropriate for the object's actual
	 * descriptor.  This check slows the system considerably, but it's
	 * occasionally valuable to enable for a short time, especially right after
	 * introducing new descriptor subclasses.
	 */
	public static final boolean shouldCheckSlots = false;

	/** A {@code long} array encoding all of my digital state. */
	private long[] longSlots;

	/** An array of all my references to other {@link AvailObject}s. */
	private AvailObject[] objectSlots;

	/**
	 * Turn the receiver into an {@linkplain IndirectionDescriptor indirection}
	 * to the specified {@linkplain AvailObject object}.
	 *
	 * <p><strong>WARNING:</strong> This alters the receiver's slots and
	 * descriptor.</p>
	 *
	 * <p><strong>WARNING:</strong> A {@linkplain Mutability#SHARED shared}
	 * object may not become an indirection. The caller must ensure that this
	 * method is not sent to a shared object.</p>
	 *
	 * @param anotherObject An object.
	 */
	@Override
	public final void becomeIndirectionTo (final A_BasicObject anotherObject)
	{
		assert !descriptor.isShared();
		// Yes, this is really gross, but it's the simplest way to ensure that
		// objectSlots can remain private ...
		final AvailObject traversed = traversed();
		final AvailObject anotherTraversed = anotherObject.traversed();
		if (traversed.sameAddressAs(anotherTraversed))
		{
			return;
		}
		final int oldSlotsSize = objectSlotsCount();
		if (oldSlotsSize == 0)
		{
			// Java-specific mechanism for now.  Requires more complex solution
			// when Avail starts using raw memory again.
			objectSlots = new AvailObject[1];
			objectSlots[0] = nil;
		}
		if (descriptor.isMutable())
		{
			scanSubobjects(
				new MarkUnreachableSubobjectVisitor(anotherObject));
			descriptor = IndirectionDescriptor.mutable(
				anotherTraversed.descriptor.typeTag);
			objectSlots[0] = anotherTraversed;
		}
		else
		{
			anotherObject.makeImmutable();
			descriptor = IndirectionDescriptor.mutable(
				anotherTraversed.descriptor.typeTag);
			objectSlots[0] = anotherTraversed;
			descriptor = IndirectionDescriptor.immutable(
				anotherTraversed.descriptor.typeTag);
		}
	}

	/**
	 * Verify that the object slot is an appropriate way to access this object
	 * (i.e., that the slot is defined in an enumeration within the class of
	 * this object's descriptor).  It fails (an assertion) if it's
	 * inappropriate, and if {@link #shouldCheckSlots} is enabled.
	 *
	 * @param field The object slot to validate for the receiver.
	 */
	private final void checkSlot (final ObjectSlotsEnum field)
	{
		if (shouldCheckSlots)
		{
			final @Nullable ObjectSlotsEnum [] permittedFields =
				descriptor.debugObjectSlots[field.ordinal()];
			if (permittedFields != null)
			{
				for (final ObjectSlotsEnum permittedField : permittedFields)
				{
					if (permittedField == field)
					{
						return;
					}
				}
			}
			// Check it the slow way.
			final Class<?> definitionClass =
				field.getClass().getEnclosingClass();
			assert definitionClass.isInstance(descriptor);
			// Cache that field for next time.
			final ObjectSlotsEnum [] newPermittedFields;
			if (permittedFields == null)
			{
				newPermittedFields = new ObjectSlotsEnum[] {field};
			}
			else
			{
				newPermittedFields = Arrays.copyOf(
					permittedFields, permittedFields.length + 1);
				newPermittedFields[permittedFields.length] = field;
			}
			descriptor.debugObjectSlots[field.ordinal()] = newPermittedFields;
		}
	}

	/**
	 * Verify that the integer slot is an appropriate way to access this object
	 * (i.e., that the slot is defined in an enumeration within the class of
	 * this object's descriptor).  It fails (an assertion) if it's
	 * inappropriate, and if {@link #shouldCheckSlots} is enabled.
	 *
	 * @param field The integer slot to validate for the receiver.
	 */
	private final void checkSlot (final IntegerSlotsEnum field)
	{
		if (shouldCheckSlots)
		{
			final @Nullable IntegerSlotsEnum [] permittedFields =
				descriptor.debugIntegerSlots[field.ordinal()];
			if (permittedFields != null)
			{
				for (final IntegerSlotsEnum permittedField : permittedFields)
				{
					if (permittedField == field)
					{
						return;
					}
				}
			}
			// Check it the slow way.
			final Class<?> definitionClass =
				field.getClass().getEnclosingClass();
			assert definitionClass.isInstance(descriptor);
			// Cache that field for next time.
			final IntegerSlotsEnum [] newPermittedFields;
			if (permittedFields == null)
			{
				newPermittedFields = new IntegerSlotsEnum[] {field};
			}
			else
			{
				newPermittedFields = Arrays.copyOf(
					permittedFields, permittedFields.length + 1);
				newPermittedFields[permittedFields.length] = field;
			}
			descriptor.debugIntegerSlots[field.ordinal()] = newPermittedFields;
		}
	}

	/**
	 * Extract the value of the {@link BitField} of the receiver.  Note that
	 * it's an {@code int} even though the underlying longSlots array contains
	 * {@code long}s.
	 *
	 * @param bitField
	 *            A {@code BitField} that defines the object's layout.
	 * @return An {@code int} extracted from this object.
	 */
	public final int slot (
		final BitField bitField)
	{
		checkSlot(bitField.integerSlot);
		final long fieldValue = longSlots[bitField.integerSlotIndex];
		return bitField.extractFromLong(fieldValue);
	}

	/**
	 * Replace the value of the {@link BitField} within this object.
	 *
	 * @param bitField A {@code BitField} that defines the object's layout.
	 * @param anInteger An {@code int} to store in the indicated bit field.
	 */
	public final void setSlot (
		final BitField bitField,
		final int anInteger)
	{
		checkWriteForField(bitField.integerSlot);
		checkSlot(bitField.integerSlot);
		long value = longSlots[bitField.integerSlotIndex];
		value = bitField.replaceBits(value, anInteger);
		longSlots[bitField.integerSlotIndex] = value;
	}

	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	public final short byteSlot (
		final IntegerSlotsEnum field,
		final int byteSubscript)
	{
		checkSlot(field);
		final int zeroBasedSubscript = byteSubscript - 1;
		final int wordIndex = field.ordinal() + (zeroBasedSubscript >> 3);
		final long word = longSlots[wordIndex];
		final int rightShift = (zeroBasedSubscript & 0x07) << 3;
		return (short) ((word >>> rightShift) & 0xFFL);
	}

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	public final void setByteSlot (
		final IntegerSlotsEnum field,
		final int byteSubscript,
		final short aByte)
	{
		assert aByte == (aByte & 0xFF);
		checkWriteForField(field);
		checkSlot(field);
		final int zeroBasedSubscript = byteSubscript - 1;
		final int wordIndex = field.ordinal() + (zeroBasedSubscript >> 3);
		long word = longSlots[wordIndex];
		final int leftShift = (zeroBasedSubscript & 0x07) << 3;
		word &= ~(0xFFL << leftShift);
		word |= ((long)aByte) << leftShift;
		longSlots[wordIndex] = word;
	}

	/**
	 * Extract a (16-bit unsigned) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The one-base index in shorts.
	 * @return The unsigned {@code short} (as an {@code int} found at the given
	 *         short-index.
	 */
	public final int shortSlot (
		final IntegerSlotsEnum field,
		final int shortIndex)
	{
		checkSlot(field);
		final long word = longSlots[field.ordinal() + ((shortIndex - 1) >>> 2)];
		return (int)((word >>> ((shortIndex - 1 & 3) << 4)) & 0xFFFF);
	}

	/**
	 * Store the (16-bit unsigned) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The one-based index in shorts.
	 * @param aShort The {@code short} to store at the given short-index, passed
	 *               as an {@code int} for safety.
	 */
	public final void setShortSlot (
		final IntegerSlotsEnum field,
		final int shortIndex,
		final int aShort)
	{
		checkWriteForField(field);
		checkSlot(field);
		final int shift = (shortIndex - 1 & 3) << 4;
		final int wordIndex = field.ordinal() + ((shortIndex - 1) >>> 2);
		long word = longSlots[wordIndex];
		word &= ~(0xFFFFL << shift);
		word |= ((long)aShort) << shift;
		longSlots[wordIndex] = word;
	}

	/**
	 * Extract a (32-bit signed) {@code int} at the given int-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param intIndex The one-base index in ints.
	 * @return The signed {@code int} found at the given int-index.
	 */
	public final int intSlot (
		final IntegerSlotsEnum field,
		final int intIndex)
	{
		checkSlot(field);
		final long word = longSlots[field.ordinal() + ((intIndex - 1) >>> 1)];
		return (int)(word >> ((intIndex - 1 & 1) << 5));
	}

	/**
	 * Store the (32-bit signed) {@code int} at the given int-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param intIndex The one-based index in ints.
	 * @param anInt The {@code int} to store at the given int-index.
	 */
	public final void setIntSlot (
		final IntegerSlotsEnum field,
		final int intIndex,
		final int anInt)
	{
		checkWriteForField(field);
		checkSlot(field);
		final int shift = (intIndex - 1 & 1) << 5;
		final int wordIndex = field.ordinal() + ((intIndex - 1) >>> 1);
		long word = longSlots[wordIndex];
		word &= ~(0xFFFFFFFFL << shift);
		word |= (anInt & 0xFFFFFFFFL) << shift;
		longSlots[wordIndex] = word;
	}

	@Override
	public final int integerSlotsCount ()
	{
		return longSlots.length;
	}

	/**
	 * Extract the (signed 64-bit) integer for the given field {@code enum}
	 * value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return A {@code long} extracted from this object.
	 */
	public final long slot (final IntegerSlotsEnum field)
	{
		checkSlot(field);
		return longSlots[field.ordinal()];
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger A {@code long} to store in the indicated slot.
	 */
	public final void setSlot (
		final IntegerSlotsEnum field,
		final long anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		longSlots[field.ordinal()] = anInteger;
	}

	/**
	 * Extract the (signed 64-bit) integer at the given field enum value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return A {@code long} extracted from this object.
	 */
	public final long slot (
		final IntegerSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		return longSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger A {@code long} to store in the indicated slot.
	 */
	public final void setSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final long anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		longSlots[field.ordinal() + subscript - 1] = anInteger;
	}

	/**
	 * Extract the (signed 64-bit) integer for the given field {@code enum}
	 * value. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return A {@code long} extracted from this object.
	 */
	public final long mutableSlot (final IntegerSlotsEnum field)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				return longSlots[field.ordinal()];
			}
		}
		return longSlots[field.ordinal()];
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field {@code enum} value. If the receiver is {@linkplain
	 * Mutability#SHARED shared}, then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger A {@code long} to store in the indicated slot.
	 */
	public final void setMutableSlot (
		final IntegerSlotsEnum field,
		final long anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				longSlots[field.ordinal()] = anInteger;
			}
		}
		else
		{
			longSlots[field.ordinal()] = anInteger;
		}
	}

	/**
	 * Extract an integer (at most 32 bits) from the given {@link BitField}.
	 * If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param bitField A {@link BitField}.
	 * @return An {@code int} extracted from this object.
	 */
	public final int mutableSlot (final BitField bitField)
	{
		checkSlot(bitField.integerSlot);
		final long fieldValue;
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				fieldValue = longSlots[bitField.integerSlotIndex];
			}
		}
		else
		{
			fieldValue = longSlots[bitField.integerSlotIndex];
		}
		return bitField.extractFromLong(fieldValue);
	}

	/**
	 * Store the (signed 32-bit) integer into the specified {@link BitField}.
	 * If the receiver is {@linkplain Mutability#SHARED shared}, then acquire
	 * its monitor.
	 *
	 * @param bitField A {@link BitField}.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	final void setMutableSlot (
		final BitField bitField,
		final int anInteger)
	{
		checkWriteForField(bitField.integerSlot);
		checkSlot(bitField.integerSlot);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				long value = longSlots[bitField.integerSlotIndex];
				value = bitField.replaceBits(value, anInteger);
				longSlots[bitField.integerSlotIndex] = value;
			}
		}
		else
		{
			long value = longSlots[bitField.integerSlotIndex];
			value = bitField.replaceBits(value, anInteger);
			longSlots[bitField.integerSlotIndex] = value;
		}
	}

	/**
	 * Extract the (signed 64-bit) integer at the given field enum value. If the
	 * receiver is {@linkplain Mutability#SHARED shared}, then acquire its
	 * monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return A {@code long} extracted from this object.
	 */
	public final long mutableSlot (
		final IntegerSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			return longSlots[field.ordinal() + subscript - 1];
		}
		return longSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field {@code enum} value. If the receiver is {@linkplain
	 * Mutability#SHARED shared}, then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger A {@code long} to store in the indicated slot.
	 */
	public final void setMutableSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final long anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				longSlots[field.ordinal() + subscript - 1] = anInteger;
			}
		}
		else
		{
			longSlots[field.ordinal() + subscript - 1] = anInteger;
		}
	}

	@Override
	public final int objectSlotsCount ()
	{
		return objectSlots.length;
	}

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	public final AvailObject slot (
		final ObjectSlotsEnum field)
	{
		checkSlot(field);
		return objectSlots[field.ordinal()];
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	public final void setSlot (
		final ObjectSlotsEnum field,
		final A_BasicObject anAvailObject)
	{
		assert !descriptor.isShared() || anAvailObject.descriptor().isShared();
		checkSlot(field);
		checkWriteForField(field);
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		objectSlots[field.ordinal()] = (AvailObject)anAvailObject;
	}

	/**
	 * Store the specified {@linkplain ContinuationDescriptor continuation} in
	 * the receiver, which must be a {@linkplain FiberDescriptor fiber}.  This
	 * is the only circumstance in all of Avail in which a field of a
	 * (potentially) {@linkplain Mutability#SHARED shared} object may hold a
	 * non-shared object.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param aContinuation The object to store at the specified slot.
	 */
	final void setContinuationSlotOfFiber (
		final ObjectSlotsEnum field,
		final A_Continuation aContinuation)
	{
		assert field == ObjectSlots.CONTINUATION;
		checkSlot(field);
		checkWriteForField(field);
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		objectSlots[field.ordinal()] = (AvailObject)aContinuation;
	}

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The object found at the specified slot in the receiver.
	 */
	public final AvailObject slot (
		final ObjectSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		return objectSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	public final void setSlot (
		final ObjectSlotsEnum field,
		final int subscript,
		final A_BasicObject anAvailObject)
	{
		assert !descriptor.isShared() || anAvailObject.descriptor().isShared();
		checkSlot(field);
		checkWriteForField(field);
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		objectSlots[field.ordinal() + subscript - 1] =
			(AvailObject)anAvailObject;
	}

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	public final AvailObject mutableSlot (final ObjectSlotsEnum field)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				return objectSlots[field.ordinal()];
			}
		}
		return objectSlots[field.ordinal()];
	}

	private static final
			AtomicReferenceFieldUpdater<AbstractAvailObject, AbstractDescriptor>
		descriptorFieldUpdater = AtomicReferenceFieldUpdater.newUpdater(
			AbstractAvailObject.class,
			AbstractDescriptor.class,
			"descriptor");

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver.  Use volatile semantics for the read.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	public final AvailObject volatileSlot (final ObjectSlotsEnum field)
	{
		checkSlot(field);
		// First, read from the volatile descriptor field.  This acts as a read
		// fence for other accesses to this object.
		descriptorFieldUpdater.get(this);
		return objectSlots[field.ordinal()];
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver.  Use volatile write semantics.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	final void setVolatileSlot (
		final ObjectSlotsEnum field,
		final A_BasicObject anAvailObject)
	{
		checkSlot(field);
		checkWriteForField(field);
		final AvailObject valueToWrite;
		if (descriptor.isShared())
		{
			// If the receiver is shared, then the new value must become shared
			// before it can be stored.
			// Note: Don't acquire the object's monitor for this, since we want
			// weaker memory semantics.
			valueToWrite = anAvailObject.traversed().makeShared();
		}
		else
		{
			valueToWrite = (AvailObject)anAvailObject;
		}
		setSlot(field, valueToWrite);
		// Force volatile semantics by performing a compare-and-set on the
		// descriptor field (leaving it safely unmodified).
		descriptorFieldUpdater.compareAndSet(this, descriptor, descriptor);
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	final void setMutableSlot (
		final ObjectSlotsEnum field,
		final A_BasicObject anAvailObject)
	{
		checkSlot(field);
		checkWriteForField(field);
		if (descriptor.isShared())
		{
			// If the receiver is shared, then the new value must become shared
			// before it can be stored.
			final AvailObject shared = anAvailObject.traversed().makeShared();
			//noinspection SynchronizeOnThis
			synchronized (this)
			{
				objectSlots[field.ordinal()] = shared;
			}
		}
		else
		{
			objectSlots[field.ordinal()] = (AvailObject)anAvailObject;
		}
	}

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The object found at the specified slot in the receiver.
	 */
	public final AvailObject mutableSlot (
		final ObjectSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				return objectSlots[field.ordinal() + subscript - 1];
			}
		}
		return objectSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	public final void setMutableSlot (
		final ObjectSlotsEnum field,
		final int subscript,
		final AvailObject anAvailObject)
	{
		checkSlot(field);
		checkWriteForField(field);
		if (descriptor.isShared())
		{
			// If the receiver is shared, then the new value must become shared
			// before it can be stored.
			final AvailObject shared = anAvailObject.traversed().makeShared();
			synchronized (this)
			{
				objectSlots[field.ordinal() + subscript - 1] = shared;
			}
		}
		else
		{
			objectSlots[field.ordinal() + subscript - 1] = anAvailObject;
		}
	}

	/**
	 * Write an equivalent replacement object into an {@link ObjectSlotsEnum
	 * object field} of this object.  Since the replacement is semantically
	 * equivalent to the previous content, don't acquire a lock.  Any necessary
	 * write barriers and other memory synchronizations are the responsibility
	 * of the caller.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store unchecked in the slot.
	 */
	final void writeBackSlot (
		final ObjectSlotsEnum field,
		final int subscript,
		final AvailObject anAvailObject)
	{
		checkSlot(field);
		objectSlots[field.ordinal() + subscript - 1] = anAvailObject;
	}

	/**
	 * Reduce the number of {@code long} slots occupied by this object.  In a
	 * raw memory model we would split the object representation into two
	 * objects, one at the original address, and a separate filler object
	 * occupying the long slots that were chopped off.
	 *
	 * In the current Java object implementation, we simply shorten the int
	 * array by replacing it.
 	 */
	@Override
	final void truncateWithFillerForNewIntegerSlotsCount (
		final int newIntegerSlotsCount)
	{
		final int oldIntegerSlotsCount = integerSlotsCount();
		assert newIntegerSlotsCount < oldIntegerSlotsCount;
		final long[] newLongSlots = new long[newIntegerSlotsCount];
		System.arraycopy(longSlots, 0, newLongSlots, 0, newIntegerSlotsCount);
		longSlots = newLongSlots;
	}

	/**
 	 * Slice the current {@linkplain AvailObject object} into two objects, the
 	 * left one (at the same starting address as the input), and the right
 	 * one (a {@linkplain FillerDescriptor filler object} that nobody should
 	 * ever create a pointer to). The new Filler can have zero post-header slots
 	 * (i.e., just the header), but the left object must not, since it may turn
 	 * into an {@linkplain IndirectionDescriptor indirection} some day and will
 	 * require at least one slot for the target pointer.
 	 */
	@Override
	final void truncateWithFillerForNewObjectSlotsCount (
		final int newObjectSlotsCount)
	{
		assert newObjectSlotsCount > 0;
		final int oldObjectSlotsCount = objectSlotsCount();
		assert newObjectSlotsCount < oldObjectSlotsCount;
		// final int fillerSlotCount =
		//   oldObjectSlotsCount - newObjectSlotsCount - 1;
		// Here's where we would write a filler header into raw memory.
		// Slots *filler =
		//   (Slots *)(_pointer.address() + 4 + (newSlotsSize << 2));
		// filler->descriptorId() = FillerDescriptor.mutable().id();
		// filler->sizeInLongs() = fillerSlotCount;
		final AvailObject[] newObjectSlots =
			new AvailObject[newObjectSlotsCount];
		System.arraycopy(
			objectSlots, 0, newObjectSlots, 0, newObjectSlotsCount);
		objectSlots = newObjectSlots;
	}

	/**
	 * Create a new {@link AvailObject} with the specified {@linkplain
	 * AbstractDescriptor descriptor}, the specified number of object slots, and
	 * the specified number of long slots.  Also copy the fields from the
	 * specified object, which must have a descriptor of the same class.  If the
	 * sizes of the long arrays differ, only transfer the minimum of the two
	 * sizes; do the same for the object slots.
	 *
	 * <p>
	 * It is the client's responsibility to mark the shared fields as immutable
	 * if necessary.  Also, any new {@code long} fields beyond the end of the
	 * original array will be initialized to 0, and any new {@code AvailObject}
	 * slots will contain a Java {@code null} (requiring initialization by the
	 * client).
	 * </p>
	 *
	 * @param descriptor
	 *            A descriptor.
	 * @param objectToCopy
	 *            The object from which to copy corresponding fields.
	 * @param deltaObjectSlots
	 *            How many AvailObject fields to add (or if negative, to
	 *            subtract).
	 * @param deltaIntegerSlots
	 *            How many long fields to add (or if negative, to subtract).
	 * @return A new object.
	 */
	static AvailObject newLike (
		final AbstractDescriptor descriptor,
		final AvailObjectRepresentation objectToCopy,
		final int deltaObjectSlots,
		final int deltaIntegerSlots)
	{
		assert deltaObjectSlots == 0 || descriptor.hasVariableObjectSlots;
		assert deltaIntegerSlots == 0 || descriptor.hasVariableIntegerSlots;
		assert descriptor.getClass().equals(objectToCopy.descriptor.getClass());
		final int newObjectSlotCount =
			objectToCopy.objectSlots.length + deltaObjectSlots;
		assert newObjectSlotCount >= descriptor.numberOfFixedObjectSlots;
		final int newIntegerSlotCount =
			objectToCopy.longSlots.length + deltaIntegerSlots;
		assert newIntegerSlotCount >= descriptor.numberOfFixedIntegerSlots;
		final AvailObject newObject =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				newObjectSlotCount - descriptor.numberOfFixedObjectSlots,
				newIntegerSlotCount - descriptor.numberOfFixedIntegerSlots,
				descriptor);
		// Even though we define the private fields in this class we aren't
		// allowed to access them in an instance of something that we know is a
		// subclass!  This surprising situation is probably related to separate
		// compilation and local verification of correctness by the bytecode
		// verifier.
		final AvailObjectRepresentation weakerNewObject = newObject;
		System.arraycopy(
			objectToCopy.longSlots,
			0,
			weakerNewObject.longSlots,
			0,
			Math.min(
				objectToCopy.longSlots.length,
				weakerNewObject.longSlots.length));
		System.arraycopy(
			objectToCopy.objectSlots,
			0,
			weakerNewObject.objectSlots,
			0,
			Math.min(
				objectToCopy.objectSlots.length,
				weakerNewObject.objectSlots.length));
		return newObject;
	}

	/**
	 * Search for the key in the 32-bit ints encoded within the {@linkplain
	 * #longSlots long slots} that occur within those slots identified with the
	 * specified {@link IntegerSlotsEnum}.  The int slots must be in ascending
	 * sorted order, and must be distinct.  If the exact int is found, answer
	 * its zero-based index within this repeated slot (i.e., ≥0).  If the exact
	 * int is not found, answer (-n-1), where n is the zero-based position of
	 * the leftmost element of the repeated slot which is greater than the key
	 * (if it was equal, the "if found" case would have applied).
	 *
	 * @param slot
	 *            The final integer slot, which must be the variable-length part
	 *            of the longSlots array.
	 * @param slotCount
	 *            The number of valid int-sized slots (starting at the specified
	 *            slot's ordinal).
	 * @param key
	 *            The long value to seek in the designated region of the
	 *            longSlots array.
	 * @return
	 *            The zero-based index of the key within the variable-length
	 *            repeated slot if found, or else (-n-1) where n is the
	 *            zero-based index of the leftmost int that is greater than the
	 *            key.
	 */
	final int intBinarySearch (
		final IntegerSlotsEnum slot,
		final int slotCount,
		final int key)
	{
		final int fromIntIndex = slot.ordinal() << 1;
		final int toIntIndex = fromIntIndex + slotCount;
		int low = fromIntIndex;
		int high = toIntIndex - 1;

		while (low <= high) {
			final int mid = (low + high) >>> 1;
			final long longMidVal = longSlots[mid >>> 1];
			// The following shift maintains the little-Endian convention set up
			// by intSlot() and setIntSlot().
			final int midVal = (int)(longMidVal >>> ((mid & 1) << 5));
			if (midVal < key)
			{
				low = mid + 1;
			}
			else if (midVal > key)
			{
				high = mid - 1;
			}
			else
			{
				return mid - fromIntIndex; // key found
			}
		}
		return -((low - fromIntIndex) + 1);  // key not found.
	}

	/**
	 * A reusable empty array of {@link AvailObject}s for objects that have no
	 * object slots.
	 */
	private static final AvailObject[] emptyObjectSlots = new AvailObject[0];

	/**
	 * A reusable empty array of {@code int}s for objects that have no int
	 * slots.
	 */
	private static final long[] emptyIntegerSlots = new long[0];

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <span style="text-decoration: line-through;">strike-out</span>.
	 * That's a convenient warning for the programmer, but we also fail if this
	 * method actually gets invoked AND the argument is not an {@code
	 * A_BasicObject}.  That means we don't allow AvailObjects to be added to
	 * Java {@linkplain Set sets} and such, at least when they're intermixed
	 * with things that are not AvailObjects.
	 * </p>
	 */
	@Override
	@Deprecated
	public boolean equals (final @Nullable Object another)
	{
		assert another instanceof AvailObject;
		return descriptor.o_Equals((AvailObject)this, (AvailObject)another);
	}

	@Override
	public final int hashCode ()
	{
		return descriptor.o_Hash((AvailObject)this);
	}

	/**
	 * Extract the type tag for this object.  Does not answer {@link
	 * TypeTag#UNKNOWN_TAG}.
	 *
	 * <p>It's usually sufficient to access this descriptor's typeTag, but
	 * rarely it may be necessary to invoke computeTypeTag().</p>
	 */
	public final TypeTag typeTag ()
	{
		// First, directly access the descriptor's typeTag, which will be
		// something other than UNKNOWN_TAG in the vast majority of attempts.
		final TypeTag tag = descriptor.typeTag;
		if (tag != TypeTag.UNKNOWN_TAG)
		{
			return tag;
		}
		// Fall back on computing the tag with a slower polymorphic method.
		return descriptor.o_ComputeTypeTag((AvailObject)this);
	}

	/**
	 * Construct a new {@link AvailObjectRepresentation}.
	 *
	 * @param descriptor This object's {@link AbstractDescriptor}.
	 * @param objectSlotsSize The number of object slots to allocate.
	 * @param integerSlotsCount The number of integer slots to allocate.
	 */
	protected AvailObjectRepresentation (
		final AbstractDescriptor descriptor,
		final int objectSlotsSize,
		final int integerSlotsCount)
	{
		super(descriptor);
		objectSlots = objectSlotsSize == 0
			? emptyObjectSlots
			: new AvailObject[objectSlotsSize];
		longSlots = integerSlotsCount == 0
			? emptyIntegerSlots
			:new long[integerSlotsCount];
	}
}
