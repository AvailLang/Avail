/*
 * ObjectTypeDescriptor.kt
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

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.Companion.createSpecialAtom
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.OBJECT_TYPE_NAME_PROPERTY_KEY
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.objects.ObjectLayoutVariant.Companion.variantForFields
import com.avail.descriptor.objects.ObjectTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.objects.ObjectTypeDescriptor.ObjectSlots.FIELD_TYPES_
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta
import com.avail.descriptor.types.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * [ObjectTypeDescriptor] represents an Avail object type. An object type
 * associates [fields][AtomDescriptor] with [types][TypeDescriptor]. An object
 * type's instances have at least the same fields and field values that are
 * instances of the corresponding types.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the new descriptor.
 * @param variant
 *   The [ObjectLayoutVariant] for the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class ObjectTypeDescriptor internal constructor(
	mutability: Mutability,
	val variant: ObjectLayoutVariant
) : TypeDescriptor(
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
			 * A bit field to hold the cached hash value of an object type.  If
			 * zero, the hash value must be computed upon request.  Note that in
			 * the very rare case that the hash value actually equals zero, the
			 * hash value has to be computed every time it is requested.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The types associated with keys for this object.  The assignment of
		 * object fields to these slots is determined by the descriptor's
		 * [variant].
		 */
		FIELD_TYPES_
	}

	public override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) =
		e === IntegerSlots.HASH_AND_MORE

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = with(builder) {
		val myFieldTypeMap = self.fieldTypeMap()
		val (names, baseTypes) = namesAndBaseTypesForObjectType(self)
		when {
			names.setSize() == 0 -> append("object")
			else -> append(
				names.map { it.asNativeString() }.sorted().joinToString(" ∩ "))
		}
		val explicitSubclassingKey = EXPLICIT_SUBCLASSING_KEY.atom
		var ignoreKeys = emptySet()
		baseTypes.forEach { baseType ->
			baseType.fieldTypeMap().mapIterable().forEach { (atom, type) ->
				if (!atom.getAtomProperty(explicitSubclassingKey).equalsNil()
					|| myFieldTypeMap.mapAt(atom).equals(type))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(atom, true)
				}
			}
		}
		var first = true
		self.fieldTypeMap().mapIterable().forEach { (key, type) ->
			if (!ignoreKeys.hasElement(key)) {
				append(if (first) " with:" else ",")
				first = false
				newlineTab(builder, indent)
				append(key.atomName().asNativeString())
				append(" : ")
				type.printOnAvoidingIndent(builder, recursionMap, indent + 1)
			}
		}
	}

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
						DebuggerObjectSlots(
							"FIELD TYPE ${fieldKey.atomName()}"),
						-1,
						self.slot(FIELD_TYPES_, index)))
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

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsObjectType(self)

	override fun o_EqualsObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean {
		if (self.sameAddressAs(anObjectType)) return true
		val otherDescriptor = anObjectType.descriptor() as ObjectTypeDescriptor
		if (variant !== otherDescriptor.variant) return false
		// If one of the hashes is already computed, compute the other if
		// necessary, then compare the hashes to eliminate the vast majority of
		// the unequal cases.
		var myHash = self.slot(HASH_OR_ZERO)
		var otherHash = anObjectType.slot(HASH_OR_ZERO)
		when {
			myHash != 0 && otherHash == 0 -> otherHash = anObjectType.hash()
			otherHash != 0 && myHash == 0 -> myHash = self.hash()
		}
		// Hashes are equal.  Compare field types, which must be in
		// corresponding positions because we share the same variant.
		when {
			myHash != otherHash -> return false
			(1..self.variableObjectSlotsCount()).any {
				!self.slot(FIELD_TYPES_, it).equals(
					anObjectType.slot(FIELD_TYPES_, it))
			} -> return false
			!isShared -> self.becomeIndirectionTo(anObjectType)
			!otherDescriptor.isShared -> anObjectType.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): AvailObject =
		// Fails with NullPointerException if key is not found.
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			0 -> instanceType(field)
			else -> self.slot(FIELD_TYPES_, slotIndex!!)
		}

	override fun o_FieldTypeMap(self: AvailObject): A_Map
	{
		// Warning: May be much slower than it was before ObjectLayoutVariant.
		return variant.fieldToSlotIndex.entries.fold(emptyMap()) {
			map, (field, slotIndex) ->
			map.mapAtPuttingCanDestroy(
				field,
				if (slotIndex == 0) instanceType(field)
				else self.slot(FIELD_TYPES_, slotIndex),
				true)
		}
	}

	override fun o_FieldTypeTuple(self: AvailObject): A_Tuple
	{
		val fieldIterator = variant.fieldToSlotIndex.entries.iterator()
		return generateObjectTupleFrom(variant.fieldToSlotIndex.size) {
			val (field, slotIndex) = fieldIterator.next()
			if (slotIndex == 0) tuple(field, instanceType(field))
			else tuple(field, self.slot(FIELD_TYPES_, slotIndex))
		}.also { assert(!fieldIterator.hasNext()) }
	}

	override fun o_Hash(self: AvailObject) =
		when (val hash = self.slot(HASH_OR_ZERO)) {
			0 -> {
				// Don't lock if we're shared.  Multiple simultaneous
				// computations of *the same* value are benign races.
				(1..self.variableObjectSlotsCount())
					.fold(variant.variantId xor -0x1ca9e0ea) { h, i ->
						(h * multiplier) - self.slot(FIELD_TYPES_, i).hash()
					}.also { self.setSlot(HASH_OR_ZERO, it) }
			}
			else -> hash
		}

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean {
		val instanceDescriptor =
			potentialInstance.descriptor() as ObjectDescriptor
		val instanceVariant = instanceDescriptor.variant
		if (instanceVariant == variant) {
			// The instance and I share a variant, so blast through the fields
			// in lock-step doing instance checks.
			return (1..variant.realSlotCount).all {
				ObjectDescriptor.getField(potentialInstance, it)
					.isInstanceOf(self.slot(FIELD_TYPES_, it))
			}
		}
		// The variants disagree.  For each field type in this object type,
		// check that there is a corresponding field value in the object, and
		// that its type conforms.  For field types that are only for explicit
		// subclassing, just make sure the same field is present in the object.
		val instanceVariantSlotMap = instanceVariant.fieldToSlotIndex
		return variant.fieldToSlotIndex.all { (field, slotIndex) ->
			when (slotIndex) {
				0 -> instanceVariantSlotMap.containsKey(field)
				else -> {
					val instanceSlotIndex = instanceVariantSlotMap[field]
						?: return false
					assert(instanceSlotIndex != 0)
					val fieldValue: A_BasicObject = ObjectDescriptor.getField(
						potentialInstance, instanceSlotIndex)
					fieldValue.isInstanceOf(self.slot(FIELD_TYPES_, slotIndex))
				}
			}
		}
	}

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type) =
		aType.isSupertypeOfObjectType(self)

	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean {
		if (self.sameAddressAs(anObjectType)) return true
		val subtypeDescriptor =
			anObjectType.descriptor() as ObjectTypeDescriptor
		val subtypeVariant = subtypeDescriptor.variant
		if (subtypeVariant == variant) {
			// The potential subtype and I share a variant, so blast through the
			// fields in lock-step doing subtype checks.
			return (1..variant.realSlotCount).all {
				val subtypeFieldType = anObjectType.slot(FIELD_TYPES_, it)
				val myFieldType = self.slot(FIELD_TYPES_, it)
				subtypeFieldType.isSubtypeOf(myFieldType)
			}
		}
		// The variants disagree.  Do some quick field count checks first.  Note
		// that since variants are canonized by the set of fields, we can safely
		// assume that the subtype has *strictly* more fields than the
		// supertype... but also note that the number of real slots can still be
		// equal while satisfying the not-same-variant but is-subtype.
		if (subtypeVariant.realSlotCount < variant.realSlotCount
			|| subtypeVariant.fieldToSlotIndex.size <=
				variant.fieldToSlotIndex.size) {
			return false
		}
		// For each of my fields, check that the field is present in the
		// potential subtype, and that its type is a subtype of my field's type.
		val subtypeVariantSlotMap = subtypeVariant.fieldToSlotIndex
		return variant.fieldToSlotIndex.all { (field, supertypeSlotIndex) ->
			when (supertypeSlotIndex) {
				0 -> subtypeVariantSlotMap.containsKey(field)
				else -> {
					val subtypeSlotIndex = subtypeVariantSlotMap[field]
						?: return false
					assert(subtypeSlotIndex != 0)
					val subtypeFieldType =
						anObjectType.slot(FIELD_TYPES_, subtypeSlotIndex)
					val supertypeFieldType =
						self.slot(FIELD_TYPES_, supertypeSlotIndex)
					subtypeFieldType.isSubtypeOf(supertypeFieldType)
				}
			}
		}
	}

	override fun o_IsVacuousType(self: AvailObject) =
		(1..self.variableObjectSlotsCount()).any {
			self.slot(FIELD_TYPES_, it).isVacuousType
		}

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = when {
		self.isSubtypeOf(another) -> self
		another.isSubtypeOf(self) -> another
		else -> another.typeIntersectionOfObjectType(self)
	}

	/**
	 * Answer the most general type that is still at least as specific as these.
	 * Here we're finding the nearest common descendant of two object types.
	 */
	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type {
		val otherDescriptor = anObjectType.descriptor() as ObjectTypeDescriptor
		val otherVariant = otherDescriptor.variant
		if (otherVariant == variant) {
			// Field slot indices agree, so blast through the slots in order.
			val intersection = variant.mutableObjectTypeDescriptor.create(
				variant.realSlotCount)
			(1..variant.realSlotCount).forEach {
				val fieldIntersection =
					self.slot(FIELD_TYPES_, it).typeIntersection(
						anObjectType.slot(FIELD_TYPES_, it))
				if (fieldIntersection.isBottom) {
					// Abandon the partially built object type.
					return bottom()
				}
				intersection.setSlot(FIELD_TYPES_, it, fieldIntersection)
			}
			intersection.setSlot(HASH_OR_ZERO, 0)
			return intersection
		}
		// The variants disagree, so do it the hard(er) way.
		val mergedFields = variant.allFields.setUnionCanDestroy(
			otherVariant.allFields, false)
		val resultVariant = variantForFields(mergedFields)
		val mySlotMap = variant.fieldToSlotIndex
		val otherSlotMap = otherVariant.fieldToSlotIndex
		val resultSlotMap = resultVariant.fieldToSlotIndex
		val result = resultVariant.mutableObjectTypeDescriptor.create(
			resultVariant.realSlotCount)
		resultSlotMap.forEach { (field, resultSlotIndex) ->
			if (resultSlotIndex > 0) {
				val mySlotIndex = mySlotMap[field]
				val otherSlotIndex = otherSlotMap[field]
				val fieldType = when {
					mySlotIndex == null ->
						anObjectType.slot(FIELD_TYPES_, otherSlotIndex!!)
					otherSlotIndex == null ->
						self.slot(FIELD_TYPES_, mySlotIndex)
					else -> {
						val intersection = self.slot(FIELD_TYPES_, mySlotIndex)
							.typeIntersection(
								anObjectType.slot(FIELD_TYPES_, otherSlotIndex))
						if (intersection.isBottom) return bottom()
						intersection
					}
				}
				result.setSlot(FIELD_TYPES_, resultSlotIndex, fieldType)
			}
		}
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type
	): A_Type = when {
		self.isSubtypeOf(another) -> another
		another.isSubtypeOf(self) -> self
		else -> another.typeUnionOfObjectType(self)
	}

	/**
	 * Answer the most specific type that is still at least as general as these.
	 * Here we're finding the nearest common ancestor of two eager object types.
	 */
	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type {
		val otherDescriptor = anObjectType.descriptor() as ObjectTypeDescriptor
		val otherVariant = otherDescriptor.variant
		if (otherVariant == variant) {
			// Field slot indices agree, so blast through the slots in order.
			val union = variant.mutableObjectTypeDescriptor.create(
				variant.realSlotCount)
			(1..variant.realSlotCount).forEach {
				val fieldUnion = self.slot(FIELD_TYPES_, it).typeUnion(
					anObjectType.slot(FIELD_TYPES_, it))
				union.setSlot(FIELD_TYPES_, it, fieldUnion)
			}
			union.setSlot(HASH_OR_ZERO, 0)
			return union
		}
		// The variants disagree, so do it the hard(er) way.
		val narrowedFields = variant.allFields.setIntersectionCanDestroy(
			otherVariant.allFields, false)
		val resultVariant = variantForFields(narrowedFields)
		val mySlotMap = variant.fieldToSlotIndex
		val otherSlotMap = otherVariant.fieldToSlotIndex
		val resultSlotMap = resultVariant.fieldToSlotIndex
		val result = resultVariant.mutableObjectTypeDescriptor.create(
			resultVariant.realSlotCount)
		resultSlotMap.forEach { (field, resultSlotIndex) ->
			if (resultSlotIndex > 0) {
				val mySlotIndex = mySlotMap[field]!!
				val otherSlotIndex = otherSlotMap[field]!!
				val fieldType = self.slot(FIELD_TYPES_, mySlotIndex).typeUnion(
					anObjectType.slot(FIELD_TYPES_, otherSlotIndex))
				result.setSlot(FIELD_TYPES_, resultSlotIndex, fieldType)
			}
		}
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.OBJECT_TYPE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("object type") }
			self.fieldTypeMap().mapIterable().forEach { (key, value) ->
				key.atomName().writeTo(writer)
				value.writeTo(writer)
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("object type") }
			self.fieldTypeMap().mapIterable().forEach { (key, value) ->
				key.atomName().writeTo(writer)
				value.writeSummaryTo(writer)
			}
		}

	/**
	 * Given an [object][ObjectDescriptor] whose variant is this mutable object
	 * type descriptor's variant, create an object type whose fields are
	 * populated with instance types based on the object's fields.
	 *
	 * @param self
	 *   An object.
	 * @return
	 *   An object type.
	 */
	fun createFromObject(self: AvailObject): AvailObject =
		create(variant.realSlotCount).apply {
			(1..variant.realSlotCount).forEach {
				val fieldValue = ObjectDescriptor.getField(self, it)
				setSlot(FIELD_TYPES_, it, instanceTypeOrMetaOn(fieldValue))
			}
			setSlot(HASH_OR_ZERO, 0)
		}

	@Deprecated(
		"ObjectTypeDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun mutable() = variant.mutableObjectTypeDescriptor

	@Deprecated(
		"ObjectTypeDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun immutable() = variant.immutableObjectTypeDescriptor

	@Deprecated(
		"ObjectTypeDescriptors are organized by ObjectLayoutVariant",
		level = DeprecationLevel.HIDDEN)
	override fun shared() = variant.sharedObjectTypeDescriptor

	companion object {
		/**
		 * Extract the field type at the specified slot index.
		 *
		 * @param self
		 *   An object type, fully traversed.
		 * @param slotIndex
		 *   The non-zero slot index.
		 * @return
		 *   The type of the field at the specified slot index.
		 */
		@Suppress("unused")
		fun getFieldType(
			self: AvailObject,
			slotIndex: Int
		): AvailObject = self.slot(FIELD_TYPES_, slotIndex)

		/**
		 * Create an `object type` using the given [A_Map] from [A_Atom]s to
		 * [types][TypeDescriptor].
		 *
		 * @param map
		 *   The map from atoms to types.
		 * @return
		 *   The new `object type`.
		 */
		@JvmStatic
		fun objectTypeFromMap(map: A_Map): AvailObject {
			val variant: ObjectLayoutVariant = variantForFields(map.keysAsSet())
			val mutableDescriptor = variant.mutableObjectTypeDescriptor
			val slotMap = variant.fieldToSlotIndex
			return mutableDescriptor.create(variant.realSlotCount).apply {
				map.mapIterable().forEach { (key, value) ->
					val slotIndex = slotMap[key]!!
					if (slotIndex > 0) {
						setSlot(FIELD_TYPES_, slotIndex, value)
					}
				}
				setSlot(HASH_OR_ZERO, 0)
			}
		}

		/**
		 * Create an `object type` from the specified [A_Tuple].
		 *
		 * @param tuple
		 *   A tuple whose elements are 2-tuples whose first element is an
		 *   [atom][AtomDescriptor] and whose second element is a
		 *   [type][TypeDescriptor].
		 * @return
		 *   The new object type.
		 */
		fun objectTypeFromTuple(tuple: A_Tuple): AvailObject =
			objectTypeFromMap(
				tuple.fold(emptyMap()) { m, (atom, type) ->
					m.mapAtPuttingCanDestroy(atom, type, true)
				})

		/**
		 * Create a mutable object type using the provided
		 * [ObjectLayoutVariant], but without initializing its fields.  The
		 * caller is responsible for initializing the fields before use.
		 *
		 * @param variant
		 * The [ObjectLayoutVariant] to instantiate as an object type.
		 * @return The new object type.
		 */
		@Suppress("unused")
		fun createUninitializedObjectType(
			variant: ObjectLayoutVariant
		): AvailObject =
			(variant.mutableObjectTypeDescriptor.create(variant.realSlotCount))
				.apply { setSlot(HASH_OR_ZERO, 0) }

		/**
		 * Assign a name to the specified `object type`.  If the only field key
		 * [A_Atom]s in the object type are
		 * [special&#32;atoms][A_Atom.Companion.isAtomSpecial], then the name
		 * will not be recorded (unless allowSpecialAtomsToHoldName is true,
		 * which is really only for naming special object types like
		 * [exceptionType]).  Note that it is technically *legal* for there to
		 * be multiple names for a particular object type, although this is of
		 * questionable value.
		 *
		 * @param anObjectType
		 *   An `object type`.
		 * @param aString
		 *   A name.
		 * @param allowSpecialAtomsToHoldName
		 *   Whether to allow the object type name to be attached to a special
		 *   atom.
		 */
		fun setNameForType(
			anObjectType: A_Type,
			aString: A_String,
			allowSpecialAtomsToHoldName: Boolean
		) {
			assert(aString.isString)
			val propertyKey = OBJECT_TYPE_NAME_PROPERTY_KEY.atom
			synchronized(propertyKey) {
				var leastNames = Int.MAX_VALUE
				var keyAtomWithLeastNames: A_Atom? = null
				var keyAtomNamesMap: A_Map? = null
				for ((atom, _) in anObjectType.fieldTypeMap().mapIterable()) {
					if (allowSpecialAtomsToHoldName || !atom.isAtomSpecial()) {
						val namesMap: A_Map = atom.getAtomProperty(propertyKey)
						if (namesMap.equalsNil()) {
							keyAtomWithLeastNames = atom
							keyAtomNamesMap = emptyMap()
							break
						}
						val mapSize = namesMap.mapSize()
						if (mapSize < leastNames) {
							keyAtomWithLeastNames = atom
							keyAtomNamesMap = namesMap
							leastNames = mapSize
						}
					}
				}
				if (keyAtomWithLeastNames !== null) {
					var namesSet = when {
						keyAtomNamesMap!!.hasKey(anObjectType) ->
							keyAtomNamesMap.mapAt(anObjectType)
						else -> emptySet()
					}
					namesSet = namesSet.setWithElementCanDestroy(aString, false)
					keyAtomNamesMap = keyAtomNamesMap.mapAtPuttingCanDestroy(
						anObjectType, namesSet, true)
					keyAtomWithLeastNames.setAtomProperty(
						propertyKey, keyAtomNamesMap)
				}
			}
		}

		/**
		 * Remove a type name from the specified user-defined object type.  If
		 * the object type does not currently have the specified type name, or
		 * if this name has already been removed, do nothing.
		 *
		 * @param aString
		 *   A name to disassociate from the type.
		 * @param anObjectType
		 *   An Avail object type.
		 */
		fun removeNameFromType(
			aString: A_String,
			anObjectType: A_Type
		) {
			assert(aString.isString)
			val propertyKey = OBJECT_TYPE_NAME_PROPERTY_KEY.atom
			synchronized(propertyKey) {
				anObjectType.fieldTypeMap().mapIterable().forEach { (atom, _) ->
					if (!atom.isAtomSpecial()) {
						var namesMap: A_Map = atom.getAtomProperty(propertyKey)
						if (!namesMap.equalsNil()
							&& namesMap.hasKey(anObjectType))
						{
							// In theory the user can give this type multiple names,
							// so only remove the one that we've been told to.
							var namesSet: A_Set = namesMap.mapAt(anObjectType)
							namesSet = namesSet.setWithoutElementCanDestroy(
								aString, false)
							namesMap = when(namesSet.setSize()) {
								0 -> namesMap.mapWithoutKeyCanDestroy(
									anObjectType, false)
								else -> namesMap.mapAtPuttingCanDestroy(
									anObjectType, namesSet, false)
							}
							atom.setAtomProperty(propertyKey, namesMap)
						}
					}
				}
			}
		}

		/**
		 * Answer information about the user-assigned name of the specified
		 * user-defined object type.
		 *
		 * @param anObjectType
		 *   A user-defined object type.
		 * @return
		 *   A tuple with two elements: (1) A set of names of the user-defined
		 *   object type, excluding names for which a strictly more specific
		 *   named type is known, and (2) A set of object types corresponding to
		 *   those names.
		 */
		fun namesAndBaseTypesForObjectType(
			anObjectType: A_Type
		): A_Tuple {
			val propertyKey = OBJECT_TYPE_NAME_PROPERTY_KEY.atom
			var applicable = emptyMap()
			synchronized(propertyKey) {
				anObjectType.fieldTypeMap().mapIterable().forEach { (key, _) ->
					val map: A_Map = key.getAtomProperty(propertyKey)
					if (!map.equalsNil()) {
						map.mapIterable().forEach { (namedType, innerValue) ->
							if (anObjectType.isSubtypeOf(namedType)) {
								var nameSet: A_Set = innerValue
								if (applicable.hasKey(namedType)) {
									nameSet = nameSet.setUnionCanDestroy(
										applicable.mapAt(namedType),
										true)
								}
								applicable = applicable.mapAtPuttingCanDestroy(
									namedType, nameSet, true)
							}
						}
					}
				}
				applicable.makeImmutable()
			}
			var filtered = applicable
			applicable.mapIterable().forEach { (childType, _) ->
				applicable.mapIterable().forEach { (parentType, _) ->
					if (!childType.equals(parentType)
						&& childType.isSubtypeOf(parentType)) {
						filtered = filtered.mapWithoutKeyCanDestroy(
							parentType, true)
					}
				}
			}
			var names = emptySet()
			var baseTypes = emptySet()
			filtered.mapIterable().forEach { (name, type) ->
				names = names.setUnionCanDestroy(type, true)
				baseTypes = baseTypes.setWithElementCanDestroy(name, true)
			}
			return tuple(names, baseTypes)
		}

		/**
		 * Answer the user-assigned names of the specified user-defined
		 * [object&#32;type][ObjectTypeDescriptor].
		 *
		 * @param anObjectType
		 *   An `object type`.
		 * @return
		 *   A [set][SetDescriptor] containing the names of the `object type`,
		 *   excluding names for which a strictly more specific named type is
		 *   known.
		 */
		fun namesForType(anObjectType: A_Type): A_Set =
			namesAndBaseTypesForObjectType(anObjectType).tupleAt(1)

		/**
		 * Answer the set of named base types for the specified user-defined
		 * [object&#32;type][ObjectTypeDescriptor].
		 *
		 * @param anObjectType
		 *   An `object type`.
		 * @return
		 *   A [set][SetDescriptor] containing the named ancestors of the
		 *   specified `object type`, excluding named types for which a strictly
		 *   more specific named type is known.
		 */
		@Suppress("unused")
		fun namedBaseTypesForType(anObjectType: A_Type): A_BasicObject =
			namesAndBaseTypesForObjectType(anObjectType).tupleAt(2)

		/**
		 * The most general [object&#32;type][ObjectTypeDescriptor].
		 */
		private val mostGeneralType: A_Type =
			objectTypeFromMap(emptyMap()).makeShared()

		/**
		 * Answer the top (i.e., most general) object type.
		 *
		 * @return
		 *   The object type that makes no constraints on its fields.
		 */
		@JvmStatic
		fun mostGeneralObjectType(): A_Type = mostGeneralType

		/**
		 * The metatype of all object types.
		 */
		private val mostGeneralMeta: A_Type =
			instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the metatype for all object types.  This is just an
		 * [instance&#32;meta][InstanceMetaDescriptor] on the
		 * [mostGeneralObjectType].
		 *
		 * @return
		 *   The (meta)type of the most general object type.
		 */
		@JvmStatic
		fun mostGeneralObjectMeta(): A_Type = mostGeneralMeta

		/**
		 * The [A_Atom] that identifies the [exception&#32;type][exceptionType].
		 */
		private val exceptionAtom: A_Atom =
			createSpecialAtom("explicit-exception").apply {
				setAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom, trueObject())
			}

		/**
		 * Answer the [atom][AtomDescriptor] that identifies the
		 * [exception&#32;type][exceptionType].
		 *
		 * @return
		 *   The special exception atom.
		 */
		@JvmStatic
		fun exceptionAtom(): A_Atom = exceptionAtom

		/**
		 * The [atom][AtomDescriptor] that identifies the stack dump
		 * [field][AtomDescriptor] of an [exception][exceptionType].
		 */
		private val stackDumpAtom: A_Atom = createSpecialAtom("stack dump")

		/**
		 * Answer the [A_Atom] that identifies the stack dump field of an
		 * [exception&#32;type][exceptionType].
		 *
		 * @return
		 *   The special stack dump atom.
		 */
		@JvmStatic
		fun stackDumpAtom(): A_Atom = stackDumpAtom

		/**
		 * The most general exception type.
		 */
		private var exceptionType: A_Type = run {
			val type: A_Type = objectTypeFromTuple(
				tuple(tuple(exceptionAtom, instanceType(exceptionAtom))))
			setNameForType(type, StringDescriptor.stringFrom("exception"), true)
			type.makeShared()
		}

		/**
		 * Answer the most general exception type. This is just an
		 * [object&#32;type][ObjectTypeDescriptor] that contains the well-known
		 * [exceptionAtom].
		 *
		 * @return
		 *   The most general exception type.
		 */
		@JvmStatic
		fun exceptionType(): A_Type = exceptionType
	}
}
