/*
 * AtomWithPropertiesSharedDescriptor.kt
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
package com.avail.descriptor.atoms

import com.avail.AvailRuntimeSupport
import com.avail.annotations.HideFieldInDebugger
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.IntegerSlots.HASH_AND_MORE
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.ObjectSlots.BUNDLE_OR_NIL
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.ObjectSlots.ISSUING_MODULE
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.ObjectSlots.NAME
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.ObjectSlots.PROPERTY_MAP_POJO
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.MethodDescriptor.Companion.newMethod
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IndirectionDescriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.Mutability.MUTABLE
import com.avail.descriptor.representation.Mutability.SHARED
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.MalformedMessageException
import com.avail.serialization.Serializer
import com.avail.serialization.SerializerOperation
import com.avail.utility.ifZero
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
 * [AvailObject]s using this descriptor are [Mutability.SHARED].
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.  While at first it
 *   seems this could always be [Mutability.SHARED], we still need a private
 *   mutable form for a moment to make use of [createShared].
 * @param isSpecial
 *   Whether this particular descriptor is used to represent an atom that has
 *   special meaning to the Avail virtual machine.
 * @param typeTag
 *   The [TypeTag] to use in this descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomDescriptor
 * @see AtomWithPropertiesDescriptor
 */
internal class AtomWithPropertiesSharedDescriptor private constructor(
	mutability: Mutability,
	private val isSpecial: Boolean,
	typeTag: TypeTag
) : AtomWithPropertiesDescriptor(
	mutability, typeTag, ObjectSlots::class.java, IntegerSlots::class.java)
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
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			init {
				assert(AtomWithPropertiesDescriptor.IntegerSlots.HASH_AND_MORE
					.ordinal
					== HASH_AND_MORE.ordinal)
				assert(AtomWithPropertiesDescriptor.IntegerSlots.HASH_OR_ZERO
					.isSamePlaceAs(HASH_OR_ZERO))
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
		 * A weak map from this atom's property keys (atoms) to property values.
		 */
		PROPERTY_MAP_POJO,

		/**
		 * The [A_Bundle] associated with this [A_Atom], or [nil] if there is
		 * none.
		 */
		BUNDLE_OR_NIL;

		companion object {
			init {
				assert(AtomDescriptor.ObjectSlots.NAME.ordinal
					== NAME.ordinal)
				assert(AtomDescriptor.ObjectSlots.ISSUING_MODULE.ordinal
					== ISSUING_MODULE.ordinal)
				assert(AtomWithPropertiesDescriptor.ObjectSlots
					.PROPERTY_MAP_POJO.ordinal
					== PROPERTY_MAP_POJO.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = super.allowsImmutableToMutableReferenceInField(e)
		|| e === PROPERTY_MAP_POJO
		|| e === BUNDLE_OR_NIL
		|| e === HASH_AND_MORE

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate (self: AvailObject): A_Bundle
	{
		var bundle: A_Bundle = self.volatileSlot(BUNDLE_OR_NIL)
		if (bundle.notNil) return bundle
		synchronized(self) {
			bundle = self.volatileSlot(BUNDLE_OR_NIL)
			if (bundle.notNil) return bundle
			val splitter = MessageSplitter(self.slot(NAME))
			val method: A_Method = newMethod(splitter.numberOfArguments)
			bundle = newBundle(self, method, splitter)
			self.setVolatileSlot(BUNDLE_OR_NIL, bundle)
		}
		return bundle
	}

	override fun o_BundleOrNil(self: AvailObject): A_Bundle =
		self.volatileSlot(BUNDLE_OR_NIL)

	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		val fields = super.o_DescribeForDebugger(self).toMutableList()
		val bundle = self.bundleOrNil()
		if (bundle.notNil)
		{
			fields.add(
				AvailObjectFieldHelper(
					self,
					DebuggerObjectSlots("Message bundle"),
					-1,
					bundle))
		}
		return fields.toTypedArray()
	}

	override fun o_ExtractBoolean (self: AvailObject): Boolean = when (this) {
		sharedForTrue -> true
		sharedForFalse -> false
		else -> error("Atom is not a boolean")
	}

	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom
	): AvailObject = when
	{
		self.volatileSlot(PROPERTY_MAP_POJO).isNil -> nil
		else -> synchronized(self) { super.o_GetAtomProperty(self, key) }
	}

	override fun o_Hash(self: AvailObject): Int {
		// Always set (to non-zero) during construction of a shared atom.
		return self.slot(HASH_OR_ZERO)
	}

	override fun o_IsAtomSpecial(self: AvailObject) = isSpecial

	override fun o_IsBoolean (self: AvailObject) =
		this === sharedForTrue || this === sharedForFalse

	override fun o_MakeImmutable(self: AvailObject) = self

	override fun o_MakeShared(self: AvailObject): AvailObject = self

	override fun o_MarshalToJava (
		self: AvailObject,
		classHint: Class<*>?
	): Any? = when {
		this === sharedForTrue -> java.lang.Boolean.TRUE
		this === sharedForFalse -> java.lang.Boolean.FALSE
		else -> super.o_MarshalToJava(self, classHint)
	}

	override fun o_SerializerOperation (self: AvailObject) = when {
		isSpecial -> SerializerOperation.SPECIAL_ATOM
		self.getAtomProperty(HERITABLE_KEY.atom).notNil ->
			SerializerOperation.HERITABLE_ATOM
		self.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom).notNil ->
			SerializerOperation.EXPLICIT_SUBCLASS_ATOM
		else -> SerializerOperation.ATOM
	}

	override fun o_SetAtomBundle(self: AvailObject, bundle: A_Bundle)
	{
		self.atomicUpdateSlot(BUNDLE_OR_NIL) {
			assert(isNil or bundle.isNil) {
				"Bundle can be cleared or set, but not changed"
			}
			bundle
		}
	}

	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject)
	{
		var map = self.volatileSlot(PROPERTY_MAP_POJO)
		if (map.isNil)
		{
			synchronized(self)
			{
				// Re-check the volatile field.
				map = self.volatileSlot(PROPERTY_MAP_POJO)
				if (map.isNil)
				{
					map = identityPojo(WeakHashMap<A_Atom, A_BasicObject>())
					self.setVolatileSlot(PROPERTY_MAP_POJO, map)
				}
			}
		}
		synchronized(self) {
			super.o_SetAtomProperty(self, key.makeShared(), value.makeShared())
		}
	}

	/**
	 * Create a new atom with the given initialization values, then setting the
	 * descriptor to the receiver.  The name is not globally unique, but serves
	 * to help to visually distinguish atoms. The hash value is provided to
	 * allow an existing non-shared [atom][AtomDescriptor] to be converted to
	 * this shared form, after which the client can convert the original atom
	 * into an [indirection][IndirectionDescriptor] to the new shared one.
	 *
	 * Note that the properties in the map, if present, are not made shared
	 * here, as this could lead to unbounded recursion if an atom is used inside
	 * one of its own properties.  The client should make these shared after
	 * this method returns.
	 *
	 * @param name
	 *   An [A_String] used to help identify the new atom.
	 * @param issuingModule
	 *   The [A_Module] that issued this atom.
	 * @param propertyMapPojoOrNil
	 *   Either a raw [pojo][RawPojoDescriptor] containing the weak property
	 *   map for the new [A_Atom], or [nil].
	 * @param originalHashOrZero
	 *   The hash value that must be set for this atom, or zero if a non-zero
	 *   hash should be generated now for the new atom.
	 * @return
	 *   The new atom, not equal to any object in use before this method was
	 *   invoked.
	 */
	fun createInitialized(
		name: A_String,
		issuingModule: A_Module,
		propertyMapPojoOrNil: AvailObject,
		originalHashOrZero: Int
	): AvailObject = initialPrivateMutable.create {
		setSlot(NAME, name.makeShared())
		setSlot(ISSUING_MODULE, issuingModule.makeShared())
		setVolatileSlot(PROPERTY_MAP_POJO, propertyMapPojoOrNil)
		val hash = originalHashOrZero.ifZero {
			AvailRuntimeSupport.nextNonzeroHash()
		}
		setSlot(HASH_OR_ZERO, hash)
		setSlot(BUNDLE_OR_NIL, nil)
		setDescriptor(this@AtomWithPropertiesSharedDescriptor)
	}

	companion object {
		private val initialPrivateMutable =
			AtomWithPropertiesSharedDescriptor(MUTABLE, false, TypeTag.ATOM_TAG)

		/** The shared [AtomWithPropertiesDescriptor].  */
		val shared =
			AtomWithPropertiesSharedDescriptor(SHARED, false, TypeTag.ATOM_TAG)

		/** The shared [AtomWithPropertiesDescriptor].  */
		val sharedSpecial =
			AtomWithPropertiesSharedDescriptor(SHARED, true, TypeTag.ATOM_TAG)

		/**
		 * The descriptor reserved for the
		 * [true&#32;atom][AtomDescriptor.trueObject].
		 */
		val sharedForTrue =
			AtomWithPropertiesSharedDescriptor(SHARED, true, TypeTag.TRUE_TAG)

		/**
		 * The descriptor reserved for the
		 * [false&#32;atom][AtomDescriptor.falseObject].
		 */
		val sharedForFalse =
			AtomWithPropertiesSharedDescriptor(SHARED, true, TypeTag.FALSE_TAG)
	}
}
