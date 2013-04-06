/**
 * ByteStringDescriptor.java
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
import static com.avail.descriptor.ByteStringDescriptor.IntegerSlots.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;

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
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		HASH_OR_ZERO,

		/**
		 * The raw 32-bit machine words ({@code int}s) that constitute the
		 * representation of the {@linkplain ByteStringDescriptor byte string}.
		 * The bytes occur in Little Endian order within each int.
		 */
		RAW_QUAD_AT_;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The number of bytes of the last {@code int} that do not participate in
	 * the representation of the {@linkplain ByteStringDescriptor byte string}.
	 * Must be between 0 and 3.
	 */
	private final int unusedBytesOfLastWord;

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
			(startIndex2 + endIndex1 - startIndex1),
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
		// They're equal (but occupy disjoint storage). If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
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

	@Override @AvailMethod
	short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the byte that encodes the character at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlotAt(RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawByteForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		//  Set the character at the given index based on the given byte.
		assert isMutable();
		assert index >= 1 && index <= object.tupleSize();
		object.byteSlotAtPut(RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = object.byteSlotAt(RAW_QUAD_AT_, index);
		return CharacterDescriptor.fromByteCodePoint(codePoint);
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject aCharacterObject)
	{
		// Set the byte at the given index to the given object (which should be
		// an AvailObject that's a one-byte character).
		assert isMutable();
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = (short) aCharacterObject.codePoint();
		object.byteSlotAtPut(RAW_QUAD_AT_, index, codePoint);
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
				if (canDestroy & isMutable())
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
			if ((codePoint & 0xFFFF) == codePoint)
			{
				return copyAsMutableTwoByteString(object)
					.tupleAtPuttingCanDestroy(
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
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object.
		return object.variableIntegerSlotsCount() * 4 - unusedBytesOfLastWord;
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
					object.rawByteForCharacterAt(index)) ^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override
	@AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.BYTE_STRING;
	}

	@Override
	Object o_MarshalToJava (
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
		if (end - start < 50)
		{
			// Make a simple copy of a small region.
			return generateByteString(
				end - start + 1,
				new Generator<Integer>()
				{
					private int sourceIndex = start;

					@Override
					public Integer value ()
					{
						return (int)object.byteSlotAt(
							RAW_QUAD_AT_, sourceIndex++);
					}
				});
		}
		return super.o_CopyTupleFromToCanDestroy(
			object,
			start,
			end,
			canDestroy);
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
		final Generator<Integer> generator)
	{
		final ByteStringDescriptor descriptor = descriptorFor(MUTABLE, size);
		final AvailObject result = descriptor.mutableObjectOfSize(size);
		// Aggregate four writes at a time for the bulk of the string.
		int index;
		for (index = 1; index <= size - 3; index += 4)
		{
			final int byte1 = generator.value();
			assert (byte1 & 255) == byte1;
			final int byte2 = generator.value();
			assert (byte2 & 255) == byte2;
			final int byte3 = generator.value();
			assert (byte3 & 255) == byte3;
			final int byte4 = generator.value();
			assert (byte4 & 255) == byte4;
			// Use little-endian, since that's what byteSlotAtPut(...) uses.
			final int combined =
				byte1
				+ (byte2 << 8)
				+ (byte3 << 16)
				+ (byte4 << 24);
			result.setSlot(RAW_QUAD_AT_, (index + 3) >> 2, combined);
		}
		// Do the last 0-3 writes the slow way.
		for (; index <= size; index++)
		{
			final int b = generator.value();
			assert (b & 255) == b;
			result.rawByteForCharacterAtPut(index, (short) b);
		}
		return result;
	}

	/**
	 * Answer a mutable copy of the {@linkplain AvailObject receiver} that also
	 * only holds byte {@linkplain CharacterDescriptor characters}.
	 *
	 * @param object The {@linkplain AvailObject receiver}.
	 * @return A mutable copy of the {@linkplain AvailObject receiver}.
	 */
	private A_String copyAsMutableByteString (
		final AvailObject object)
	{
		final AvailObject result = mutableObjectOfSize(object.tupleSize());
		assert result.integerSlotsCount() == object.integerSlotsCount();
		result.hashOrZero(object.hashOrZero());
		// Copy four bytes at a time.
		for (int i = 1, end = object.variableIntegerSlotsCount(); i <= end; i++)
		{
			result.setSlot(RAW_QUAD_AT_, i, object.slot(RAW_QUAD_AT_, i));
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
	private A_String copyAsMutableTwoByteString (
		final A_String object)
	{
		final A_String result =
			TwoByteStringDescriptor.mutableObjectOfSize(object.tupleSize());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			result.rawShortForCharacterAtPut(
				i,
				object.rawByteForCharacterAt(i));
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
		assert (size + unusedBytesOfLastWord & 3) == 0;
		final AvailObject result = create((size + 3) >> 2);
		return result;
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
			new Generator<Integer>()
			{
				private int sourceIndex = 0;

				@Override
				public Integer value ()
				{
					return (int)aNativeByteString.charAt(sourceIndex++);
				}
			});
	}

	/**
	 * Construct a new {@link ByteStringDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedBytes
	 *        The number of unused bytes of the last word.
	 */
	private ByteStringDescriptor (
		final Mutability mutability,
		final int unusedBytes)
	{
		super(mutability);
		unusedBytesOfLastWord = unusedBytes;
	}

	/** The {@link ByteStringDescriptor} instances. */
	private static final ByteStringDescriptor[] descriptors =
	{
		new ByteStringDescriptor(MUTABLE, 0),
		new ByteStringDescriptor(IMMUTABLE, 0),
		new ByteStringDescriptor(SHARED, 0),
		new ByteStringDescriptor(MUTABLE, 3),
		new ByteStringDescriptor(IMMUTABLE, 3),
		new ByteStringDescriptor(SHARED, 3),
		new ByteStringDescriptor(MUTABLE, 2),
		new ByteStringDescriptor(IMMUTABLE, 2),
		new ByteStringDescriptor(SHARED, 2),
		new ByteStringDescriptor(MUTABLE, 1),
		new ByteStringDescriptor(IMMUTABLE, 1),
		new ByteStringDescriptor(SHARED, 1)
	};

	@Override
	ByteStringDescriptor mutable ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + MUTABLE.ordinal()];
	}

	@Override
	ByteStringDescriptor immutable ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	ByteStringDescriptor shared ()
	{
		return descriptors[
			((4 - unusedBytesOfLastWord) & 3) * 3 + SHARED.ordinal()];
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
		return descriptors[(size & 3) * 3 + flag.ordinal()];
	}
}
