/*
 * ObjectLayoutVariant.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.objects

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.objects.ObjectLayoutVariant.Companion.allVariants
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantsCounter
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantsLock
import avail.descriptor.representation.Mutability
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.isSubsetOf
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.utility.safeWrite
import java.lang.ref.SoftReference
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read

/**
 * The [ObjectLayoutVariant]s capture field layouts for objects and object
 * types.  An object or object type's descriptor refers to a variant, and the
 * variant contains a mapping from each present field atom to the slot number
 * within the object or object type.  All objects or object types with a
 * particular set of field atoms have the same variant.
 *
 * @property variantId
 *   A unique int suitable for distinguishing variants.  This may become more
 *   useful when Level Two code needs to track metrics and create variant
 *   specific versions of code without garbage collection complexity.  It's
 *   allocated from the [variantsCounter] while holding the [variantsLock].
 *
 * @constructor
 *
 * @param allFieldsSet
 *   The set of fields for which to produce an [ObjectLayoutVariant].  Only one
 *   variant may exist for each set of fields, so this constructor is private.
 *
 * @see ObjectDescriptor
 * @see ObjectTypeDescriptor
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ObjectLayoutVariant private constructor(
	allFieldsSet: A_Set,
	val variantId: Int)
{
	/**
	 * The set of all fields present in this variant.  This includes not just
	 * the real fields that can hold multiple potential values, but also the
	 * fields that were created solely for the purpose of explicit subclassing.
	 */
	val allFields: A_Set = allFieldsSet.makeShared()

	/**
	 * The [List] of [A_Atom]s for which to allocate slots in an object or
	 * object type. This only includes the real fields, and excludes the keys
	 * that were created solely for the purpose of explicit subclassing.  These
	 * slots are in the same order that the slots should have in the object or
	 * object type.
	 */
	val realSlots: List<A_Atom>

	/**
	 * The number of slots to allocate in an object or object type to
	 * accommodate the real fields.  This excludes the keys that were created
	 * solely for the purpose of explicit subclassing.  This value is always the
	 * largest value in fieldToSlotIndex.
	 */
	val realSlotCount: Int

	/**
	 * The mapping from field atoms to slots.  The fields that are created just
	 * for making explicit subclasses all map to 0, which is not a valid slot.
	 */
	val fieldToSlotIndex: Map<A_Atom, Int>

	init
	{
		val explicitSubclassingKey = SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom
		// Alphabetize the fields to make debugging nice.  Note that field names
		// don't have to be lexicographically unique.
		val sortedFields = allFields.sortedBy { it.atomName.asNativeString() }
		fieldToSlotIndex = mutableMapOf()
		var slotCount = 0
		realSlots = sortedFields.filter { field ->
			val isReal = field.getAtomProperty(explicitSubclassingKey).isNil
			fieldToSlotIndex[field] = if (isReal) ++slotCount else 0
			isReal
		}
		realSlotCount = slotCount
	}

	override fun toString(): String
	{
		return allFields
			.map { it.atomName.asNativeString() }
			.sorted()
			.joinToString(", ", "var#$variantId(", ")")
	}

	/** The mutable object descriptor for this variant. */
	val mutableObjectDescriptor = ObjectDescriptor(Mutability.MUTABLE, this)

	/** The immutable object descriptor for this variant. */
	val immutableObjectDescriptor = ObjectDescriptor(Mutability.IMMUTABLE, this)

	/** The shared object descriptor for this variant. */
	val sharedObjectDescriptor = ObjectDescriptor(Mutability.SHARED, this)

	/** The mutable object type descriptor for this variant. */
	val mutableObjectTypeDescriptor =
		ObjectTypeDescriptor(Mutability.MUTABLE, this)

	/** The immutable object type descriptor for this variant. */
	val immutableObjectTypeDescriptor =
		ObjectTypeDescriptor(Mutability.IMMUTABLE, this)

	/** The shared object type descriptor for this variant. */
	val sharedObjectTypeDescriptor =
		ObjectTypeDescriptor(Mutability.SHARED, this)

	/**
	 * Check whether an object type based on the receiver could be a subtype of
	 * a type based on [another], based only on the fields present in each.
	 */
	fun isSubvariantOf(another: ObjectLayoutVariant): Boolean
	{
		return another.allFields.isSubsetOf(allFields)
	}

	/** The most general object type using this variant. */
	val mostGeneralObjectType by lazy {
		ObjectTypeDescriptor.createUninitializedObjectType(this).let { type ->
			type.fillSlots(
				ObjectTypeDescriptor.ObjectSlots.FIELD_TYPES_,
				1,
				type.variableObjectSlotsCount(),
				ANY.o)
			type.makeShared()
		}
	}

	/**
	 * The [instanceMeta] of the most general object type using this variant.
	 */
	val mostGeneralObjectMeta by lazy {
		instanceMeta(mostGeneralObjectType)
	}

	companion object {
		/**
		 * The collection of all variants, indexed by the set of field atoms.
		 * The key is weak, but it's the canonical, shared set of fields for the
		 * variant.  The variant also holds a strong reference to this set, so
		 * it won't be removed while the variant is reachable.  The value of the
		 * map is a soft reference to the variant, allowing old variants to be
		 * garbage collected when neither the variant nor the canonical set of
		 * fields is referenced from outside this map.
		 *
		 * In theory this structure could thrash, if no object types (or object
		 * instances) for a particular combination of fields is kept around, but
		 * is periodically reconstructed from a list of field atoms.  This is
		 * not only unlikely, but also still correct.  Also, the soft references
		 * should significantly slow down the rate of thrash, preventing the
		 * [variantsCounter] from reaching [Int.MAX_VALUE] in all but the most
		 * pathological abuses.  When we switch to 64-bit int registers in L2,
		 * this counter will be astronomically safer.
		 */
		@GuardedBy("variantsLock")
		private val allVariants =
			WeakHashMap<A_Set, SoftReference<ObjectLayoutVariant>>()

		/** The lock used to protect access to the [allVariants] map. */
		private val variantsLock = ReentrantReadWriteLock()

		/**
		 * A monotonically increasing counter for allocating a unique
		 * [variantId] for each variant.  Should only be accessed while holding
		 * the [variantsLock]'s [writeLock][ReentrantReadWriteLock.writeLock].
		 *
		 * It isn't current protected against overflows, but we'll switch to
		 * 64-bit unboxed L2 registers before this becomes even remotely a
		 * problem.
		 */
		@GuardedBy("variantsLock")
		private var variantsCounter = 0

		/**
		 * Look up or create a variant for the given set of fields ([A_Atom]s).
		 *
		 * @param allFields
		 *   The [A_Set] of fields for which a variant is requested.
		 * @return
		 *   The lookup for that set of fields.
		 */
		fun variantForFields(allFields: A_Set): ObjectLayoutVariant {
			variantsLock.read {
				// By far the most likely path.
				allVariants[allFields]?.get()?.let { return it }
			}
			// Didn't find it while holding the read lock.  We could create it
			// outside of the lock, then test for its presence again inside the
			// write lock, abandoning it for the existing one if found.
			// Instead, hold the write lock, test again, and create and add if
			// necessary.
			return variantsLock.safeWrite {
				when (val theirVariant = allVariants[allFields]?.get()) {
					null -> ObjectLayoutVariant(allFields, ++variantsCounter)
						.also { allVariants[allFields] = SoftReference(it) }
					else -> theirVariant
				}
			}
		}
	}
}
