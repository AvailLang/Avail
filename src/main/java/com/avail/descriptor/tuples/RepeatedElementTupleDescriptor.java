/*
 * RepeatedElementTupleDescriptor.java
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
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;

import static com.avail.descriptor.representation.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.tuples.ByteStringDescriptor.generateByteString;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.*;
import static com.avail.descriptor.tuples.RepeatedElementTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.RepeatedElementTupleDescriptor.IntegerSlots.SIZE;
import static com.avail.descriptor.tuples.RepeatedElementTupleDescriptor.ObjectSlots.ELEMENT;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.tuples.TwoByteStringDescriptor.generateTwoByteString;

/**
 * {@code RepeatedElementTupleDescriptor} represents a tuple with a single
 * ELEMENT repeated SIZE times. Note that SIZE is the number of tuple slots
 * containing the element and is therefore the size of the tuple.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public final class RepeatedElementTupleDescriptor
extends TupleDescriptor
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
		HASH_AND_MORE;

		/**
		 * The number of elements in the tuple.
		 *
		 * The API's {@link AvailObject#tupleSize() tuple size accessor}
		 * currently returns a Java {@code int}, because there wasn't much of a
		 * problem limiting manually-constructed tuples to two billion elements.
		 * This restriction will eventually be removed.
		 */
		static final BitField SIZE = new BitField(HASH_AND_MORE, 32, 32);

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		static final BitField HASH_OR_ZERO = new BitField(HASH_AND_MORE, 0, 32);

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
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/** The element to be repeated. */
		ELEMENT
	}

	/**
	 * The minimum size for repeated element tuple creation. All tuples
	 * requested below this size will be created as standard tuples or the empty
	 * tuple.
	 */
	private static final int minimumRepeatSize = 2;

	@Override @AvailMethod
	public boolean o_IsRepeatedElementTuple(final AvailObject object)
	{
		return true;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final int size = object.slot(SIZE);
		if (size < minimumRepeatSize)
		{
			super.printObjectOnAvoidingIndent(
				object,
				aStream,
				recursionMap,
				indent);
		}
		else
		{
			aStream.append(size);
			aStream.append(" of ");
			object.slot(ELEMENT).printOnAvoidingIndent(
				aStream,
				recursionMap,
				indent + 1);
		}
	}

	@Override @AvailMethod
	protected A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Ensure parameters are in bounds
		final int oldSize = object.slot(SIZE);
		assert 1 <= start && start <= end + 1 && end <= oldSize;
		final int newSize = end - start + 1;

		// If the requested copy is a proper subrange, create it.
		if (newSize != oldSize)
		{
			if (isMutable() && canDestroy)
			{
				// Recycle the object.
				object.setSlot(SIZE, newSize);
				return object;
			}
			return createRepeatedElementTuple(newSize, object.slot(ELEMENT));
		}

		// Otherwise, this method is requesting a full copy of the original.
		if (isMutable() && !canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithRepeatedElementTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aRepeatedElementTuple))
		{
			// The objects refer to the same memory, and the lengths being
			// compared are the same, and we don't care about the offsets, so
			// they're equal.
			return true;
		}

		if (object.slot(ELEMENT).equals(aRepeatedElementTuple.tupleAt(1)))
		{
			// The elements are the same, so the subranges must be as well.
			// Coalesce equal tuples as a nicety.
			if (object.slot(SIZE) == aRepeatedElementTuple.tupleSize())
			{
				// Indirect one to the other if it is not shared.
				if (!isShared())
				{
					aRepeatedElementTuple.makeImmutable();
					object.becomeIndirectionTo(aRepeatedElementTuple);
				}
				else if (!aRepeatedElementTuple.descriptor().isShared())
				{
					object.makeImmutable();
					aRepeatedElementTuple.becomeIndirectionTo(object);
				}
			}
			// Regardless of the starting positions, the subranges are the same.
			return true;
		}

		// The elements differ, so the subranges must differ.
		return false;
	}

	@Override
	protected A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}

		// Assess the possibility that the concatenation will still be a
		// repeated element tuple.
		if (otherTuple.isRepeatedElementTuple())
		{
			final AvailObject otherDirect = otherTuple.traversed();
			final AvailObject element = object.slot(ELEMENT);

			// If the other's element is the same as mine,
			if (element.equals(otherDirect.slot(ELEMENT)))
			{
				// then we can be concatenated.
				final int newSize = object.slot(SIZE) +
					otherDirect.slot(SIZE);

				// If we can do replacement in place,
				// use me for the return value.
				if (isMutable())
				{
					object.setSlot(SIZE, newSize);
					object.hashOrZero(0);
					return object;
				}
				// Or the other one.
				if (otherTuple.descriptor().isMutable())
				{
					otherDirect.setSlot(SIZE, newSize);
					otherDirect.hashOrZero(0);
					return otherDirect;
				}

				// Otherwise, create a new repeated element tuple.
				return createRepeatedElementTuple(newSize, element);
			}
		}
		if (otherTuple.treeTupleLevel() == 0)
		{
			if (otherTuple.tupleSize() == 0)
			{
				// Trees aren't allowed to have empty subtuples.
				return object;
			}
			return createTwoPartTreeTuple(object, otherTuple, 1, 0);
		}
		return concatenateAtLeastOneTree(object, otherTuple, true);
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsRepeatedElementTuple(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aRepeatedElementTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aRepeatedElementTuple))
		{
			return true;
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		final AvailObject secondTraversed = aRepeatedElementTuple.traversed();

		// Check that the slots match.
		final int firstHash = object.slot(HASH_OR_ZERO);
		final int secondHash = secondTraversed.slot(HASH_OR_ZERO);
		if (firstHash != 0 && secondHash != 0 && firstHash != secondHash)
		{
			return false;
		}
		if (object.slot(SIZE) != secondTraversed.slot(SIZE))
		{
			return false;
		}
		if (!object.slot(ELEMENT).equals(secondTraversed.slot(ELEMENT)))
		{
			return false;
		}

		// All the slots match. Indirect one to the other if it is not shared.
		if (!isShared())
		{
			aRepeatedElementTuple.makeImmutable();
			object.becomeIndirectionTo(aRepeatedElementTuple);
		}
		else if (!aRepeatedElementTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aRepeatedElementTuple.becomeIndirectionTo(object);
		}
		return true;

	}

	@Override
	protected int o_BitsPerEntry (final AvailObject object)
	{
		// Consider a billion-element tuple. Since a repeated element tuple
		// requires only O(1) storage, irrespective of its size, the average
		// bits per entry is 0.
		return 0;
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// Every element in this tuple is identical.
		assert index >= 1 && index <= object.slot(SIZE);
		return object.slot(ELEMENT);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		final int size = object.slot(SIZE);
		assert index >= 1 && index <= size;
		final AvailObject element = object.slot(ELEMENT);
		if (element.equals(newValueObject))
		{
			// Replacement is the same as the repeating element.
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		if (size < 64)
		{
			// The result will be reasonably small, so make it flat.
			element.makeImmutable();
			@Nullable A_Tuple result = null;
			if (element.isInt())
			{
				// Make it a numeric tuple.
				result = tupleFromIntegerList(
					Collections.nCopies(size, element.extractInt()));
			}
			else if (element.isCharacter())
			{
				// Make it a string.
				final int codePoint = element.codePoint();
				if (codePoint <= 255)
				{
					result = generateByteString(size, ignored -> codePoint);
				}
				else if (codePoint <= 65535)
				{
					result = generateTwoByteString(size, ignored -> codePoint);
				}
			}
			if (result == null)
			{
				result = generateObjectTupleFrom(size, i -> element);
			}
			// Replace the element, which might need to switch representation in
			// some cases which we assume are infrequent.
			return result.tupleAtPuttingCanDestroy(index, newValueObject, true);
		}
		// Otherwise, a flat tuple would be unacceptably large, so use append
		// and concatenate to construct what will probably be a tree tuple.
		final A_Tuple left = object.copyTupleFromToCanDestroy(
			1, index - 1, false);
		final A_Tuple right = object.copyTupleFromToCanDestroy(
			index + 1, size, false);
		return left.appendCanDestroy(newValueObject, true).concatenateWith(
			right, true);
	}

	@Override @AvailMethod
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		if (object.slot(ELEMENT).equals(newElement))
		{
			final AvailObject result = canDestroy && isMutable()
				?  object
				: newLike(mutable, object, 0, 0);
			result.setSlot(SIZE, object.slot(SIZE) + 1);
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		// Transition to a tree tuple.
		final A_Tuple singleton = tuple(newElement);
		return object.concatenateWith(singleton, canDestroy);
	}

	@Override @AvailMethod
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		assert 1 <= index && index <= object.slot(SIZE);
		return object.slot(ELEMENT).extractInt();
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return object.slot(SIZE);
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return object.slot(ELEMENT).isInstanceOf(type);
	}

	/** The mutable {@link RepeatedElementTupleDescriptor}. */
	public static final RepeatedElementTupleDescriptor mutable =
		new RepeatedElementTupleDescriptor(Mutability.MUTABLE);

	@Override
	public RepeatedElementTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link RepeatedElementTupleDescriptor}. */
	private static final RepeatedElementTupleDescriptor immutable =
		new RepeatedElementTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	public RepeatedElementTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link RepeatedElementTupleDescriptor}. */
	private static final RepeatedElementTupleDescriptor shared =
		new RepeatedElementTupleDescriptor(Mutability.SHARED);

	@Override
	public RepeatedElementTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@code RepeatedElementTupleDescriptor}.
	 *
	 * @param mutability How its instances can be shared or modified.
	 */
	private RepeatedElementTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Create a new repeated element tuple according to the parameters.
	 *
	 * @param size The number of repetitions of the element.
	 * @param element The value to be repeated.
	 * @return The new repeated element tuple.
	 */
	public static A_Tuple createRepeatedElementTuple (
		final int size,
		final A_BasicObject element)
	{
		// If there are no members in the range, return the empty tuple.
		if (size == 0)
		{
			return emptyTuple();
		}

		// If there are fewer than minimumRepeatSize members in this tuple,
		// create a normal tuple with them in it instead.
		if (size < minimumRepeatSize)
		{
			return tupleFromList(Collections.nCopies(size, element));
		}

		// No other efficiency shortcuts. Create a repeated element tuple.
		return forceCreate(size, element);
	}

	/**
	 * Create a new RepeatedElement using the supplied arguments,
	 * regardless of the suitability of other representations.
	 *
	 * @param size The number of repetitions of the element.
	 * @param element The value to be repeated.
	 * @return The new repeated element tuple.
	 */
	static A_Tuple forceCreate (
		final int size,
		final A_BasicObject element)
	{
		final AvailObject repeatedElementTuple = mutable.create();
		repeatedElementTuple.setSlot(HASH_OR_ZERO, 0);
		repeatedElementTuple.setSlot(SIZE, size);
		repeatedElementTuple.setSlot(ELEMENT, element);
		return repeatedElementTuple;
	}
}
