/**
 * TwoByteStringDescriptor.java
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.Mutability.*;
import com.avail.annotations.*;
import com.avail.utility.*;

/**
 * A {@linkplain TupleDescriptor tuple} implementation that consists entirely of
 * two-byte characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class TwoByteStringDescriptor
extends StringDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
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
	 * IntegerSlots#RAW_QUAD_AT_ integer slot}. Zero when the number of
	 * characters is even, one if odd.
	 */
	int unusedShortsOfLastWord;

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
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

	@Override @AvailMethod
	boolean o_CompareFromToWithTwoByteStringStartingAt (
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
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (object.rawShortForCharacterAt(index1)
					!= aTwoByteString.rawShortForCharacterAt(index2))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.equalsTwoByteString(object);
	}

	@Override @AvailMethod
	boolean o_EqualsTwoByteString (
		final AvailObject object,
		final AvailObject aTwoByteString)
	{
		// First, check for object-structure (address) identity.
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
		// They're equal, but occupy disjoint storage. If possible, then replace
		// one with an indirection to the other to keep down the frequency of
		// byte-wise comparisons.
		if (!isShared())
		{
			aTwoByteString.makeImmutable();
			object.becomeIndirectionTo(aTwoByteString);
		}
		else if (!aTwoByteString.descriptor.isShared())
		{
			object.makeImmutable();
			aTwoByteString.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsString (final AvailObject object)
	{
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

	/**
	 * Answer the int that encodes the character at the given index.
	 */
	@Override @AvailMethod
	int o_RawShortForCharacterAt (final AvailObject object, final int index)
	{
		return object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		// Set the character at the given index based on the given byte.
		assert isMutable();
		object.shortSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object. It's a
		// two-byte character.
		assert index >= 1 && index <= object.tupleSize();
		return CharacterDescriptor.fromCodePoint(
				object.shortSlotAt(IntegerSlots.RAW_QUAD_AT_, index)
			& 0xFFFF);
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject aCharacterObject)
	{
		// Set the short at the given index to the given object (which should be
		// an AvailObject that's a two-byte character).
		assert isMutable();
		assert index >= 1 && index <= object.tupleSize();
		object.shortSlotAtPut(
			IntegerSlots.RAW_QUAD_AT_,
			index,
			(short)aCharacterObject.codePoint());
	}

	@Override @AvailMethod
	AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isCharacter())
		{
			final int codePoint = newValueObject.codePoint();
			if (codePoint >= 0 && codePoint <= 0xFFFF)
			{
				if (canDestroy & isMutable())
				{
					object.rawShortForCharacterAtPut(index, (short)codePoint);
					object.hashOrZero(0);
					return object;
				}
				// Clone it then modify the copy in place.
				return copyAsMutableTwoByteString(object)
					.tupleAtPuttingCanDestroy(
						index,
						newValueObject,
						true);
			}
		}
		// Convert to an arbitrary Tuple instead.
		return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			true);
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return object.variableIntegerSlotsCount() * 2 - unusedShortsOfLastWord;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 16;
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
			final int itemHash =
				CharacterDescriptor.computeHashOfCharacterWithCodePoint(
					object.rawShortForCharacterAt(index))
				^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.asNativeString();
	}

	/**
	 * Construct a new {@link TwoByteStringDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedShortsOfLastWord
	 *        The number of unused shorts of the last word.
	 */
	private TwoByteStringDescriptor (
		final Mutability mutability,
		final int unusedShortsOfLastWord)
	{
		super(mutability);
		this.unusedShortsOfLastWord = unusedShortsOfLastWord;
	}

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * {@link #descriptorFor(Mutability, int)} can find them by mutability and
	 * number of unused shorts in the last word.
	 */
	static final TwoByteStringDescriptor[] descriptors =
	{
		new TwoByteStringDescriptor(MUTABLE, 0),
		new TwoByteStringDescriptor(IMMUTABLE, 0),
		new TwoByteStringDescriptor(SHARED, 0),
		new TwoByteStringDescriptor(MUTABLE, 1),
		new TwoByteStringDescriptor(IMMUTABLE, 1),
		new TwoByteStringDescriptor(SHARED, 1)
	};

	@Override
	TwoByteStringDescriptor mutable ()
	{
		return descriptors[
			((2 - unusedShortsOfLastWord) & 1) * 3 + MUTABLE.ordinal()];
	}

	@Override
	TwoByteStringDescriptor immutable ()
	{
		return descriptors[
			((2 - unusedShortsOfLastWord) & 1) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	TwoByteStringDescriptor shared ()
	{
		return descriptors[
			((2 - unusedShortsOfLastWord) & 1) * 3 + SHARED.ordinal()];
	}

	/**
	 * Create a new mutable {@linkplain TwoByteStringDescriptor two-byte string}
	 * with the specified number of elements.
	 *
	 * @param size The number of elements in the new tuple.
	 * @return The new tuple, initialized to null characters (code point 0).
	 */
	static AvailObject mutableObjectOfSize (final int size)
	{
		return descriptorFor(MUTABLE, size).create(size + 1 >> 1);
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
	private AvailObject copyAsMutableTwoByteString (final AvailObject object)
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
	private static TwoByteStringDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 1) * 3 + flag.ordinal()];
	}

	/**
	 * Create a mutable {@linkplain TwoByteStringDescriptor two-byte string}
	 * with the specified Java {@linkplain String}'s characters.
	 *
	 * @param aNativeTwoByteString
	 *        A Java String that may contain characters outside the Latin-1
	 *        range (0-255).
	 * @return A two-byte string with the given content.
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
	 * of {@link TwoByteStringDescriptor}. Note that it can only store Unicode
	 * characters from the Basic Multilingual Plane (i.e., those having Unicode
	 * code points 0..65535). Run the generator for each position in ascending
	 * order to produce the code points with which to populate the string.
	 *
	 * @param size The size of two-byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@linkplain TwoByteStringDescriptor string}.
	 */
	static AvailObject generateTwoByteString(
		final int size,
		final Generator<Integer> generator)
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
