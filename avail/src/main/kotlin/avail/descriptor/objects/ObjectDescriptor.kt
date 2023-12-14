/*
 * ObjectDescriptor.kt
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

import avail.annotations.HideFieldInDebugger
import avail.annotations.ThreadSafe
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.objects.ObjectDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.objects.ObjectDescriptor.IntegerSlots.HASH_AND_MORE
import avail.descriptor.objects.ObjectDescriptor.ObjectSlots.FIELD_VALUES_
import avail.descriptor.objects.ObjectDescriptor.ObjectSlots.KIND
import avail.descriptor.objects.ObjectDescriptor.ObjectSlots.TYPE_VETTINGS_CACHE
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantForFields
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.namesAndBaseTypesForObjectType
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.descriptor.representation.AbstractDescriptor.Companion.staticTypeTagOrdinal
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.hasObjectInstance
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor
import avail.dispatch.LookupTree
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.L2Optimizer
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import avail.utility.Strings.newlineTab
import avail.utility.ifZero
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * Avail [user-defined&#32;object&#32;types][ObjectTypeDescriptor] are novel.
 * They consist of a [map][MapDescriptor] of keys (field name
 * [atoms][AtomDescriptor]) and their associated field [types][A_Type].
 * Similarly, user-defined objects consist of a map from field names to field
 * values. An object instance conforms to an object type if and only if the
 * instance's field keys are a superset of the type's field keys, and for each
 * field key in common, the field value is an instance of the field type.
 *
 * To support code-splitting in the [L2Optimizer], and for a more compact
 * representation than a simple map, objects and object types are represented by
 * way of an [ObjectLayoutVariant], which, for any set of fields, defines a
 * unique layout into numbered slots which objects or object types having those
 * exact fields will provide.  The optimizer sometimes chooses to dispatch based
 * on the variant number, and captures that tested information (positive or
 * negative) for subsequent tests alongside the type information, within
 * [TypeRestriction]s.
 *
 * [LookupTree]s can also decide to test by variant as well.
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
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }
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
		 * Either [nil] or a [raw&#32;pojo][RawPojoDescriptor] containing a
		 * [VettingsCache], for caching the results of type checks against
		 * object types that are already marked shared.  Since method and
		 * function signatures are always shared, as well as types used by
		 * [LookupTree]s, this improves lookup performance and argument/return
		 * type checking substantially.
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
		fields.add(
			AvailObjectFieldHelper(
				self,
				KIND,
				-1,
				self[KIND]))
		val otherAtoms = mutableListOf<A_Atom>()
		variant.fieldToSlotIndex.forEach { (fieldKey, index) ->
			when (index) {
				0 -> otherAtoms.add(fieldKey)
				else -> fields.add(
					AvailObjectFieldHelper(
						self,
						DUMMY_DEBUGGER_SLOT,
						-1,
						self[FIELD_VALUES_, index],
						slotName = "FIELD " + fieldKey.atomName))
			}
		}
		fields.sortBy(AvailObjectFieldHelper::nameForDebugger)
		if (otherAtoms.isNotEmpty()) {
			fields.add(
				AvailObjectFieldHelper(
					self,
					DUMMY_DEBUGGER_SLOT,
					-1,
					tupleFromList(otherAtoms),
					slotName = "SUBCLASS_FIELDS"))
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
		if (variant !== anObject.objectVariant) return false
		// If one of the hashes is already computed, compute the other if
		// necessary, then compare the hashes to eliminate the vast majority of
		// the unequal cases.
		var myHash = self[HASH_OR_ZERO]
		var otherHash = anObject[HASH_OR_ZERO]
		when
		{
			myHash != 0 && otherHash == 0 -> otherHash = anObject.hash()
			otherHash != 0 && myHash == 0 -> myHash = self.hash()
		}
		if (myHash != otherHash) return false
		// Hashes are equal (perhaps both still zero).  Compare fields, which
		// must be in corresponding positions because we share the same variant.
		for (i in 1..self.variableObjectSlotsCount())
		{
			if (!self[FIELD_VALUES_, i]
					.equals(anObject[FIELD_VALUES_, i]))
				return false
		}
		val kind = self[KIND].ifNil { anObject[KIND] }
		if (!isShared)
		{
			if (anObject.descriptor().isShared)
				anObject[KIND] = kind.makeShared()
			else
				anObject[KIND] = kind.makeImmutable()
			self.becomeIndirectionTo(anObject)
		}
		else if (!anObject.descriptor().isShared)
		{
			// We tested it above, and the receiver wasn't shared.
			self[KIND] = kind.makeImmutable()
			anObject.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_FieldAt(self: AvailObject, field: A_Atom): AvailObject =
		// Fails with NullPointerException if key is not found.
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			0 -> field as AvailObject
			else -> self[FIELD_VALUES_, slotIndex!!]
		}

	override fun o_FieldAtIndex(self: AvailObject, index: Int): AvailObject =
		// One-based index must specify a real field, and be in range.
		self[FIELD_VALUES_, index]

	override fun o_FieldAtOrNull(
		self: AvailObject,
		field: A_Atom
	): AvailObject? =
		when (val slotIndex = variant.fieldToSlotIndex[field]) {
			null -> null
			0 -> field as AvailObject
			else -> self[FIELD_VALUES_, slotIndex]
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
							self[FIELD_VALUES_, value1]))
					}
					@Suppress("MapGetWithNotNullAssertionOperator")
					val newVariantSlotIndex = newVariantSlotMap[field]!!
					if (newVariantSlotIndex != 0) {
						setSlot(FIELD_VALUES_, newVariantSlotIndex, value)
					}
					setSlot(KIND, nil)
					setSlot(TYPE_VETTINGS_CACHE, nil)
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
					setSlot(TYPE_VETTINGS_CACHE, nil)
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
				else self[FIELD_VALUES_, slotIndex],
				true)
		}

	override fun o_FieldTuple(self: AvailObject): A_Tuple
	{
		val fieldIterator = variant.fieldToSlotIndex.entries.iterator()
		return generateObjectTupleFrom(variant.fieldToSlotIndex.size) {
			val (field, slotIndex) = fieldIterator.next()
			if (slotIndex == 0) tuple(field, field)
			else tuple(field, self[FIELD_VALUES_, slotIndex])
		}.also { assert(!fieldIterator.hasNext()) }
	}

	override fun o_Hash(self: AvailObject): Int =
		self[HASH_OR_ZERO].ifZero {
			// Don't lock if we're shared.  Multiple simultaneous computations
			// of *the same* value are benign races.
			(1..self.variableObjectSlotsCount())
				.fold(combine2(variant.variantId, -0x7d4d2f29)) { h, i ->
					combine3(h, self[FIELD_VALUES_, i].hash(), 0x5cfd93e6)
				}.also { self[HASH_OR_ZERO] = it }
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

		// At this point, either a VettingsCache already exists, or one will be
		// needed to store the positive/negative result.
		var cachePojo = self[TYPE_VETTINGS_CACHE]
		if (cachePojo.isNil)
		{
			cachePojo = identityPojo(VettingsCache()).makeShared()
			// Store it into the object early, to slightly reduce dropped
			// entries during (benign) races.
			self[TYPE_VETTINGS_CACHE] = cachePojo
		}
		val cache = cachePojo.javaObjectNotNull<VettingsCache>()
		return cache.testObjectAgainstType(self, typeTraversed)
	}

	override fun o_Kind(self: AvailObject): A_Type {
		val kind = self[KIND]
		if (kind.notNil) return kind
		self.makeImmutable()
		val objectType =
			variant.mutableObjectTypeDescriptor.createFromObject(self)
		// Make the object shared since it's being written to a mutable slot
		// of a shared object. Don't lock, since multiple threads would
		// compute equal values anyhow.
		self[KIND] =
			when
			{
				isShared -> objectType.makeShared()
				else -> objectType.makeImmutable()
			}
		return objectType
	}

	override fun o_NameForDebugger(self: AvailObject): String
	{
		val baseName = buildString {
			val (names, _) = namesAndBaseTypesForObjectType(self.kind())
			append("a/an ")
			when (names.setSize)
			{
				0 -> append("object")
				else -> append(
					names.sortedBy { it.asNativeString() }
						.joinToString(" ∩ "))
			}
		}
		return super.o_NameForDebugger(self) + " = " + baseName
	}

	override fun o_ObjectVariant(self: AvailObject) = variant

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
							else -> self[FIELD_VALUES_, slotIndex]
						}
						field.atomName.writeTo(writer)
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
							else -> self[FIELD_VALUES_, slotIndex]
						}
						field.atomName.writeTo(writer)
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
	) = builder.brief {
		val (names, baseTypes) = namesAndBaseTypesForObjectType(self.kind())
		append("{= ")
		when (names.setSize)
		{
			0 -> append("object")
			else -> append(
				names.map { it.asNativeString() }.sorted()
					.joinToString(" ∩ ")
			)
		}
		val explicitSubclassingKey = EXPLICIT_SUBCLASSING_KEY.atom
		var ignoreKeys = emptySet
		baseTypes.forEach { baseType ->
			baseType.fieldTypeMap.forEach { k, _ ->
				if (k.getAtomProperty(explicitSubclassingKey).notNil)
				{
					ignoreKeys =
						ignoreKeys.setWithElementCanDestroy(k, true)
				}
			}
		}
		var first = true
		self.fieldMap().forEach { key, value ->
			if (!ignoreKeys.hasElement(key))
			{
				append(if (first) " |" else ",")
				first = false
				newlineTab(indent)
				append(key.atomName.asNativeString())
				append(" :")
				if (value.isInstanceOfKind(
					VariableTypeDescriptor.mostGeneralVariableType))
				{
					append("= ")
					value.value().printOnAvoidingIndent(
						this, recursionMap, indent + 1)
				}
				else
				{
					append(":= ")
					value.printOnAvoidingIndent(this, recursionMap, indent + 1)
				}
			}
		}
		append("\n=}")
	}

	override fun mutable() = variant.mutableObjectDescriptor

	override fun immutable() = variant.immutableObjectDescriptor

	override fun shared() = variant.sharedObjectDescriptor

	companion object
	{
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
			self[FIELD_VALUES_, slotIndex]

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
			value: A_BasicObject
		): AvailObject {
			self[FIELD_VALUES_, slotIndex] = value as AvailObject
			return self
		}

		/** Access the [setField] method. */
		val setFieldMethod = staticMethod(
			ObjectDescriptor::class.java,
			::setField.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!,
			A_BasicObject::class.java)

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
			val variant = variantForFields(map.keysAsSet)
			val mutableDescriptor = variant.mutableObjectDescriptor
			val slotMap = variant.fieldToSlotIndex
			return mutableDescriptor.create(variant.realSlotCount) {
				map.forEach { key, value ->
					@Suppress("MapGetWithNotNullAssertionOperator")
					val slotIndex = slotMap[key]!!
					if (slotIndex > 0) {
						setSlot(FIELD_VALUES_, slotIndex, value)
					}
				}
				setSlot(KIND, nil)
				setSlot(TYPE_VETTINGS_CACHE, nil)
				setSlot(HASH_OR_ZERO, 0)
			}
		}

		/**
		 * The [CheckedMethod] for [objectFromMap].
		 */
		@Suppress("unused")
		val objectFromMapMethod = staticMethod(
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
				setSlot(KIND, nil)
				setSlot(TYPE_VETTINGS_CACHE, nil)
				setSlot(HASH_OR_ZERO, 0)
			}

		/**
		 * Access the [createUninitializedObject] static method.
		 */
		val createUninitializedObjectMethod = staticMethod(
			ObjectDescriptor::class.java,
			::createUninitializedObject.name,
			AvailObject::class.java,
			ObjectLayoutVariant::class.java)

		/**
		 * Produce the given object's [ObjectLayoutVariant]'s variantId.
		 *
		 * @param anObject
		 *   The [object][ObjectDescriptor] to examine.
		 * @return
		 *   The object's variantId, which is an [Int].
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun staticObjectVariantId(anObject: AvailObject): Int =
			anObject.objectVariant.variantId

		/** The [CheckedMethod] for [staticTypeTagOrdinal]. */
		val staticObjectVariantIdMethod = staticMethod(
			ObjectDescriptor::class.java,
			::staticObjectVariantId.name,
			Int::class.javaPrimitiveType!!,
			AvailObject::class.java)
	}
}
