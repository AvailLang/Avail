/**
 * TwoByteStringDescriptor.java
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

import static com.avail.descriptor.TwoByteStringDescriptor.IntegerSlots.*;
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
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * an new tuple.
	 */
	private static final int maximumCopySize = 32;

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
		final A_Tuple anotherObject,
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
		final A_String aTwoByteString,
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
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsTwoByteString(object);
	}

	@Override @AvailMethod
	boolean o_EqualsTwoByteString (
		final AvailObject object,
		final A_String aTwoByteString)
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
		else if (!aTwoByteString.descriptor().isShared())
		{
			object.makeImmutable();
			aTwoByteString.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsTwoByteString (final AvailObject object)
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
		return object.shortSlotAt(RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		// Set the character at the given index based on the given byte.
		assert isMutable();
		object.shortSlotAtPut(RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object. It's a
		// two-byte character.
		assert index >= 1 && index <= object.tupleSize();
		return CharacterDescriptor.fromCodePoint(
			object.shortSlotAt(RAW_QUAD_AT_, index) & 0xFFFF);
	}

	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isCharacter())
		{
			final int codePoint = ((A_Character)newValueObject).codePoint();
			if (codePoint >= 0 && codePoint <= 0xFFFF)
			{
				if (canDestroy && isMutable())
				{
					object.rawShortForCharacterAtPut(index, (short)codePoint);
					object.hashOrZero(0);
					return object;
				}
				// Clone it then modify the copy in place.
				return
					copyAsMutableTwoByteString(object).tupleAtPuttingCanDestroy(
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
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size > maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable bytes out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
		return generateTwoByteString(
			size,
			new Generator<Integer>()
			{
				private int sourceIndex = size;

				@Override
				public Integer value ()
				{
					return (int)object.shortSlotAt(
						RAW_QUAD_AT_, sourceIndex--);
				}
			});
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
					object.rawShortForCharacterAt(index));
			hash = hash * multiplier + (itemHash ^ preToggle);
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
		if (otherTuple.isTwoByteString() && newSize <= maximumCopySize)
		{
			// Copy the characters.
			final int newWordCount = (newSize + 1) >> 1;
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
				result.shortSlotAtPut(
					RAW_QUAD_AT_,
					dest,
					otherTuple.rawShortForCharacterAt(src));
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
		if (size > 0 && size < tupleSize && size < 16)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable shorts out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			return generateTwoByteString(
				size,
				new Generator<Integer>()
				{
					private int sourceIndex = start;

					@Override
					public Integer value ()
					{
						return (int)object.shortSlotAt(
							RAW_QUAD_AT_,
							sourceIndex++);
					}
				});
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
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
		super(mutability, null, IntegerSlots.class);
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
	private A_String copyAsMutableTwoByteString (final AvailObject object)
	{
		return newLike(mutable(), object, 0, 0);
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
	static AvailObject generateTwoByteString (
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
