/**
 * ByteTupleDescriptor.java
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

import static com.avail.descriptor.ByteTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.nio.ByteBuffer;
import com.avail.annotations.*;

/**
 * {@code ByteTupleDescriptor} represents a tuple of integers that happen to
 * fall in the range 0..255.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class ByteTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/**
		 * The raw 32-bit machine words that constitute the representation of
		 * the {@linkplain ByteTupleDescriptor byte tuple}.
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
	 * an new tuple.
	 */
	private static final int maximumCopySize = 32;

	/**
	 * The number of bytes of the last {@code int} that do not participate in
	 * the representation of the {@linkplain ByteTupleDescriptor byte tuple}.
	 * Must be between 0 and 3.
	 */
	private final int unusedBytesOfLastWord;

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithByteTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aByteTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual bytes.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (object.rawByteAt(index1) != aByteTuple.rawByteAt(index2))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.equalsByteTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aByteTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteTuple))
		{
			return true;
		}
		if (object.tupleSize() != aByteTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithByteTupleStartingAt(
			1,
			object.tupleSize(),
			aByteTuple,
			1))
		{
			return false;
		}
		// They're equal (but occupy disjoint storage). If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
		if (!isShared())
		{
			aByteTuple.makeImmutable();
			object.becomeIndirectionTo(aByteTuple);
		}
		else if (!aByteTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aByteTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsByteTuple (final AvailObject object)
	{
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
		//  See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesInt(object.tupleSize()))
		{
			return false;
		}
		//  tuple's size is out of range.
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
		if (IntegerRangeTypeDescriptor.bytes().isSubtypeOf(defaultTypeObject))
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
	short o_RawByteAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the byte at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlotAt(RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawByteAtPut (
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		//  Set the byte at the given index.
		assert index >= 1 && index <= object.tupleSize();
		object.byteSlotAtPut(RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		return (AvailObject)IntegerDescriptor.fromUnsignedByte(
			object.byteSlotAt(RAW_QUAD_AT_, index));
	}

	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.

		assert index >= 1 && index <= object.tupleSize();
		if (!newValueObject.isUnsignedByte())
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		final AvailObject result = canDestroy && isMutable()
			? object
			: newLike(mutable(), object, 0, 0);
		result.rawByteAtPut(
			index,
			((A_Number)newValueObject).extractUnsignedByte());
		result.hashOrZero(0);
		return result;
	}

	@Override @AvailMethod
	int o_TupleIntAt (
		final AvailObject object,
		final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		return object.byteSlotAt(RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int tupleSize = object.tupleSize();
		if (tupleSize > 0 && tupleSize < maximumCopySize)
		{
			// It's not empty and it's reasonably small.
			final AvailObject result = mutableObjectOfSize(tupleSize);
			for (int dest = 1, src = tupleSize; src > 0; dest++, src--)
			{
				result.byteSlotAtPut(
					RAW_QUAD_AT_,
					dest,
					object.byteSlotAt(RAW_QUAD_AT_, src));
			}
			return result;
		}
		return super.o_TupleReverse(object);
	}

	@Override @AvailMethod
	int o_BitsPerEntry (
		final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass. This method must produce the same value.
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = IntegerDescriptor.hashOfUnsignedByte(
				object.rawByteAt(index)) ^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return object.variableIntegerSlotsCount() * 4 - unusedBytesOfLastWord;
	}

	@Override
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
		if (otherTuple.isByteTuple() && newSize <= 64)
		{
			// Copy the bytes.
			final int newWordCount = (newSize + 3) >>> 2;
			final int deltaSlots =
				newWordCount - object.variableIntegerSlotsCount();
			final AvailObject result;
			if (canDestroy && isMutable() && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				result = object;
				result.descriptor = descriptorFor(MUTABLE, newSize);
			}
			else
			{
				result = newLike(
					descriptorFor(MUTABLE, newSize), object, 0, deltaSlots);
			}
			int dest = size1 + 1;
			for (int src = 1; src <= size2; src++, dest++)
			{
				result.byteSlotAtPut(
					RAW_QUAD_AT_,
					dest,
					otherTuple.rawByteAt(src));
			}
			result.setSlot(HASH_OR_ZERO, 0);
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
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			final AvailObject result = mutableObjectOfSize(size);
			int dest = 1;
			for (int src = start; src <= end; src++, dest++)
			{
				result.byteSlotAtPut(
					RAW_QUAD_AT_,
					dest,
					object.byteSlotAt(RAW_QUAD_AT_, src));
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
			outputByteBuffer.put((byte) object.byteSlotAt(RAW_QUAD_AT_, index));
		}
	}

	/**
	 * Construct a new {@link ByteTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedBytes
	 *        The number of unused bytes of the last word.
	 */
	private ByteTupleDescriptor (
		final Mutability mutability,
		final int unusedBytes)
	{
		super(mutability, null, IntegerSlots.class);
		unusedBytesOfLastWord = unusedBytes;
	}

	/** The {@link ByteTupleDescriptor} instances. */
	private static final ByteTupleDescriptor[] descriptors =
	{
		new ByteTupleDescriptor(MUTABLE, 0),
		new ByteTupleDescriptor(IMMUTABLE, 0),
		new ByteTupleDescriptor(SHARED, 0),
		new ByteTupleDescriptor(MUTABLE, 3),
		new ByteTupleDescriptor(IMMUTABLE, 3),
		new ByteTupleDescriptor(SHARED, 3),
		new ByteTupleDescriptor(MUTABLE, 2),
		new ByteTupleDescriptor(IMMUTABLE, 2),
		new ByteTupleDescriptor(SHARED, 2),
		new ByteTupleDescriptor(MUTABLE, 1),
		new ByteTupleDescriptor(IMMUTABLE, 1),
		new ByteTupleDescriptor(SHARED, 1)
	};

	@Override
	ByteTupleDescriptor mutable ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + MUTABLE.ordinal()];
	}

	@Override
	ByteTupleDescriptor immutable ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	ByteTupleDescriptor shared ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer the appropriate {@linkplain ByteTupleDescriptor descriptor} to
	 * represent an {@linkplain AvailObject object} of the specified mutability
	 * and size.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        The desired number of elements.
	 * @return A {@linkplain ByteTupleDescriptor descriptor}.
	 */
	private static ByteTupleDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		final int delta = flag.ordinal();
		return descriptors[(size & 3) * 3 + delta];
	}

	/**
	 * Build a mutable {@linkplain ByteTupleDescriptor byte tuple} with room for
	 * the specified number of elements.
	 *
	 * @param size The number of bytes in the resulting tuple.
	 * @return A byte tuple with the specified number of bytes (initially zero).
	 */
	public static AvailObject mutableObjectOfSize (final int size)
	{
		final ByteTupleDescriptor descriptor = descriptorFor(MUTABLE, size);
		assert (size + descriptor.unusedBytesOfLastWord & 3) == 0;
		return descriptor.create(size + 3 >> 2);
	}
}
