/**
 * descriptor/ByteStringDescriptor.java
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

import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.min;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;

public class ByteStringDescriptor extends TupleDescriptor
{

	public enum IntegerSlots
	{
		HASH_OR_ZERO,
		RAW_QUAD_AT_
	}
	final int _unusedBytesOfLastWord;


	// GENERATED accessors

	@Override
	public int o_RawQuadAt (
			final AvailObject object,
			final int subscript)
	{
		//  GENERATED getter method (indexed).

		return object.integerSlotAt(IntegerSlots.RAW_QUAD_AT_, subscript);
	}

	@Override
	public void o_RawQuadAtPut (
			final AvailObject object,
			final int subscript,
			final int value)
	{
		//  GENERATED setter method (indexed).

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
		aStream.append('"');
		for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
		{
			aStream.append((char) object.rawByteForCharacterAt(i));
		}
		aStream.append('"');
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
		//  Compare sections of two tuples.  My instance is a string.

		return anotherObject.compareFromToWithByteStringStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override
	public boolean o_CompareFromToWithByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aByteString,
			final int startIndex2)
	{
		//  Compare sections of two byte strings.

		if (object.sameAddressAs(aByteString) && startIndex1 == startIndex2)
		{
			return true;
		}
		//  Compare actual bytes.
		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (object.rawByteForCharacterAt(index1) != aByteString.rawByteForCharacterAt(index2))
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
		return another.equalsByteString(object);
	}

	@Override
	public boolean o_EqualsByteString (
			final AvailObject object,
			final AvailObject aByteString)
	{
		//  First, check for object-structure (address) identity.

		if (object.sameAddressAs(aByteString))
		{
			return true;
		}
		if (object.tupleSize() != aByteString.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteString.hash())
		{
			return false;
		}
		if (!object.compareFromToWithByteStringStartingAt(
			1,
			object.tupleSize(),
			aByteString,
			1))
		{
			return false;
		}
		//  They're equal (but occupy disjoint storage).  Replace one with an indirection to the other
		//  to keep down the frequency of byte-wise comparisons.
		object.becomeIndirectionTo(aByteString);
		aByteString.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean o_IsString (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.  Note that because object is a string,
		//  it is already known that each element is a character.

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
		//
		//  Make sure the element types accept character, up to my actual size.
		final AvailObject typeTuple = aType.typeTuple();
		final int limit = min(object.tupleSize(), (typeTuple.tupleSize() + 1));
		for (int i = 1; i <= limit; i++)
		{
			if (!Types.character.object().isSubtypeOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public AvailObject o_MakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor(ByteStringDescriptor.isMutableSize(false, object.tupleSize()));
			object.makeSubobjectsImmutable();
		}
		return object;
	}



	// operations-tuples

	@Override
	public short o_RawByteForCharacterAt (
			final AvailObject object,
			final int index)
	{
		//  Answer the byte that encodes the character at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
	}

	@Override
	public void o_RawByteForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		//  Set the character at the given index based on the given byte.
		assert index >= 1 && index <= object.tupleSize();
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, anInteger);
	}

	@Override
	public AvailObject o_TupleAt (
			final AvailObject object,
			final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
		return CharacterDescriptor.newImmutableCharacterWithByteCodePoint(
			codePoint);
	}

	@Override
	public void o_TupleAtPut (
			final AvailObject object,
			final int index,
			final AvailObject aCharacterObject)
	{
		// Set the byte at the given index to the given object (which should be
		// an AvailObject that's a one-byte character).
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = (short) aCharacterObject.codePoint();
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, codePoint);
	}

	@Override
	public AvailObject o_TupleAtPuttingCanDestroy (
			final AvailObject object,
			final int index,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isCharacter())
		{
			final int codePoint = newValueObject.codePoint();
			if (codePoint >= 0 && codePoint <= 255)
			{
				if (canDestroy & isMutable)
				{
					object.rawByteForCharacterAtPut(index, (short)codePoint);
					object.hashOrZero(0);
					return object;
				}
				//  Clone it then modify the copy in place.
				return copyAsMutableByteString(object).tupleAtPuttingCanDestroy(
					index,
					newValueObject,
					true);
			}
			if (codePoint >= 0 && codePoint <= 0xFFFF)
			{
				return copyAsMutableTwoByteString(object).tupleAtPuttingCanDestroy(
					index,
					newValueObject,
					true);
			}
		}
		//  Convert to an arbitrary Tuple instead.
		return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			true);
	}

	@Override
	public int o_TupleIntAt (
			final AvailObject object,
			final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		error("Strings store characters, not integers", object);
		return 0;
	}



	// private-accessing

	@Override
	public int o_BitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 8;
	}



	// private-computation

	@Override
	public int o_ComputeHashFromTo (
			final AvailObject object,
			final int start,
			final int end)
	{
		//  See comment in superclass.  This method must produce the same value.

		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = CharacterDescriptor.hashOfByteCharacterWithCodePoint(object.rawByteForCharacterAt(index)) ^ PreToggle;
			hash = TupleDescriptor.multiplierTimes(hash) + itemHash;
		}
		return TupleDescriptor.multiplierTimes(hash);
	}



	// private-copying

	AvailObject copyAsMutableByteString (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that also only holds byte characters.

		final AvailObject result = isMutableSize(true, object.tupleSize()).create(
			(object.tupleSize() + 3) / 4);
		assert result.integerSlotsCount() == object.integerSlotsCount();
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result.rawByteForCharacterAtPut(i, object.rawByteForCharacterAt(i));
		}
		return result;
	}

	AvailObject copyAsMutableTwoByteString (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that holds 16-bit characters.

		final AvailObject result =
			TwoByteStringDescriptor.isMutableSize(true, object.tupleSize())
				.create((object.tupleSize() + 1) / 2);
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result.rawShortForCharacterAtPut(i, object.rawByteForCharacterAt(i));
		}
		return result;
	}

	@Override
	public int o_TupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		return (object.integerSlotsCount() - numberOfFixedIntegerSlots) * 4 - _unusedBytesOfLastWord;
	}


	AvailObject mutableObjectOfSize (
			final int size)
	{
		//  Build a new object instance with room for size elements.

		if (!isMutable)
		{
			error("This descriptor should be mutable");
			return VoidDescriptor.voidObject();
		}
		assert (size + _unusedBytesOfLastWord & 3) == 0;
		final AvailObject result = this.create(((size + 3) / 4));
		return result;
	}



	// private-initialization

	AvailObject privateMutableObjectFromNativeByteString (
			final String aNativeByteString)
	{
		final AvailObject result = mutableObjectOfSize(aNativeByteString.length());
		for (int index = 1; index <= aNativeByteString.length(); index++)
		{
			final char c = aNativeByteString.charAt(index - 1);
			assert 0 <= c && c <= 255;
			result.rawByteForCharacterAtPut(index, (short)c);
		}
		return result;
	}





	// Descriptor lookup
	static ByteStringDescriptor isMutableSize(final boolean flag, final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors[delta + (size & 3) * 2];
	};

	/**
	 * Construct a new {@link ByteStringDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param unusedBytes The number of unused bytes of the last word.
	 */
	protected ByteStringDescriptor (
		final boolean isMutable,
		final int unusedBytes)
	{
		super(isMutable);
		_unusedBytesOfLastWord = unusedBytes;
	}

	final static ByteStringDescriptor descriptors[] = {
		new ByteStringDescriptor(true, 0),
		new ByteStringDescriptor(false, 0),
		new ByteStringDescriptor(true, 3),
		new ByteStringDescriptor(false, 3),
		new ByteStringDescriptor(true, 2),
		new ByteStringDescriptor(false, 2),
		new ByteStringDescriptor(true, 1),
		new ByteStringDescriptor(false, 1)
	};




	// Object creation
	public static AvailObject mutableObjectFromNativeByteString(final String aNativeByteString)
	{
		final ByteStringDescriptor descriptor = isMutableSize(true, aNativeByteString.length());
		return descriptor.privateMutableObjectFromNativeByteString(aNativeByteString);
	}

	public static AvailObject fromNativeString(final String aNativeString)
	{
		for (int i = 0; i < aNativeString.length(); i++)
		{
			if (aNativeString.codePointAt(i) > 255)
			{
				return TwoByteStringDescriptor.mutableObjectFromNativeTwoByteString(aNativeString);
			}
		}
		return ByteStringDescriptor.mutableObjectFromNativeByteString(aNativeString);
	}

}
