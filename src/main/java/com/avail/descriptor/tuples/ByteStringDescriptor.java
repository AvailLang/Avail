/*
 * ByteStringDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.tuples;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Character;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.serialization.SerializerOperation;

import javax.annotation.Nullable;
import java.util.function.IntUnaryOperator;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObject.newLike;
import static com.avail.descriptor.CharacterDescriptor.fromByteCodePoint;
import static com.avail.descriptor.CharacterDescriptor.hashOfByteCharacterWithCodePoint;
import static com.avail.descriptor.representation.Mutability.*;
import static com.avail.descriptor.tuples.ByteStringDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.ByteStringDescriptor.IntegerSlots.RAW_LONGS_;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.tuples.TwoByteStringDescriptor.mutableTwoByteStringOfSize;

/**
 * {@code ByteStringDescriptor} represents a string of Latin-1 characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ByteStringDescriptor
extends StringDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
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
		static final BitField HASH_OR_ZERO = AbstractDescriptor
			.bitField(HASH_AND_MORE, 0, 32);

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
		if ((intValue & ~255) != 0)
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int newSize = originalSize + 1;
		if (isMutable() && canDestroy && (originalSize & 7) != 0)
		{
			// Enlarge it in place, using more of the final partial long field.
			object.setDescriptor(descriptorFor(MUTABLE, newSize));
			object.setByteSlot(RAW_LONGS_, newSize, (short) intValue);
			object.setSlot(HASH_OR_ZERO, 0);
			return object;
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, newSize),
			object,
			0,
			(originalSize & 7) == 0 ? 1 : 0);
		result.setByteSlot(RAW_LONGS_, newSize, (short) intValue);
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
		return anotherObject.compareFromToWithByteStringStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithByteStringStartingAt (
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
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsByteString(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aByteString)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteString))
		{
			return true;
		}
		final int tupleSize = object.tupleSize();
		return tupleSize == aByteString.tupleSize()
			&& object.hash() == aByteString.hash()
			&& object.compareFromToWithByteStringStartingAt(
				1, tupleSize, aByteString, 1);
	}

	@Override @AvailMethod
	protected boolean o_IsByteString (final AvailObject object)
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

	@Override @AvailMethod
	protected short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the byte that encodes the character at the given index.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlot(RAW_LONGS_, index);
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert index >= 1 && index <= object.tupleSize();
		final short codePoint = object.byteSlot(RAW_LONGS_, index);
		return (AvailObject) fromByteCodePoint(codePoint);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleAtPuttingCanDestroy (
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
	protected int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlot(RAW_LONGS_, index);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
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
			size, i -> (char) object.byteSlot(RAW_LONGS_, size + 1 - i));
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object.
		return (object.variableIntegerSlotsCount() << 3)
			- unusedBytesOfLastLong;
	}

	@Override @AvailMethod
	protected int o_BitsPerEntry (final AvailObject object)
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
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash =
				hashOfByteCharacterWithCodePoint(
					object.byteSlot(RAW_LONGS_, index))
				^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.BYTE_STRING;
	}

	@Override
	protected Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.asNativeString();
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
		if (size > 0 && size < tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			return generateByteString(
				size, i -> object.byteSlot(RAW_LONGS_, i + start - 1));
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
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
			return createTwoPartTreeTuple(object, otherTuple, 1, 0);
		}
		return concatenateAtLeastOneTree(object, otherTuple, true);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code ByteStringDescriptor}.  Note that it can only store Latin-1
	 * characters (i.e., those having Unicode code points 0..255).  Run the
	 * generator for each position in ascending order to produce the code
	 * points with which to populate the string.
	 *
	 * @param size The size of byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@link A_String}.
	 */
	public static AvailObject generateByteString(
		final int size,
		final IntUnaryOperator generator)
	{
		final ByteStringDescriptor descriptor = descriptorFor(MUTABLE, size);
		final AvailObject result = descriptor.mutableObjectOfSize(size);
		int counter = 1;
		// Aggregate eight writes at a time for the bulk of the string.
		for (
			int slotIndex = 1, limit = size >>> 3;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = 0;
			for (int shift = 0; shift < 64; shift += 8)
			{
				final long c = generator.applyAsInt(counter++);
				assert (c & 255) == c;
				combined += c << shift;
			}
			result.setSlot(RAW_LONGS_, slotIndex, combined);
		}
		// Do the last 0-7 writes the slow way.
		for (int index = (size & ~7) + 1; index <= size; index++)
		{
			final long c = generator.applyAsInt(counter++);
			assert (c & 255) == c;
			result.setByteSlot(RAW_LONGS_, index, (short) c);
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
		final A_String result = mutableTwoByteStringOfSize(object.tupleSize());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			result.rawShortForCharacterAtPut(
				i, object.byteSlot(RAW_LONGS_, i));
		}
		return result;
	}

	/**
	 * Answer a new byte string capacious enough to hold the specified number of
	 * elements.
	 *
	 * @param size The desired number of elements.
	 * @return A new mutable byte string.
	 */
	private AvailObject mutableObjectOfSize (final int size)
	{
		assert isMutable();
		assert ((size + unusedBytesOfLastLong) & 7) == 0;
		return create((size + 7) >> 3);
	}

	/**
	 * Convert the specified Java {@link String} of purely Latin-1 characters
	 * into an Avail {@link A_String}.
	 *
	 * @param aNativeByteString
	 *        A Java {@link String} whose code points are all 0..255.
	 * @return
	 *        A corresponding Avail {@link A_String}.
	 */
	static AvailObject mutableObjectFromNativeByteString(
		final String aNativeByteString)
	{
		return generateByteString(
			aNativeByteString.length(), i -> aNativeByteString.charAt(i - 1));
	}

	/**
	 * Construct a new {@code ByteStringDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedBytes
	 *        The number of unused bytes of the last long.
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
			descriptors[i++] = new ByteStringDescriptor(MUTABLE, excess);
			descriptors[i++] = new ByteStringDescriptor(IMMUTABLE, excess);
			descriptors[i++] = new ByteStringDescriptor(SHARED, excess);
		}
	}

	@Override
	public ByteStringDescriptor mutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + MUTABLE.ordinal()];
	}

	@Override
	public ByteStringDescriptor immutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	public ByteStringDescriptor shared ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer the appropriate {@code ByteStringDescriptor} to represent an
	 * {@linkplain AvailObject object} of the specified mutability and size.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        The desired number of elements.
	 * @return A {@code ByteStringDescriptor} suitable for representing a
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
