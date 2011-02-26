/**
 * descriptor/SetDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.CanAllocateObjects;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.NotNull;

public class SetDescriptor
extends Descriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		ROOT_BIN
	}

	@Override
	public void o_RootBin (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.ROOT_BIN, value);
	}

	@Override
	public @NotNull AvailObject o_RootBin (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ROOT_BIN);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject tuple = object.asTuple();
		aStream.append('{');
		for (int i = 1, limit = tuple.tupleSize(); i <= limit; i++)
		{
			if (i != 1)
			{
				aStream.append(", ");
			}
			tuple.tupleAt(i).printOnAvoidingIndent(aStream, recursionList, indent + 1);
		}
		aStream.append('}');
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsSet(object);
	}

	@Override
	public boolean o_EqualsSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSet)
	{
		//  First eliminate the trivial case of different sizes.

		if (object.setSize() != aSet.setSize())
		{
			return false;
		}
		if (object.hash() != aSet.hash())
		{
			return false;
		}
		//  Do a simple comparison, neglecting order of elements...
		//
		//  Unfortunately, this also has time at least proportional to the set sizes, even if the sets
		//  are virtually identical and share structural subcomponents.  We can do better,
		//  ostensibly by navigating the tries together, checking for structural sharing.
		return object.rootBin().isBinSubsetOf(aSet);
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ALL.o()))
		{
			return true;
		}
		if (!aTypeObject.isSetType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.fromInt(object.setSize());
		if (!size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		//  set's size is out of range.
		return object.rootBin().binUnionType().isSubtypeOf(aTypeObject.contentType());
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.  Not very efficient - usually optimized out via '_type<=_'.

		final int size = object.setSize();
		final AvailObject sizeRange = IntegerDescriptor.fromInt(size).type();
		final AvailObject unionType = object.rootBin().binUnionType();
		unionType.makeImmutable();
		return SetTypeDescriptor.setTypeForSizesContentType(sizeRange, unionType);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  A set's hash is a simple function of its rootBin's binHash, which is always the sum
		//  of its elements' hashes.

		return object.rootBin().binHash() ^ 0xCD9EFC6;
	}

	@Override
	public boolean o_IsSet (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable()).makeImmutable();
	}

	@Override
	public boolean o_HasElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject)
	{
		return object.rootBin().binHasElementHash(elementObject, elementObject.hash());
	}

	@Override
	public boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Check if object is a subset of another.

		if (object.setSize() > another.setSize())
		{
			return false;
		}
		return object.rootBin().isBinSubsetOf(another);
	}

	@Override
	public @NotNull AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		//  Compute the intersection of two sets.  May destroy one of them if it's mutable and canDestroy is true.

		AvailObject smaller;
		AvailObject larger;
		if (object.setSize() <= otherSet.setSize())
		{
			smaller = object;
			larger = otherSet.traversed();
		}
		else
		{
			larger = object;
			smaller = otherSet.traversed();
		}
		if (!canDestroy)
		{
			smaller.makeImmutable();
		}
		AvailObject result = smaller;
		//  Do something hokey for now - convert smaller to a tuple then iterate over it.
		//  The right answer probably involves creating a set visitor mechanism.
		final AvailObject smallerAsTuple = smaller.asTuple();
		for (int i = 1, _end1 = smallerAsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject element = smallerAsTuple.tupleAt(i);
			if (!larger.hasElement(element))
			{
				result = result.setWithoutElementCanDestroy(smallerAsTuple.tupleAt(i), true);
			}
		}
		return result;
	}

	@Override
	public @NotNull AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		//  Compute the asymmetric difference of two sets (a \ b).  May destroy one of them if it's
		//  mutable and canDestroy is true.

		if (!canDestroy)
		{
			object.makeImmutable();
		}
		AvailObject result = object;
		//  Do something hokey for now - convert object to a tuple then iterate over it.
		//  The right answer probably involves implementing a set visitor mechanism.
		final AvailObject objectAsTuple = object.asTuple();
		for (int i = 1, _end1 = objectAsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject element = objectAsTuple.tupleAt(i);
			if (otherSet.hasElement(element))
			{
				result = result.setWithoutElementCanDestroy(objectAsTuple.tupleAt(i), true);
			}
		}
		return result;
	}

	@Override
	public @NotNull AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		//  Compute the union of two sets.  May destroy one of them if it's mutable and canDestroy is true.

		AvailObject smaller;
		AvailObject larger;
		if (object.setSize() <= otherSet.setSize())
		{
			smaller = object;
			larger = otherSet.traversed();
		}
		else
		{
			larger = object;
			smaller = otherSet.traversed();
		}
		if (!canDestroy
				|| !smaller.descriptor().isMutable()
					&& !larger.descriptor().isMutable())
		{
			final AvailObject copy = mutable().create();
			copy.rootBin(larger.rootBin().makeImmutable());
			larger = copy;
		}
		AvailObject toModify;
		AvailObject toScan;
		if (larger.descriptor().isMutable())
		{
			toModify = larger;
			toScan = smaller;
		}
		else
		{
			toModify = smaller;
			toScan = larger;
		}
		//  Do something hokey for now - convert toScan to a tuple then iterate over it.
		//  The right answer probably involves creating a set visitor mechanism.
		final AvailObject toScanAsTuple = toScan.asTuple();
		for (int i = 1, _end1 = toScanAsTuple.tupleSize(); i <= _end1; i++)
		{
			toModify = toModify.setWithElementCanDestroy(toScanAsTuple.tupleAt(i), true);
		}
		return toModify;
	}

	@Override
	public @NotNull AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newElementObject,
		final boolean canDestroy)
	{
		//  Ensure newElementObject is in the set, adding it if necessary.  May destroy the
		//  set if it's mutable and canDestroy is true.

		final int elementHash = newElementObject.hash();
		final AvailObject root = object.rootBin();
		final int oldSize = root.binSize();
		final AvailObject newRootBin = root.binAddingElementHashLevelCanDestroy(
			newElementObject,
			elementHash,
			((byte)0),
			(canDestroy & isMutable));
		if (newRootBin.binSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		AvailObject result;
		if (canDestroy & isMutable)
		{
			result = object;
		}
		else
		{
			result = mutable().create();
		}
		result.rootBin(newRootBin);
		return result;
	}

	@Override
	public @NotNull AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		//  Ensure elementObjectToExclude is not in the set, removing it if necessary.
		//  May destroy the set if it's mutable and canDestroy is true.

		final AvailObject root = object.rootBin();
		final int oldSize = root.binSize();
		final AvailObject newRootBin = root.binRemoveElementHashCanDestroy(
			elementObjectToExclude,
			elementObjectToExclude.hash(),
			(canDestroy & isMutable));
		if (newRootBin.binSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		AvailObject result;
		if (canDestroy & isMutable)
		{
			result = object;
		}
		else
		{
			result = mutable().create();
		}
		result.rootBin(newRootBin);
		return result;
	}

	/**
	 * This is the {@link Iterator} subclass used to enumerate Avail {@linkplain
	 * SetDescriptor sets}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	private static class SetIterator implements Iterator<AvailObject>
	{
		/**
		 * The path through set bins to the current leaf non-bin.
		 */
		final Deque<AvailObject> binStack = new ArrayDeque<AvailObject>();

		/**
		 * The position navigated through each bin.  It should contain one
		 * fewer than the number of elements in binStack, except when they're
		 * both empty, indicating {@code !hasNext()}.
		 */
		final Deque<Integer> subscriptStack = new ArrayDeque<Integer>();

		/**
		 * Construct a new {@link SetIterator}.
		 *
		 * @param root
		 */
		SetIterator (final AvailObject root)
		{
			followLeftmost(root);
		}

		/**
		 * @param binOrElement
		 */
		private void followLeftmost (AvailObject binOrElement)
		{
			if (binOrElement.equalsVoid())
			{
				assert binStack.isEmpty();
				assert subscriptStack.isEmpty();
			}
			else
			{
				binStack.addLast(binOrElement);
				while (binOrElement.isSetBin())
				{
					subscriptStack.addLast(1);
					binOrElement = binOrElement.binElementAt(1);
					binStack.addLast(binOrElement);
				}
				assert binStack.size() == subscriptStack.size() + 1;
			}
		}

		@Override
		public AvailObject next ()
		{
			assert !binStack.isEmpty();
			AvailObject result = binStack.removeLast();
			assert binStack.size() == subscriptStack.size();
			while (true)
			{
				if (subscriptStack.isEmpty())
				{
					return result;
				}
				final int subscript = subscriptStack.getLast();
				final AvailObject bin = binStack.getLast().traversed();
				final int maxSubscript = bin.objectSlotsCount()
					- bin.descriptor().numberOfFixedObjectSlots;
				if (subscript != maxSubscript)
				{
					assert !binStack.isEmpty();
					subscriptStack.removeLast();
					subscriptStack.addLast(subscript + 1);
					assert binStack.size() == subscriptStack.size();
					followLeftmost(binStack.getLast().binElementAt(subscript + 1));
					assert binStack.size() == subscriptStack.size() + 1;
					return result;
				}
				subscriptStack.removeLast();
				binStack.removeLast();
				assert binStack.size() == subscriptStack.size();
			}
		}

		@Override
		public boolean hasNext ()
		{
			return !binStack.isEmpty();
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	};

	/**
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	@Override
	public @NotNull Iterator<AvailObject> o_Iterator (
		final @NotNull AvailObject object)
	{
		return new SetIterator(object.rootBin());
	}

	/**
	 * Convert me to a tuple.  The ordering will be arbitrary and unstable.
	 */
	@Override
	public @NotNull AvailObject o_AsTuple (
		final @NotNull AvailObject object)
	{
		final AvailObject result = ObjectTupleDescriptor.mutable().create(
			object.setSize());
		CanAllocateObjects(false);
		result.hashOrZero(0);
		final int pastEnd = object.rootBin().populateTupleStartingAt(result, 1);
		assert pastEnd == object.setSize() + 1;
		assert result.tupleSize() + 1 == pastEnd;
		CanAllocateObjects(true);
		return result;
	}

	@Override
	public int o_SetSize (
		final @NotNull AvailObject object)
	{
		//  Answer how many elements are in the set.  Delegate to the rootBin.

		return object.rootBin().binSize();
	}

	// Startup/shutdown

	static AvailObject EmptySet;

	static void createWellKnownObjects ()
	{
		//  Initialize my EmptySet class variable in addition to the usual classInstVars.

		EmptySet = mutable().create();
		EmptySet.rootBin(VoidDescriptor.voidObject());
		EmptySet.makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		//  Initialize my EmptySet class variable in addition to the usual classInstVars.

		EmptySet = null;
	}

	/* Object creation */
	public static AvailObject empty ()
	{
		return EmptySet;
	};

	/**
	 * Construct a new {@link ObjectMetaMetaDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected SetDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SetDescriptor}.
	 */
	private final static SetDescriptor mutable = new SetDescriptor(true);

	/**
	 * Answer the mutable {@link SetDescriptor}.
	 *
	 * @return The mutable {@link SetDescriptor}.
	 */
	public static SetDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SetDescriptor}.
	 */
	private final static SetDescriptor immutable = new SetDescriptor(false);

	/**
	 * Answer the immutable {@link SetDescriptor}.
	 *
	 * @return The immutable {@link SetDescriptor}.
	 */
	public static SetDescriptor immutable ()
	{
		return immutable;
	}
}
