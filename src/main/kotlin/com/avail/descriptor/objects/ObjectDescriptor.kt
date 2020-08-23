/*
 * ObjectDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package com.avail.descriptor.objects

import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.ThreadSafe
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.keysAsSet
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapIterable
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.objects.ObjectDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.objects.ObjectDescriptor.IntegerSlots.HASH_AND_MORE
import com.avail.descriptor.objects.ObjectDescriptor.ObjectSlots.FIELD_VALUES_
import com.avail.descriptor.objects.ObjectDescriptor.ObjectSlots.KIND
import com.avail.descriptor.objects.ObjectDescriptor.ObjectSlots.TYPE_VETTINGS_CACHE
import com.avail.descriptor.objects.ObjectLayoutVariant.Companion.variantForFields
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.namesAndBaseTypesForObjectType
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.hasElement
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.singletonSet
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.component1
import com.avail.descriptor.tuples.A_Tuple.Companion.component2
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.fieldTypeMap
import com.avail.descriptor.types.A_Type.Companion.hasObjectInstance
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.optimizer.L2Optimizer
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * Avail [user-defined&#32;object&#32;types][ObjectTypeDescriptor] are novel.
 * They consist of a [map][MapDescriptor] of keys (field name
 * [atoms][AtomDescriptor]) and their associated field [types][A_Type].
 * Similarly, user-defined objects consist of a map from field names to field
 * values. An object instance conforms to an object type if and only the
 * instance's field keys are a superset of the type's field keys, and for each
 * field key in common, the field value is an instance of the field type.
 *
 * To support code-splitting in the [L2Optimizer], and for a more compact
 * representation than a simple map, objects and object types are represented by
 * way of an [ObjectLayoutVariant], which, for any set of fields, defines a
 * unique layout into numbered slots which objects or object types having those
 * exact fields will provide.  The optimizer will eventually dispatch based on
 * the variant number, and capture that tested information for subsequent
 * alongside the type information, within [TypeRestriction]s.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the new descriptor.
 * @param variant
 *   The [ObjectLayoutVariant] for the new descriptor.
 *
 * @see ObjectTypeDescriptor
 * @see ObjectLayoutVariant
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ObjectDescriptor internal constructor(
	mutability: Mutability,
	val variant: ObjectLayoutVariant
) : Descriptor(
	mutability,
	TypeTag.OBJECT_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO].
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * A bit field to hold the cached hash value of an object.  If zero,
			 * then the hash value must be computed upon request.  Note that in
			 * the very rare case that the hash value actually equals zero, the
			 * hash value has to be computed every time it is requested.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [kind][ObjectTypeDescriptor] of the [object][ObjectDescriptor].
		 */
		KIND,

		/**
		 * A 0-, 2-, or 4-tuple containing results from previous instance tests
		 * against non-enumeration, [Mutability.SHARED] object types.
		 *
		 * The presence of an object type in element 1 (or 3, if present)
		 * indicates that this object is an instance of that object type.  The
		 * presence of an object type in element 2 (or 4, if present) indicates
		 * that this object is *not* an instance of that object type.
		 *
		 * This is purely a cache for performance, and should be treated as such
		 * by any custom garbage collector.  For now, we only capture object
		 * types that are [Mutability.SHARED], since type tests for method
		 * dispatching are always against shared object types, and that's the
		 * case we're attempting to speed up.
		 *
		 * Note that object types cache their hash value once computed.  The
		 * two sets can be quickly searched because different object types very
		 * rarely have equal hashes, and equal ones merge via indirections after
		 * a successful comparison.  Shared object types are placed in a
		 * canonical weak map to ensure these comparisons are fast.
		 *
		 * For memory safety, we bound the set sizes to some reasonably large
		 * value (to deal with large dispatch trees).  When this threshold is
		 * reached, we extend the tuple from two to four values, moving the
		 * first and second sets to the third and fourth, respectively,
		 * replacing the first and second with empty sets.  This is a simple
		 * approximate mechanism to retain the most commonly accessed vettings.
		 * If there are already four elements in the tuple, the previous third
		 * and fourth sets are simply discarded.  Note that all four sets must
		 * be examined to determine the result of a previous vetting.
		 */
		@HideFieldInDebugger
		TYPE_VETTINGS_CACHE,

		/**
		 * The values associated with keys for this object.  The assignment of
		 * object fields to these slots is determined by the descriptor's
		 * [variant].
		 */
		FIELD_VALUES_
	}

	public override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = (e === HASH_AND_MORE
		|| e === KIND
		|| e === TYPE_VETTINGS_CACHE)

	/**
	 * Show the fields nicely.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		val fields = mutableListOf<AvailObjectFieldHelper>()
		val otherAtoms = mutableListOf<A_Atom>()
		variant.fieldToSlotIndex.forEach { (fieldKey, index) ->
			when (index) {
				0 -> otherAtoms.add(fieldKey)
				else -> fields.add(
					AvailObjectFieldHelper(
						self,
						DebuggerObjectSlots("FIELD " + fieldKey.atomName()),
						-1,
						self.slot(FIELD_VALUES_, index)))
			}
		}
		fields.sortBy(AvailObjectFieldHelper::nameForDebugger)
		if (otherAtoms.isNotEmpty()) {
			fields.add(
				AvailObjectFieldHelper(
					self,
					DebuggerObjectSlots("SUBCLASS_FIELDS"),
					-1,
					tupleFromList(otherAtoms)))
		}
		return fields.toTypedArray()
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsObject(self)

	override fun o_EqualsObject(
		self: AvailObject,
		anObject: AvailObject
	): Boolean {
		if (self.sameAddressAs(anObject)) return true
		val otherDescriptor = anObject.descriptor() as ObjectDescriptor
		if (variant !== otherDescriptor.variant) {
			return false
		}
		// If one of the hashes is already computed, compute the other if
		// necessary, then compare the hashes to eliminate the vast majority of
		// the unequal cases.
		var myHash = self.slot(HASH_OR_ZERO)
		var otherHash = anObject.slot(HASH_OR_ZERO)
		when {
			myHash != 0 && otherHash == 0 -> otherHash = anObject.hash()
			otherHash != 0 && myHash == 0 -> myHash = self.hash()
		}
		when {
			myHash != otherHash -> return false
			// Hashes are equal (perhaps both still zero).  Compare fields,
			// which must be in corresponding positions because we share the
			// same variant.
			(1..self.variableObjectSlotsCount()).any {
				!self.slot(FIELD_VALUES_, it)
					.equals(anObject.slot(FIELD_VALUES_, it))
			} -> return false
			!isShared && self.slot(KIND).equalsNil() ->
				self.becomeIndirectionTo(anObject)
			!otherDescriptor.isShared -> anObject.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_FieldAt(self: AvailObject, field: A_Atom): AvailObject =
		// Fails with NullPointerException if key is not found.
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			0 -> field as AvailObject
			else -> self.slot(FIELD_VALUES_, slotIndex!!)
		}

	override fun o_FieldAtOrNull(
		self: AvailObject,
		field: A_Atom
	): AvailObject? =
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			null -> null
			0 -> field as AvailObject
			else -> self.slot(FIELD_VALUES_, slotIndex)
		}

	override fun o_FieldAtPuttingCanDestroy(
		self: AvailObject,
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	): A_BasicObject {
		if (!canDestroy && isMutable) {
			self.makeImmutable()
		}
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			null -> {
				// Make room for another slot and find/create the variant.
				val newFieldsSet =
					variant.allFields.setWithElementCanDestroy(field, false)
				val newVariant = variantForFields(newFieldsSet)
				val newVariantSlotMap = newVariant.fieldToSlotIndex
				return newVariant.mutableObjectDescriptor.create(
					newVariant.realSlotCount
				) {
					variant.fieldToSlotIndex.forEach { (key, value1) ->
						@Suppress("MapGetWithNotNullAssertionOperator")
						(setSlot(
							FIELD_VALUES_,
							newVariantSlotMap[key]!!,
							self.slot(FIELD_VALUES_, value1)))
					}
					@Suppress("MapGetWithNotNullAssertionOperator")
					val newVariantSlotIndex = newVariantSlotMap[field]!!
					if (newVariantSlotIndex != 0) {
						setSlot(FIELD_VALUES_, newVariantSlotIndex, value)
					}
					setSlot(KIND, nil)
					setSlot(TYPE_VETTINGS_CACHE, emptyTuple())
					setSlot(HASH_OR_ZERO, 0)
				}
			}
			0 -> {
				assert(value.equals(field))
				return self
			}
			else -> {
				// Replace an existing real field.
				return when {
					canDestroy && isMutable -> self
					else -> newLike(variant.mutableObjectDescriptor, self, 0, 0)
				}.apply {
					setSlot(FIELD_VALUES_, slotIndex, value)
					setSlot(KIND, nil)
					setSlot(TYPE_VETTINGS_CACHE, emptyTuple())
					setSlot(HASH_OR_ZERO, 0)
				}
			}
		}
	}

	override fun o_FieldMap(self: AvailObject): A_Map =
		// Warning: May be much slower than it was before ObjectLayoutVariant.
		variant.fieldToSlotIndex.entries.fold(emptyMap) {
			map, (field, slotIndex) ->
			map.mapAtPuttingCanDestroy(
				field,
				if (slotIndex == 0) field
				else self.slot(FIELD_VALUES_, slotIndex),
				true)
		}

	override fun o_FieldTuple(self: AvailObject): A_Tuple
	{
		val fieldIterator = variant.fieldToSlotIndex.entries.iterator()
		return generateObjectTupleFrom(variant.fieldToSlotIndex.size) {
			val (field, slotIndex) = fieldIterator.next()
			if (slotIndex == 0) tuple(field, field)
			else tuple(field, self.slot(FIELD_VALUES_, slotIndex))
		}.also { assert(!fieldIterator.hasNext()) }
	}

	override fun o_Hash(self: AvailObject): Int {
		val hash = self.slot(HASH_OR_ZERO)
		if (hash != 0) return hash
		// Don't lock if we're shared.  Multiple simultaneous computations
		// of *the same* value are benign races.
		return (1..self.variableObjectSlotsCount()).fold(variant.variantId) {
			h, i -> (h * multiplier) xor self.slot(FIELD_VALUES_, i).hash()
		}.also { self.setSlot(HASH_OR_ZERO, it) }
	}

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)) return true
		val typeTraversed = aType.traversed()
		val typeDescriptor = typeTraversed.descriptor()
		if (typeDescriptor !is ObjectTypeDescriptor) return false
		if (!typeDescriptor.isShared)
			return typeTraversed.hasObjectInstance(self)

		// We want to test this instance against a shared object type.  First,
		// search the vettings cache.
		val answer: Boolean
		var vettings: A_Tuple = self.slot(TYPE_VETTINGS_CACHE)
		val tupleSize = vettings.tupleSize()
		vettings =
			if (tupleSize == 0)
			{
				answer = typeTraversed.hasObjectInstance(self)
				if (answer) tuple(singletonSet(typeTraversed), emptySet)
				else tuple(emptySet, singletonSet(typeTraversed))
			}
			else
			{
				val set1: A_Set = vettings.tupleAt(1)
				if (set1.hasElement(typeTraversed)) return true
				val set2: A_Set = vettings.tupleAt(2)
				if (set2.hasElement(typeTraversed)) return false
				val set3: A_Set
				val set4: A_Set
				answer =
					if (tupleSize == 2)
					{
						set3 = emptySet
						set4 = emptySet
						typeTraversed.hasObjectInstance(self)
					}
					else
					{
						assert(tupleSize == 4)
						set3 = vettings.tupleAt(3)
						set4 = vettings.tupleAt(4)
						when
						{
							set3.hasElement(typeTraversed) -> true
							set4.hasElement(typeTraversed) -> false
							else -> typeTraversed.hasObjectInstance(self)
						}
					}
				val set = if (answer) set1 else set2
				when
				{
					set.setSize() < maximumVettingSetSize ->
						vettings.tupleAtPuttingCanDestroy(
							if (answer) 1 else 2,
							set.setWithElementCanDestroy(typeTraversed, true),
							true)
					answer ->
						tuple(singletonSet(typeTraversed), set2, set1, set4)
					else ->
						tuple(set1, singletonSet(typeTraversed), set3, set2)
				}
			}
		when (mutability)
		{
			Mutability.MUTABLE ->
				self.setSlot(TYPE_VETTINGS_CACHE, vettings)
			Mutability.IMMUTABLE ->
				self.setSlot(TYPE_VETTINGS_CACHE, vettings.makeImmutable())
			Mutability.SHARED ->
				self.setMutableSlot(TYPE_VETTINGS_CACHE, vettings.makeShared())
		}
		return answer
	}

	override fun o_Kind(self: AvailObject): A_Type {
		val kind = self.slot(KIND)
		if (!kind.equalsNil()) return kind
		self.makeImmutable()
		return variant.mutableObjectTypeDescriptor.createFromObject(self).also {
			// Make the object shared since it's being written to a mutable slot
			// of a shared object. Don't lock, since multiple threads would
			// compute equal values anyhow.
			self.setSlot(KIND, if (isShared) it.makeShared() else it)
		}
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.OBJECT

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("object") }
			at("map") {
				writeObject {
					variant.fieldToSlotIndex.forEach { (field, slotIndex) ->
						val value = when (slotIndex)
						{
							0 -> field
							else -> self.slot(FIELD_VALUES_, slotIndex)
						}
						field.atomName().writeTo(writer)
						value.writeTo(writer)
					}
				}
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("object") }
			at("map") {
				writeObject {
					variant.fieldToSlotIndex.forEach { (field, slotIndex) ->
						val value = when (slotIndex)
						{
							0 -> field
							else -> self.slot(FIELD_VALUES_, slotIndex)
						}
						field.atomName().writeTo(writer)
						value.writeSummaryTo(writer)
					}
				}
			}
		}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = with(builder) {
		val (names, baseTypes) = namesAndBaseTypesForObjectType(self.kind())
		append("a/an ")
		when {
			names.setSize() == 0 -> append("object")
			else -> append(
				names.map { it.asNativeString() }.sorted().joinToString(" ∩ "))
		}
		val explicitSubclassingKey = EXPLICIT_SUBCLASSING_KEY.atom
		var ignoreKeys = emptySet
		baseTypes.forEach { baseType ->
			baseType.fieldTypeMap().mapIterable().forEach { (k, _) ->
				if (!k.getAtomProperty(explicitSubclassingKey).equalsNil()) {
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(k, true)
				}
			}
		}
		var first = true
		self.fieldMap().mapIterable().forEach { (key, value) ->
			if (!ignoreKeys.hasElement(key)) {
				append(if (first) " with:" else ",")
				first = false
				newlineTab(builder, indent)
				append(key.atomName().asNativeString())
				append(" = ")
				value.printOnAvoidingIndent(builder, recursionMap, indent + 1)
			}
		}
	}

	@Deprecated(
		"ObjectDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun mutable() = variant.mutableObjectDescriptor

	@Deprecated(
		"ObjectDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun immutable() = variant.immutableObjectDescriptor

	@Deprecated(
		"ObjectDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun shared() = variant.sharedObjectDescriptor

	companion object {
		/**
		 * Extract the field value at the specified slot index.
		 *
		 * @param self
		 *   An object.
		 * @param slotIndex
		 *   The non-zero slot index.
		 * @return
		 *   The value of the field at the specified slot index.
		 */
		fun getField(self: AvailObject, slotIndex: Int): AvailObject =
			self.slot(FIELD_VALUES_, slotIndex)

		/**
		 * Update the field value at the specified slot index of the mutable
		 * object.
		 *
		 * @param self
		 *   An object.
		 * @param slotIndex
		 *   The non-zero slot index.
		 * @param value
		 *   The value to write to the specified slot.
		 * @return
		 *   The given object, to facilitate chaining.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun setField(
			self: AvailObject,
			slotIndex: Int,
			value: AvailObject
		): AvailObject {
			self.setSlot(FIELD_VALUES_, slotIndex, value)
			return self
		}

		/** Access the [setField] method.  */
		@JvmField
		var setFieldMethod: CheckedMethod = staticMethod(
			ObjectDescriptor::class.java,
			::setField.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!,
			AvailObject::class.java)

		/**
		 * Construct an object with attribute [keys][AtomDescriptor] and values
		 * taken from the provided [A_Map].
		 *
		 * @param map
		 *   A map from keys to their corresponding values.
		 * @return
		 *   The new object.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun objectFromMap(map: A_Map): AvailObject {
			val variant = variantForFields(map.keysAsSet())
			val mutableDescriptor = variant.mutableObjectDescriptor
			val slotMap = variant.fieldToSlotIndex
			return mutableDescriptor.create(variant.realSlotCount) {
				map.mapIterable().forEach { (key, value) ->
					@Suppress("MapGetWithNotNullAssertionOperator")
					val slotIndex = slotMap[key]!!
					if (slotIndex > 0) {
						setSlot(FIELD_VALUES_, slotIndex, value)
					}
				}
				setSlot(KIND, nil)
				setSlot(TYPE_VETTINGS_CACHE, emptyTuple())
				setSlot(HASH_OR_ZERO, 0)
			}
		}

		/**
		 * The [CheckedMethod] for [objectFromMap].
		 */
		@Suppress("unused")
		val objectFromMapMethod: CheckedMethod = staticMethod(
			ObjectDescriptor::class.java,
			::objectFromMap.name,
			AvailObject::class.java,
			A_Map::class.java)

		/**
		 * Construct an object from the specified [tuple][TupleDescriptor] of
		 * field assignments.
		 *
		 * @param tuple
		 *   A tuple of 2-tuples whose first element is an
		 *   [atom][AtomDescriptor] and whose second element is an arbitrary
		 *   value.
		 * @return
		 *   The new object.
		 */
		fun objectFromTuple(tuple: A_Tuple): AvailObject =
			objectFromMap(
				tuple.fold(emptyMap) { m, (atom, value) ->
					m.mapAtPuttingCanDestroy(atom, value, true)
				})

		/**
		 * Create a mutable object using the provided [ObjectLayoutVariant], but
		 * without initializing its fields.  The caller is responsible for
		 * initializing the fields before use.
		 *
		 * @param variant
		 *   The [ObjectLayoutVariant] to instantiate as an object.
		 * @return
		 *   The new object.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun createUninitializedObject(
			variant: ObjectLayoutVariant
		): AvailObject =
			variant.mutableObjectDescriptor.create(variant.realSlotCount) {
				setSlot(HASH_OR_ZERO, 0)
			}

		/**
		 * Access the [createUninitializedObject] static method.
		 */
		@JvmField
		var createUninitializedObjectMethod: CheckedMethod = staticMethod(
			ObjectDescriptor::class.java,
			::createUninitializedObject.name,
			AvailObject::class.java,
			ObjectLayoutVariant::class.java)

		/**
		 * The maximum size that one of the four sets in the
		 * [TYPE_VETTINGS_CACHE] may be before taking action to reduce it.
		 */
		private const val maximumVettingSetSize = 20
	}
}

