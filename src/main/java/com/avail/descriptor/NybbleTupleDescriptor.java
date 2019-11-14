/*
 * NybbleTupleDescriptor.java
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
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.serialization.SerializerOperation;

import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.ByteTupleDescriptor.generateByteTupleFrom;
import static com.avail.descriptor.IntegerDescriptor.fromUnsignedByte;
import static com.avail.descriptor.IntegerDescriptor.hashOfUnsignedByte;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.nybbles;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.NybbleTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.NybbleTupleDescriptor.IntegerSlots.RAW_LONG_AT_;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.TypeDescriptor.Types.NONTYPE;
import static java.lang.Math.min;

/**
 * {@code NybbleTupleDescriptor} represents a tuple of integers that happen to
 * fall in the range 0..15. They are packed eight per {@code int}.
 *
 * <p>
 * This representation is particularly useful for {@linkplain
 * CompiledCodeDescriptor compiled code}, which uses nybblecodes.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class NybbleTupleDescriptor
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
		HASH_AND_MORE,

		/**
		 * The raw 64-bit machine words that constitute the representation of
		 * the {@linkplain NybbleTupleDescriptor nybble tuple}.
		 */
		RAW_LONG_AT_;

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
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 128;

	/**
	 * The number of nybbles of the last {@code long} {@linkplain
	 * IntegerSlots#RAW_LONG_AT_ integer slot} that are not considered part of
	 * the tuple.
	 */
	private final int unusedNybblesOfLastLong;

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
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int intValue = ((A_Number) newElement).extractInt();
		if ((intValue & ~15) != 0)
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final int newSize = originalSize + 1;
		final AvailObject result;
		if (isMutable() && canDestroy && (originalSize & 15) != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			result = object;
			result.setDescriptor(descriptorFor(MUTABLE, newSize));
		}
		else
		{
			result = newLike(
				descriptorFor(MUTABLE, newSize),
				object,
				0,
				(originalSize & 15) == 0 ? 1 : 0);
		}
		setNybble(result, newSize, (byte) intValue);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	protected int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 4;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aNybbleTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		if (endIndex1 < startIndex1)
		{
			return true;
		}
		//  Compare actual nybbles.
		int index2 = startIndex2;
		for (int i = startIndex1; i <= endIndex1; i++)
		{
			if (getNybble(object, i)
				!= aNybbleTuple.extractNybbleFromTupleAt(index2))
			{
				return false;
			}
			index2++;
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
		return anotherObject.compareFromToWithNybbleTupleStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override @AvailMethod
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass.  This method must produce the same value.
		// This could eventually be rewritten to do a byte at a time (table
		// lookup) and to use the square of the current multiplier.
		int hash = 0;
		for (int nybbleIndex = end; nybbleIndex >= start; nybbleIndex--)
		{
			final int itemHash =
				hashOfUnsignedByte(getNybble(object, nybbleIndex)) ^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override @AvailMethod
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
		if (newSize <= maximumCopySize)
		{
			// Copy the nybbles.
			final int newWordCount = (newSize + 15) >>> 4;
			final int deltaSlots =
				newWordCount - object.variableIntegerSlotsCount();
			final AvailObject copy;
			if (canDestroy && isMutable() && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				object.setDescriptor(descriptorFor(MUTABLE, newSize));
				copy = object;
			}
			else
			{
				copy = newLike(
					descriptorFor(MUTABLE, newSize), object, 0, deltaSlots);
			}
			copy.setSlot(HASH_OR_ZERO, 0);
			int dest = size1 + 1;
			A_Tuple result = copy;
			for (int src = 1; src <= size2; src++, dest++)
			{
				// If the slots we want are nybbles then we won't have to copy
				// into a bulkier representation.
				result = result.tupleAtPuttingCanDestroy(
					dest,
					otherTuple.tupleAt(src),
					canDestroy || src > 1);
			}
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
			// Just copy the applicable nybbles out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			final AvailObject result = generateNybbleTupleFrom(
				size, i -> getNybble(object, i + start - 1));
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
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsNybbleTuple(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final A_Tuple aNybbleTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aNybbleTuple))
		{
			return true;
		}
		if (object.tupleSize() != aNybbleTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != aNybbleTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithNybbleTupleStartingAt(
			1,
			object.tupleSize(),
			aNybbleTuple,
			1))
		{
			return false;
		}
		// They're equal, but occupy disjoint storage. If, then replace one with
		// an indirection to the other to reduce storage costs and the frequency
		// of nybble-wise comparisons.
		if (!isShared())
		{
			aNybbleTuple.makeImmutable();
			object.becomeIndirectionTo(aNybbleTuple);
		}
		else if (!aNybbleTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aNybbleTuple.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int nybbleIndex)
	{
		return getNybble(object, nybbleIndex);
	}

	@Override @AvailMethod
	protected boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than the
		// second one? Currently there is no more desirable representation than
		// a nybble tuple.  [NB MvG 2015.09.24 - other than integer interval
		// tuples.  Update this at some point, perhaps.]
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
		// See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesInt(object.tupleSize()))
		{
			return false;
		}
		// Tuple's size is out of range.
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
		if (nybbles().isSubtypeOf(defaultTypeObject))
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
	@Override @AvailMethod
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.NYBBLE_TUPLE;
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
			outputByteBuffer.put(getNybble(object, index));
		}
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the nybble tuple object.
		return fromUnsignedByte(getNybble(object, index));
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
		if (!newValueObject.isNybble())
		{
			if (newValueObject.isUnsignedByte())
			{
				return copyAsMutableByteTuple(object).tupleAtPuttingCanDestroy(
					index, newValueObject, true);
			}
			if (newValueObject.isInt())
			{
				return object.copyAsMutableIntTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true);
			}
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true);
		}
		final AvailObject result;
		if (canDestroy && isMutable())
		{
			result = object;
		}
		else
		{
			result = newLike(mutable(), object, 0, 0);
		}
		// All clear.  Clobber the object in place...
		final byte newNybble = ((A_Number)newValueObject).extractNybble();
		setNybble(result, index, newNybble);
		result.hashOrZero(0);
		//  ...invalidate the hash value. Probably cheaper than computing the
		// difference or even testing for an actual change.
		return result;
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return nybbles().isSubtypeOf(type)
			|| super.o_TupleElementsInRangeAreInstancesOf(
				object, startIndex, endIndex, type);
	}

	@Override @AvailMethod
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the integer element at the given index in the nybble tuple
		// object.
		return getNybble(object, index);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size >= maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable nybbles out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
		return generateNybbleTupleFrom(
			size, i -> getNybble(object, size + 1 - i));
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return (object.variableIntegerSlotsCount() << 4)
			- unusedNybblesOfLastLong;
	}

	/**
	 * Extract the nybble from the specified position of the nybble tuple.
	 *
	 * @param object A nybble tuple.
	 * @param nybbleIndex The index.
	 * @return The nybble at that index.
	 */
	static byte getNybble (
		final AvailObject object,
		final int nybbleIndex)
	{
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		final int longIndex = (nybbleIndex + 15) >>> 4;
		final long longValue = object.slot(RAW_LONG_AT_, longIndex);
		final int shift = ((nybbleIndex - 1) & 15) << 2;
		return (byte) ((longValue >>> shift) & 0x0F);
	}

	/**
	 * Overwrite the specified position of the nybble tuple with a replacement
	 * nybble.
	 *
	 * @param object The nybble tuple.
	 * @param nybbleIndex The index.
	 * @param aNybble The replacement value, a nybble.
	 */
	private static void setNybble (
		final AvailObject object,
		final int nybbleIndex,
		final byte aNybble)
	{
		assert nybbleIndex >= 1 && nybbleIndex <= object.tupleSize();
		assert (aNybble & 15) == aNybble;
		final int longIndex = (nybbleIndex + 15) >>> 4;
		long longValue = object.slot(RAW_LONG_AT_, longIndex);
		final int leftShift = ((nybbleIndex - 1) & 15) << 2;
		longValue &= ~(0x0FL << leftShift);
		longValue |= ((long) aNybble) << leftShift;
		object.setSlot(RAW_LONG_AT_, longIndex, longValue);
	}

	/**
	 * Construct a new {@code NybbleTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedNybbles The number of unused nybbles of the last word.
	 */
	private NybbleTupleDescriptor (
		final Mutability mutability,
		final int unusedNybbles)
	{
		super(mutability, null, IntegerSlots.class);
		unusedNybblesOfLastLong = unusedNybbles;
	}

	/**
	 * The static array of descriptors of this kind, organized in such a way
	 * that {@link #descriptorFor(Mutability, int)} can find them by mutability
	 * and number of unused nybbles in the last word.
	 */
	private static final NybbleTupleDescriptor[] descriptors =
		new NybbleTupleDescriptor[16 * 3];

	static {
		int i = 0;
		for (final int excess
			: new int[] {0,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1})
		{
			descriptors[i++] = new NybbleTupleDescriptor(MUTABLE, excess);
			descriptors[i++] = new NybbleTupleDescriptor(IMMUTABLE, excess);
			descriptors[i++] = new NybbleTupleDescriptor(SHARED, excess);
		}
	}

	@Override
	protected NybbleTupleDescriptor mutable ()
	{
		return descriptors[
			((16 - unusedNybblesOfLastLong) & 15) * 3 + MUTABLE.ordinal()];
	}

	@Override
	protected NybbleTupleDescriptor immutable ()
	{
		return descriptors[
			((16 - unusedNybblesOfLastLong) & 15) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	protected NybbleTupleDescriptor shared ()
	{
		return descriptors[
			((16 - unusedNybblesOfLastLong) & 15) * 3 + SHARED.ordinal()];
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code NybbleTupleDescriptor}.  Run the generator for each position in
	 * ascending order to produce the nybbles with which to populate the tuple.
	 *
	 * @param size The size of nybble tuple to create.
	 * @param generator A generator to provide nybbles to store.
	 * @return The new tuple of nybbles.
	 */
	public static AvailObject generateNybbleTupleFrom (
		final int size,
		final IntUnaryOperator generator)
	{
		final AvailObject result = mutableObjectOfSize(size);
		int tupleIndex = 1;
		// Aggregate sixteen writes at a time for the bulk of the tuple.
		for (
			int slotIndex = 1, limit = size >>> 4;
			slotIndex <= limit;
			slotIndex++)
		{
			long combined = 0;
			for (int shift = 0; shift < 64; shift += 4)
			{
				final byte nybble = (byte) generator.applyAsInt(tupleIndex++);
				assert (nybble & 15) == nybble;
				combined |= ((long) nybble) << shift;
			}
			result.setSlot(RAW_LONG_AT_, slotIndex, combined);
		}
		// Do the last 0-15 writes the slow way.
		for (int index = (size & ~15) + 1; index <= size; index++)
		{
			final byte nybble = (byte) generator.applyAsInt(tupleIndex++);
			assert (nybble & 15) == nybble;
			setNybble(result, index, nybble);
		}
		assert tupleIndex == size + 1;
		return result;
	}

	/**
	 * Answer a mutable copy of object that holds bytes, as opposed to just
	 * nybbles.
	 *
	 * @param object
	 *        A nybble tuple to copy as a {@linkplain ByteTupleDescriptor byte
	 *        tuple}.
	 * @return A new {@linkplain ByteTupleDescriptor byte tuple} with the same
	 *         sequence of integers as the argument.
	 */
	private static A_Tuple copyAsMutableByteTuple (final AvailObject object)
	{
		final AvailObject result = generateByteTupleFrom(
			object.tupleSize(), index -> (short) getNybble(object, index));
		result.hashOrZero(object.hashOrZero());
		return result;
	}

	/**
	 * Build a new object instance with room for size elements.
	 *
	 * @param size The number of elements for which to leave room.
	 * @return A mutable nybble tuple.
	 */
	public static AvailObject mutableObjectOfSize (final int size)
	{
		final NybbleTupleDescriptor d = descriptorFor(MUTABLE, size);
		assert (size + d.unusedNybblesOfLastLong & 15) == 0;
		return d.create((size + 15) >>> 4);
	}

	/**
	 * Answer the descriptor that has the specified mutability flag and is
	 * suitable to describe a tuple with the given number of elements.
	 *
	 * @param flag
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param size
	 *        How many elements are in a tuple to be represented by the
	 *        descriptor.
	 * @return
	 *        A {@code NybbleTupleDescriptor} suitable for representing a nybble
	 *        tuple of the given mutability and {@linkplain
	 *        AvailObject#tupleSize() size}.
	 */
	private static NybbleTupleDescriptor descriptorFor (
		final Mutability flag,
		final int size)
	{
		return descriptors[(size & 15) * 3 + flag.ordinal()];
	}
}
