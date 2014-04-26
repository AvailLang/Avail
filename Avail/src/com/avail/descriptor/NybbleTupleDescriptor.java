/**
 * NybbleTupleDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.NybbleTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.nio.ByteBuffer;
import com.avail.annotations.*;

/**
 * {@code NybbleTupleDescriptor} represents a tuple of integers that happen to
 * fall in the range 0..15. They are packed eight per {@code int}.
 *
 * <p>
 * This representation is particularly useful for {@linkplain
 * CompiledCodeDescriptor compiled code}, which uses nybblecodes.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class NybbleTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash of the tuple or zero.  In the rare case that the hash is
		 * actually zero, it will have to be recalculated each time it is
		 * requested.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/**
		 * The {@code int} slots that hold the nybble values of the tuple, eight
		 * per slot (except the last one which may be less), in Little Endian
		 * order.
		 */
		RAW_QUAD_AT_;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 128;

	/**
	 * The number of nybbles of the last {@linkplain IntegerSlots#RAW_QUAD_AT_
	 * integer slot} that are not considered part of the tuple.
	 */
	int unusedNybblesOfLastWord;

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithNybbleTupleStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aNybbleTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		if (endIndex1 < startIndex1)
		{
			return true;
		}
		//  Compare actual nybbles.
		int index2 = startIndex2;
		for (int i = startIndex1; i <= endIndex1; i++)
		{
			if (getNybble(object, i)
				!= aNybbleTuple.extractNybbleFromTupleAt(index2))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsNybbleTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final A_Tuple aNybbleTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aNybbleTuple))
		{
			return true;
		}
		if (object.tupleSize() != aNybbleTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aNybbleTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithNybbleTupleStartingAt(
			1,
			object.tupleSize(),
			aNybbleTuple,
			1))
		{
			return false;
		}
		// They're equal, but occupy disjoint storage. If possible, then replace
		// one with an indirection to the other to reduce storage costs and the
		// frequency of nybble-wise comparisons.
		if (!isShared())
		{
			aNybbleTuple.makeImmutable();
			object.becomeIndirectionTo(aNybbleTuple);
		}
		else if (!aNybbleTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aNybbleTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than the
		// second one? Currently there is no more desirable representation than
		// a nybble tuple.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
		{
			return true;
		}
		if (!aType.isTupleType())
		{
			return false;
		}
		// See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesInt(object.tupleSize()))
		{
			return false;
		}
		// Tuple's size is out of range.
		final A_Tuple typeTuple = aType.typeTuple();
		final int breakIndex = min(object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		final A_Type defaultTypeObject = aType.defaultType();
		if (IntegerRangeTypeDescriptor.nybbles().isSubtypeOf(defaultTypeObject))
		{
			return true;
		}
		for (int i = breakIndex + 1, end = object.tupleSize(); i <= end; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	void o_RawNybbleAtPut (
		final AvailObject object,
		final int nybbleIndex,
		final byte aNybble)
	{
		assert isMutable();
		setNybble(object, nybbleIndex, aNybble);
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.descriptor = descriptorFor(IMMUTABLE, object.tupleSize());
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = descriptorFor(SHARED, object.tupleSize());
		}
		return object;
	}

	@Override @AvailMethod
	byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int nybbleIndex)
	{
		return getNybble(object, nybbleIndex);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the nybble tuple object.
		return (AvailObject)IntegerDescriptor.fromUnsignedByte(
			getNybble(object, index));
	}

	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int nybbleIndex,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		if (!newValueObject.isNybble())
		{
			if (newValueObject.isUnsignedByte())
			{
				return copyAsMutableByteTuple(object).tupleAtPuttingCanDestroy(
					nybbleIndex,
					newValueObject,
					true);
			}
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				nybbleIndex,
				newValueObject,
				true);
		}
		if (!canDestroy || !isMutable())
		{
			return copyAsMutableByteTuple(object).tupleAtPuttingCanDestroy(
				nybbleIndex,
				newValueObject,
				true);
		}
		// All clear.  Clobber the object in place...
		final AvailObject strongValue = (AvailObject)newValueObject;
		object.rawNybbleAtPut(nybbleIndex, strongValue.extractNybble());
		object.hashOrZero(0);
		//  ...invalidate the hash value. Probably cheaper than computing the
		// difference or even testing for an actual change.
		return object;
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the integer element at the given index in the nybble tuple
		// object.
		return getNybble(object, index);
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size >= maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable nybbles out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
		final AvailObject result = mutableObjectOfSize(size);
		for (int i = 1; i <= size; i++)
		{
			setNybble(result, i, getNybble(object, size-i+1));
		}
		return result;
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return (object.variableIntegerSlotsCount() << 3)
			- unusedNybblesOfLastWord;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 4;
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass.  This method must produce the same value.
		// This could eventually be rewritten to do a byte at a time (table
		// lookup) and to use the square of the current multiplier.
		int hash = 0;
		for (int nybbleIndex = end; nybbleIndex >= start; nybbleIndex--)
		{
			final int itemHash = IntegerDescriptor.hashOfUnsignedByte(
				getNybble(object, nybbleIndex));
			hash = hash * multiplier + (itemHash ^ preToggle);
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		final int size1 = object.tupleSize();
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable();
			}
			return otherTuple;
		}
		final int size2 = otherTuple.tupleSize();
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int newSize = size1 + size2;
		if (newSize <= maximumCopySize)
		{
			// Copy the nybbles.
			final int newWordCount = (newSize + 7) >>> 3;
			final int deltaSlots =
				newWordCount - object.variableIntegerSlotsCount();
			final AvailObject copy;
			if (canDestroy && isMutable() && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				object.descriptor = descriptorFor(MUTABLE, newSize);
				copy = object;
			}
			else
			{
				copy = newLike(
					descriptorFor(MUTABLE, newSize), object, 0, deltaSlots);
			}
			copy.setSlot(HASH_OR_ZERO, 0);
			int dest = size1 + 1;
			A_Tuple result = copy;
			for (int src = 1; src <= size2; src++, dest++)
			{
				// If the slots we want are nybbles then we won't have to copy
				// into a bulkier representation.
				result = result.tupleAtPuttingCanDestroy(
					dest,
					otherTuple.tupleAt(src),
					canDestroy | src > 1);
			}
			return result;
		}
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}
		if (otherTuple.treeTupleLevel() == 0)
		{
			return TreeTupleDescriptor.createPair(object, otherTuple, 1, 0);
		}
		return TreeTupleDescriptor.concatenateAtLeastOneTree(
			object,
			otherTuple,
			true);
	}

	@Override
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		assert 1 <= start && start <= end + 1;
		final int tupleSize = object.tupleSize();
		assert 0 <= end && end <= tupleSize;
		final int size = end - start + 1;
		if (size > 0 && size < tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable nybbles out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			final AvailObject result = mutableObjectOfSize(size);
			int dest = 1;
			for (int src = start; src <= end; src++, dest++)
			{
				setNybble(result, dest, getNybble(object, src));
			}
			if (canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return result;
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	@Override
	void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		for (int index = startIndex; index <= endIndex; index++)
		{
			outputByteBuffer.put(getNybble(object, index));
		}
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		if (IntegerRangeTypeDescriptor.nybbles().isSubtypeOf(type))
		{
			return true;
		}
		return super.o_TupleElementsInRangeAreInstancesOf(
			object,
			startIndex,
			endIndex,
			type);
	}

	/**
	 * Extract the nybble from the specified position of the {@linkplain
	 * NybbleTupleDescriptor nybble tuple}.
	 *
	 * @param object A nybble tuple.
	 * @param nybbleIndex The index.
	 * @return The nybble at that index.
	 */
	private static byte getNybble (
		final AvailObject object,
		final int nybbleIndex)
	{
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		final int quadIndex = (nybbleIndex + 7) >>> 3;
		final int quad = object.slot(RAW_QUAD_AT_, quadIndex);
		final int shift = (nybbleIndex - 1 & 7) * 4;
		return (byte) (quad>>>shift & 0x0F);
	}

	/**
	 * Overwrite the specified position of the {@linkplain NybbleTupleDescriptor
	 * nybble tuple} with a replacement nybble.
	 *
	 * @param object The nybble tuple.
	 * @param nybbleIndex The index.
	 * @param aNybble The replacement value, a nybble.
	 */
	private static void setNybble (
		final AvailObject object,
		final int nybbleIndex,
		final byte aNybble)
	{
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		assert (aNybble & 15) == aNybble;
		final int wordIndex = (nybbleIndex + 7) >>> 3;
		int quad = object.slot(RAW_QUAD_AT_, wordIndex);
		final int leftShift = (nybbleIndex - 1 & 7) * 4;
		quad &= ~(0x0F << leftShift);
		quad |= aNybble << leftShift;
		object.setSlot(RAW_QUAD_AT_, wordIndex, quad);
	}

	/**
	 * Construct a new {@link NybbleTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedNybbles The number of unused nybbles of the last word.
	 */
	private NybbleTupleDescriptor (
		final Mutability mutability,
		final int unusedNybbles)
	{
		super(mutability, null, IntegerSlots.class);
		unusedNybblesOfLastWord = unusedNybbles;
	}

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * {@link #descriptorFor(Mutability, int)} can find them by mutability and
	 * number of unused nybbles in the last word.
	 */
	static final NybbleTupleDescriptor[] descriptors =
	{
		new NybbleTupleDescriptor(MUTABLE, 0),
		new NybbleTupleDescriptor(IMMUTABLE, 0),
		new NybbleTupleDescriptor(SHARED, 0),
		new NybbleTupleDescriptor(MUTABLE, 7),
		new NybbleTupleDescriptor(IMMUTABLE, 7),
		new NybbleTupleDescriptor(SHARED, 7),
		new NybbleTupleDescriptor(MUTABLE, 6),
		new NybbleTupleDescriptor(IMMUTABLE, 6),
		new NybbleTupleDescriptor(SHARED, 6),
		new NybbleTupleDescriptor(MUTABLE, 5),
		new NybbleTupleDescriptor(IMMUTABLE, 5),
		new NybbleTupleDescriptor(SHARED, 5),
		new NybbleTupleDescriptor(MUTABLE, 4),
		new NybbleTupleDescriptor(IMMUTABLE, 4),
		new NybbleTupleDescriptor(SHARED, 4),
		new NybbleTupleDescriptor(MUTABLE, 3),
		new NybbleTupleDescriptor(IMMUTABLE, 3),
		new NybbleTupleDescriptor(SHARED, 3),
		new NybbleTupleDescriptor(MUTABLE, 2),
		new NybbleTupleDescriptor(IMMUTABLE, 2),
		new NybbleTupleDescriptor(SHARED, 2),
		new NybbleTupleDescriptor(MUTABLE, 1),
		new NybbleTupleDescriptor(IMMUTABLE, 1),
		new NybbleTupleDescriptor(SHARED, 1)
	};

	@Override
	NybbleTupleDescriptor mutable ()
	{
		return descriptors[
			((8 - unusedNybblesOfLastWord) & 7) * 3 + MUTABLE.ordinal()];
	}

	@Override
	NybbleTupleDescriptor immutable ()
	{
		return descriptors[
			((8 - unusedNybblesOfLastWord) & 7) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	NybbleTupleDescriptor shared ()
	{
		return descriptors[
			((8 - unusedNybblesOfLastWord) & 7) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer a mutable copy of object that holds bytes, as opposed to just
	 * nybbles.
	 *
	 * @param object
	 *         A {@linkplain NybbleTupleDescriptor nybble tuple} to copy as a
	 *         {@linkplain ByteTupleDescriptor byte tuple}.
	 * @return
	 *         A new {@linkplain ByteTupleDescriptor byte tuple} with the same
	 *         sequence of integers as the argument.
	 */
	private A_Tuple copyAsMutableByteTuple (final AvailObject object)
	{
		final A_Tuple result =
			ByteTupleDescriptor.mutableObjectOfSize(object.tupleSize());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = result.tupleSize(); i <= end; i++)
		{
			result.rawByteAtPut(i, getNybble(object, i));
		}
		return result;
	}

	/**
	 * Build a new object instance with room for size elements.
	 *
	 * @param size The number of elements for which to leave room.
	 * @return A mutable {@linkplain NybbleTupleDescriptor nybble tuple}.
	 */
	public static AvailObject mutableObjectOfSize (final int size)
	{
		final NybbleTupleDescriptor d = descriptorFor(MUTABLE, size);
		assert (size + d.unusedNybblesOfLastWord & 7) == 0;
		final AvailObject result = d.create((size + 7) >>> 3);
		return result;
	}

	/**
	 * Answer the descriptor that has the specified mutability flag and is
	 * suitable to describe a tuple with the given number of elements.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        How many elements are in a tuple to be represented by the
	 *        descriptor.
	 * @return
	 *        A {@link NybbleTupleDescriptor} suitable for representing a
	 *        nybble tuple of the given mutability and {@linkplain
	 *        AvailObject#tupleSize() size}.
	 */
	private static NybbleTupleDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 7) * 3 + flag.ordinal()];
	}
}
