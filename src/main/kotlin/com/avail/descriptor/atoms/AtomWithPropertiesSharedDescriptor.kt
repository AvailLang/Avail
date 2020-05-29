/*
 * AtomWithPropertiesSharedDescriptor.kt
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
package com.avail.descriptor.atoms

import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.Mutability.SHARED
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.MalformedMessageException
import com.avail.serialization.Serializer

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
	private val isSpecial: Boolean,
	typeTag: TypeTag
) : AtomWithPropertiesDescriptor(
	SHARED, typeTag, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the hash value, or zero if it has not been
			 * computed. The hash of an atom is a random number, computed once.
			 */
			@JvmField
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
		PROPERTY_MAP_POJO;

		companion object {
			init {
				assert(AtomDescriptor.ObjectSlots.NAME.ordinal
					== NAME.ordinal)
				assert(AtomDescriptor.ObjectSlots.ISSUING_MODULE.ordinal
					== ISSUING_MODULE.ordinal)
				assert(AtomWithPropertiesDescriptor.ObjectSlots.PROPERTY_MAP_POJO.ordinal
					== PROPERTY_MAP_POJO.ordinal)
			}
		}
	}

	override fun o_MakeImmutable(self: AvailObject) = self

	override fun o_MakeShared(self: AvailObject): AvailObject = self

	override fun o_Hash(self: AvailObject): Int {
		val hash = self.slot(HASH_OR_ZERO)
		if (hash == 0) {
			synchronized(self) {
				return super.o_Hash(self)
			}
		}
		return hash
	}

	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom
	): AvailObject = synchronized(self) {
		return super.o_GetAtomProperty(self, key)
	}

	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject
	) = synchronized(self) {
		super.o_SetAtomProperty(self, key.makeShared(), value.makeShared())
	}

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate(self: AvailObject): A_Bundle =
		synchronized(self) {
			return super.o_BundleOrCreate(self)
		}

	override fun o_BundleOrNil(self: AvailObject): A_Bundle =
		synchronized(self) {
			return super.o_BundleOrNil(self)
		}

	override fun o_IsAtomSpecial(self: AvailObject) = isSpecial

	companion object {
		/** The shared [AtomWithPropertiesDescriptor].  */
		val shared = AtomWithPropertiesSharedDescriptor(false, TypeTag.ATOM_TAG)

		/** The sharedAndSpecial [AtomWithPropertiesDescriptor].  */
		val sharedAndSpecial = AtomWithPropertiesSharedDescriptor(
			true, TypeTag.ATOM_TAG)

		/**
		 * The descriptor reserved for the
		 * [true&#32;atom][AtomDescriptor.trueObject].
		 */
		val sharedAndSpecialForTrue = AtomWithPropertiesSharedDescriptor(
			true, TypeTag.TRUE_TAG)

		/**
		 * The descriptor reserved for the
		 * [false&#32;atom][AtomDescriptor.falseObject].
		 */
		val sharedAndSpecialForFalse = AtomWithPropertiesSharedDescriptor(
			true, TypeTag.FALSE_TAG)
	}

}
