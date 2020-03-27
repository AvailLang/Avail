/*
 * ByteTupleDescriptor.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;
import com.avail.utility.MutableInt;
import com.avail.utility.json.JSONWriter;

import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObject.newLike;
import static com.avail.descriptor.numbers.IntegerDescriptor.fromUnsignedByte;
import static com.avail.descriptor.numbers.IntegerDescriptor.hashOfUnsignedByte;
import static com.avail.descriptor.representation.Mutability.*;
import static com.avail.descriptor.tuples.ByteTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.ByteTupleDescriptor.IntegerSlots.RAW_LONG_AT_;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.types.TypeDescriptor.Types.NONTYPE;
import static java.lang.Math.min;

/**
 * {@code ByteTupleDescriptor} represents a tuple of integers that happen to
 * fall in the range 0..255.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ByteTupleDescriptor
extends NumericTupleDescriptor
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
		 * The raw 64-bit machine words that constitute the representation of
		 * the {@linkplain ByteTupleDescriptor byte tuple}.
		 */
		RAW_LONG_AT_;

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
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
		}
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 64;

	/**
	 * The number of bytes of the last {@code long} that do not participate in
	 * the representation of the {@linkplain ByteTupleDescriptor byte tuple}.
	 * Must be between 0 and 7.
	 */
	private final int unusedBytesOfLastLong;

	@Override @AvailMethod
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		if (originalSize >= maximumCopySize || !newElement.isInt())
		{
			// Transition to a tree tuple.
			return object.concatenateWith(tuple(newElement), canDestroy);
		}
		final int intValue = ((A_Number) newElement).extractInt();
		if ((intValue & ~255) != 0)
		{
			// Transition to a tree tuple.
			return object.concatenateWith(tuple(newElement), canDestroy);
		}
		final int newSize = originalSize + 1;
		if (isMutable() && canDestroy && (originalSize & 7) != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			object.setDescriptor(descriptorFor(MUTABLE, newSize));
			object.setByteSlot(RAW_LONG_AT_, newSize, (short) intValue);
			object.setSlot(HASH_OR_ZERO, 0);
			return object;
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, newSize),
			object,
			0,
			(originalSize & 7) == 0 ? 1 : 0);
		result.setByteSlot(RAW_LONG_AT_, newSize, (short) intValue);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	protected int o_BitsPerEntry (
		final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 8;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aByteTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual bytes.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (object.tupleIntAt(index1) != aByteTuple.tupleIntAt(index2))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithByteTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
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
			final int itemHash = hashOfUnsignedByte(
				(short) object.tupleIntAt(index)) ^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
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
		if (otherTuple.isByteTuple() && newSize <= maximumCopySize)
		{
			// Copy the bytes.
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
			int destination = size1 + 1;
			for (int source = 1; source <= size2; source++, destination++)
			{
				result.setByteSlot(
					RAW_LONG_AT_,
					destination,
					(short) otherTuple.tupleIntAt(source));
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
		if (size > 0 && size < tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			final AvailObject result = mutableObjectOfSize(size);
			int destination = 1;
			for (int src = start; src <= end; src++, destination++)
			{
				result.setByteSlot(
					RAW_LONG_AT_,
					destination,
					object.byteSlot(RAW_LONG_AT_, src));
			}
			if (canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return result;
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	@Override @AvailMethod
	public boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.equalsByteTuple(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aByteTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aByteTuple))
		{
			return true;
		}
		if (object.tupleSize() != aByteTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aByteTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithByteTupleStartingAt(
			1,
			object.tupleSize(),
			aByteTuple,
			1))
		{
			return false;
		}
		// They're equal (but occupy disjoint storage). If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
		if (!isShared())
		{
			aByteTuple.makeImmutable();
			object.becomeIndirectionTo(aByteTuple);
		}
		else if (!aByteTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aByteTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	protected boolean o_IsByteTuple (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
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
		//  See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesInt(object.tupleSize()))
		{
			return false;
		}
		//  tuple's size is in range.
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

	@Override
	protected void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		for (int index = startIndex; index <= endIndex; index++)
		{
			outputByteBuffer.put((byte) object.byteSlot(RAW_LONG_AT_, index));
		}
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (
		final AvailObject object,
		final int index)
	{
		//  Answer the element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		return fromUnsignedByte(object.byteSlot(RAW_LONG_AT_, index));
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
		final AvailObject result = canDestroy && isMutable()
			? object
			: newLike(mutable(), object, 0, 0);
		result.setByteSlot(
			RAW_LONG_AT_,
			index,
			((A_Number)newValueObject).extractUnsignedByte());
		result.hashOrZero(0);
		return result;
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return bytes().isSubtypeOf(type)
			|| super.o_TupleElementsInRangeAreInstancesOf(
				object, startIndex, endIndex, type);
	}

	@Override @AvailMethod
	protected int o_TupleIntAt (
		final AvailObject object,
		final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		return object.byteSlot(RAW_LONG_AT_, index);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int tupleSize = object.tupleSize();
		if (tupleSize > 0 && tupleSize < maximumCopySize)
		{
			// It's not empty and it's reasonably small.
			final MutableInt sourceIndex = new MutableInt(tupleSize);
			return generateByteTupleFrom(
				tupleSize,
				ignored -> object.byteSlot(RAW_LONG_AT_, sourceIndex.value--));
		}
		return super.o_TupleReverse(object);
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return
			(object.variableIntegerSlotsCount() << 3) - unusedBytesOfLastLong;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startArray();
		for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
		{
			writer.write((short) object.tupleIntAt(i));
		}
		writer.endArray();
	}

	/**
	 * Construct a new {@code ByteTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedBytes
	 *        The number of unused bytes of the last word.
	 */
	private ByteTupleDescriptor (
		final Mutability mutability,
		final int unusedBytes)
	{
		super(mutability, null, IntegerSlots.class);
		unusedBytesOfLastLong = unusedBytes;
	}

	/** The {@link ByteTupleDescriptor} instances. */
	private static final ByteTupleDescriptor[] descriptors =
		new ByteTupleDescriptor[8 * 3];

	static {
		int i = 0;
		for (final int excess : new int[] {0,7,6,5,4,3,2,1})
		{
			descriptors[i++] = new ByteTupleDescriptor(MUTABLE, excess);
			descriptors[i++] = new ByteTupleDescriptor(IMMUTABLE, excess);
			descriptors[i++] = new ByteTupleDescriptor(SHARED, excess);
		}
	}

	@Override
	public ByteTupleDescriptor mutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + MUTABLE.ordinal()];
	}

	@Override
	public ByteTupleDescriptor immutable ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	public ByteTupleDescriptor shared ()
	{
		return descriptors[
			((8 - unusedBytesOfLastLong) & 7) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer the appropriate {@code ByteTupleDescriptor descriptor} to
	 * represent an {@linkplain AvailObject object} of the specified mutability
	 * and size.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        The desired number of elements.
	 * @return A {@code ByteTupleDescriptor descriptor}.
	 */
	private static ByteTupleDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		final int delta = flag.ordinal();
		return descriptors[(size & 7) * 3 + delta];
	}

	/**
	 * Build a mutable byte tuple with the specified number of zeroed elements.
	 *
	 * @param size The number of bytes in the resulting tuple.
	 * @return A byte tuple with the specified number of bytes (initially zero).
	 */
	public static AvailObject mutableObjectOfSize (final int size)
	{
		final ByteTupleDescriptor descriptor = descriptorFor(MUTABLE, size);
		assert (size + descriptor.unusedBytesOfLastLong & 7) == 0;
		return descriptor.create(size + 7 >> 3);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code ByteTupleDescriptor}.  Run the generator for each position in
	 * ascending order to produce the unsigned bytes (as shorts in the range
	 * [0..15]) with which to populate the tuple.
	 *
	 * @param size The size of byte tuple to create.
	 * @param generator A generator to provide unsigned bytes to store.
	 * @return The new tuple.
	 */
	public static AvailObject generateByteTupleFrom (
		final int size,
		final IntUnaryOperator generator)
	{
		final AvailObject result = mutableObjectOfSize(size);
		int tupleIndex = 1;
		// Aggregate eight writes at a time for the bulk of the tuple.
		for (
			int slotIndex = 1, limit = size >>> 3;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = 0;
			for (int shift = 0; shift < 64; shift += 8)
			{
				final long c = generator.applyAsInt(tupleIndex++);
				assert (c & 255) == c;
				combined += c << shift;
			}
			result.setSlot(RAW_LONG_AT_, slotIndex, combined);
		}
		// Do the last 0-7 writes the slow way.
		for (int index = (size & ~7) + 1; index <= size; index++)
		{
			final long c = generator.applyAsInt(tupleIndex++);
			assert (c & 255) == c;
			result.setByteSlot(RAW_LONG_AT_, index, (short) c);
		}
		assert tupleIndex == size + 1;
		return result;
	}
}
