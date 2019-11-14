/*
 * ObjectLayoutVariant.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.objects.A_BasicObject;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY;

/**
 * The {@link ObjectLayoutVariant}s capture field layouts for objects and object
 * types.  An object or object type's descriptor refers to a variant, and the
 * variant contains a mapping from each present field atom to the slot number
 * within the object or object type.  All objects or object types with a
 * particular set of field atoms have the same variant.
 *
 * @see ObjectDescriptor
 * @see ObjectTypeDescriptor
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ObjectLayoutVariant
{
	/**
	 * The number of slots to allocate in an object or object type to
	 * accommodate the real fields.  This excludes the keys that were created
	 * solely for the purpose of explicit subclassing.  This value is always the
	 * largest value in fieldToSlotIndex.
	 */
	final int realSlotCount;

	/**
	 * A unique int suitable for distinguishing variants.  This may become more
	 * useful when Level Two code needs to track metrics and create variant
	 * specific versions of code without garbage collection complexity.
	 */
	final int variantId;

	/**
	 * The set of all fields present in this variant.  This includes not just
	 * the real fields that can hold multiple potential values, but also the
	 * fields that were created solely for the purpose of explicit subclassing.
	 */
	final A_Set allFields;

	/**
	 * The mapping from field atoms to slots.  The fields that are created just
	 * for making explicit subclasses all map to 0, which is not a valid slot.
	 */
	public final Map<A_Atom, Integer> fieldToSlotIndex;

	/**
	 * A {@link RawPojoDescriptor raw POJO} that wraps to this {@link
	 * ObjectLayoutVariant}.  Makes it convenient to capture the variant in an
	 * object register in Level Two code.
	 */
	final A_BasicObject thisPojo;

	/** The mutable object descriptor for this variant. */
	final ObjectDescriptor mutableObjectDescriptor;

	/** The immutable object descriptor for this variant. */
	final ObjectDescriptor immutableObjectDescriptor;

	/** The shared object descriptor for this variant. */
	final ObjectDescriptor sharedObjectDescriptor;

	/** The mutable object type descriptor for this variant. */
	final ObjectTypeDescriptor mutableObjectTypeDescriptor;

	/** The immutable object type descriptor for this variant. */
	final ObjectTypeDescriptor immutableObjectTypeDescriptor;

	/** The shared object type descriptor for this variant. */
	final ObjectTypeDescriptor sharedObjectTypeDescriptor;

	/**
	 * Create a new {@link ObjectLayoutVariant} for the given set of fields.
	 *
	 * @param allFields
	 *        The complete {@link A_Set set} of {@link A_Atom atom}s that are
	 *        present in objects and object types that will use the new variant.
	 * @param variantId
	 *        An {@code int} unique to this variant, allocated from the
	 *        {@link #variantsCounter} while holding the writeLock of the
	 *        {@link #variantsLock}.
	 */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	private ObjectLayoutVariant (final A_Set allFields, final int variantId)
	{
		this.allFields = allFields.makeShared();
		final A_Atom explicitSubclassingKey = EXPLICIT_SUBCLASSING_KEY.atom;
		// Alphabetize the fields to make debugging nice.  Note that field names
		// don't have to be lexicographically unique.
		final List<A_Atom> sortedFields = toList(allFields.asTuple());
		sortedFields.sort(
			Comparator.comparing(atom -> atom.atomName().asNativeString()));
		this.fieldToSlotIndex = new HashMap<>(sortedFields.size());
		int slotCount = 0;
		for (final A_Atom field : sortedFields)
		{
			final boolean isReal =
				field.getAtomProperty(explicitSubclassingKey).equalsNil();
			fieldToSlotIndex.put(field, isReal ? ++slotCount : 0);
		}
		this.realSlotCount = slotCount;
		this.variantId = variantId;
		thisPojo = identityPojo(this);
		this.mutableObjectDescriptor =
			new ObjectDescriptor(Mutability.MUTABLE, this);
		this.immutableObjectDescriptor =
			new ObjectDescriptor(Mutability.IMMUTABLE, this);
		this.sharedObjectDescriptor =
			new ObjectDescriptor(Mutability.SHARED, this);
		this.mutableObjectTypeDescriptor =
			new ObjectTypeDescriptor(Mutability.MUTABLE, this);
		this.immutableObjectTypeDescriptor =
			new ObjectTypeDescriptor(Mutability.IMMUTABLE, this);
		this.sharedObjectTypeDescriptor =
			new ObjectTypeDescriptor(Mutability.SHARED, this);
	}

	/** The collection of all variants, indexed by the set of field atoms. */
	private static final Map<A_Set, ObjectLayoutVariant> allVariants =
		new HashMap<>();

	/** The lock used to protect access to the allVariants map. */
	private static final ReadWriteLock variantsLock =
		new ReentrantReadWriteLock();

	/**
	 * A monotonically increasing counter for allocating a unique {@link
	 * #variantId} for each variant.  Should only be accessed while holding
	 * the variantsLock's writeLock.
	 */
	private static int variantsCounter = 0;

	/**
	 * Look up or create a variant for the given set of fields ({@link
	 * A_Atom}s).
	 *
	 * @param allFields
	 *        The set of fields for which a variant is requested.
	 * @return The lookup for that set of fields.
	 */
	static ObjectLayoutVariant variantForFields (final A_Set allFields)
	{
		variantsLock.readLock().lock();
		try
		{
			final ObjectLayoutVariant variant = allVariants.get(allFields);
			if (variant != null)
			{
				// By far the most likely path.
				return variant;
			}
		}
		finally
		{
			variantsLock.readLock().unlock();
		}

		// Didn't find it while holding the read lock.  We could create it
		// outside of the lock, then test for its presence again inside the
		// write lock, abandoning it for the existing one if found.  Instead,
		// hold the write lock, test again, and create and add if necessary.
		variantsLock.writeLock().lock();
		try
		{
			// Check if someone added it between our read and write locks.
			final ObjectLayoutVariant theirVariant = allVariants.get(allFields);
			if (theirVariant != null)
			{
				return theirVariant;
			}
			final ObjectLayoutVariant variant =
				new ObjectLayoutVariant(allFields, ++variantsCounter);
			allVariants.put(allFields, variant);
			return variant;
		}
		finally
		{
			variantsLock.writeLock().unlock();
		}
	}
}
