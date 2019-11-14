/*
 * TwoByteStringDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;

import javax.annotation.Nullable;
import java.util.function.IntUnaryOperator;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObject.newLike;
import static com.avail.descriptor.CharacterDescriptor.computeHashOfCharacterWithCodePoint;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.TwoByteStringDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.TwoByteStringDescriptor.IntegerSlots.RAW_LONGS_;

/**
 * A {@linkplain TupleDescriptor tuple} implementation that consists entirely of
 * two-byte characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class TwoByteStringDescriptor
extends StringDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses of {@link
		 * TupleDescriptor}.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/**
		 * The raw 64-bit ({@code long}s) that constitute the representation of
		 * the string of two-byte characters.  Each long contains up to four
		 * characters occupying 16 bits each, in little-endian order.  Only the
		 * last long can be incomplete, and is required to have zeros for the
		 * unused elements.  The descriptor instances include the field
		 * {@link #unusedShortsOfLastLong}, which indicates how many (0-3) of
		 * the 16-bit subfields of the last long are unused.
		 */
		RAW_LONGS_;

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
		}
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 32;

	/**
	 * The number of shorts that are unused in the last {@linkplain
	 * IntegerSlots#RAW_LONGS_ long slot}. Must be between 0 and 3.
	 */
	int unusedShortsOfLastLong;

	@Override @AvailMethod
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		if (originalSize >= maximumCopySize || !newElement.isCharacter())
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int intValue = ((A_Character) newElement).codePoint();
		if ((intValue & ~0xFFFF) != 0)
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int newSize = originalSize + 1;
		if (isMutable() && canDestroy && (originalSize & 3) != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			object.setDescriptor(descriptorFor(MUTABLE, newSize));
			object.rawShortForCharacterAtPut(newSize, intValue);
			object.setSlot(HASH_OR_ZERO, 0);
			return object;
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, newSize),
			object,
			0,
			(originalSize & 3) == 0 ? 1 : 0);
		result.rawShortForCharacterAtPut(newSize, intValue);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithStartingAt (
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
	protected boolean o_CompareFromToWithTwoByteStringStartingAt (
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
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsTwoByteString(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsTwoByteString (
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
		// They're equal, but occupy disjoint storage. If possible, replace one
		// with an indirection to the other to keep down the frequency of
		// character comparisons.
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
	protected boolean o_IsTwoByteString (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.setDescriptor(descriptorFor(IMMUTABLE, object.tupleSize()));
		}
		return object;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.setDescriptor(descriptorFor(SHARED, object.tupleSize()));
		}
		return object;
	}

	/**
	 * Answer the int that encodes the character at the given index.
	 */
	@Override @AvailMethod
	protected int o_RawShortForCharacterAt (final AvailObject object, final int index)
	{
		return object.shortSlot(RAW_LONGS_, index);
	}

	@Override @AvailMethod
	protected void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		// Set the character at the given index based on the given byte.
		assert isMutable();
		object.setShortSlot(RAW_LONGS_, index, anInteger);
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object. It's a
		// two-byte character.
		assert index >= 1 && index <= object.tupleSize();
		return (AvailObject) fromCodePoint(object.shortSlot(RAW_LONGS_, index));
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleAtPuttingCanDestroy (
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
			if ((codePoint & 0xFFFF) == codePoint)
			{
				if (canDestroy && isMutable())
				{
					object.rawShortForCharacterAtPut(index, codePoint);
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
		// Convert to a general object tuple instead.
		return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true);
	}

	@Override
	protected int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		assert index >= 1 && index <= object.tupleSize();
		return object.shortSlot(RAW_LONGS_, index);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size > maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		// It's reasonably small, so just copy the characters.
		// Just copy the applicable two-byte characters in reverse.
		return generateTwoByteString(
			size, i -> object.shortSlot(RAW_LONGS_, size + 1 - i));
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return (object.variableIntegerSlotsCount() << 2)
			- unusedShortsOfLastLong;
	}

	@Override @AvailMethod
	protected int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 16;
	}

	@Override @AvailMethod
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass. This method must produce the same value.
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash =
				computeHashOfCharacterWithCodePoint(
					object.shortSlot(RAW_LONGS_, index))
				^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override
	protected Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.asNativeString();
	}

	@Override
	protected A_Tuple o_ConcatenateWith (
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
			final int newLongCount = (newSize + 3) >> 2;
			final int deltaSlots =
				newLongCount - object.variableIntegerSlotsCount();
			final AvailObject result;
			if (canDestroy && isMutable() && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				result = object;
				result.setDescriptor(descriptorFor(MUTABLE, newSize));
			}
			else
			{
				result = newLike(
					descriptorFor(MUTABLE, newSize), object, 0, deltaSlots);
			}
			int dest = size1 + 1;
			for (int src = 1; src <= size2; src++, dest++)
			{
				result.setShortSlot(
					RAW_LONGS_,
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
			return createTwoPartTreeTuple(object, otherTuple, 1, 0);
		}
		return concatenateAtLeastOneTree(object, otherTuple, true);
	}

	@Override
	protected A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		final int tupleSize = object.tupleSize();
		assert 1 <= start && start <= end + 1 && end <= tupleSize;
		final int size = end - start + 1;
		if (size > 0 && size < tupleSize && size <= maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable shorts out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			return generateTwoByteString(
				size, i -> object.shortSlot(RAW_LONGS_, i + start - 1));
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	/**
	 * Construct a new {@code TwoByteStringDescriptor}.
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
		this.unusedShortsOfLastLong = unusedShortsOfLastWord;
	}

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * {@link #descriptorFor(Mutability, int)} can find them by mutability and
	 * number of unused shorts in the last long.
	 */
	private static final TwoByteStringDescriptor[] descriptors =
		new TwoByteStringDescriptor[4 * 3];

	static {
		int i = 0;
		for (final int excess : new int[] {0,3,2,1})
		{
			descriptors[i++] = new TwoByteStringDescriptor(MUTABLE, excess);
			descriptors[i++] = new TwoByteStringDescriptor(IMMUTABLE, excess);
			descriptors[i++] = new TwoByteStringDescriptor(SHARED, excess);
		}
	}

	@Override
	protected TwoByteStringDescriptor mutable ()
	{
		return descriptors[
			((4 - unusedShortsOfLastLong) & 3) * 3 + MUTABLE.ordinal()];
	}

	@Override
	protected TwoByteStringDescriptor immutable ()
	{
		return descriptors[
			((4 - unusedShortsOfLastLong) & 3) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	protected TwoByteStringDescriptor shared ()
	{
		return descriptors[
			((4 - unusedShortsOfLastLong) & 3) * 3 + SHARED.ordinal()];
	}

	/**
	 * Create a new mutable two-byte string with the specified number of
	 * elements.
	 *
	 * @param size The number of elements in the new tuple.
	 * @return The new tuple, initialized to null characters (code point 0).
	 */
	static AvailObject mutableTwoByteStringOfSize (final int size)
	{
		return descriptorFor(MUTABLE, size).create(size + 3 >> 2);
	}

	/**
	 * Answer a mutable copy of object that also only holds 16-bit characters.
	 *
	 * @param object The two-byte string to copy.
	 * @return A new two-byte string with the same content as the argument.
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
	 *        Whether the requested descriptor should be mutable.
	 * @param size
	 *        How many elements are in a tuple to be represented by the
	 *        descriptor.
	 * @return A {@code TwoByteStringDescriptor} suitable for representing a
	 *            two-byte string of the given mutability and {@link
	 *            AvailObject#tupleSize() size}.
	 */
	private static TwoByteStringDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 3) * 3 + flag.ordinal()];
	}

	/**
	 * Create a mutable instance of {@code TwoByteStringDescriptor} with the
	 * specified Java {@linkplain String}'s characters.
	 *
	 * @param aNativeTwoByteString
	 *        A Java String that may contain characters outside the Latin-1
	 *        range (0-255), but not beyond 65535.
	 * @return A two-byte string with the given content.
	 */
	static AvailObject mutableObjectFromNativeTwoByteString (
		final String aNativeTwoByteString)
	{
		return generateTwoByteString(
			aNativeTwoByteString.length(),
			i -> aNativeTwoByteString.codePointAt(i - 1));
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code TwoByteStringDescriptor}. Note that it can only store Unicode
	 * characters from the Basic Multilingual Plane (i.e., those having Unicode
	 * code points 0..65535). Run the generator for each position in ascending
	 * order to produce the code points with which to populate the string.
	 *
	 * @param size The size of two-byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail string.
	 */
	public static AvailObject generateTwoByteString (
		final int size,
		final IntUnaryOperator generator)
	{
		final AvailObject result = mutableTwoByteStringOfSize(size);
		int counter = 1;
		// Aggregate four writes at a time for the bulk of the string.
		for (
			int slotIndex = 1, limit = size >>> 2;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = 0;
			for (int shift = 0; shift < 64; shift += 16)
			{
				final long c = generator.applyAsInt(counter++);
				assert (c & 0xFFFF) == c;
				combined += c << shift;
			}
			result.setSlot(RAW_LONGS_, slotIndex, combined);
		}
		// Do the last 0-3 writes the slow way.
		for (int index = (size & ~3) + 1; index <= size; index++)
		{
			final long c = generator.applyAsInt(counter++);
			assert (c & 0xFFFF) == c;
			result.setShortSlot(RAW_LONGS_, index, (int) c);
		}
		return result;
	}
}
