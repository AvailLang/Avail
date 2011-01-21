/**
 * descriptor/TwoByteStringDescriptor.java
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.min;
import java.util.List;
import com.avail.annotations.NotNull;

public class TwoByteStringDescriptor extends TupleDescriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH_OR_ZERO,
		RAW_QUAD_AT_
	}
	int unusedShortsOfLastWord;


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



	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append("\"");
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			final char c = (char)object.rawShortForCharacterAt(i);
			if (c == '\"' || c == '\'' || c == '\\')
			{
				aStream.append('\\');
				aStream.append(c);
			}
			else
			{
				aStream.append((char)object.rawShortForCharacterAt(i));
			}
		}
		aStream.append('\"');
	}



	/**
	 * Compare sections of two tuples.  My instance is a two-byte-string.
	 */
	@Override
	public boolean o_CompareFromToWithStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anotherObject,
			final int startIndex2)
	{
		return anotherObject.compareFromToWithTwoByteStringStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	/**
	 * Compare sections of two two-byte strings.
	 */
	@Override
	public boolean o_CompareFromToWithTwoByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aTwoByteString,
			final int startIndex2)
	{
		if (object.sameAddressAs(aTwoByteString) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual bytes.
		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (object.rawShortForCharacterAt(index1)
					!= aTwoByteString.rawShortForCharacterAt(index2))
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
		return another.equalsTwoByteString(object);
	}

	@Override
	public boolean o_EqualsTwoByteString (
			final AvailObject object,
			final AvailObject aTwoByteString)
	{
		//  First, check for object-structure (address) identity.

		if (object.sameAddressAs(aTwoByteString))
		{
			return true;
		}
		if (object.tupleSize() != aTwoByteString.tupleSize())
		{
			return false;
		}
		if (object.hash() != aTwoByteString.hash())
		{
			return false;
		}
		if (!object.compareFromToWithTwoByteStringStartingAt(
			1,
			object.tupleSize(),
			aTwoByteString,
			1))
		{
			return false;
		}
		// They're equal (but occupy disjoint storage).  Replace one with an
		// indirection to the other to keep down the frequency of byte-wise
		// comparisons.
		object.becomeIndirectionTo(aTwoByteString);
		// Make it immutable, now that there are at least two references to it.
		aTwoByteString.makeImmutable();
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

	/**
	 * Answer whether object is an instance of a subtype of aType.  Don't
	 * generate an approximate type and do the comparison, because the
	 * approximate type will just send this message recursively.  Note that
	 * because object is a string, it is already known that each element is a
	 * character.
	 */
	@Override
	public boolean o_IsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		if (aType.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aType.equals(ALL.o()))
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
			// This tuple's size is out of range.
			return false;
		}
		//  Make sure the element types accept character, up to my actual size.
		final AvailObject typeTuple = aType.typeTuple();
		final int limit = min(object.tupleSize(), (typeTuple.tupleSize() + 1));
		for (int i = 1; i <= limit; i++)
		{
			if (!CHARACTER.o().isSubtypeOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}


	/**
	 * Make the object immutable so it can be shared safely.
	 */
	@Override
	public AvailObject o_MakeImmutable (
			final AvailObject object)
	{
		if (isMutable)
		{
			object.descriptor(
				TwoByteStringDescriptor.isMutableSize(
					false,
					object.tupleSize()));
			object.makeSubobjectsImmutable();
		}
		return object;
	}


	/**
	 * Answer the byte that encodes the character at the given index.
	 */
	@Override
	public short o_RawShortForCharacterAt (
			final AvailObject object,
			final int index)
	{
		return object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
	}

	@Override
	public void o_RawShortForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		// Set the character at the given index based on the given byte.
		object.shortSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, anInteger);
	}

	@Override
	public AvailObject o_TupleAt (
			final AvailObject object,
			final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// two-byte character.
		assert index >= 1 && index <= object.tupleSize();
		return CharacterDescriptor.newImmutableCharacterWithCodePoint(
			object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index));
	}

	@Override
	public void o_TupleAtPut (
			final AvailObject object,
			final int index,
			final AvailObject aCharacterObject)
	{
		// Set the short at the given index to the given object (which should be
		// an AvailObject that's a two-byte character).
		assert index >= 1 && index <= object.tupleSize();
		object.shortSlotAtPut(
			IntegerSlots.RAW_QUAD_AT_,
			index,
			(short)aCharacterObject.codePoint());
	}

	@Override
	public AvailObject o_TupleAtPuttingCanDestroy (
			final AvailObject object,
			final int index,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isCharacter())
		{
			final int codePoint = newValueObject.codePoint();
			if (codePoint >= 0 && codePoint <= 0xFFFF)
			{
				if (canDestroy & isMutable)
				{
					object.rawShortForCharacterAtPut(index, (short)codePoint);
					object.hashOrZero(0);
					return object;
				}
				//  Clone it then modify the copy in place.
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

	@Override
	public int o_TupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object.
		return (object.integerSlotsCount() - numberOfFixedIntegerSlots) * 2
				- unusedShortsOfLastWord;
	}



	// private-accessing

	@Override
	public int o_BitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 16;
	}

	void unusedShortsOfLastWord (
			final int anInteger)
	{
		//  Set unusedBytesOfLastWord in this descriptor instance.  Must be 0 or 1.
		assert 0 <= anInteger && anInteger <= 1;
		unusedShortsOfLastWord = anInteger;
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
			final int itemHash =
				CharacterDescriptor.computeHashOfCharacterWithCodePoint(
					object.rawShortForCharacterAt(index))
				^ PreToggle;
			hash = hash * Multiplier + itemHash;
		}
		return hash * Multiplier;
	}



	// private-copying

	AvailObject copyAsMutableTwoByteString (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that also only holds 16-bit characters.

		final AvailObject result = isMutableSize(true, object.tupleSize()).create(
			object.tupleSize() + 1 >> 1);
		assert result.integerSlotsCount() == object.integerSlotsCount();
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result.rawShortForCharacterAtPut(i, object.rawShortForCharacterAt(i));
		}
		return result;
	}



	// private-initialization

	AvailObject privateMutableObjectFromNativeTwoByteString (
			final String aNativeTwoByteString)
	{
		final AvailObject result = mutableObjectOfSize(
			aNativeTwoByteString.length());
		for (int index = 1; index <= aNativeTwoByteString.length(); index++)
		{
			final char c = aNativeTwoByteString.charAt(index - 1);
			result.rawShortForCharacterAtPut(index, (short)c);
		}
		return result;
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
		assert ((size + unusedShortsOfLastWord) * 2 & 3) == 0;
		final AvailObject result = this.create(
			size + 1 >> 1);
		return result;
	}

	// Descriptor lookup
	static TwoByteStringDescriptor isMutableSize(
		final boolean flag,
		final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors [delta + (size & 1) * 2];
	};


	// Object creation
	public static AvailObject mutableObjectFromNativeTwoByteString(final String aNativeTwoByteString)
	{
		final TwoByteStringDescriptor descriptor = isMutableSize(true, aNativeTwoByteString.length());
		return descriptor.privateMutableObjectFromNativeTwoByteString(aNativeTwoByteString);
	}

	/**
	 * Construct a new {@link TwoByteStringDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param unusedShortsOfLastWord
	 *        The number of unused shorts of the last word.
	 */
	protected TwoByteStringDescriptor (
		final boolean isMutable,
		final int unusedShortsOfLastWord)
	{
		super(isMutable);
		this.unusedShortsOfLastWord = unusedShortsOfLastWord;
	}


	final static TwoByteStringDescriptor descriptors[] = {
		new TwoByteStringDescriptor(true, 0),
		new TwoByteStringDescriptor(false, 0),
		new TwoByteStringDescriptor(true, 1),
		new TwoByteStringDescriptor(false, 1)
	};

}
