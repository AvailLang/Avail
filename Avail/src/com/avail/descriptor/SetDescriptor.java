/**
 * SetDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
 * Hash Tree. If the set is empty, the root is {@linkplain NilDescriptor
 * nil}, which may not be a member of a set. If the set has one element, the
 * root is the element itself. If the set has two or more elements then a
 * {@linkplain SetBinDescriptor bin} must be used.  There are two types of bin,
 * the {@linkplain LinearSetBinDescriptor linear bin} and the {@linkplain
 * HashedSetBinDescriptor hashed bin}. The linear bin is used below a threshold
 * size, after which a hashed bin is substituted. The linear bin simply
 * contains an arbitrarily ordered list of elements, which are examined
 * exhaustively when testing set membership. The hashed bin uses up to five
 * bits of an element's {@link AvailObject#hash() hash value} to partition
 * the set. The depth of the bin in the hash tree determines which hash bits
 * are used.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class SetDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The topmost bin of this set.  If it's {@link NilDescriptor null},
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
	private static AvailObject rootBin (final AvailObject object)
	{
		return object.slot(ObjectSlots.ROOT_BIN);
	}

	/**
	 * Replace the {@linkplain SetDescriptor set}'s root {@linkplain
	 * SetBinDescriptor bin}. The replacement may be the {@link
	 * NilDescriptor#nil() nil} to indicate an empty map.
	 *
	 * @param object
	 *        The set (must not be an indirection).
	 * @param bin
	 *        The root bin for the set, or nil.
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
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.equalsSet(object);
	}

	@Override @AvailMethod
	boolean o_EqualsSet (final AvailObject object, final AvailObject aSet)
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
		// TODO: [MvG] Unfortunately, this also has time at least proportional
		// to the set sizes, even if the sets are virtually identical and share
		// structural subcomponents.  We can do better, ostensibly by navigating
		// the tries together, checking for structural sharing.
		if (!rootBin(object).isBinSubsetOf(aSet))
		{
			return false;
		}
		if (!isShared())
		{
			aSet.makeImmutable();
			object.becomeIndirectionTo(aSet);
		}
		else if (!aSet.descriptor.isShared())
		{
			object.makeImmutable();
			aSet.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
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
	int o_Hash (final AvailObject object)
	{
		// A set's hash is a simple function of its rootBin's binHash, which is
		// always the sum of its elements' hashes.
		return rootBin(object).binHash() ^ 0xCD9EFC6;
	}

	@Override @AvailMethod
	boolean o_IsSet (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
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
		return rootBin(object).binHasElementWithHash(
			elementObject, elementObject.hash());
	}

	@Override @AvailMethod
	boolean o_IsSubsetOf (final AvailObject object, final AvailObject another)
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
		// Compute the intersection of two sets. May destroy one of them if
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
		boolean intersected = false;
		AvailObject result = smaller;
		for (final AvailObject element : smaller)
		{
			if (!larger.hasElement(element))
			{
				if (!intersected)
				{
					assert result == smaller;
					result.makeImmutable();
					intersected = true;
				}
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
		boolean intersected = false;
		AvailObject result = object;
		for (final AvailObject element : object)
		{
			if (otherSet.hasElement(element))
			{
				if (!intersected)
				{
					assert result == object;
					result.makeImmutable();
					intersected = true;
				}
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
		// Compute the union of two sets. May destroy one of them if it's
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
		// Ensure newElementObject is in the set, adding it if necessary. May
		// destroy the set if it's mutable and canDestroy is true.
		final int elementHash = newElementObject.hash();
		final AvailObject root = rootBin(object);
		final int oldSize = root.binSize();
		final AvailObject newRootBin =
			root.setBinAddingElementHashLevelCanDestroy(
				newElementObject,
				elementHash,
				(byte)0,
				canDestroy & isMutable());
		if (newRootBin.binSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		AvailObject result;
		if (canDestroy & isMutable())
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
		// Ensure elementObjectToExclude is not in the set, removing it if
		// necessary. May destroy the set if it's mutable and canDestroy is
		// true.
		final AvailObject root = rootBin(object);
		final int oldSize = root.binSize();
		final AvailObject newRootBin = root.binRemoveElementHashCanDestroy(
			elementObjectToExclude,
			elementObjectToExclude.hash(),
			(canDestroy & isMutable()));
		if (newRootBin.binSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		AvailObject result;
		if (canDestroy & isMutable())
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
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	/**
	 * A {@link SetIterator} is returned when a {@link SetDescriptor set} is
	 * asked for its {@link AvailObject#iterator()}.  Among other uses, this is
	 * useful when combined with Java's "foreach" control structure.
	 */
	abstract static class SetIterator
	implements Iterator<AvailObject>
	{
		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	}

	@Override @AvailMethod
	SetIterator o_Iterator (final AvailObject object)
	{
		return rootBin(object).setBinIterator();
	}

	@Override @AvailMethod
	AvailObject o_AsTuple (final AvailObject object)
	{
		final int size = object.setSize();
		final AvailObject result = ObjectTupleDescriptor.mutable.create(size);
		for (int i = 1; i <= size; i++)
		{
			// Initialize it for when we use our own garbage collection again.
			result.tupleAtPut(i, NilDescriptor.nil());
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
	int o_SetSize (final AvailObject object)
	{
		// Answer how many elements are in the set. Delegate to the rootBin.
		return rootBin(object).binSize();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SET;
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
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SetDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link SetDescriptor}. */
	private static final SetDescriptor mutable =
		new SetDescriptor(Mutability.MUTABLE);

	@Override
	SetDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link SetDescriptor}. */
	private static final SetDescriptor immutable =
		new SetDescriptor(Mutability.IMMUTABLE);

	@Override
	SetDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SetDescriptor}. */
	private static final SetDescriptor shared =
		new SetDescriptor(Mutability.SHARED);

	@Override
	SetDescriptor shared ()
	{
		return shared;
	}

	/** The empty set. */
	private static AvailObject emptySet;

	/**
	 * Answer the empty set.
	 *
	 * @return The empty set.
	 */
	public static AvailObject empty ()
	{
		return emptySet;
	}

	/**
	 * Initialize the {@link #emptySet} static in addition to the usual statics.
	 */
	static void createWellKnownObjects ()
	{
		emptySet = mutable.create();
		rootBin(emptySet, NilDescriptor.nil());
		emptySet.hash();
		emptySet.makeShared();
	}

	/**
	 * Clear the {@link #emptySet} static in addition to the usual statics.
	 */
	static void clearWellKnownObjects ()
	{
		emptySet = null;
	}
}
