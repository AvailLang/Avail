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

import com.avail.annotations.NotNull;
import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import java.util.Iterator;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

@ObjectSlots("rootBin")
public class SetDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field rootBin.
	 */
	@Override
	public void ObjectRootBin (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field rootBin.
	 */
	@Override
	public AvailObject ObjectRootBin (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		AvailObject tuple = object.asTuple();
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



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsSet(object);
	}

	@Override
	public boolean ObjectEqualsSet (
			final AvailObject object,
			final AvailObject aSet)
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
	public boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(Types.voidType.object()))
		{
			return true;
		}
		if (aTypeObject.equals(Types.all.object()))
		{
			return true;
		}
		if (!aTypeObject.isSetType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.setSize());
		if (!size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		//  set's size is out of range.
		return object.rootBin().binUnionType().isSubtypeOf(aTypeObject.contentType());
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Not very efficient - usually optimized out via '_type<=_'.

		final int size = object.setSize();
		final AvailObject sizeRange = IntegerDescriptor.objectFromInt(size).type();
		final AvailObject unionType = object.rootBin().binUnionType();
		unionType.makeImmutable();
		return SetTypeDescriptor.setTypeForSizesContentType(sizeRange, unionType);
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  A set's hash is a simple function of its rootBin's binHash, which is always the sum
		//  of its elements' hashes.

		return (object.rootBin().binHash() ^ 0xCD9EFC6);
	}

	@Override
	public boolean ObjectIsSet (
			final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable()).makeImmutable();
	}



	// operations-set

	@Override
	public boolean ObjectHasElement (
			final AvailObject object,
			final AvailObject elementObject)
	{
		return object.rootBin().binHasElementHash(elementObject, elementObject.hash());
	}

	@Override
	public boolean ObjectIsSubsetOf (
			final AvailObject object,
			final AvailObject another)
	{
		//  Check if object is a subset of another.

		if ((object.setSize() > another.setSize()))
		{
			return false;
		}
		return object.rootBin().isBinSubsetOf(another);
	}

	@Override
	public AvailObject ObjectSetIntersectionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		//  Compute the intersection of two sets.  May destroy one of them if it's mutable and canDestroy is true.

		AvailObject smaller;
		AvailObject larger;
		if ((object.setSize() <= otherSet.setSize()))
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
	public AvailObject ObjectSetMinusCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
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
	public AvailObject ObjectSetUnionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		//  Compute the union of two sets.  May destroy one of them if it's mutable and canDestroy is true.

		AvailObject smaller;
		AvailObject larger;
		if ((object.setSize() <= otherSet.setSize()))
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
				|| (!smaller.descriptor().isMutable()
					&& !larger.descriptor().isMutable()))
		{
			final AvailObject copy = AvailObject.newIndexedDescriptor(0, SetDescriptor.mutableDescriptor());
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
	public AvailObject ObjectSetWithElementCanDestroy (
			final AvailObject object,
			final AvailObject newElementObject,
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
			((byte)(0)),
			(canDestroy & isMutable));
		if ((newRootBin.binSize() == oldSize))
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
			result = AvailObject.newIndexedDescriptor(0, SetDescriptor.mutableDescriptor());
		}
		result.rootBin(newRootBin);
		return result;
	}

	@Override
	public AvailObject ObjectSetWithoutElementCanDestroy (
			final AvailObject object,
			final AvailObject elementObjectToExclude,
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
		if ((newRootBin.binSize() == oldSize))
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
			result = AvailObject.newIndexedDescriptor(0, SetDescriptor.mutableDescriptor());
		}
		result.rootBin(newRootBin);
		return result;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public @NotNull Iterator<AvailObject> ObjectIterator (
		final @NotNull AvailObject object)
	{
		return object.asTuple().iterator();
	}

	@Override
	public AvailObject ObjectAsTuple (
			final AvailObject object)
	{
		//  Convert me to a tuple.  The ordering will be arbitrary and unstable.

		final AvailObject result = AvailObject.newIndexedDescriptor(object.setSize(), ObjectTupleDescriptor.mutableDescriptor());
		CanAllocateObjects(false);
		result.hashOrZero(0);
		final int pastEnd = object.rootBin().populateTupleStartingAt(result, 1);
		assert (pastEnd == (object.setSize() + 1));
		assert ((result.tupleSize() + 1) == pastEnd);
		CanAllocateObjects(true);
		return result;
	}

	@Override
	public int ObjectSetSize (
			final AvailObject object)
	{
		//  Answer how many elements are in the set.  Delegate to the rootBin.

		return object.rootBin().binSize();
	}

	// Startup/shutdown

	static AvailObject EmptySet;

	static void createWellKnownObjects ()
	{
		//  Initialize my EmptySet class variable in addition to the usual classInstVars.

		EmptySet = AvailObject.newIndexedDescriptor(0, mutableDescriptor());
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
	private final static SetDescriptor mutableDescriptor = new SetDescriptor(true);

	/**
	 * Answer the mutable {@link SetDescriptor}.
	 *
	 * @return The mutable {@link SetDescriptor}.
	 */
	public static SetDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link SetDescriptor}.
	 */
	private final static SetDescriptor immutableDescriptor = new SetDescriptor(false);

	/**
	 * Answer the immutable {@link SetDescriptor}.
	 *
	 * @return The immutable {@link SetDescriptor}.
	 */
	public static SetDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
