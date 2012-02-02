/**
 * TwoByteStringDescriptor.java
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

import static com.avail.descriptor.AvailObject.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.utility.*;

/**
 * A {@linkplain TupleDescriptor tuple} implementation that consists entirely of
 * two-byte characters.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
class TwoByteStringDescriptor
extends StringDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this two-byte string or zero.  In the rare case
		 * that the hash of the string is actually zero, it must be recomputed
		 * every time.
		 */
		HASH_OR_ZERO,

		/**
		 * A sequence of {@code int}s that encode the two-byte characters of the
		 * string.  Each int stores two two-byte characters, in Little Endian
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
	 * The number of shorts that are unused in the last {@linkplain
	 * IntegerSlots#RAW_QUAD_AT_ integer slot}.  Zero when the number of
	 * characters is even, one if odd.
	 */
	int unusedShortsOfLastWord;

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append('"');
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			final char c = (char)object.rawShortForCharacterAt(i);
			if (c == '\"' || c == '\\')
			{
				aStream.append('\\');
			}
			aStream.append(c);
		}
		aStream.append('"');
	}

	/**
	 * Compare sections of two tuples.  My instance is a two-byte-string.
	 */
	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anotherObject,
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
	@Override @AvailMethod
	boolean o_CompareFromToWithTwoByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aTwoByteString,
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

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsTwoByteString(object);
	}

	@Override @AvailMethod
	boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTwoByteString)
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
	@Override @AvailMethod
	boolean o_IsString (final @NotNull AvailObject object)
	{
		return true;
	}

	/**
	 * Make the object immutable so it can be shared safely.
	 */
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

	/**
	 * Answer the byte that encodes the character at the given index.
	 */
	@Override @AvailMethod
	short o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		// Set the character at the given index based on the given byte.
		object.shortSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// two-byte character.
		assert index >= 1 && index <= object.tupleSize();
		return CharacterDescriptor.fromCodePoint(
				object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index)
			& 0xFFFF);
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject aCharacterObject)
	{
		// Set the short at the given index to the given object (which should be
		// an AvailObject that's a two-byte character).
		assert index >= 1 && index <= object.tupleSize();
		object.shortSlotAtPut(
			IntegerSlots.RAW_QUAD_AT_,
			index,
			(short)aCharacterObject.codePoint());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject newValueObject,
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

	@Override @AvailMethod
	int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		error("Strings store characters, not integers", object);
		return 0;
	}

	@Override @AvailMethod
	int o_TupleSize (
		final @NotNull AvailObject object)
	{
		return object.variableIntegerSlotsCount() * 2 - unusedShortsOfLastWord;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 16;
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
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

	/**
	 * Construct a new {@link TwoByteStringDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param unusedShortsOfLastWord
	 *        The number of unused shorts of the last word.
	 */
	private TwoByteStringDescriptor (
		final boolean isMutable,
		final int unusedShortsOfLastWord)
	{
		super(isMutable);
		this.unusedShortsOfLastWord = unusedShortsOfLastWord;
	}

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * {@link #isMutableSize(boolean, int)} can find them by mutability and
	 * number of unused shorts in the last word.
	 */
	static final TwoByteStringDescriptor[] descriptors =
	{
		new TwoByteStringDescriptor(true, 0),
		new TwoByteStringDescriptor(false, 0),
		new TwoByteStringDescriptor(true, 1),
		new TwoByteStringDescriptor(false, 1)
	};

	/**
	 * Create a new mutable {@linkplain TwoByteStringDescriptor two-byte string} with
	 * the specified number of elements.
	 *
	 * @param size The number of elements in the new tuple.
	 * @return The new tuple, initialized to null characters (codepoint 0).
	 */
	static AvailObject mutableObjectOfSize (
		final int size)
	{
		return isMutableSize(true, size).create(size + 1 >> 1);
	}

	/**
	 * Answer a mutable copy of object that also only holds 16-bit characters.
	 *
	 * @param object
	 *            The {@linkplain TwoByteStringDescriptor two-byte string} to
	 *            copy.
	 * @return
	 *            A new {@linkplain TwoByteStringDescriptor two-byte string}
	 *            with the same content as the argument.
	 */
	AvailObject copyAsMutableTwoByteString (
		final @NotNull AvailObject object)
	{
		final AvailObject result = mutableObjectOfSize(object.tupleSize());
		assert result.integerSlotsCount() == object.integerSlotsCount();
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = object.variableIntegerSlotsCount(); i <= end; i++)
		{
			result.setSlot(
				IntegerSlots.RAW_QUAD_AT_,
				i,
				object.slot(IntegerSlots.RAW_QUAD_AT_, i));
		}
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
	 *            A {@link TwoByteStringDescriptor} suitable for representing a
	 *            two-byte string of the given mutability and {@link
	 *            AvailObject#tupleSize() size}.
	 */
	static TwoByteStringDescriptor isMutableSize (
		final boolean flag,
		final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors [delta + (size & 1) * 2];
	}

	/**
	 * Create a mutable {@linkplain TwoByteStringDescriptor two-byte string}
	 * with the specified Java {@linkplain String}'s characters.
	 *
	 * @param aNativeTwoByteString
	 *            A Java String that may contain characters outside the Latin-1
	 *            range (0-255).
	 * @return
	 *            A two-byte string with the given content.
	 */
	static AvailObject mutableObjectFromNativeTwoByteString (
		final String aNativeTwoByteString)
	{
		return generateTwoByteString(
			aNativeTwoByteString.length(),
			new Generator<Integer>()
			{
				private int sourceIndex = 0;

				@Override
				public Integer value ()
				{
					return aNativeTwoByteString.codePointAt(sourceIndex++);
				}
			});
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@link TwoByteStringDescriptor}.  Note that it can only store Unicode
	 * characters from the Basic Multilingual Plane (i.e., those having Unicode
	 * code points 0..65535).  Run the generator for each position in ascending
	 * order to produce the code points with which to populate the string.
	 *
	 * @param size The size of two-byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@linkplain TwoByteStringDescriptor string}.
	 */
	static @NotNull AvailObject generateTwoByteString(
		final int size,
		final @NotNull Generator<Integer> generator)
	{
		final AvailObject result =
			TwoByteStringDescriptor.mutableObjectOfSize(size);
		for (int index = 1; index <= size; index++)
		{
			result.rawShortForCharacterAtPut(
				index,
				(short)(int)generator.value());
		}
		return result;
	}
}
