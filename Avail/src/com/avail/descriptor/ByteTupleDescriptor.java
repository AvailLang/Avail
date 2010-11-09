/**
 * descriptor/ByteTupleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteTupleDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;

@IntegerSlots({
	"hashOrZero", 
	"rawQuadAt#"
})
public class ByteTupleDescriptor extends TupleDescriptor
{
	int _unusedBytesOfLastWord;


	// GENERATED accessors

	int ObjectRawQuadAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.integerSlotAtByteIndex(((index * 4) + 4));
	}

	void ObjectRawQuadAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED setter method (indexed).

		object.integerSlotAtByteIndexPut(((index * 4) + 4), value);
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		if (isMutable())
		{
			aStream.append("(mut) ");
		}
		aStream.append("ByteTuple with: #[");
		int rowSize = max ((30 - ((indent * 3) / 2)), 8);
		rowSize = (((rowSize + 3) / 4) * 4);
		//  How many equal (shorter by at least 1 on the last) rows are needed?
		final int rows = ((object.tupleSize() + rowSize) / rowSize);
		//  How many on each row for that layout?
		rowSize = ((object.tupleSize() + rows) / rows);
		//  Round up to a multiple of four per row.
		rowSize = (((rowSize + 3) / 4) * 4);
		int rowStart = 1;
		while ((rowStart <= object.tupleSize())) {
			aStream.append('\n');
			for (int _count1 = 1; _count1 <= indent; _count1++)
			{
				aStream.append('\t');
			}
			for (int i = rowStart, _end2 = min (((rowStart + rowSize) - 1), object.tupleSize()); i <= _end2; i++)
			{
				final short val = object.tupleAt(i).extractByte();
				aStream.append(Integer.toHexString(val >> 4));
				aStream.append(Integer.toHexString(val & 15));
				if (((i % 4) == 0))
				{
					aStream.append(' ');
				}
				aStream.append(' ');
			}
			rowStart += rowSize;
		}
		aStream.append(']');
	}



	// operations

	boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.

		return anotherObject.compareFromToWithByteTupleStartingAt(
			startIndex2,
			((startIndex2 + endIndex1) - startIndex1),
			object,
			startIndex1);
	}

	boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteTuple, 
			final int startIndex2)
	{
		//  Compare sections of two byte tuples.

		if ((object.sameAddressAs(aByteTuple) && (startIndex1 == startIndex2)))
		{
			return true;
		}
		//  Compare actual bytes.
		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (! (object.rawByteAt(index1) == aByteTuple.rawByteAt(index2)))
			{
				return false;
			}
			++index2;
		}
		return true;
	}

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsByteTuple(object);
	}

	boolean ObjectEqualsByteTuple (
			final AvailObject object, 
			final AvailObject aByteTuple)
	{
		//  First, check for object-structure (address) identity.

		if (object.sameAddressAs(aByteTuple))
		{
			return true;
		}
		if (! (object.tupleSize() == aByteTuple.tupleSize()))
		{
			return false;
		}
		if (! (object.hash() == aByteTuple.hash()))
		{
			return false;
		}
		if (! object.compareFromToWithByteTupleStartingAt(
			1,
			object.tupleSize(),
			aByteTuple,
			1))
		{
			return false;
		}
		//  They're equal (but occupy disjoint storage).  Replace one with an indirection to the other
		//  to keep down the frequency of byte-wise comparisons.
		object.becomeIndirectionTo(aByteTuple);
		aByteTuple.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aType.equals(TypeDescriptor.voidType()))
		{
			return true;
		}
		if (aType.equals(TypeDescriptor.all()))
		{
			return true;
		}
		if (! aType.isTupleType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.tupleSize());
		if (! size.isInstanceOfSubtypeOf(aType.sizeRange()))
		{
			return false;
		}
		//  tuple's size is out of range.
		final AvailObject typeTuple = aType.typeTuple();
		final int breakIndex = min (object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (! object.tupleAt(i).isInstanceOfSubtypeOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aType.defaultType();
		if (IntegerRangeTypeDescriptor.bytes().isSubtypeOf(defaultTypeObject))
		{
			return true;
		}
		for (int i = (breakIndex + 1), _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (! object.tupleAt(i).isInstanceOfSubtypeOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (_isMutable)
		{
			object.descriptor(ByteTupleDescriptor.isMutableSize(false, object.tupleSize()));
			object.makeSubobjectsImmutable();
		}
		return object;
	}



	// operations-tuples

	short ObjectRawByteAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the byte at the given index.

		return object.byteSlotAtByteIndex((((_numberOfFixedIntegerSlots * 4) + index) + 3));
	}

	void ObjectRawByteAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  Set the byte at the given index.

		object.byteSlotAtByteIndexPut((((_numberOfFixedIntegerSlots * 4) + index) + 3), anInteger);
	}

	AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the element at the given index in the tuple object.

		if (! ((index >= 1) && (index <= object.tupleSize())))
		{
			error("index out of bounds", object);
			return VoidDescriptor.voidObject();
		}
		return IntegerDescriptor.objectFromByte(object.byteSlotAtByteIndex((((_numberOfFixedIntegerSlots * 4) + index) + 3)));
	}

	void ObjectTupleAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject aByteObject)
	{
		//  Set the byte at the given index to the given object (which should be an AvailObject that's an integer 0<=n<=255.
		//
		//  [index between: 1 and: object tupleSize] assert.

		object.byteSlotAtByteIndexPut((((numberOfFixedIntegerSlots() * 4) + index) + 3), ((byte)(aByteObject.extractByte())));
	}

	AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		assert ((index >= 1) && (index <= object.tupleSize()));
		if (! newValueObject.isByte())
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		if (! (canDestroy & _isMutable))
		{
			return copyAsMutableByteTuple(object).tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		//  Ok, clobber the object in place...
		object.rawByteAtPut(index, ((byte)(newValueObject.extractByte())));
		object.hashOrZero(0);
		//  ...invalidate the hash value.
		return object;
	}

	int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		return object.byteSlotAtByteIndex((((numberOfFixedIntegerSlots() * 4) + index) + 3));
	}



	// private-accessing

	int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 8;
	}

	void unusedBytesOfLastWord (
			final int anInteger)
	{
		//  Set unusedBytesOfLastWord in this descriptor instance.

		_unusedBytesOfLastWord = anInteger;
	}



	// private-computation

	int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int start, 
			final int end)
	{
		//  See comment in superclass.  This method must produce the same value.

		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = (IntegerDescriptor.hashOfByte(object.rawByteAt(index)) ^ PreToggle);
			hash = ((TupleDescriptor.multiplierTimes(hash) + itemHash) & HashMask);
		}
		return (TupleDescriptor.multiplierTimes(hash) & HashMask);
	}



	// private-copying

	AvailObject copyAsMutableByteTuple (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that also only holds bytes.

		final AvailObject result = AvailObject.newIndexedDescriptor(((object.tupleSize() + 3) / 4), ByteTupleDescriptor.isMutableSize(true, object.tupleSize()));
		assert (result.integerSlotsCount() == object.integerSlotsCount());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result.rawByteAtPut(i, object.rawByteAt(i));
		}
		return result;
	}

	int ObjectTupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		return (((object.integerSlotsCount() - _numberOfFixedIntegerSlots) * 4) - _unusedBytesOfLastWord);
	}

	AvailObject privateMutableObjectOfSize (
			final int size)
	{
		//  Build a new object instance with room for size elements.

		if (! _isMutable)
		{
			error("This descriptor should be mutable");
			return VoidDescriptor.voidObject();
		}
		assert (((size + _unusedBytesOfLastWord) & 3) == 0);
		final AvailObject result = AvailObject.newIndexedDescriptor(((size + 3) / 4), this);
		return result;
	}





	/* Descriptor lookup */
	public static ByteTupleDescriptor isMutableSize(boolean flag, int size)
	{
		int delta = (flag ? 0 : 1);
		return (ByteTupleDescriptor) AllDescriptors [16 + delta + ((size & 3) * 2)];
	};

	//  Descriptor initialization
	Descriptor initDescriptorWithUnusedBytes(
			int theId,
			boolean mut,
			int numFixedObjectSlots,
			int numFixedIntegerSlots,
			boolean hasVariableObjectSlots,
			boolean hasVariableIntegerSlots,
			int unusedBytes)
	{
		initDescriptor(
			theId,
			mut,
			numFixedObjectSlots,
			numFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
		_unusedBytesOfLastWord = unusedBytes;
		return this;
	};

}
