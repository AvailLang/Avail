/*
 * AtomWithPropertiesDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.descriptor.atoms

import avail.annotations.HideFieldInDebugger
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import avail.descriptor.atoms.AtomWithPropertiesDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.atoms.AtomWithPropertiesDescriptor.IntegerSlots.HASH_AND_MORE
import avail.descriptor.atoms.AtomWithPropertiesDescriptor.ObjectSlots.ISSUING_MODULE
import avail.descriptor.atoms.AtomWithPropertiesDescriptor.ObjectSlots.NAME
import avail.descriptor.atoms.AtomWithPropertiesDescriptor.ObjectSlots.PROPERTY_MAP_POJO
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IndirectionDescriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.types.TypeTag
import avail.serialization.Serializer
import avail.serialization.SerializerOperation
import java.util.WeakHashMap

/**
 * An `atom` is an object that has identity by fiat, i.e., it is distinguished
 * from all other objects by the fact of its creation event and the history of
 * what happens to its references.  Not all objects in Avail have that property
 * (hence the acronym Advanced Value And Identity Language), unlike most
 * object-oriented programming languages.
 *
 * At any time an atom can have properties associated with it.  A property is an
 * association between another atom, known as the property key, and the value of
 * that property, any Avail object.  Atoms without properties have a
 * [representation][AtomDescriptor] that does not include a slot for the
 * properties information, but adding a property causes it to transform (via
 * [AvailObject.becomeIndirectionTo] into an [AtomWithPropertiesDescriptor]
 * representation that has a slot which contains a map from property keys to
 * property values.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to use in this descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's integer slots layout, or null if there are no integer slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @see AtomDescriptor
 * @see AtomWithPropertiesSharedDescriptor
 */
open class AtomWithPropertiesDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>
) : AtomDescriptor(
	mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the hash value, or zero if it has not been
			 * computed. The hash of an atom is a random number, computed once.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }

			init {
				assert(AtomDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
					== HASH_AND_MORE.ordinal)
				assert(AtomDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
					HASH_OR_ZERO))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.  Must have the same ordinal as
		 * [AtomDescriptor.ObjectSlots.NAME].
		 */
		NAME,

		/**
		 * The [module][ModuleDescriptor] that was active when this atom was
		 * issued.  This information is crucial to [serialization][Serializer].
		 * Must have the same ordinal as [AtomDescriptor.ObjectSlots.NAME].
		 */
		ISSUING_MODULE,

		/**
		 * A pojo holding a weak map from this atom's property keys (atoms) to
		 * property values.  It's never [nil] for this descriptor class, but it
		 * may be [nil] in the [Mutability.SHARED] subclass.
		 */
		@HideFieldInDebugger
		PROPERTY_MAP_POJO;

		companion object {
			init {
				assert(AtomDescriptor.ObjectSlots.NAME.ordinal
					== NAME.ordinal)
				assert(AtomDescriptor.ObjectSlots.ISSUING_MODULE.ordinal
					== ISSUING_MODULE.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = super.allowsImmutableToMutableReferenceInField(e)
		|| e === HASH_AND_MORE

	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper>
	{
		val fieldsArray = super.o_DescribeForDebugger(self)
		val propertiesPojo = self.slot(PROPERTY_MAP_POJO)
		if (propertiesPojo.isNil)
		{
			return fieldsArray
		}
		val fields = fieldsArray.toMutableList()
		val properties =
			propertiesPojo.javaObjectNotNull<Map<A_Atom, A_BasicObject>>()
		properties.entries
			.sortedBy { it.key.nameForDebugger() }
			.forEach { (key, value) ->
				fields.add(
					AvailObjectFieldHelper(
						self,
						DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT,
						-1,
						value,
						slotName = "Key " + key.atomName))
			}
		return fields.toTypedArray()
	}

	/**
	 * Extract the property value of this atom at the specified key.  Return
	 * [nil] if no such property exists.
	 */
	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom
	): AvailObject {
		assert(key.isAtom)
		val propertyMapPojo: A_BasicObject = self.slot(PROPERTY_MAP_POJO)
		val propertyMap: Map<A_Atom, AvailObject> =
			propertyMapPojo.javaObjectNotNull()
		return propertyMap[key] ?: nil
	}

	override fun o_MakeShared(self: AvailObject): AvailObject {
		assert(!isShared)
		val propertyMapPojo = self.slot(PROPERTY_MAP_POJO)
		assert(propertyMapPojo.notNil)
		val substituteAtom: AvailObject =
			AtomWithPropertiesSharedDescriptor.shared.createInitialized(
				self.slot(NAME),
				self.slot(ISSUING_MODULE),
				propertyMapPojo,
				self.slot(HASH_OR_ZERO))
		self.becomeIndirectionTo(substituteAtom)
		// In case a property key is the atom itself, only make the property
		// keys and atoms shared after making self shared.  Otherwise there
		// could be unbounded recursion.
		val propertyMap: Map<A_Atom, AvailObject> =
			propertyMapPojo.javaObjectNotNull()
		propertyMap.forEach { (key, value) ->
			key.makeShared()
			value.makeShared()
		}
		return substituteAtom
	}

	override fun o_SerializerOperation (self: AvailObject) = when {
		self.getAtomProperty(HERITABLE_KEY.atom).notNil ->
			SerializerOperation.HERITABLE_ATOM
		self.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom).notNil ->
			SerializerOperation.EXPLICIT_SUBCLASS_ATOM
		else -> SerializerOperation.ATOM
	}

	/**
	 * Add or replace a property of this [A_Atom].  If the provided value is
	 * [nil], remove the property.
	 */
	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject
	) {
		assert(key.isAtom)
		val propertyMapPojo = self.slot(PROPERTY_MAP_POJO)
		val map: MutableMap<A_Atom, A_BasicObject> =
			propertyMapPojo.javaObjectNotNull()
		when {
			value.isNil -> map.remove(key)
			else -> map[key.makeShared()] = value.makeShared()
		}
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	companion object {
		/**
		 * Create a new atom with the given name, module, and hash value.  The
		 * name is not globally unique, but serves to help to visually
		 * distinguish atoms. The hash value is provided to allow an existing
		 * [simple&#32;atom][AtomDescriptor] to be converted to an
		 * [atom&#32;with&#32;properties][AtomWithPropertiesDescriptor].  The
		 * client can convert the original simple atom into an
		 * [indirection][IndirectionDescriptor] to the new atom with properties.
		 *
		 * @param name
		 *   An [A_String] used to help identify the new atom.
		 * @param issuingModule
		 *   The [A_Module] that issued this atom.
		 * @param originalHashOrZero
		 *   The hash value that must be set for this atom, or zero if it has
		 *   not yet been computed.
		 * @return
		 *   The new atom, not equal to any object in use before this method was
		 *   invoked.
		 */
		fun createWithProperties(
			name: A_String,
			issuingModule: A_Module,
			originalHashOrZero: Int
		): AvailObject = mutable.create {
			setSlot(NAME, name)
			setSlot(ISSUING_MODULE, issuingModule)
			setSlot(
				PROPERTY_MAP_POJO,
				identityPojo(WeakHashMap<A_Atom, A_BasicObject>()))
			setSlot(HASH_OR_ZERO, originalHashOrZero)
		}

		/** The mutable [AtomWithPropertiesDescriptor]. */
		private val mutable = AtomWithPropertiesDescriptor(
			Mutability.MUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [AtomWithPropertiesDescriptor]. */
		private val immutable = AtomWithPropertiesDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}
