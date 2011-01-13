/**
 * descriptor/NybbleTupleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.descriptor.TypeDescriptor.Types;

public class NybbleTupleDescriptor extends TupleDescriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH_OR_ZERO,
		RAW_QUAD_AT_
	}
	int unusedNybblesOfLastWord;


	// GENERATED accessors

	@Override
	public int o_RawQuadAt (
			final AvailObject object,
			final int subscript)
	{
		return object.integerSlotAt(IntegerSlots.RAW_QUAD_AT_, subscript);
	}

	@Override
	public void o_RawQuadAtPut (
			final AvailObject object,
			final int subscript,
			final int value)
	{
		object.integerSlotAtPut(IntegerSlots.RAW_QUAD_AT_, subscript, value);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		if (object.tupleSize() == 0)
		{
			aStream.append("<>");
			return;
		}
		if (isMutable())
		{
			aStream.append("(mut)");
		}
		aStream.append("NybbleTuple with: #[");
		int rowSize = max(60 - indent * 3 / 2, 8);
		//  How many equal (shorter by at least 1 on the last) rows are needed?
		final int rows = (object.tupleSize() + rowSize) / rowSize;
		//  How many on each row for that layout?
		rowSize = (object.tupleSize() + rows) / rows;
		//  Round up to a multiple of eight per row.
		rowSize = (rowSize + 7) / 8 * 8;
		int rowStart = 1;
		while (rowStart <= object.tupleSize()) {
			aStream.append('\n');
			for (int _count1 = 1; _count1 <= indent; _count1++)
			{
				aStream.append('\t');
			}
			for (
					int
						i = rowStart,
						_end2 = min(rowStart + rowSize - 1, object.tupleSize());
					i <= _end2;
					i++)
			{
				final byte val = object.extractNybbleFromTupleAt(i);
				assert 0 <= val && val <= 15;
				aStream.append(Integer.toHexString(val));
				if (i % 8 == 0)
				{
					aStream.append(' ');
				}
			}
			rowStart += rowSize;
		}
		aStream.append(']');
	}



	// operations

	@Override
	public boolean o_CompareFromToWithStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anotherObject,
			final int startIndex2)
	{
		//  Compare sections of two tuples.

		return anotherObject.compareFromToWithNybbleTupleStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aNybbleTuple,
			final int startIndex2)
	{
		//  Compare sections of two nybble tuples.

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
			if (object.rawNybbleAt(i) != aNybbleTuple.rawNybbleAt(index2))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsNybbleTuple(object);
	}

	@Override
	public boolean o_EqualsNybbleTuple (
			final AvailObject object,
			final AvailObject aNybbleTuple)
	{
		//  First, check for object-structure (address) identity.

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
		//  They're equal (but occupy disjoint storage).  Replace one with an indirection to the other
		//  to reduce storage costs and the frequency of nybble-wise comparisons.
		object.becomeIndirectionTo(aNybbleTuple);
		aNybbleTuple.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
			final AvailObject object,
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  Currently there is no more desirable representation than a nybble tuple
		return true;
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aType.equals(Types.voidType.object()))
		{
			return true;
		}
		if (aType.equals(Types.all.object()))
		{
			return true;
		}
		if (!aType.isTupleType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.tupleSize());
		if (!size.isInstanceOfSubtypeOf(aType.sizeRange()))
		{
			return false;
		}
		//  tuple's size is out of range.
		final AvailObject typeTuple = aType.typeTuple();
		final int breakIndex = min(object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOfSubtypeOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aType.defaultType();
		if (IntegerRangeTypeDescriptor.nybbles().isSubtypeOf(defaultTypeObject))
		{
			return true;
		}
		for (int i = breakIndex + 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (!object.tupleAt(i).isInstanceOfSubtypeOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public void o_RawNybbleAtPut (
			final AvailObject object,
			final int nybbleIndex,
			final byte aNybble)
	{
		//  Set the nybble at the given index.  Use little Endian.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		assert aNybble >= 0 && aNybble <= 15;
		object.checkWriteForField(IntegerSlots.RAW_QUAD_AT_);
		object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		int word = object.integerSlotAt(IntegerSlots.RAW_QUAD_AT_, wordIndex);
		final int leftShift = (nybbleIndex - 1 & 7) * 4;
		word &= ~(0x0F << leftShift);
		word |= aNybble << leftShift;
		object.integerSlotAtPut(IntegerSlots.RAW_QUAD_AT_, wordIndex, word);
	}

	@Override
	public AvailObject o_MakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor(NybbleTupleDescriptor.isMutableSize(false, object.tupleSize()));
			object.makeSubobjectsImmutable();
		}
		return object;
	}



	// operations-tuples

	@Override
	public byte o_ExtractNybbleFromTupleAt (
			final AvailObject object,
			final int nybbleIndex)
	{
		// Get the element at the given index in the tuple object, and extract
		// a nybble from it.  Fail if it's not a nybble.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		final int word = object.integerSlotAt(IntegerSlots.RAW_QUAD_AT_, wordIndex);
		final int shift = (nybbleIndex - 1 & 7) * 4;
		return (byte) (word>>>shift & 0x0F);
	}

	@Override
	public short o_RawByteAt (
			final AvailObject object,
			final int byteIndex)
	{
		// Answer the byte at the given byte-index.  This is actually two
		// nybbles packed together.  Use little endian.
		return object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, byteIndex);
	}

	@Override
	public void o_RawByteAtPut (
			final AvailObject object,
			final int byteIndex,
			final short anInteger)
	{
		// Set the byte at the given byte-index.  This is actually two nybbles
		// packed together.  Use little endian.
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, byteIndex, anInteger);
	}

	@Override
	public byte o_RawNybbleAt (
			final AvailObject object,
			final int nybbleIndex)
	{
		// Answer the nybble at the given index in the nybble tuple object.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		final int word = object.integerSlotAt(IntegerSlots.RAW_QUAD_AT_, wordIndex);
		final int shift = (nybbleIndex - 1 & 7) * 4;
		return (byte) (word>>>shift & 0x0F);
	}

	@Override
	public AvailObject o_TupleAt (
			final AvailObject object,
			final int index)
	{
		//  Answer the element at the given index in the nybble tuple object.

		return IntegerDescriptor.objectFromByte(object.rawNybbleAt(index));
	}

	@Override
	public void o_TupleAtPut (
			final AvailObject object,
			final int index,
			final AvailObject aNybbleObject)
	{
		//  Set the nybble at the given index to the given object (which should be an AvailObject that's an integer 0<=n<=15).

		object.rawNybbleAtPut(index, aNybbleObject.extractNybble());
	}

	@Override
	public AvailObject o_TupleAtPuttingCanDestroy (
			final AvailObject object,
			final int nybbleIndex,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		if (!newValueObject.isNybble())
		{
			if (newValueObject.isByte())
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
		if (!canDestroy || !isMutable)
		{
			return copyAsMutableByteTuple(object).tupleAtPuttingCanDestroy(
				nybbleIndex,
				newValueObject,
				true);
		}
		//  Ok, clobber the object in place...
		object.rawNybbleAtPut(nybbleIndex, newValueObject.extractNybble());
		object.hashOrZero(0);
		//  ...invalidate the hash value.  Probably cheaper than computing the difference or even testing for an actual change.
		return object;
	}

	@Override
	public int o_TupleIntAt (
			final AvailObject object,
			final int index)
	{
		//  Answer the integer element at the given index in the nybble tuple object.

		return object.rawNybbleAt(index);
	}

	@Override
	public int o_TupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		return (object.integerSlotsCount() - numberOfFixedIntegerSlots()) * 8 - unusedNybblesOfLastWord;
	}



	// private-accessing

	@Override
	public int o_BitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 4;
	}

	/**
	 * Set how many unused nybbles that this descriptor leaves in the last
	 * word.
	 *
	 * @param anInteger The number of unused nybbles in the last word of all of
	 *                  this descriptor's objects.
	 */
	void unusedNybblesOfLastWord (
			final int anInteger)
	{
		unusedNybblesOfLastWord = anInteger;
	}



	// private-computation

	@Override
	public int o_ComputeHashFromTo (
			final AvailObject object,
			final int start,
			final int end)
	{
		//  See comment in superclass.  This method must produce the same value.
		//  This could eventually be rewritten to do byte at a time (table lookup) and to
		//  use the square of the current multiplier.

		int hash = 0;
		for (int nybbleIndex = end; nybbleIndex >= start; nybbleIndex--)
		{
			final int itemHash = IntegerDescriptor.hashOfByte(object.rawNybbleAt(nybbleIndex)) ^ PreToggle;
			hash = hash * Multiplier + itemHash;
		}
		return hash * Multiplier;
	}


	/**
	 * Answer a mutable copy of object that holds bytes, as opposed to just
	 * nybbles.
	 */
	AvailObject copyAsMutableByteTuple (
			final AvailObject object)
	{
		final AvailObject result =
			ByteTupleDescriptor.isMutableSize(true, object.tupleSize()).create(
				(object.tupleSize() + 3) / 4);
		// Transfer the leading information (the stuff before the tuple's first
		// element).
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = result.tupleSize(); i <= _end1; i++)
		{
			result.rawByteAtPut(i, object.rawNybbleAt(i));
		}
		return result;
	}


	/**
	 * Build a new object instance with room for size elements.
	 *
	 * @param size The number of elements for which to leave room.
	 * @return A mutable {@linkplain NybbleTupleDescriptor nybble tuple}.
	 */
	AvailObject mutableObjectOfSize (
			final int size)
	{
		if (!isMutable)
		{
			error("This descriptor should be mutable");
			return VoidDescriptor.voidObject();
		}
		assert (size + unusedNybblesOfLastWord & 7) == 0;
		final AvailObject result = this.create(((size + 7) / 8));
		return result;
	}


	public static NybbleTupleDescriptor isMutableSize(
		final boolean flag,
		final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors[(size & 7) * 2 + delta];
	};

	/**
	 * Construct a new {@link NybbleTupleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param unusedNybbles The number of unused nybbles of the last word.
	 */
	protected NybbleTupleDescriptor (
		final boolean isMutable,
		final int unusedNybbles)
	{
		super(isMutable);
		unusedNybblesOfLastWord = unusedNybbles;
	}

	final static NybbleTupleDescriptor descriptors[] = {
		new NybbleTupleDescriptor(true, 0),
		new NybbleTupleDescriptor(false, 0),
		new NybbleTupleDescriptor(true, 7),
		new NybbleTupleDescriptor(false, 7),
		new NybbleTupleDescriptor(true, 6),
		new NybbleTupleDescriptor(false, 6),
		new NybbleTupleDescriptor(true, 5),
		new NybbleTupleDescriptor(false, 5),
		new NybbleTupleDescriptor(true, 4),
		new NybbleTupleDescriptor(false, 4),
		new NybbleTupleDescriptor(true, 3),
		new NybbleTupleDescriptor(false, 3),
		new NybbleTupleDescriptor(true, 2),
		new NybbleTupleDescriptor(false, 2),
		new NybbleTupleDescriptor(true, 1),
		new NybbleTupleDescriptor(false, 1)
	};

}
