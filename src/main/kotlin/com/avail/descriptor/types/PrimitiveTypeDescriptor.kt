/*
 * PrimitiveTypeDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.types

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.PrimitiveTypeDescriptor.IntegerSlots.Companion.HASH
import com.avail.descriptor.types.PrimitiveTypeDescriptor.ObjectSlots.*
import com.avail.descriptor.types.TypeDescriptor.Types.Companion.all
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * The primitive types of Avail are different from the notion of primitive types
 * in other object-oriented languages. Traditionally, a compiler or virtual
 * machine encodes representation knowledge about and makes other special
 * provisions about its primitive types. Since *all* types are in a
 * sense provided by the Avail system, it has no special primitive types that
 * fill that role – they're *all* special.
 *
 * [any][TypeDescriptor.Types.ANY], and various specialties such as
 * [atom][TypeDescriptor.Types.ATOM] and [number][TypeDescriptor.Types.NUMBER].
 * Type hierarchies that have a natural root don't bother with a primitive type
 * to delimit the hierarchy, using the natural root itself. For of the tuple
 * types.
 *
 * @see TypeDescriptor.Types all primitive types
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
open class PrimitiveTypeDescriptor : TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, and the upper 32 are
		 * for the ordinal of the primitive type.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The hash, populated during construction.
			 */
			val HASH = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [name][StringDescriptor] of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(self.slot(NAME).asNativeString())
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPrimitiveType(self)

	// Primitive types compare by identity.
	override fun o_EqualsPrimitiveType(
		self: AvailObject,
		aPrimitiveType: A_Type): Boolean = self.sameAddressAs(aPrimitiveType)

	override fun o_Hash(self: AvailObject): Int = self.slot(HASH)

	override fun o_Parent(self: AvailObject): A_BasicObject =
		self.slot(PARENT)

	// Check if object (a type) is a subtype of aType (should also be a
	// type).
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfPrimitiveTypeEnum(extractEnum(self))

	// This primitive type is a supertype of aFiberType if and only if
	// this primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// This primitive type is a supertype of aFunctionType if and only if
	// this primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// A primitive type is a supertype of a variable type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// A primitive type is a supertype of a continuation type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// A primitive type is a supertype of a compiled code type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// Parent of the top integer range type is number, so continue
	// searching there.
	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NUMBER)

	// This primitive type is a supertype of aTokenType if and only if this
	// primitive type is a supertype of TOKEN.
	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.TOKEN)

	// This primitive type is a supertype of aLiteralTokenType if and only
	// if this primitive type is a supertype of TOKEN.
	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.TOKEN)

	// This primitive type is a supertype of aMapType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// Check if I'm a supertype of the given eager object type. Only NONTYPE
	// and its ancestors are supertypes of an object type.
	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): Boolean =
			primitiveTypeEnum.superTests[extractOrdinal(self)]

	// This primitive type is a supertype of aSetType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	// This primitive type is a supertype of aTupleType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE)

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_Type): Boolean =
			topMeta().isSubtypeOf(self)

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			// There is no immutable descriptor; use the shared one.
			self.makeShared()
		}
		return self
	}

	override fun o_MarshalToJava(self: AvailObject, classHint: Class<*>?): Any?
	{
		for (type in all())
		{
			if (self.equals(type.o()))
			{
				return when (type)
				{
					Types.TOP -> Void::class.java
					Types.ANY -> Any::class.java
					Types.DOUBLE -> Double::class.javaPrimitiveType
					Types.FLOAT -> Float::class.javaPrimitiveType
					Types.ABSTRACT_DEFINITION, Types.ATOM, Types.CHARACTER,
					Types.DEFINITION_PARSING_PLAN, Types.FORWARD_DEFINITION,
					Types.LEXER, Types.MACRO_DEFINITION, Types.MESSAGE_BUNDLE,
					Types.MESSAGE_BUNDLE_TREE, Types.METHOD,
					Types.METHOD_DEFINITION, Types.MODULE, Types.NONTYPE,
					Types.NUMBER, Types.PARSING_PLAN_IN_PROGRESS,
					Types.RAW_POJO, Types.DEFINITION, Types.TOKEN ->
						super.o_MarshalToJava(self, classHint)
				}
			}
		}
		assert(false) {
			"All cases have been dealt with, and each forces a return"
		}
		throw RuntimeException()
	}

	// Most of the primitive types are already handled as special objects,
	// so this only kicks in as a backup.
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.ARBITRARY_PRIMITIVE_TYPE

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		another.typeIntersectionOfPrimitiveTypeEnum(extractEnum(self))

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type =
			if (Types.NONTYPE.superTests[extractOrdinal(self)]) aListNodeType
			else bottom()

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type =
			if (Types.NONTYPE.superTests[extractOrdinal(self)]) aPhraseType
			else bottom()

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			primitiveTypeEnum.intersectionTypes[extractOrdinal(self)]!!

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		another.typeUnionOfPrimitiveTypeEnum(extractEnum(self))

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			primitiveTypeEnum.unionTypes[extractOrdinal(self)]!!

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write(
			"${self.slot(NAME).asNativeString().toLowerCase()} type")
		writer.endObject()
	}

	/**
	 * Complete the given partially-initialized primitive type.  Set the type's
	 * parent and (shared) descriptor.  Don't force the parent to be shared yet
	 * if it isn't.
	 *
	 * @param parentType
	 *   The parent of this object, not necessarily shared.
	 */
	fun finishInitializingPrimitiveTypeWithParent(
		self: AvailObject,
		parentType: A_Type)
	{
		assert(mutability === Mutability.SHARED)
		self.setSlot(PARENT, parentType)
		self.setDescriptor(this)
	}

	/**
	 * The [primitive&#32;type][TypeDescriptor.Types] represented by this
	 * descriptor.
	 */
	val primitiveType: Types?

	/**
	 * Construct a new [shared][Mutability.SHARED] [PrimitiveTypeDescriptor].
	 *
	 * @param typeTag
	 *   The [TypeTag] to embed in the new descriptor.
	 * @param primitiveType
	 *   The [primitive&#32;type][TypeDescriptor.Types] represented by this
	 *   descriptor.
	 * @param objectSlotsEnumClass
	 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines
	 *   this object's object slots layout, or `null` if there are no object
	 *   slots.
	 * @param integerSlotsEnumClass
	 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines
	 *   this object's integer slots layout, or `null` if there are no integer
	 *   slots.
	 */
	protected constructor(
		typeTag: TypeTag,
		primitiveType: Types,
		objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
		integerSlotsEnumClass: Class<out IntegerSlotsEnum>?) : super(
			Mutability.SHARED,
			typeTag,
			objectSlotsEnumClass,
			integerSlotsEnumClass)
	{
		this.primitiveType = primitiveType
	}

	/**
	 * Construct a new [shared][Mutability.SHARED] [PrimitiveTypeDescriptor].
	 *
	 * @param typeTag
	 *   The [TypeTag] to embed in the new descriptor.
	 * @param primitiveType
	 * The [primitive&#32;type][TypeDescriptor.Types] represented by this
	 * descriptor.
	 */
	internal constructor(typeTag: TypeTag, primitiveType: Types)
		: this(
			typeTag,
			primitiveType,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

	/**
	 * Construct the sole mutable [PrimitiveTypeDescriptor], used only during
	 * early instantiation of the primitive types.
	 */
	private constructor() : super(
		Mutability.MUTABLE,
		TypeTag.UNKNOWN_TAG,
		ObjectSlots::class.java,
		IntegerSlots::class.java)
	{
		primitiveType = null
	}

	override fun mutable(): PrimitiveTypeDescriptor = transientMutable

	override fun immutable(): PrimitiveTypeDescriptor
	{
		// There are no immutable versions.
		assert(mutability === Mutability.SHARED)
		return this
	}

	override fun shared(): PrimitiveTypeDescriptor
	{
		assert(mutability === Mutability.SHARED)
		return this
	}

	companion object
	{
		/**
		 * Extract the [TypeDescriptor.Types] enum value from this primitive
		 * type.
		 *
		 * @param self
		 *   The primitive type.
		 * @return
		 *   The [TypeDescriptor.Types] enum value.
		 */
		private fun extractEnum(self: AvailObject): Types =
			(self.descriptor() as PrimitiveTypeDescriptor).primitiveType!!

		/**
		 * Extract the [TypeDescriptor.Types] enum value's [Enum.ordinal] from
		 * this primitive type.
		 *
		 * @param self
		 *   The primitive type.
		 * @return
		 *   The [TypeDescriptor.Types] enum value's ordinal.
		 */
		fun extractOrdinal(self: AvailObject): Int = extractEnum(self).ordinal

		/**
		 * Create a partially-initialized primitive type with the given name.
		 * The type's parent will be set later, to facilitate arbitrary
		 * construction order.  Set these fields to [nil][NilDescriptor] to
		 * ensure pointer safety.
		 *
		 * @param typeNameString
		 *   The name to give the object being initialized.
		 * @return
		 *   The partially initialized type.
		 */
		fun createMutablePrimitiveObjectNamed(
			typeNameString: String
		): AvailObject = transientMutable.create {
			val name = stringFrom(typeNameString)
			setSlot(NAME, name.makeShared())
			setSlot(PARENT, NilDescriptor.nil)
			setSlot(HASH, typeNameString.hashCode() * multiplier)
		}

		/**
		 * The sole mutable [PrimitiveTypeDescriptor], only used during early
		 * instantiation.
		 */
		val transientMutable = PrimitiveTypeDescriptor()
	}
}
