/**
 * ByteStringDescriptor.java
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
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;

/**
 * {@code ByteStringDescriptor} represents a string of Latin-1 characters.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
class ByteStringDescriptor
extends StringDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
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
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anotherObject,
		final int startIndex2)
	{
		//  Compare sections of two tuples.  My instance is a string.

		return anotherObject.compareFromToWithByteStringStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aByteString,
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

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsByteString(object);
	}

	@Override @AvailMethod
	boolean o_EqualsByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aByteString)
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
	@Override @AvailMethod
	boolean o_IsString (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor = isMutableSize(false, object.tupleSize());
		}
		return object;
	}

	@Override @AvailMethod
	short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the byte that encodes the character at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
	}

	@Override @AvailMethod
	void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		//  Set the character at the given index based on the given byte.
		assert index >= 1 && index <= object.tupleSize();
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, anInteger);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint =
			object.byteSlotAt(IntegerSlots.RAW_QUAD_AT_, index);
		return CharacterDescriptor.fromByteCodePoint(codePoint);
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject aCharacterObject)
	{
		// Set the byte at the given index to the given object (which should be
		// an AvailObject that's a one-byte character).
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = (short) aCharacterObject.codePoint();
		object.byteSlotAtPut(IntegerSlots.RAW_QUAD_AT_, index, codePoint);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isCharacter())
		{
			final int codePoint = newValueObject.codePoint();
			if ((codePoint & 0xFF) == codePoint)
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
		// Answer the number of elements in the object (as a Smalltalk Integer).
		return object.variableIntegerSlotsCount() * 4 - unusedBytesOfLastWord;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * See comment in superclass.  This overridden method must produce the same
	 * value.
	 * </p>
	 */
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash =
				CharacterDescriptor.hashOfByteCharacterWithCodePoint(
					object.rawByteForCharacterAt(index)) ^ PreToggle;
			hash = hash * Multiplier + itemHash;
		}
		return hash * Multiplier;
	}

	@Override
	@AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.BYTE_STRING;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		return object.asNativeString();
	}

	/**
	 * Answer a mutable copy of the {@linkplain AvailObject receiver} that also
	 * only holds byte {@linkplain CharacterDescriptor characters}.
	 *
	 * @param object The {@linkplain AvailObject receiver}.
	 * @return A mutable copy of the {@linkplain AvailObject receiver}.
	 */
	private @NotNull AvailObject copyAsMutableByteString (
		final @NotNull AvailObject object)
	{
		final AvailObject result = mutableObjectOfSize(object.tupleSize());
		assert result.integerSlotsCount() == object.integerSlotsCount();
		result.hashOrZero(object.hashOrZero());
		// Copy four bytes at a time.
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
	 * Answer a mutable copy of the {@linkplain AvailObject receiver} that holds
	 * 16-bit characters.
	 *
	 * @param object The {@linkplain AvailObject receiver}.
	 * @return A mutable copy of the {@linkplain AvailObject receiver}.
	 */
	private @NotNull AvailObject copyAsMutableTwoByteString (
		final @NotNull AvailObject object)
	{
		final AvailObject result =
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
	private @NotNull AvailObject mutableObjectOfSize (final int size)
	{
		if (!isMutable)
		{
			error("This descriptor should be mutable");
			return NullDescriptor.nullObject();
		}
		assert (size + unusedBytesOfLastWord & 3) == 0;
		final AvailObject result = this.create(size + 3 >> 2);
		return result;
	}

	/**
	 * Answer the appropriate {@linkplain ByteStringDescriptor descriptor} to
	 * represent an {@linkplain AvailObject object} of the specified mutability
	 * and size.
	 *
	 * @param flag
	 *        {@code true} if the desired {@linkplain ByteStringDescriptor
	 *        descriptor} is mutable, {@code false} otherwise.
	 * @param size
	 *        The desired number of elements.
	 * @return A {@linkplain ByteStringDescriptor descriptor}.
	 */
	private static @NotNull ByteStringDescriptor isMutableSize (
		final boolean flag,
		final int size)
	{
		final int delta = flag ? 0 : 1;
		return descriptors[delta + (size & 3) * 2];
	}

	/**
	 * Construct a new {@link ByteStringDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param unusedBytes The number of unused bytes of the last word.
	 */
	private ByteStringDescriptor (
		final boolean isMutable,
		final int unusedBytes)
	{
		super(isMutable);
		unusedBytesOfLastWord = unusedBytes;
	}

	/** The {@link ByteStringDescriptor} instances. */
	private static final ByteStringDescriptor[] descriptors =
	{
		new ByteStringDescriptor(true, 0),
		new ByteStringDescriptor(false, 0),
		new ByteStringDescriptor(true, 3),
		new ByteStringDescriptor(false, 3),
		new ByteStringDescriptor(true, 2),
		new ByteStringDescriptor(false, 2),
		new ByteStringDescriptor(true, 1),
		new ByteStringDescriptor(false, 1)
	};

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
	static @NotNull AvailObject mutableObjectFromNativeByteString(
		final @NotNull String aNativeByteString)
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
	static @NotNull AvailObject generateByteString(
		final int size,
		final @NotNull Generator<Integer> generator)
	{
		final ByteStringDescriptor descriptor = isMutableSize(true, size);
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
			result.setSlot(
				IntegerSlots.RAW_QUAD_AT_,
				(index + 3) >> 2,
				combined);
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
}
