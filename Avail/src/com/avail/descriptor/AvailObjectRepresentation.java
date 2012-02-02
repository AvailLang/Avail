/**
 * AvailObjectRepresentation.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import com.avail.annotations.*;
import com.avail.visitor.AvailMarkUnreachableSubobjectVisitor;

/**
 * {@code AvailObjectRepresentation} is the representation used for all Avail
 * objects.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
abstract class AvailObjectRepresentation
extends AbstractAvailObject
{
	/** An array of all my references to other {@link AvailObject}s. */
	private @NotNull AvailObject[] objectSlots;

	/** An {@code int} array encoding all of my digital state. */
	private @NotNull int[] intSlots;

	/**
	 * Turn the receiver into an {@linkplain IndirectionDescriptor indirection}
	 * to the specified {@linkplain AvailObject object}.
	 *
	 * <p><strong>WARNING:</strong> This alters the receiver's slots and
	 * descriptor.</p>
	 *
	 * @param anotherObject An object.
	 */
	final void becomeIndirectionTo (
		final @NotNull AvailObject anotherObject)
	{
		// Yes, this is really gross, but it's the simplest way to ensure that
		// objectSlots can remain private ...
		final AvailObject me = (AvailObject) this;
		// verifyToSpaceAddress();
		final AvailObject traversed = me.traversed();
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
			objectSlots[0] = NullDescriptor.nullObject();
		}
		if (descriptor().isMutable())
		{
//			if (canDestroyObjects())
//			{
				me.scanSubobjects(
					new AvailMarkUnreachableSubobjectVisitor(anotherObject));
//			}
			descriptor = IndirectionDescriptor.mutable();
			objectSlots[0] = anotherTraversed;
		}
		else
		{
			anotherObject.makeImmutable();
			descriptor = IndirectionDescriptor.mutable();
			objectSlots[0] = anotherTraversed;
			me.makeImmutable();
		}
	}

	@Override
	final int bitSlot (
		final @NotNull IntegerSlotsEnum field,
		final @NotNull BitField bitField)
	{
//		verifyToSpaceAddress();
		int value = intSlots[field.ordinal()];
		value >>>= bitField.shift();
		final int mask = ~(-1 << bitField.bits());
		return value & mask;
	}

	@Override
	final void bitSlotPut (
		final @NotNull IntegerSlotsEnum field,
		final @NotNull BitField bitField,
		final int anInteger)
	{
		checkWriteForField(field);
//		verifyToSpaceAddress();
		int value = intSlots[field.ordinal()];
		final int mask = ~(-1 << bitField.bits());
		value &= ~(mask << bitField.shift());
		value |= (anInteger & mask) << bitField.shift();
		intSlots[field.ordinal()] = value;
	}

	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	@Override
	final short byteSlotAt (
		final @NotNull IntegerSlotsEnum e,
		final int byteSubscript)
	{
//		verifyToSpaceAddress();
		final int word = intSlots[e.ordinal() + (byteSubscript - 1) / 4];
		return (short) (word >>> ((byteSubscript - 1 & 0x03) << 3) & 0xFF);
	}

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	@Override
	final void byteSlotAtPut (
		final @NotNull IntegerSlotsEnum e,
		final int byteSubscript,
		final short aByte)
	{
		assert aByte == (aByte & 0xFF);
		checkWriteForField(e);
//		verifyToSpaceAddress();
		final int wordIndex = e.ordinal() + (byteSubscript - 1) / 4;
		int word = intSlots[wordIndex];
		final int leftShift = (byteSubscript - 1 & 3) << 3;
		word &= ~(0xFF << leftShift);
		word |= aByte << leftShift;
		intSlots[wordIndex] = word;
	}

	@Override
	final short shortSlotAt (
		final @NotNull IntegerSlotsEnum e,
		final int shortIndex)
	{
//		verifyToSpaceAddress();
		final int word = intSlots[e.ordinal() + (shortIndex - 1) / 2];
		return (short)(word >>> ((shortIndex - 1 & 1) << 4));
	}

	@Override
	final void shortSlotAtPut (
		final @NotNull IntegerSlotsEnum e,
		final int shortIndex,
		final short aShort)
	{
		checkWriteForField(e);
//		verifyToSpaceAddress();
		final int shift = (shortIndex - 1 & 1) << 4;
		final int wordIndex = e.ordinal() + (shortIndex - 1) / 2;
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

	@Override
	final int slot (final @NotNull IntegerSlotsEnum e)
	{
//		verifyToSpaceAddress();
		return intSlots[e.ordinal()];
	}

	@Override
	final void setSlot (
		final @NotNull IntegerSlotsEnum e,
		final int anInteger)
	{
		checkWriteForField(e);
//		verifyToSpaceAddress();
		intSlots[e.ordinal()] = anInteger;
	}

	@Override
	final int slot (
		final @NotNull IntegerSlotsEnum e,
		final int subscript)
	{
//		verifyToSpaceAddress();
		return intSlots[e.ordinal() + subscript - 1];
	}

	@Override
	final void setSlot (
		final @NotNull IntegerSlotsEnum e,
		final int subscript,
		final int anInteger)
	{
		checkWriteForField(e);
//		verifyToSpaceAddress();
		intSlots[e.ordinal() + subscript - 1] = anInteger;
	}

	@Override
	public final int objectSlotsCount ()
	{
		return objectSlots.length;
	}

	@Override
	final AvailObject slot (
		final @NotNull ObjectSlotsEnum e)
	{
//		verifyToSpaceAddress();
		final AvailObject result = objectSlots[e.ordinal()];
//		result.verifyToSpaceAddress();
		return result;
	}

	@Override
	final void setSlot (
		final @NotNull ObjectSlotsEnum e,
		final @NotNull AvailObject anAvailObject)
	{
//		verifyToSpaceAddress();
		checkWriteForField(e);
		objectSlots[e.ordinal()] = anAvailObject;
	}

	@Override
	final AvailObject slot (
		final @NotNull ObjectSlotsEnum e,
		final int subscript)
	{
//		verifyToSpaceAddress();
		final AvailObject result = objectSlots[e.ordinal() + subscript - 1];
//		result.verifyToSpaceAddress();
		return result;
	}

	@Override
	final void setSlot (
		final @NotNull ObjectSlotsEnum e,
		final int subscript,
		final @NotNull AvailObject anAvailObject)
	{
//		verifyToSpaceAddress();
		checkWriteForField(e);
		objectSlots[e.ordinal() + subscript - 1] = anAvailObject;
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
	final void truncateWithFillerForNewIntegerSlotsCount (
		final int newIntegerSlotsCount)
	{
//		verifyToSpaceAddress();
//		assert(objectSlotsCount > 0);
		final int oldIntegerSlotsCount = integerSlotsCount();
		assert newIntegerSlotsCount < oldIntegerSlotsCount;
		// final int fillerSlotCount =
		//	 oldIntegerSlotsCount - newIntegerSlotsCount - 1;
		// Here's where we would write a filler header into raw memory.
		// filler->descriptorId() = FillerDescriptor.mutable().id();
		// Slots *filler =
		//   (Slots *)(_pointer.address() + 4 + (newIntegerSlotsCount << 2));
		// filler->sizeInLongs() = fillerSlotCount;
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
//		verifyToSpaceAddress();
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
	 * Construct a new {@link AvailObjectRepresentation}.
	 *
	 * @param descriptor This object's {@link AbstractDescriptor}.
	 * @param objectSlotsSize The number of object slots to allocate.
	 * @param intSlotsCount The number of integer slots to allocate.
	 */
	protected AvailObjectRepresentation (
		final @NotNull AbstractDescriptor descriptor,
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
