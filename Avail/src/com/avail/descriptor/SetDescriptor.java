/**
 * SetDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * An Avail {@linkplain SetDescriptor set} refers to the root of a Bagwell Ideal
 * Hash Tree.  If the set is empty, the root is {@linkplain NullDescriptor the
 * null object}, which may not be a member of a set.  If the set has one
 * element, the root is the element itself.  If the set has two or more elements
 * then a {@linkplain SetBinDescriptor bin} must be used.  There are two types
 * of bin, the {@linkplain LinearSetBinDescriptor linear bin} and the
 * {@linkplain HashedSetBinDescriptor hashed bin}.  The linear bin is used below
 * a threshold size, after which a hashed bin is substituted.  The linear bin
 * simply contains an arbitrarily ordered list of elements, which are examined
 * exhaustively when testing set membership.  The hashed bin uses up to five
 * bits of an element's {@link AvailObject#hash(int) hash value} to partition
 * the set.  The depth of the bin in the hash tree determines which hash bits
 * are used.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SetDescriptor extends Descriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The topmost bin of this set.  If it's {@link NullDescriptor null},
		 * the set is empty.  If it's a {@link SetBinDescriptor set bin} then
		 * the bin contains the elements.  Otherwise the set contains one
		 * element, the object in this field.
		 */
		ROOT_BIN
	}

	/**
	 * Extract the root {@linkplain SetBinDescriptor bin} from the {@linkplain
	 * SetDescriptor set}.
	 *
	 * @param object The set from which to extract the root bin.
	 * @return The set's bin.
	 */
	private static AvailObject rootBin (
		final AvailObject object)
	{
		return object.slot(ObjectSlots.ROOT_BIN);
	}

	/**
	 * Replace the {@linkplain SetDescriptor set}'s root {@linkplain
	 * SetBinDescriptor bin}.  The replacement may be the {@link
	 * NullDescriptor#nullObject() null object} to indicate an empty map.
	 *
	 * @param object
	 *            The set (must not be an indirection).
	 * @param bin
	 *            The root bin for the set, or the null object.
	 */
	private static void rootBin (
		final AvailObject object,
		final AvailObject bin)
	{
		object.setSlot(ObjectSlots.ROOT_BIN, bin);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject tuple = object.asTuple();
		if (tuple.tupleSize() == 0)
		{
			aStream.append('∅');
		}
		else
		{
			aStream.append('{');
			for (int i = 1, limit = tuple.tupleSize(); i <= limit; i++)
			{
				if (i != 1)
				{
					aStream.append(", ");
				}
				tuple.tupleAt(i).printOnAvoidingIndent(
					aStream, recursionList, indent + 1);
			}
			aStream.append('}');
		}
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsSet(object);
	}

	@Override @AvailMethod
	boolean o_EqualsSet (
		final AvailObject object,
		final AvailObject aSet)
	{
		// First eliminate the trivial case of different sizes.
		if (object.setSize() != aSet.setSize())
		{
			return false;
		}
		if (object.hash() != aSet.hash())
		{
			return false;
		}
		// Unfortunately, this also has time at least proportional to the set
		// sizes, even if the sets are virtually identical and share structural
		// subcomponents.  We can do better, ostensibly by navigating the tries
		// together, checking for structural sharing.
		if (!rootBin(object).isBinSubsetOf(aSet))
		{
			return false;
		}
		// They're equal.  Collapse them together.
		object.becomeIndirectionTo(aSet);
		// Note that it now has at least two references.
		object.makeImmutable();
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(ANY))
		{
			return true;
		}
		if (!aTypeObject.isSetType())
		{
			return false;
		}
		// See if it's an acceptable size...
		if (!aTypeObject.sizeRange().rangeIncludesInt(object.setSize()))
		{
			return false;
		}
		final AvailObject expectedContentType = aTypeObject.contentType();
		if (expectedContentType.isEnumeration())
		{
			// Check the complete membership.
			for (final AvailObject member : object)
			{
				if (!expectedContentType.enumerationIncludesInstance(member))
				{
					return false;
				}
			}
			return true;
		}
		return rootBin(object).binElementsAreAllInstancesOfKind(
			expectedContentType);
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		// A set's hash is a simple function of its rootBin's binHash, which is always the sum
		// of its elements' hashes.

		return rootBin(object).binHash() ^ 0xCD9EFC6;
	}

	@Override @AvailMethod
	boolean o_IsSet (
		final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		final int size = object.setSize();
		final AvailObject sizeRange = InstanceTypeDescriptor.on(
			IntegerDescriptor.fromInt(size));
		return SetTypeDescriptor.setTypeForSizesContentType(
			sizeRange,
			AbstractEnumerationTypeDescriptor.withInstances(object));
	}

	@Override @AvailMethod
	boolean o_HasElement (
		final AvailObject object,
		final AvailObject elementObject)
	{
		return rootBin(object).binHasElementWithHash(elementObject, elementObject.hash());
	}

	@Override @AvailMethod
	boolean o_IsSubsetOf (
		final AvailObject object,
		final AvailObject another)
	{
		// Check if object is a subset of another.
		if (object.setSize() > another.setSize())
		{
			return false;
		}
		return rootBin(object).isBinSubsetOf(another);
	}

	/**
	 * Answer whether all my elements are instances of the specified kind.
	 */
	@Override @AvailMethod
	boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		return rootBin(object).binElementsAreAllInstancesOfKind(kind);
	}

	@Override @AvailMethod
	AvailObject o_SetIntersectionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		// Compute the intersection of two sets.  May destroy one of them if
		// it's mutable and canDestroy is true.
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
		// Freeze smaller because we're going to create a new version with some
		// elements removed.  Technically we might be able to do slightly better
		// by freezing it the first time a removal is actually needed.
		smaller.makeImmutable();
		AvailObject result = smaller;
		for (final AvailObject element : smaller)
		{
			if (!larger.hasElement(element))
			{
				result = result.setWithoutElementCanDestroy(element, true);
			}
		}
		return result;
	}

	@Override @AvailMethod
	AvailObject o_SetMinusCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		// Compute the asymmetric difference of two sets (a \ b).  May destroy
		// one of them if it's mutable and canDestroy is true.

		// Freeze object because we're going to create a new version with some
		// elements removed.  Technically we might be able to do slightly better
		// by freezing it the first time a removal is actually needed.
		object.makeImmutable();
		AvailObject result = object;
		for (final AvailObject element : object)
		{
			if (otherSet.hasElement(element))
			{
				result = result.setWithoutElementCanDestroy(element, true);
			}
		}
		return result;
	}

	@Override @AvailMethod
	AvailObject o_SetUnionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		// Compute the union of two sets.  May destroy one of them if it's
		// mutable and canDestroy is true.

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
		if (smaller.setSize() == 0)
		{
			if (larger.descriptor().isMutable() & !canDestroy)
			{
				larger.makeImmutable();
			}
			return larger;
		}
		if (larger.descriptor().isMutable() & !canDestroy)
		{
			larger.makeImmutable();
		}
		AvailObject result = larger;
		for (final AvailObject element : smaller)
		{
			result = result.setWithElementCanDestroy(
				element.makeImmutable(),
				canDestroy);
		}
		return result;
	}

	@Override @AvailMethod
	AvailObject o_SetWithElementCanDestroy (
		final AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		// Ensure newElementObject is in the set, adding it if necessary.  May
		// destroy the set if it's mutable and canDestroy is true.
		final int elementHash = newElementObject.hash();
		final AvailObject root = rootBin(object);
		final int oldSize = root.binSize();
		final AvailObject newRootBin =
			root.setBinAddingElementHashLevelCanDestroy(
				newElementObject,
				elementHash,
				(byte)0,
				canDestroy & isMutable);
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
		rootBin(result, newRootBin);
		return result;
	}

	@Override @AvailMethod
	AvailObject o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		// Ensure elementObjectToExclude is not in the set, removing it if necessary.
		// May destroy the set if it's mutable and canDestroy is true.

		final AvailObject root = rootBin(object);
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
		rootBin(result, newRootBin);
		return result;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
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
		 * Construct a new {@link SetIterator} over the elements recursively
		 * contained in the given bin / null / single object.
		 *
		 * @see ObjectSlots#ROOT_BIN
		 * @param root The root bin over which to iterate.
		 */
		SetIterator (final AvailObject root)
		{
			followLeftmost(root);
		}

		/**
		 * Visit this bin or element.  In particular, travel down its left spine
		 * so that it's positioned at the leftmost descendant.
		 *
		 * @param binOrElement The bin or element to begin enumerating.
		 */
		private void followLeftmost (final AvailObject binOrElement)
		{
			if (binOrElement.equalsNull())
			{
				assert binStack.isEmpty();
				assert subscriptStack.isEmpty();
			}
			else
			{
				binStack.addLast(binOrElement);
				AvailObject currentBinOrElement = binOrElement;
				while (currentBinOrElement.isSetBin())
				{
					subscriptStack.addLast(1);
					currentBinOrElement = currentBinOrElement.binElementAt(1);
					binStack.addLast(currentBinOrElement);
				}
				assert binStack.size() == subscriptStack.size() + 1;
			}
		}

		@Override
		public AvailObject next ()
		{
			assert !binStack.isEmpty();
			final AvailObject result = binStack.removeLast();
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
					followLeftmost(
						binStack.getLast().binElementAt(subscript + 1));
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
	}

	@Override @AvailMethod
	Iterator<AvailObject> o_Iterator (
		final AvailObject object)
	{
		return new SetIterator(rootBin(object));
	}

	@Override @AvailMethod
	AvailObject o_AsTuple (
		final AvailObject object)
	{
		final int size = object.setSize();
		final AvailObject result = ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			// Initialize it for when do use our own garbage collection again.
			result.tupleAtPut(i, NullDescriptor.nullObject());
		}
		int index = 1;
		for (final AvailObject element : object)
		{
			result.tupleAtPut(index, element.makeImmutable());
			index++;
		}
		assert index == size + 1;
		result.hashOrZero(0);
		return result;
	}

	@Override @AvailMethod
	int o_SetSize (
		final AvailObject object)
	{
		// Answer how many elements are in the set.  Delegate to the rootBin.
		return rootBin(object).binSize();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.SET;
	}


	/**
	 * The empty set (immutable).
	 */
	static AvailObject EmptySet;

	/**
	 * Initialize the {@link #EmptySet} static in addition to the usual statics.
	 */
	static void createWellKnownObjects ()
	{
		final AvailObject empty = mutable().create();
		rootBin(empty, NullDescriptor.nullObject());
		empty.makeImmutable();
		EmptySet = empty;
	}

	/**
	 * Clear the {@link #EmptySet} static in addition to the usual statics.
	 */
	static void clearWellKnownObjects ()
	{
		EmptySet = null;
	}

	/**
	 * Answer the (immutable) empty set.
	 *
	 * @return The empty set.
	 */
	public static AvailObject empty ()
	{
		return EmptySet;
	}

	/**
	 * Construct a new {@linkplain SetDescriptor set} from the specified
	 * {@linkplain Collection collection} of {@linkplain AvailObject objects}.
	 * Neither the elements nor the resultant set are made immutable.
	 *
	 * @param collection A collection.
	 * @return A new mutable set containing the elements of the collection.
	 */
	public static AvailObject fromCollection (
		final Collection<AvailObject> collection)
	{
		AvailObject set = empty();
		for (final AvailObject element : collection)
		{
			set = set.setWithElementCanDestroy(element, true);
		}
		return set;
	}

	/**
	 * Construct a new {@link SetDescriptor}.
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
	private static final SetDescriptor mutable = new SetDescriptor(true);

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
	private static final SetDescriptor immutable = new SetDescriptor(false);

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
