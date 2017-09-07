/**
 * ByteStringDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import static com.avail.descriptor.ByteStringDescriptor.IntegerSlots.*;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;
import javax.annotation.Nullable;

/**
 * {@code ByteStringDescriptor} represents a string of Latin-1 characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class ByteStringDescriptor
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
		 * the {@linkplain ByteStringDescriptor byte string}.  The bytes occur
		 * in Little Endian order within each long.
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
	 * The number of bytes of the last {@code long} that do not participate in
	 * the representation of the {@linkplain ByteStringDescriptor byte string}.
	 * Must be between 0 and 7.
	 */
	private final int unusedBytesOfLastLong;

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 64;

	@Override @AvailMethod
	A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		final int intValue;
		if (originalSize >= maximumCopySize
			|| !newElement.isCharacter()
			|| ((intValue = ((A_Character)newElement).codePoint()) & ~255) != 0)
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = TupleDescriptor.tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int newSize = originalSize + 1;
		if (isMutable() && canDestroy && (originalSize & 7) != 0)
		{
			// Enlarge it in place, using more of the final partial long field.
			object.descriptor = descriptorFor(MUTABLE, newSize);
			object.setByteSlot(RAW_LONGS_, newSize, (short)intValue);
			object.setSlot(HASH_OR_ZERO, 0);
			return object;
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, newSize),
			object,
			0,
			(originalSize & 7) == 0 ? 1 : 0);
		result.setByteSlot(RAW_LONGS_, newSize, (short)intValue);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithByteStringStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		// Compare sections of two byte strings.
		if (object.sameAddressAs(aByteString) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual bytes.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (object.rawByteForCharacterAt(index1)
				!= aByteString.rawByteForCharacterAt(index2))
			{
				return false;
			}
		}
		if (startIndex1 == 1
			&& startIndex2 == 1
			&& endIndex1 == object.tupleSize()
			&& endIndex1 == aByteString.tupleSize())
		{
			// They're *completely* equal (but occupy disjoint storage). If
			// possible, replace one with an indirection to the other to keep
			// down the frequency of byte-wise comparisons.
			if (!isShared())
			{
				aByteString.makeImmutable();
				object.becomeIndirectionTo(aByteString);
			}
			else if (!aByteString.descriptor().isShared())
			{
				object.makeImmutable();
				aByteString.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsByteString(object);
	}

	@Override @AvailMethod
	boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aByteString)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteString))
		{
			return true;
		}
		final int tupleSize = object.tupleSize();
		if (tupleSize != aByteString.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteString.hash())
		{
			return false;
		}
		return object.compareFromToWithByteStringStartingAt(
			1, tupleSize, aByteString, 1);
	}

	@Override @AvailMethod
	boolean o_IsByteString (final AvailObject object)
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

	@Override @AvailMethod
	short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the byte that encodes the character at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlot(RAW_LONGS_, index);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = object.byteSlot(RAW_LONGS_, index);
		return (AvailObject)CharacterDescriptor.fromByteCodePoint(codePoint);
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
		if (newValueObject.isCharacter())
		{
			final int codePoint = ((A_Character)newValueObject).codePoint();
			if ((codePoint & 0xFF) == codePoint)
			{
				final AvailObject result = canDestroy && isMutable()
					? object
					: newLike(mutable(), object, 0, 0);
				result.setByteSlot(RAW_LONGS_, index, (short) codePoint);
				result.hashOrZero(0);
				return result;
			}
			if ((codePoint & 0xFFFF) == codePoint)
			{
				return copyAsMutableTwoByteString(object)
					.tupleAtPuttingCanDestroy(
						index,
						newValueObject,
						true);
			}
			// Fall through for SMP Unicode characters.
		}
		//  Convert to an arbitrary Tuple instead.
		return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true);
	}

	@Override
	int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlot(RAW_LONGS_, index);
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
		return generateByteString(
			size,
			new Generator<Character>()
			{
				private int sourceIndex = size;

				@Override
				public Character value ()
				{
					return (char)object.byteSlot(RAW_LONGS_, sourceIndex--);
				}
			});
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object.
		return (object.variableIntegerSlotsCount() << 3)
			- unusedBytesOfLastLong;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * See comment in superclass. This overridden method must produce the same
	 * value.
	 * </p>
	 */
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash =
				CharacterDescriptor.hashOfByteCharacterWithCodePoint(
					object.byteSlot(RAW_LONGS_, index))
				^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.BYTE_STRING;
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.asNativeString();
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
			return generateByteString(
				size,
				new Generator<Character>()
				{
					private int sourceIndex = start;

					@Override
					public Character value ()
					{
						return (char)object.byteSlot(RAW_LONGS_, sourceIndex++);
					}
				});
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
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
		if (otherTuple.isByteString() && newSize <= maximumCopySize)
		{
			// Copy the characters.
			final int newLongCount = (newSize + 7) >>> 3;
			final int deltaSlots =
				newLongCount - object.variableIntegerSlotsCount();
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
				result.setByteSlot(
					RAW_LONGS_,
					dest,
					otherTuple.rawByteForCharacterAt(src));
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
			object, otherTuple, true);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@link ByteStringDescriptor}.  Note that it can only store Latin-1
	 * characters (i.e., those having Unicode code points 0..255).  Run the
	 * generator for each position in ascending order to produce the code
	 * points with which to populate the string.
	 *
	 * @param size The size of byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@linkplain ByteStringDescriptor string}.
	 */
	static AvailObject generateByteString(
		final int size,
		final Generator<Character> generator)
	{
		final ByteStringDescriptor descriptor = descriptorFor(MUTABLE, size);
		final AvailObject result = descriptor.mutableObjectOfSize(size);
		// Aggregate eight writes at a time for the bulk of the string.
		for (
			int slotIndex = 1, limit = size >>> 3;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = 0;
			for (int shift = 0; shift < 64; shift += 8)
			{
				final long c = generator.value();
				assert (c & 255) == c;
				combined += c << shift;
			}
			result.setSlot(RAW_LONGS_, slotIndex, combined);
		}
		// Do the last 0-7 writes the slow way.
		for (int index = (size & ~7) + 1; index <= size; index++)
		{
			final long c = generator.value();
			assert (c & 255) == c;
			result.setByteSlot(RAW_LONGS_, index, (short)c);
		}
		return result;
	}

	/**
	 * Answer a mutable copy of the {@linkplain AvailObject receiver} that holds
	 * 16-bit characters.
	 *
	 * @param object The {@linkplain AvailObject receiver}.
	 * @return A mutable copy of the {@linkplain AvailObject receiver}.
	 */
	private static A_String copyAsMutableTwoByteString (
		final AvailObject object)
	{
		final A_String result =
			TwoByteStringDescriptor.mutableObjectOfSize(object.tupleSize());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			result.rawShortForCharacterAtPut(
				i,
				object.byteSlot(RAW_LONGS_, i));
		}
		return result;
	}

	/**
	 * Answer a new {@linkplain ByteStringDescriptor object} capacious enough to
	 * hold the specified number of elements.
	 *
	 * @param size The desired number of elements.
	 * @return A new {@linkplain ByteStringDescriptor object}.
	 */
	private AvailObject mutableObjectOfSize (final int size)
	{
		assert isMutable();
		assert ((size + unusedBytesOfLastLong) & 7) == 0;
		return create((size + 7) >> 3);
	}

	/**
	 * Convert the specified Java {@link String} of purely Latin-1 characters
	 * into an Avail {@linkplain ByteStringDescriptor string}.
	 *
	 * @param aNativeByteString
	 *            A Java {@link String} whose code points are all 0..255.
	 * @return
	 *            A corresponding Avail {@linkplain ByteStringDescriptor
	 *            string}.
	 */
	static AvailObject mutableObjectFromNativeByteString(
		final String aNativeByteString)
	{
		return generateByteString(
			aNativeByteString.length(),
			new Generator<Character>()
			{
				private int sourceIndex = 0;

				@Override
				public Character value ()
				{
					return aNativeByteString.charAt(sourceIndex++);
				}
			});
	}

	/**
	 * Construct a new {@link ByteStringDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedBytes
	 *            The number of unused bytes of the last long.
	 */
	private ByteStringDescriptor (
		final Mutability mutability,
		final int unusedBytes)
	{
		super(mutability, null, IntegerSlots.class);
		unusedBytesOfLastLong = unusedBytes;
	}

	/** The {@link ByteStringDescriptor} instances. */
	private static final ByteStringDescriptor[] descriptors =
		new ByteStringDescriptor[8 * 3];

	static {
		int i = 0;
		for (final int excess : new int[] {0,7,6,5,4,3,2,1})
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[i++] = new ByteStringDescriptor(mut, excess);
			}
		}
	}

	@Override
	ByteStringDescriptor mutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + MUTABLE.ordinal()];
	}

	@Override
	ByteStringDescriptor immutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	ByteStringDescriptor shared ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer the appropriate {@linkplain ByteStringDescriptor descriptor} to
	 * represent an {@linkplain AvailObject object} of the specified mutability
	 * and size.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        The desired number of elements.
	 * @return A {@link ByteStringDescriptor} suitable for representing a
	 *         byte string of the given mutability and {@linkplain
	 *         AvailObject#tupleSize() size}.
	 */
	private static ByteStringDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 7) * 3 + flag.ordinal()];
	}
}
