/*
 * ByteArrayTupleDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.utility.json.JSONWriter;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ByteArrayTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.ByteArrayTupleDescriptor.ObjectSlots.BYTE_ARRAY_POJO;
import static com.avail.descriptor.ByteTupleDescriptor.generateByteTupleFrom;
import static com.avail.descriptor.IntegerDescriptor.fromUnsignedByte;
import static com.avail.descriptor.IntegerDescriptor.hashOfUnsignedByte;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.TypeDescriptor.Types.NONTYPE;
import static java.lang.Math.min;

/**
 * {@code ByteArrayTupleDescriptor} represents a tuple of integers that happen
 * to fall in the range {@code [0..255]}. Unlike {@link ByteTupleDescriptor}, it
 * is backed by a {@linkplain RawPojoDescriptor thinly wrapped} byte array.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ByteArrayTupleDescriptor
extends NumericTupleDescriptor
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
		HASH_AND_MORE;

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
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the byte array
		 * that backs this {@linkplain ByteArrayTupleDescriptor tuple}.
		 */
		BYTE_ARRAY_POJO
	}

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
		if (originalSize < maximumCopySize && newElement.isInt())
		{
			final int intValue = ((A_Number) newElement).extractInt();
			if ((intValue & ~255) == 0)
			{
				// Convert to a ByteTupleDescriptor.
				final byte[] array =
					object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
				return generateByteTupleFrom(
					originalSize + 1,
					index -> index <= originalSize
						? (short) (array[index - 1] & 255)
						: (short) intValue);
			}
		}
		// Transition to a tree tuple.
		return object.concatenateWith(tuple(newElement), canDestroy);
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	@Override @AvailMethod
	byte[] o_ByteArray (final AvailObject object)
	{
		return object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteArrayTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aByteArrayTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		final byte[] array1 = object.byteArray();
		final byte[] array2 = aByteArrayTuple.byteArray();
		for (
			int index1 = startIndex1 - 1,
				index2 = startIndex2 - 1,
				lastIndex = endIndex1 - 1;
			index1 <= lastIndex;
			index1++, index2++)
		{
			if (array1[index1] != array2[index2])
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithByteArrayTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass. This method must produce the same value.
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		int hash = 0;
		for (int index = end - 1, first = start - 1; index >= first; index--)
		{
			final int itemHash = hashOfUnsignedByte(
				(short) (array[index] & 0xFF)) ^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
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
		if (otherTuple.isByteArrayTuple() && size1 + size2 <= 128)
		{
			final byte[] bytes = new byte[size1 + size2];
			System.arraycopy(object.byteArray(), 0, bytes, 0, size1);
			System.arraycopy(
				otherTuple.byteArray(), 0, bytes, size1, size2);
			return tupleForByteArray(bytes);
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

	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
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
			final byte[] originalBytes = object.byteArray();
			final AvailObject result = generateByteTupleFrom(
				size, i -> (short) (originalBytes[i + start - 2] & 255));
			if (canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			result.hashOrZero(0);
			return result;
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsByteArrayTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aByteArrayTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteArrayTuple))
		{
			return true;
		}
		if (object.byteArray() == aByteArrayTuple.byteArray())
		{
			return true;
		}
		if (object.tupleSize() != aByteArrayTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteArrayTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithByteArrayTupleStartingAt(
			1,
			object.tupleSize(),
			aByteArrayTuple,
			1))
		{
			return false;
		}
		// They're equal, but occupy disjoint storage. If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
		if (!isShared())
		{
			aByteArrayTuple.makeImmutable();
			object.becomeIndirectionTo(aByteArrayTuple);
		}
		else if (!aByteArrayTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aByteArrayTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsByteArrayTuple (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsByteTuple (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
		{
			return true;
		}
		if (!aType.isTupleType())
		{
			return false;
		}
		// See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesInt(object.tupleSize()))
		{
			return false;
		}
		// tuple's size is in range.
		final A_Tuple typeTuple = aType.typeTuple();
		final int breakIndex = min(object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
			{
				return false;
			}
		}
		final A_Type defaultTypeObject = aType.defaultType();
		if (bytes().isSubtypeOf(defaultTypeObject))
		{
			return true;
		}
		for (int i = breakIndex + 1, end = object.tupleSize(); i <= end; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.descriptor = immutable;
			object.slot(BYTE_ARRAY_POJO).makeImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = shared;
			object.slot(BYTE_ARRAY_POJO).makeShared();
		}
		return object;
	}

	@Override
	void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		final byte [] byteArray = object.byteArray();
		outputByteBuffer.put(
			byteArray,
			startIndex - 1,
			endIndex - startIndex + 1);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		return fromUnsignedByte((short) (array[index - 1] & 0xFF));
	}

	@Override
	@AvailMethod
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
		if (!newValueObject.isUnsignedByte())
		{
			if (newValueObject.isInt())
			{
				return object.copyAsMutableIntTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true);
			}
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true);
		}
		if (!canDestroy || !isMutable())
		{
			return copyAsMutableByteArrayTuple(object).tupleAtPuttingCanDestroy(
				index, newValueObject, true);
		}
		// Clobber the object in place...
		final byte theByte =
			(byte) ((AvailObject) newValueObject).extractUnsignedByte();
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		array[index - 1] = theByte;
		object.hashOrZero(0);
		//  ...invalidate the hash value.
		return object;
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return bytes().isSubtypeOf(type)
			|| super.o_TupleElementsInRangeAreInstancesOf(
				object, startIndex, endIndex, type);
	}

	@Override
	@AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		return array[index - 1] & 0xFF;
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse (final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size >= maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable bytes out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
		final byte[] originalBytes = object.byteArray();
		final AvailObject result = generateByteTupleFrom(
			size, i -> (short) (originalBytes[size - i] & 255));
		result.hashOrZero(0);
		return result;

	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		return array.length;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		final byte[] bytes = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		writer.startArray();
		for (final byte aByte : bytes)
		{
			writer.write(aByte);
		}
		writer.endArray();
	}

	/**
	 * Construct a new {@code ByteArrayTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ByteArrayTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ByteArrayTupleDescriptor}. */
	private static final ByteArrayTupleDescriptor mutable =
		new ByteArrayTupleDescriptor(Mutability.MUTABLE);

	@Override
	ByteArrayTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ByteArrayTupleDescriptor}. */
	private static final ByteArrayTupleDescriptor immutable =
		new ByteArrayTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	ByteArrayTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ByteArrayTupleDescriptor}. */
	private static final ByteArrayTupleDescriptor shared =
		new ByteArrayTupleDescriptor(Mutability.SHARED);

	@Override
	ByteArrayTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Answer a mutable copy of object that also only holds bytes.
	 *
	 * @param object The byte tuple to copy.
	 * @return The new mutable byte tuple.
	 */
	private static A_Tuple copyAsMutableByteArrayTuple (
		final AvailObject object)
	{
		final byte[] array = object.slot(BYTE_ARRAY_POJO).javaObjectNotNull();
		final byte[] copy = Arrays.copyOf(array, array.length);
		final AvailObject result = tupleForByteArray(copy);
		result.setSlot(HASH_OR_ZERO, object.hashOrZero());
		return result;
	}

	/**
	 * Create a new {@code ByteArrayTupleDescriptor} instance for the specified
	 * byte array.
	 *
	 * @param array A Java byte array.
	 * @return The requested tuple.
	 */
	public static AvailObject tupleForByteArray (final byte[] array)
	{
		final AvailObject wrapped = identityPojo(array);
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(BYTE_ARRAY_POJO, wrapped);
		return newObject;
	}
}
