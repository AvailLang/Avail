/**
 * ByteBufferTupleDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ByteBufferTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ByteBufferTupleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.NONTYPE;
import static java.lang.Math.min;
import java.nio.ByteBuffer;
import com.avail.annotations.*;
import com.avail.utility.json.JSONWriter;

/**
 * {@code ByteBufferTupleDescriptor} represents a tuple of integers that happen
 * to fall in the range {@code [0..255]}. Unlike {@link ByteTupleDescriptor}, it
 * is backed by a {@linkplain RawPojoDescriptor thinly wrapped} {@linkplain
 * ByteBuffer byte buffer}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class ByteBufferTupleDescriptor
extends TupleDescriptor
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
		@HideFieldInDebugger
		HASH_OR_ZERO;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * ByteBuffer byte buffer} that backs this {@linkplain
		 * ByteBufferTupleDescriptor tuple}.
		 */
		BYTE_BUFFER
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 64;

	@Override @AvailMethod
	ByteBuffer o_ByteBuffer (final AvailObject object)
	{
		return (ByteBuffer) object.slot(BYTE_BUFFER).javaObject();
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass. This method must produce the same value.
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		int hash = 0;
		for (int index = end - 1, first = start - 1; index >= first; index--)
		{
			final int itemHash = IntegerDescriptor.hashOfUnsignedByte(
				(short) (buffer.get(index) & 0xFF)) ^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsByteBufferTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsByteBufferTuple (
		final AvailObject object,
		final A_Tuple aByteBufferTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteBufferTuple))
		{
			return true;
		}
		if (object.byteBuffer() == aByteBufferTuple.byteBuffer())
		{
			return true;
		}
		if (object.tupleSize() != aByteBufferTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteBufferTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithByteBufferTupleStartingAt(
			1,
			object.tupleSize(),
			aByteBufferTuple,
			1))
		{
			return false;
		}
		// They're equal, but occupy disjoint storage. If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
		if (!isShared())
		{
			aByteBufferTuple.makeImmutable();
			object.becomeIndirectionTo(aByteBufferTuple);
		}
		else if (!aByteBufferTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aByteBufferTuple.becomeIndirectionTo(object);
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
		return anotherObject.compareFromToWithByteBufferTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteBufferTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aByteBufferTuple)
			&& startIndex1 == startIndex2)
		{
			return true;
		}
		final ByteBuffer buffer1 = object.byteBuffer().slice();
		final ByteBuffer buffer2 = aByteBufferTuple.byteBuffer().slice();
		buffer1.position(startIndex1 - 1);
		buffer1.limit(endIndex1);
		buffer2.position(startIndex2 - 1);
		buffer2.limit(startIndex2 + endIndex1 - startIndex1);
		return buffer1.equals(buffer2);
	}

	@Override @AvailMethod
	boolean o_IsByteTuple (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsByteBufferTuple (final AvailObject object)
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
		// tuple's size is out of range.
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
		if (IntegerRangeTypeDescriptor.bytes().isSubtypeOf(defaultTypeObject))
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
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		return (AvailObject)IntegerDescriptor.fromUnsignedByte(
			(short) (buffer.get(index - 1) & 0xFF));
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
		assert 1 <= index && index <= object.tupleSize();
		if (!newValueObject.isUnsignedByte())
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		if (!canDestroy || !isMutable())
		{
			return copyAsMutableByteBufferTuple(object)
				.tupleAtPuttingCanDestroy(
					index,
					newValueObject,
					true);
		}
		// Clobber the object in place...
		final byte theByte =
			(byte)((A_Number)newValueObject).extractUnsignedByte();
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		buffer.put(index - 1, theByte);
		object.hashOrZero(0);
		//  ...invalidate the hash value.
		return object;
	}

	@Override @AvailMethod
	void o_RawByteAtPut (
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		// Set the byte at the given index.
		assert isMutable();
		assert index >= 1 && index <= object.tupleSize();
		final byte theByte = (byte) anInteger;
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		buffer.put(index - 1, theByte);
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		return buffer.get(index - 1) & 0xFF;
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
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
		final ByteBuffer originalBuffer = object.byteBuffer();
		final AvailObject result =
			ByteTupleDescriptor.mutableObjectOfSize(size);
		for (int i = 1; i <= size; i++)
		{
			// Remember to adjust between 1-based inclusive and 0-based
			// inclusive/exclusive.
			result.rawByteAtPut(size - i + 1, originalBuffer.get(i - 1));
		}
		result.hashOrZero(0);
		return result;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObjectNotNull();
		return buffer.limit();
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.descriptor = immutable;
			object.slot(BYTE_BUFFER).makeImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = shared;
			object.slot(BYTE_BUFFER).makeShared();
		}
		return object;
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
		if (otherTuple.treeTupleLevel() == 0)
		{
			return TreeTupleDescriptor.createPair(object, otherTuple, 1, 0);
		}
		return TreeTupleDescriptor.concatenateAtLeastOneTree(
			object, otherTuple, canDestroy);
	}

	@Override @AvailMethod
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
			final ByteBuffer originalBuffer = object.byteBuffer();
			final AvailObject result =
				ByteTupleDescriptor.mutableObjectOfSize(size);
			int dest = 1;
			for (int src = start; src <= end; src++, dest++)
			{
				// Remember to adjust between 1-based inclusive and 0-based
				// inclusive/exclusive.
				result.rawByteAtPut(
					dest, (short)(originalBuffer.get(src - 1) & 255));
			}
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

	@Override
	void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		final ByteBuffer sourceBuffer = object.byteBuffer().duplicate();
		sourceBuffer.position(startIndex - 1);
		sourceBuffer.limit(endIndex);
		assert sourceBuffer.remaining() == endIndex - startIndex + 1;
		outputByteBuffer.put(sourceBuffer);
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		if (IntegerRangeTypeDescriptor.bytes().isSubtypeOf(type))
		{
			return true;
		}
		return super.o_TupleElementsInRangeAreInstancesOf(
			object,
			startIndex,
			endIndex,
			type);
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		final ByteBuffer buffer =
			(ByteBuffer) object.slot(BYTE_BUFFER).javaObject();
		assert buffer != null;
		writer.startArray();
		for (int i = 0; i < buffer.limit(); i++)
		{
			writer.write(buffer.get(i));
		}
		writer.endArray();
	}

	/**
	 * Construct a new {@link ByteBufferTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ByteBufferTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ByteBufferTupleDescriptor}. */
	private static final ByteBufferTupleDescriptor mutable =
		new ByteBufferTupleDescriptor(Mutability.MUTABLE);

	@Override
	AbstractDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ByteBufferTupleDescriptor}. */
	private static final ByteBufferTupleDescriptor immutable =
		new ByteBufferTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	AbstractDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ByteBufferTupleDescriptor}. */
	private static final ByteBufferTupleDescriptor shared =
		new ByteBufferTupleDescriptor(Mutability.SHARED);

	@Override
	AbstractDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Answer a mutable copy of object that also only holds bytes.
	 *
	 * @param object The byte tuple to copy.
	 * @return The new mutable byte tuple.
	 */
	private AvailObject copyAsMutableByteBufferTuple (
		final AvailObject object)
	{
		final int size = object.tupleSize();
		final ByteBuffer newBuffer = ByteBuffer.allocate(size);
		object.transferIntoByteBuffer(1, size, newBuffer);
		assert newBuffer.limit() == size;
		final AvailObject result = forByteBuffer(newBuffer);
		result.setSlot(HASH_OR_ZERO, object.hashOrZero());
		return result;
	}

	/**
	 * Create a new {@link ByteBufferTupleDescriptor} for the specified
	 * {@linkplain ByteBuffer byte buffer}.
	 *
	 * @param buffer A byte buffer.
	 * @return The requested {@linkplain ByteBufferTupleDescriptor tuple}.
	 */
	public static AvailObject forByteBuffer (final ByteBuffer buffer)
	{
		assert buffer.position() == 0;
		final AvailObject wrapped = RawPojoDescriptor.identityWrap(buffer);
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(BYTE_BUFFER, wrapped);
		return newObject;
	}
}
