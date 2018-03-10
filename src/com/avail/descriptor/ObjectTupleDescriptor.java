/*
 * ObjectTupleDescriptor.java
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
import com.avail.annotations.InnerAccess;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.IndexedGenerator;
import com.avail.utility.IteratorNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.ObjectTupleDescriptor.ObjectSlots.TUPLE_AT_;
import static com.avail.descriptor.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;

/**
 * This is a representation for {@linkplain TupleDescriptor tuples} that can
 * consist of arbitrary {@link AvailObject}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ObjectTupleDescriptor
extends TupleDescriptor
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
		 * The tuple elements themselves.
		 */
		TUPLE_AT_;
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	public static final int maximumCopySize = 32;

	@Override @AvailMethod
	A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		if (originalSize >= maximumCopySize)
		{
			// Transition to a tree tuple.
			final A_Tuple singleton = tuple(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		if (!canDestroy)
		{
			newElement.makeImmutable();
			if (isMutable())
			{
				object.makeImmutable();
			}
		}
		final AvailObject newTuple = newLike(mutable,
			object,
			1,
			0);
		newTuple.setSlot(TUPLE_AT_, originalSize + 1, newElement);
		newTuple.setSlot(HASH_OR_ZERO, 0);
		return newTuple;
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 64;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		// Compare sections of two object tuples.
		if (object.sameAddressAs(anObjectTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual entries.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (!object.tupleAt(index1).equals(anObjectTuple.tupleAt(index2)))
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
		return anotherObject.compareFromToWithObjectTupleStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass.  This method must produce the same value.
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = object.tupleAt(index).hash() ^ preToggle;
			hash = (hash + itemHash) * multiplier;
		}
		return hash;
	}

	@Override @AvailMethod
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
		if (newSize <= maximumCopySize)
		{
			// Copy the objects.
			final int deltaSlots = newSize - object.variableObjectSlotsCount();
			final AvailObject result =
				newLike(mutable(), object, deltaSlots, 0);
			result.setSlotsFromTuple(
				TUPLE_AT_, size1 + 1, otherTuple, 1, size2);
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
	 * Answer a mutable copy of object that holds arbitrary objects.
	 */
	@Override @AvailMethod
	A_Tuple o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		return newLike(mutable, object, 0, 0);
	}

	/**
	 * If a subrange ends up getting constructed from this object tuple then it
	 * may leak memory.  The references that are out of bounds of the subrange
	 * might no longer be semantically reachable by Avail, but Java won't be
	 * able to collect them.  Eventually we'll have an Avail-specific garbage
	 * collector again, at which point we'll solve this problem for real – along
	 * with many others, I'm sure.
	 */
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
			// Just copy the applicable entries out.  In theory we could use
			// newLike() if start is 1.
			final AvailObject result = createUninitialized(size);
			result.setSlotsFromObjectSlots(
				TUPLE_AT_, 1, object, TUPLE_AT_, start, size);
			if (canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			else
			{
				result.makeSubobjectsImmutable();
			}
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsObjectTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final A_Tuple anObjectTuple)
	{
		if (object.sameAddressAs(anObjectTuple))
		{
			return true;
		}
		if (o_TupleSize(object) != anObjectTuple.tupleSize())
		{
			return false;
		}
		if (o_Hash(object) != anObjectTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithObjectTupleStartingAt(
			1,
			object.tupleSize(),
			anObjectTuple,
			1))
		{
			return false;
		}
		if (anObjectTuple.isBetterRepresentationThan(object))
		{
			if (!isShared())
			{
				anObjectTuple.makeImmutable();
				object.becomeIndirectionTo(anObjectTuple);
			}
		}
		else
		{
			if (!anObjectTuple.descriptor().isShared())
			{
				object.makeImmutable();
				anObjectTuple.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsByteTuple (final AvailObject object)
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built object tuples that happen to only contain bytes onto the start
		// or end of other byte tuples.
		final int tupleSize = object.tupleSize();
		if (tupleSize <= 5)
		{
			for (int i = 1; i <= tupleSize; i++)
			{
				if (!object.slot(TUPLE_AT_, i).isUnsignedByte())
				{
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override @AvailMethod
	boolean o_IsIntTuple (final AvailObject object)
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built object tuples that happen to only contain ints onto the start
		// or end of other int tuples.
		final int tupleSize = object.tupleSize();
		if (tupleSize <= 5)
		{
			for (int i = 1; i <= tupleSize; i++)
			{
				if (!object.slot(TUPLE_AT_, i).isInt())
				{
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * A simple {@link Iterator} over an object-tuple's elements.
	 */
	private static final class ObjectTupleIterator
	implements IteratorNotNull<AvailObject>
	{
		/**
		 * The tuple over which to iterate.
		 */
		private final AvailObject tuple;

		/**
		 * The size of the tuple.
		 */
		private final int size;

		/**
		 * The index of the next {@linkplain AvailObject element}.
		 */
		int index = 1;

		/**
		 * Construct a new {@code ObjectTupleIterator} on the given {@linkplain
		 * TupleDescriptor tuple}, which must be have an {@linkplain
		 * ObjectTupleDescriptor} as its descriptor.
		 *
		 * @param tuple The tuple to iterate over.
		 */
		@InnerAccess ObjectTupleIterator (final AvailObject tuple)
		{
			this.tuple = tuple;
			this.size = tuple.tupleSize();
		}

		@Override
		public boolean hasNext ()
		{
			return index <= size;
		}

		@Override
		public AvailObject next ()
		{
			if (index > size)
			{
				throw new NoSuchElementException();
			}

			// It's safe to access the slot directly.  If the tuple is mutable
			// or immutable, no other thread can be changing it (and the caller
			// shouldn't while iterating), and if the tuple is shared, its
			// descriptor cannot be changed.
			return tuple.slot(TUPLE_AT_, index++);
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public IteratorNotNull<AvailObject> o_Iterator (final AvailObject object)
	{
		object.makeImmutable();
		return new ObjectTupleIterator(object);
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int subscript)
	{
		return object.slot(TUPLE_AT_, subscript);
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
		final AvailObject result;
		if (canDestroy && isMutable())
		{
			result = object;
		}
		else
		{
			result = newLike(mutable, object, 0, 0);
			if (isMutable())
			{
				result.setSlot(TUPLE_AT_, index, nil);
				result.makeSubobjectsImmutable();
			}
		}
		result.setSlot(TUPLE_AT_, index, newValueObject);
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse (final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size >= maximumCopySize)
		{
			return super.o_TupleReverse(object);
		}
		return generateReversedFrom(
			size, i -> object.slot(TUPLE_AT_, size + 1 - i));
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object (as a Java int).
		return object.variableObjectSlotsCount();
	}

	/**
	 * Create an {@code ObjectTupleDescriptor object tuple} whose slots
	 * have not been initialized.
	 *
	 * @param size The number of elements in the resulting tuple.
	 * @return An uninitialized object tuple of the requested size.
	 */
	private static AvailObject createUninitialized (final int size)
	{
		return mutable.create(size);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code ObjectTupleDescriptor}.  Run the generator for each position in
	 * ascending order to produce the {@link AvailObject}s with which to
	 * populate the tuple.
	 *
	 * @param size The size of the object tuple to create.
	 * @param generator A generator to provide {@link AvailObject}s to store.
	 * @return The new object tuple.
	 */
	public static AvailObject generateObjectTupleFrom (
		final int size,
		final IndexedGenerator<? extends A_BasicObject> generator)
	{
		final AvailObject result = createUninitialized(size);
		// Initialize it for safe GC within the loop below.  Might be
		// unnecessary if the substrate already initialized it safely.
		result.fillSlots(TUPLE_AT_, 1, size, nil);
		for (int i = 1; i <= size; i++)
		{
			result.setSlot(TUPLE_AT_, i, generator.value(i));
		}
		return result;
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@code ObjectTupleDescriptor}.  Run the generator for each position in
	 * descending order (passing a descending index) to produce the {@link
	 * AvailObject}s with which to populate the tuple.
	 *
	 * @param size The size of the object tuple to create.
	 * @param generator A generator to provide {@link AvailObject}s to store.
	 * @return The new object tuple.
	 */
	public static AvailObject generateReversedFrom (
		final int size,
		final IndexedGenerator<? extends A_BasicObject> generator)
	{
		final AvailObject result = createUninitialized(size);
		// Initialize it for safe GC within the loop below.  Might be
		// unnecessary if the substrate already initialized it safely.
		result.fillSlots(TUPLE_AT_, 1, size, nil);
		for (int i = size; i >= 1; i--)
		{
			result.setSlot(TUPLE_AT_, i, generator.value(i));
		}
		return result;
	}

	/**
	 * Create a tuple with the specified elements. The elements are not made
	 * immutable first, nor is the new tuple.
	 *
	 * @param elements
	 *        The array of Avail values from which to construct a tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tupleFromArray (
		final A_BasicObject... elements)
	{
		final int size = elements.length;
		if (size == 0)
		{
			return emptyTuple();
		}
		final AvailObject result = createUninitialized(size);
		result.setSlotsFromArray(TUPLE_AT_, 1, elements, 0, size);
		return result;
	}

	/**
	 * Create a tuple with the specified sole element. The element is not made
	 * immutable first, nor is the new tuple.
	 *
	 * @param element1
	 *        The value for the first element of the tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tuple (
		final A_BasicObject element1)
	{
		final AvailObject result = createUninitialized(1);
		result.setSlot(TUPLE_AT_, 1, element1);
		return result;
	}

	/**
	 * Create a tuple with the specified two elements. The elements are not made
	 * immutable first, nor is the new tuple.
	 *
	 * @param element1
	 *        The value for the first element of the tuple.
	 * @param element2
	 *        The value for the second element of the tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tuple (
		final A_BasicObject element1,
		final A_BasicObject element2)
	{
		final AvailObject result = createUninitialized(2);
		result.setSlot(TUPLE_AT_, 1, element1);
		result.setSlot(TUPLE_AT_, 2, element2);
		return result;
	}

	/**
	 * Create a tuple with the specified three elements. The elements are not
	 * made immutable first, nor is the new tuple.
	 *
	 * @param element1
	 *        The value for the first element of the tuple.
	 * @param element2
	 *        The value for the second element of the tuple.
	 * @param element3
	 *        The value for the third element of the tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tuple (
		final A_BasicObject element1,
		final A_BasicObject element2,
		final A_BasicObject element3)
	{
		final AvailObject result = createUninitialized(3);
		result.setSlot(TUPLE_AT_, 1, element1);
		result.setSlot(TUPLE_AT_, 2, element2);
		result.setSlot(TUPLE_AT_, 3, element3);
		return result;
	}

	/**
	 * Create a tuple with the specified four elements. The elements are not
	 * made immutable first, nor is the new tuple.
	 *
	 * @param element1
	 *        The value for the first element of the tuple.
	 * @param element2
	 *        The value for the second element of the tuple.
	 * @param element3
	 *        The value for the third element of the tuple.
	 * @param element4
	 *        The value for the fourth element of the tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tuple (
		final A_BasicObject element1,
		final A_BasicObject element2,
		final A_BasicObject element3,
		final A_BasicObject element4)
	{
		final AvailObject result = createUninitialized(4);
		result.setSlot(TUPLE_AT_, 1, element1);
		result.setSlot(TUPLE_AT_, 2, element2);
		result.setSlot(TUPLE_AT_, 3, element3);
		result.setSlot(TUPLE_AT_, 4, element4);
		return result;
	}

	/**
	 * Create a tuple with the specified five elements. The elements are not
	 * made immutable first, nor is the new tuple.
	 *
	 * @param element1
	 *        The value for the first element of the tuple.
	 * @param element2
	 *        The value for the second element of the tuple.
	 * @param element3
	 *        The value for the third element of the tuple.
	 * @param element4
	 *        The value for the fourth element of the tuple.
	 * @param element5
	 *        The value for the fifth element of the tuple.
	 * @return The new mutable tuple.
	 */
	@ReferencedInGeneratedCode
	public static A_Tuple tuple (
		final A_BasicObject element1,
		final A_BasicObject element2,
		final A_BasicObject element3,
		final A_BasicObject element4,
		final A_BasicObject element5)
	{
		final AvailObject result = createUninitialized(5);
		result.setSlot(TUPLE_AT_, 1, element1);
		result.setSlot(TUPLE_AT_, 2, element2);
		result.setSlot(TUPLE_AT_, 3, element3);
		result.setSlot(TUPLE_AT_, 4, element4);
		result.setSlot(TUPLE_AT_, 5, element5);
		return result;
	}

	/**
	 * Construct a new tuple of arbitrary {@linkplain AvailObject Avail objects}
	 * passed in a list.  The elements are not made immutable first, nor is the
	 * new tuple necessarily made immutable.
	 *
	 * @param list
	 *        The list of {@linkplain AvailObject Avail objects} from which
	 *        to construct a tuple.
	 * @return The corresponding tuple of objects.
	 */
	public static <E extends A_BasicObject> A_Tuple tupleFromList (
		final List<E> list)
	{
		final int size = list.size();
		if (size == 0)
		{
			return emptyTuple();
		}
		final AvailObject result = createUninitialized(size);
		result.setSlotsFromList(TUPLE_AT_, 1, list, 0, size);
		return result;
	}

	/**
	 * Construct a new {@code ObjectTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ObjectTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@code ObjectTupleDescriptor}. */
	public static final ObjectTupleDescriptor mutable =
		new ObjectTupleDescriptor(Mutability.MUTABLE);

	@Override
	ObjectTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@code ObjectTupleDescriptor}. */
	private static final ObjectTupleDescriptor immutable =
		new ObjectTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	ObjectTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@code ObjectTupleDescriptor}. */
	private static final ObjectTupleDescriptor shared =
		new ObjectTupleDescriptor(Mutability.SHARED);

	@Override
	ObjectTupleDescriptor shared ()
	{
		return shared;
	}
}
