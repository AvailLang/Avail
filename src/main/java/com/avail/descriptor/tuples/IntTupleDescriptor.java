/*
 * IntTupleDescriptor.java
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

import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;
import com.avail.utility.json.JSONWriter;

import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

import static com.avail.descriptor.numbers.IntegerDescriptor.computeHashOfInt;
import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.representation.AvailObject.newIndexedDescriptor;
import static com.avail.descriptor.representation.AvailObject.newLike;
import static com.avail.descriptor.representation.Mutability.IMMUTABLE;
import static com.avail.descriptor.representation.Mutability.MUTABLE;
import static com.avail.descriptor.representation.Mutability.SHARED;
import static com.avail.descriptor.tuples.IntTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.IntTupleDescriptor.IntegerSlots.RAW_LONG_AT_;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.types.TypeDescriptor.Types.NONTYPE;
import static java.lang.Math.min;

/**
 * {@code IntTupleDescriptor} efficiently represents a tuple of integers that
 * happen to fall in the range of a Java {@code int}, which is
 * [-2<sup>31</sup>..2<sup>31</sup>-1].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class IntTupleDescriptor
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
		 * the {@linkplain IntTupleDescriptor byte tuple}.
		 */
		RAW_LONG_AT_;

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		public static final BitField HASH_OR_ZERO =
			new BitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
		}
	}

	/**
	 * The number of ints of the last {@code long} that do not participate in
	 * the representation of the {@linkplain IntTupleDescriptor tuple}.
	 * Must be 0 or 1.
	 */
	private final int unusedIntsOfLastLong;

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 32;

	@Override
	public A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		if (!newElement.isInt())
		{
			// Transition to a tree tuple because it's not an int.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int intValue = ((AvailObject) newElement).extractInt();
		if (originalSize >= maximumCopySize)
		{
			// Transition to a tree tuple because it's too big.
			final A_Tuple singleton =
				generateIntTupleFrom(1, ignored -> intValue);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int newSize = originalSize + 1;
		if (isMutable() && canDestroy && (originalSize & 1) != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			object.setDescriptor(descriptorFor(MUTABLE, newSize));
			object.setIntSlot(RAW_LONG_AT_, newSize, intValue);
			object.setSlot(HASH_OR_ZERO, 0);
			return object;
		}
		// Copy to a potentially larger IntTupleDescriptor.
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, newSize),
			object,
			0,
			(originalSize & 1) == 0 ? 1 : 0);
		result.setIntSlot(RAW_LONG_AT_, newSize, intValue);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override
	public int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 32;
	}

	@Override
	public boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		// Compare the argument's bytes to my ints.
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

	@Override
	public boolean o_CompareFromToWithIntTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(anIntTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare the argument's bytes to my ints.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (object.tupleIntAt(index1) != anIntTuple.tupleIntAt(index2))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithIntTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override
	public int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass. This method must produce the same value.
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash =
				computeHashOfInt(object.tupleIntAt(index)) ^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override
	public A_Tuple o_ConcatenateWith (
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
		if (otherTuple.isIntTuple() && newSize <= maximumCopySize)
		{
			// Copy the ints.
			final int newLongCount = (newSize + 1) >>> 1;
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
				result.setIntSlot(
					RAW_LONG_AT_, destination, otherTuple.tupleIntAt(source));
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
	public A_Tuple o_CopyTupleFromToCanDestroy (
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
			// Just copy the applicable ints out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last long in that
			// case.
			final AvailObject result = mutableObjectOfSize(size);
			int destination = 1;
			for (int source = start; source <= end; source++, destination++)
			{
				result.setIntSlot(
					RAW_LONG_AT_,
					destination,
					object.intSlot(RAW_LONG_AT_, source));
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

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.equalsIntTuple(object);
	}

	@Override
	public boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aByteTuple)
	{
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
		// They're equal (but occupy disjoint storage). If possible, replace one
		// with an indirection to the other to keep down the frequency of
		// byte/int-wise comparisons.  Prefer the byte representation if there's
		// a choice.
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

	@Override
	public boolean o_EqualsIntTuple (
		final AvailObject object,
		final A_Tuple anIntTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(anIntTuple))
		{
			return true;
		}
		if (object.tupleSize() != anIntTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != anIntTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithIntTupleStartingAt(
			1,
			object.tupleSize(),
			anIntTuple,
			1))
		{
			return false;
		}
		// They're equal (but occupy disjoint storage). If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of int-wise comparisons.
		if (!isShared())
		{
			anIntTuple.makeImmutable();
			object.becomeIndirectionTo(anIntTuple);
		}
		else if (!anIntTuple.descriptor().isShared())
		{
			object.makeImmutable();
			anIntTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override
	public boolean o_IsByteTuple (final AvailObject object)
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built int tuples that happen to only contain bytes onto the start
		// or end of other byte tuples.
		final int tupleSize = object.tupleSize();
		if (tupleSize <= 10)
		{
			for (int i = 1; i <= tupleSize; i++)
			{
				final int element = object.intSlot(RAW_LONG_AT_, i);
				if (element != (element & 255))
				{
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean o_IsIntTuple (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsInstanceOfKind (
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
		if (int32().isSubtypeOf(defaultTypeObject))
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

	@Override
	public AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.setDescriptor(descriptorFor(IMMUTABLE, object.tupleSize()));
		}
		return object;
	}

	@Override
	public AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.setDescriptor(descriptorFor(SHARED, object.tupleSize()));
		}
		return object;
	}

	@Override
	public void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		for (int index = startIndex; index <= endIndex; index++)
		{
			final int mustBeByte = object.intSlot(RAW_LONG_AT_, index);
			assert mustBeByte == (mustBeByte & 255);
			//noinspection NumericCastThatLosesPrecision
			outputByteBuffer.put((byte) mustBeByte);
		}
	}

	@Override
	public AvailObject o_TupleAt (
		final AvailObject object,
		final int index)
	{
		// Answer the element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		return fromInt(object.intSlot(RAW_LONG_AT_, index));
	}

	@Override
	public A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (!newValueObject.isInt())
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		final AvailObject result = canDestroy && isMutable()
			? object
			: newLike(mutable(), object, 0, 0);
		result.setIntSlot(
			RAW_LONG_AT_, index, ((A_Number)newValueObject).extractInt());
		result.setHashOrZero(0);
		return result;
	}

	@Override
	public boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return int32().isSubtypeOf(type)
			|| super.o_TupleElementsInRangeAreInstancesOf(
				object, startIndex, endIndex, type);
	}

	@Override
	public int o_TupleIntAt (
		final AvailObject object,
		final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		assert index >= 1 && index <= object.tupleSize();
		return object.intSlot(RAW_LONG_AT_, index);
	}

	@Override
	public A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int tupleSize = object.tupleSize();
		if (tupleSize <= 1)
		{
			return object;
		}
		if (tupleSize < maximumCopySize)
		{
			// It's not empty or singular, but it's reasonably small.
			final AvailObject result = mutableObjectOfSize(tupleSize);
			for (
				int destination = 1, src = tupleSize;
				src > 0;
				destination++, src--)
			{
				result.setIntSlot(
					RAW_LONG_AT_,
					destination,
					object.intSlot(RAW_LONG_AT_, src));
			}
			return result;
		}
		return super.o_TupleReverse(object);
	}

	@Override
	public int o_TupleSize (final AvailObject object)
	{
		return (object.variableIntegerSlotsCount() << 1) - unusedIntsOfLastLong;
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startArray();
		for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
		{
			writer.write(object.tupleIntAt(i));
		}
		writer.endArray();
	}

	/**
	 * Construct a new {@code IntTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedInts
	 *        The number of unused ints of the last long.
	 */
	private IntTupleDescriptor (
		final Mutability mutability,
		final int unusedInts)
	{
		super(mutability, null, IntegerSlots.class);
		unusedIntsOfLastLong = unusedInts;
	}

	/** The {@link IntTupleDescriptor} instances. */
	private static final IntTupleDescriptor[] descriptors =
		new IntTupleDescriptor[2 * 3];

	static {
		int i = 0;
		for (final int excess : new int[] {0,1})
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[i++] = new IntTupleDescriptor(mut, excess);
			}
		}
	}

	@Override
	public IntTupleDescriptor mutable ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + MUTABLE.ordinal()];
	}

	@Override
	public IntTupleDescriptor immutable ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	public IntTupleDescriptor shared ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + SHARED.ordinal()];
	}

	/**
	 * Answer the appropriate {@code IntTupleDescriptor descriptor} to represent
	 * an {@linkplain AvailObject object} of the specified mutability and size.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        The desired number of elements.
	 * @return An {@code IntTupleDescriptor}.
	 */
	private static IntTupleDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 1) * 3 + flag.ordinal()];
	}

	/**
	 * Build a mutable int tuple with room for the specified number of elements.
	 *
	 * @param size The number of ints in the resulting tuple.
	 * @return An int tuple with the specified number of ints (initially zero).
	 */
	public static AvailObject mutableObjectOfSize (final int size)
	{
		final IntTupleDescriptor descriptor = descriptorFor(MUTABLE, size);
		assert (size + descriptor.unusedIntsOfLastLong & 1) == 0;
		return descriptor.create((size + 1) >>> 1);
	}

	/**
	 * Answer a mutable copy of object that holds int objects.
	 */
	@Override
	public A_Tuple o_CopyAsMutableIntTuple (final AvailObject object)
	{
		return newLike(mutable(), object, 0, 0);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code IntTupleDescriptor}.  Run the generator for each position in
	 * ascending order to produce the {@code int}s with which to populate the
	 * tuple.
	 *
	 * @param size The size of int-tuple to create.
	 * @param generator A generator to provide ints to store.
	 * @return The new tuple.
	 */
	public static AvailObject generateIntTupleFrom (
		final int size,
		final IntUnaryOperator generator)
	{
		final IntTupleDescriptor descriptor = descriptorFor(MUTABLE, size);
		final AvailObject result =
			newIndexedDescriptor((size + 1) >>> 1, descriptor);
		int tupleIndex = 1;
		// Aggregate two writes at a time for the bulk of the tuple.
		for (
			int slotIndex = 1, limit = size >>> 1;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = generator.applyAsInt(tupleIndex++) & 0xFFFF_FFFFL;
			combined += ((long) generator.applyAsInt(tupleIndex++)) << 32L;
			result.setSlot(RAW_LONG_AT_, slotIndex, combined);
		}
		if ((size & 1) == 1)
		{
			// Do the last (odd) write the slow way.  Assume the upper int was
			// zeroed.
			result.setIntSlot(
				RAW_LONG_AT_, size, generator.applyAsInt(tupleIndex++));
		}
		assert tupleIndex == size + 1;
		return result;
	}
}
