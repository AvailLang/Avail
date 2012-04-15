/**
 * NybbleTupleDescriptor.java
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

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.*;

/**
 * {@code NybbleTupleDescriptor} represents a tuple of integers that happen to
 * fall in the range 0..15.  They are packed eight per {@code int}.
 *
 * <p>
 * This representation is particularly useful for {@linkplain
 * CompiledCodeDescriptor compiled code}, which uses nybblecodes.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class NybbleTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash of the tuple or zero.  In the rare case that the hash is
		 * actually zero, it will have to be recalculated each time it is
		 * requested.
		 */
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
	 * The number of nybbles of the last {@linkplain IntegerSlots#RAW_QUAD_AT_
	 * integer slot} that are not considered part of the tuple.
	 */
	int unusedNybblesOfLastWord;

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
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
		while (rowStart <= object.tupleSize())
		{
			aStream.append('\n');
			for (int _count1 = 1; _count1 <= indent; _count1++)
			{
				aStream.append('\t');
			}
			for (
					int
						i = rowStart,
						end = min(rowStart + rowSize - 1, object.tupleSize());
					i <= end;
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

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anotherObject,
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
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aNybbleTuple,
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
			if (object.rawNybbleAt(i) != aNybbleTuple.rawNybbleAt(index2))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsNybbleTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNybbleTuple)
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
		// They're equal (but occupy disjoint storage).  Replace one with an
		// indirection to the other to reduce storage costs and the frequency
		// of nybble-wise comparisons.
		object.becomeIndirectionTo(aNybbleTuple);
		aNybbleTuple.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than the
		// second one?  Currently there is no more desirable representation than
		// a nybble tuple.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		if (aType.equals(TOP.o()))
		{
			return true;
		}
		if (aType.equals(ANY.o()))
		{
			return true;
		}
		if (!aType.isTupleType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.fromInt(object.tupleSize());
		if (!size.isInstanceOf(aType.sizeRange()))
		{
			return false;
		}
		//  tuple's size is out of range.
		final AvailObject typeTuple = aType.typeTuple();
		final int breakIndex = min(object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aType.defaultType();
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
		final @NotNull AvailObject object,
		final int nybbleIndex,
		final byte aNybble)
	{
		//  Set the nybble at the given index.  Use little Endian.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		assert aNybble >= 0 && aNybble <= 15;
		object.checkWriteForField(IntegerSlots.RAW_QUAD_AT_);
		// object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		int word = object.slot(IntegerSlots.RAW_QUAD_AT_, wordIndex);
		final int leftShift = (nybbleIndex - 1 & 7) * 4;
		word &= ~(0x0F << leftShift);
		word |= aNybble << leftShift;
		object.setSlot(IntegerSlots.RAW_QUAD_AT_, wordIndex, word);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.descriptor = isMutableSize(false, object.tupleSize());
		}
		return object;
	}

	@Override @AvailMethod
	byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int nybbleIndex)
	{
		// Get the element at the given index in the tuple object, and extract
		// a nybble from it.  Fail if it's not a nybble.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		// object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		final int word = object.slot(
			IntegerSlots.RAW_QUAD_AT_,
			wordIndex);
		final int shift = (nybbleIndex - 1 & 7) * 4;
		return (byte) (word>>>shift & 0x0F);
	}

	@Override @AvailMethod
	short o_RawByteAt (
		final @NotNull AvailObject object,
		final int byteIndex)
	{
		// Answer the byte at the given byte-index.  This is actually two
		// nybbles packed together.  Use little endian.
		return object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, byteIndex);
	}

	@Override @AvailMethod
	void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int byteIndex,
		final short anInteger)
	{
		// Set the byte at the given byte-index.  This is actually two nybbles
		// packed together.  Use little endian.
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, byteIndex, anInteger);
	}

	@Override @AvailMethod
	byte o_RawNybbleAt (
		final @NotNull AvailObject object,
		final int nybbleIndex)
	{
		// Answer the nybble at the given index in the nybble tuple object.
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		// object.verifyToSpaceAddress();
		final int wordIndex = (nybbleIndex + 7) / 8;
		final int word = object.slot(IntegerSlots.RAW_QUAD_AT_, wordIndex);
		final int shift = (nybbleIndex - 1 & 7) * 4;
		return (byte) (word>>>shift & 0x0F);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the element at the given index in the nybble tuple object.

		return IntegerDescriptor.fromUnsignedByte(object.rawNybbleAt(index));
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject aNybbleObject)
	{
		//  Set the nybble at the given index to the given object (which should be an AvailObject that's an integer 0<=n<=15).

		object.rawNybbleAtPut(index, aNybbleObject.extractNybble());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int nybbleIndex,
		final @NotNull AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

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
		//  ...invalidate the hash value.  Probably cheaper than computing the
		// difference or even testing for an actual change.
		return object;
	}

	@Override @AvailMethod
	int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the integer element at the given index in the nybble tuple object.

		return object.rawNybbleAt(index);
	}

	@Override @AvailMethod
	int o_TupleSize (
		final @NotNull AvailObject object)
	{
		return object.variableIntegerSlotsCount() * 8 - unusedNybblesOfLastWord;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 4;
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass.  This method must produce the same value.
		// This could eventually be rewritten to do a byte at a time (table
		// lookup) and to use the square of the current multiplier.

		int hash = 0;
		for (int nybbleIndex = end; nybbleIndex >= start; nybbleIndex--)
		{
			int itemHash = IntegerDescriptor.hashOfUnsignedByte(
				object.rawNybbleAt(nybbleIndex));
			itemHash ^= PreToggle;
			hash = hash * Multiplier + itemHash;
		}
		return hash * Multiplier;
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

	/**
	 * Answer a mutable copy of object that holds bytes, as opposed to just
	 * nybbles.
	 *
	 * @param object
	 *            A {@linkplain NybbleTupleDescriptor nybble tuple} to copy as a
	 *            {@linkplain ByteTupleDescriptor byte tuple}.
	 * @return
	 *            A new {@linkplain ByteTupleDescriptor byte tuple} with the
	 *            same sequence of integers as the argument.
	 */
	AvailObject copyAsMutableByteTuple (
		final @NotNull AvailObject object)
	{
		final AvailObject result =
			ByteTupleDescriptor.mutableObjectOfSize(object.tupleSize());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = result.tupleSize(); i <= end; i++)
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
	public static AvailObject mutableObjectOfSize (
		final int size)
	{
		final NybbleTupleDescriptor descriptor = isMutableSize(true, size);
		assert (size + descriptor.unusedNybblesOfLastWord & 7) == 0;
		final AvailObject result = descriptor.create((size + 7) / 8);
		return result;
	}

	/**
	 * Answer the descriptor that has the specified mutability flag and is
	 * suitable to describe a tuple with the given number of elements.
	 *
	 * @param flag
	 *            Whether the requested descriptor should be mutable.
	 * @param size
	 *            How many elements are in a tuple to be represented by the
	 *            descriptor.
	 * @return
	 *            A {@link NybbleTupleDescriptor} suitable for representing a
	 *            nybble tuple of the given mutability and {@link
	 *            AvailObject#tupleSize() size}.
	 */
	private static NybbleTupleDescriptor isMutableSize (
		final boolean flag,
		final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors[(size & 7) * 2 + delta];
	}

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

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * {@link #isMutableSize(boolean, int)} can find them by mutability and
	 * number of unused nybbles in the last word.
	 */
	static final NybbleTupleDescriptor descriptors[] =
	{
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
