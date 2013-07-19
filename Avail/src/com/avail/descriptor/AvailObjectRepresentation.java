/**
 * AvailObjectRepresentation.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.util.Arrays;
import java.util.Set;
import com.avail.annotations.Nullable;
import com.avail.visitor.MarkUnreachableSubobjectVisitor;

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
	private final static boolean shouldCheckSlots = false;

	/** An {@code int} array encoding all of my digital state. */
	private int[] intSlots;

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
			objectSlots[0] = NilDescriptor.nil();
		}
		if (descriptor.isMutable())
		{
			scanSubobjects(
				new MarkUnreachableSubobjectVisitor(anotherObject));
			descriptor = IndirectionDescriptor.mutable;
			objectSlots[0] = anotherTraversed;
		}
		else
		{
			anotherObject.makeImmutable();
			descriptor = IndirectionDescriptor.mutable;
			objectSlots[0] = anotherTraversed;
			makeImmutable();
		}
	}

	/**
	 * Verify that the slot is an appropriate way to access this object (i.e.,
	 * that the slot is defined in an enumeration within the class of this
	 * object's descriptor).  It fails (an assertion) if it's inappropriate, and
	 * if {@link #shouldCheckSlots} is enabled.
	 *
	 * @param field The slot to validate for the receiver.
	 */
	private void checkSlot (final AbstractSlotsEnum field)
	{
		if (shouldCheckSlots)
		{
			final Class<?> definitionClass =
				field.getClass().getEnclosingClass();
			assert definitionClass.isInstance(descriptor);
		}
	}
	/**
	 * Extract the value of the {@link BitField} of the receiver.
	 *
	 * @param bitField
	 *            A {@code BitField} that defines the object's layout.
	 * @return An {@code int} extracted from this object.
	 */
	public final int slot (
		final BitField bitField)
	{
		checkSlot(bitField.integerSlot);
		final int value = intSlots[bitField.integerSlotIndex];
		return (value >>> bitField.shift) & bitField.lowMask;
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
		int value = intSlots[bitField.integerSlotIndex];
		value &= bitField.invertedMask;
		value |= (anInteger << bitField.shift) & bitField.mask;
		intSlots[bitField.integerSlotIndex] = value;
	}

	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	public final short byteSlotAt (
		final IntegerSlotsEnum field,
		final int byteSubscript)
	{
		checkSlot(field);
		final int zeroBasedSubscript = byteSubscript - 1;
		final int wordIndex = field.ordinal() + (zeroBasedSubscript >> 2);
		final int word = intSlots[wordIndex];
		final int rightShift = (zeroBasedSubscript & 0x03) << 3;
		return (short) ((word >>> rightShift) & 0xFF);
	}

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	public final void byteSlotAtPut (
		final IntegerSlotsEnum field,
		final int byteSubscript,
		final short aByte)
	{
		assert aByte == (aByte & 0xFF);
		checkWriteForField(field);
		checkSlot(field);
		final int zeroBasedSubscript = byteSubscript - 1;
		final int wordIndex = field.ordinal() + (zeroBasedSubscript >> 2);
		int word = intSlots[wordIndex];
		final int leftShift = (zeroBasedSubscript & 0x03) << 3;
		word &= ~(0xFF << leftShift);
		word |= aByte << leftShift;
		intSlots[wordIndex] = word;
	}

	/**
	 * Extract a (16-bit unsigned) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @return The unsigned {@code short} (as an {@code int} found at the given
	 *         short-index.
	 */
	public final int shortSlotAt (
		final IntegerSlotsEnum field,
		final int shortIndex)
	{
		checkSlot(field);
		final int word = intSlots[field.ordinal() + (shortIndex - 1) / 2];
		return (word >>> ((shortIndex - 1 & 1) << 4)) & 0xFFFF;
	}

	/**
	 * Store the (16-bit unsigned) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @param aShort The {@code short} to store at the given short-index, passed
	 *               as an {@code int} for safety.
	 */
	public final void shortSlotAtPut (
		final IntegerSlotsEnum field,
		final int shortIndex,
		final int aShort)
	{
		checkWriteForField(field);
		checkSlot(field);
		final int shift = (shortIndex - 1 & 1) << 4;
		final int wordIndex = field.ordinal() + (shortIndex - 1) / 2;
		int word = intSlots[wordIndex];
		word &= ~(0xFFFF << shift);
		word |= aShort << shift;
		intSlots[wordIndex] = word;
	}

	@Override
	public final int integerSlotsCount ()
	{
		return intSlots.length;
	}

	/**
	 * Extract the (signed 32-bit) integer for the given field {@code enum}
	 * value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return An {@code int} extracted from this object.
	 */
	public final int slot (final IntegerSlotsEnum field)
	{
		checkSlot(field);
		return intSlots[field.ordinal()];
	}

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	public final void setSlot (
		final IntegerSlotsEnum field,
		final int anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		intSlots[field.ordinal()] = anInteger;
	}

	/**
	 * Extract the (signed 32-bit) integer at the given field enum value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return An {@code int} extracted from this object.
	 */
	public final int slot (
		final IntegerSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		return intSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	public final void setSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final int anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		intSlots[field.ordinal() + subscript - 1] = anInteger;
	}

	/**
	 * Extract the (signed 32-bit) integer for the given field {@code enum}
	 * value. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return An {@code int} extracted from this object.
	 */
	public final int mutableSlot (final IntegerSlotsEnum field)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				return intSlots[field.ordinal()];
			}
		}
		return intSlots[field.ordinal()];
	}

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value. If the receiver is {@linkplain
	 * Mutability#SHARED shared}, then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	public final void setMutableSlot (
		final IntegerSlotsEnum field,
		final int anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				intSlots[field.ordinal()] = anInteger;
			}
		}
		else
		{
			intSlots[field.ordinal()] = anInteger;
		}
	}

	/**
	 * Extract the (signed 32-bit) integer at the given field enum value. If the
	 * receiver is {@linkplain Mutability#SHARED shared}, then acquire its
	 * monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return An {@code int} extracted from this object.
	 */
	public final int mutableSlot (
		final IntegerSlotsEnum field,
		final int subscript)
	{
		checkSlot(field);
		if (descriptor.isShared())
		{
			return intSlots[field.ordinal() + subscript - 1];
		}
		return intSlots[field.ordinal() + subscript - 1];
	}

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value. If the receiver is {@linkplain
	 * Mutability#SHARED shared}, then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	public final void setMutableSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final int anInteger)
	{
		checkWriteForField(field);
		checkSlot(field);
		if (descriptor.isShared())
		{
			synchronized (this)
			{
				intSlots[field.ordinal() + subscript - 1] = anInteger;
			}
		}
		else
		{
			intSlots[field.ordinal() + subscript - 1] = anInteger;
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
		final AvailObject result = objectSlots[field.ordinal()];
		return result;
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
		final AvailObject result = objectSlots[field.ordinal() + subscript - 1];
		return result;
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
				final AvailObject result = objectSlots[field.ordinal()];
				return result;
			}
		}
		final AvailObject result = objectSlots[field.ordinal()];
		return result;
	}

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver. If the receiver is {@linkplain Mutability#SHARED shared}, then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	public final void setMutableSlot (
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
				final AvailObject result =
					objectSlots[field.ordinal() + subscript - 1];
				return result;
			}
		}
		final AvailObject result = objectSlots[field.ordinal() + subscript - 1];
		return result;
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
	 * Reduce the number of int slots occupied by this object.  In a raw memory
	 * model we would split the object representation into two objects, one
	 * at the original address, and a separate filler object occupying the int
	 * slots that were chopped off.
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
		final int[] newIntSlots = new int[newIntegerSlotsCount];
		System.arraycopy(intSlots, 0, newIntSlots, 0, newIntegerSlotsCount);
		intSlots = newIntSlots;
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
	 * the specified number of integer slots.  Also copy the fields from the
	 * specified object, which must have a descriptor of the same class.  If the
	 * sizes of the int arrays differ, only transfer the minimum of the two
	 * sizes, and do the same for the object slots.
	 *
	 * <p>
	 * It is the client's responsibility to mark the shared fields as immutable
	 * if necessary.  Also, any new {@code int} fields beyond the end of the
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
	 *            How many int fields to add (or if negative, to subtract).
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
		final int newIntegerSlotCount =
			objectToCopy.intSlots.length + deltaIntegerSlots;
		assert newObjectSlotCount >= descriptor.numberOfFixedObjectSlots;
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
			objectToCopy.intSlots,
			0,
			weakerNewObject.intSlots,
			0,
			Math.min(
				objectToCopy.intSlots.length,
				weakerNewObject.intSlots.length));
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
	 * Search for the key in the {@linkplain #intSlots integer slots} that occur
	 * within those identified with the specified {@link IntegerSlotsEnum}.
	 * These ints must be in ascending sorted order, and must be distinct.  If
	 * the exact int is found, answer its zero-based index within this repeated
	 * slot (i.e., ≥0).  If the exact int is not found, answer (-n-1), where
	 * n is the zero-based position of the leftmost element of the repeated slot
	 * which is greater than the key (if it was equal, the "if found" case would
	 * have applied).
	 *
	 * @param slot
	 *            The final integer slot, which must be the variable-length part
	 *            of the intSlots array.
	 * @param key
	 *            The value to seek in the designated region of the intSlots
	 *            array.
	 * @return
	 *            The zero-based index of the key within the variable-length
	 *            repeated slot if found, or else (-n-1) where n is the
	 *            zero-based index of the leftmost int that is greater than the
	 *            key.
	 */
	public final int binarySearch (final IntegerSlotsEnum slot, final int key)
	{
		return Arrays.binarySearch(
			intSlots,
			slot.ordinal(),
			intSlots.length,
			key);
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
	private static final int[] emptyIntegerSlots = new int[0];

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <strike>strike-out</strike>.  That's a convenient warning for the
	 * programmer, but we also fail if this method actually gets invoked AND
	 * the argument is not an {@code A_BasicObject}.  That means we don't allow
	 * AvailObjects to be added to Java {@linkplain Set sets} and such, at least
	 * when they're intermixed with things that are not AvailObjects.
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
	 * Construct a new {@link AvailObjectRepresentation}.
	 *
	 * @param descriptor This object's {@link AbstractDescriptor}.
	 * @param objectSlotsSize The number of object slots to allocate.
	 * @param intSlotsCount The number of integer slots to allocate.
	 */
	protected AvailObjectRepresentation (
		final AbstractDescriptor descriptor,
		final int objectSlotsSize,
		final int intSlotsCount)
	{
		super(descriptor);
		if (objectSlotsSize == 0)
		{
			objectSlots = emptyObjectSlots;
		}
		else
		{
			objectSlots = new AvailObject[objectSlotsSize];
		}

		if (intSlotsCount == 0)
		{
			intSlots = emptyIntegerSlots;
		}
		else
		{
			intSlots = new int[intSlotsCount];
		}
	}
}
