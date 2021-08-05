/*
 * LiteralTokenTypeDescriptor.kt
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
package com.avail.descriptor.types

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine2
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tokens.LiteralTokenDescriptor
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfLiteralTokenType
import com.avail.descriptor.types.A_Type.Companion.literalType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfLiteralTokenType
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfLiteralTokenType
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.ObjectSlots.LITERAL_TYPE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * I represent the type of some [literal&#32;tokens][LiteralTokenDescriptor].
 * Like any object, a particular literal token has an exact
 * [instance&#32;type][InstanceTypeDescriptor], and [tokens][TokenDescriptor] in
 * general have a simple [primitive&#32;type][PrimitiveTypeDescriptor] of
 * [TypeDescriptor.Types.TOKEN], but `LiteralTokenTypeDescriptor` covariantly
 * constrains a literal token's type with the type of the value it contains.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [LiteralTokenTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class LiteralTokenTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability, TypeTag.NONTYPE_TYPE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The type constraint on a literal token's value.
		 */
		LITERAL_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("literal token⇒")
		self.literalType.printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsLiteralTokenType(self)

	override fun o_EqualsLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean =
			self.literalType.equals(aLiteralTokenType.literalType)

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.slot(LITERAL_TYPE).hash(), -0xb800e4f)

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean = true

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// There is no immutable descriptor, so share the object.
			self.makeShared()
		}
		else self

	// Check if object (a type) is a subtype of aType (should also be a
	// type).
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfLiteralTokenType(self)

	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean =
			aLiteralTokenType.literalType.isSubtypeOf(
				self.literalType)

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		self.slot(LITERAL_TYPE).isVacuousType

	override fun o_LiteralType(self: AvailObject): A_Type =
		self.slot(LITERAL_TYPE)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.LITERAL_TOKEN_TYPE

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.equals(another) -> self
			self.isSubtypeOf(another) -> self
			another.isSubtypeOf(self) -> another
			else -> another.typeIntersectionOfLiteralTokenType(self)
		}

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type
	{
		// Note that the 'inner' type must be made immutable in case one of the
		// input literal token types is mutable (and may be destroyed
		// *recursively* by post-primitive code).
		val instance = self.literalType.typeIntersection(
			aLiteralTokenType.literalType)
		instance.makeImmutable()
		return literalTokenType(instance)
	}

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			if (Types.TOKEN.superTests[primitiveTypeEnum.ordinal]) self
			else bottom

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfLiteralTokenType(self)
		}

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type
	{
		// Note that the 'inner' type must be made immutable in case one of the
		// input literal token types is mutable (and may be destroyed
		// *recursively* by post-primitive code).
		val instance = self.literalType.typeUnion(
			aLiteralTokenType.literalType)
		instance.makeImmutable()
		return literalTokenType(instance)
	}

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			Types.TOKEN.unionTypes[primitiveTypeEnum.ordinal]!!

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("literal token type")
		writer.write("literal type")
		self.slot(LITERAL_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): LiteralTokenTypeDescriptor = mutable

	// There is no immutable variant.
	override fun immutable(): LiteralTokenTypeDescriptor = shared

	override fun shared(): LiteralTokenTypeDescriptor = shared

	companion object
	{
		/**
		 * Create a new literal token type whose literal values comply with the
		 * given type.
		 *
		 * @param literalType
		 *   The type with which to constrain literal values.
		 * @return
		 *   A [literal&#32;token&#32;type][LiteralTokenTypeDescriptor].
		 */
		fun literalTokenType(literalType: A_Type): AvailObject =
			mutable.create {
				setSlot(LITERAL_TYPE, literalType.makeImmutable())
			}

		/** The mutable [LiteralTokenTypeDescriptor]. */
		private val mutable = LiteralTokenTypeDescriptor(Mutability.MUTABLE)

		/** The shared [LiteralTokenTypeDescriptor]. */
		private val shared = LiteralTokenTypeDescriptor(Mutability.SHARED)

		/** The most general literal token type. */
		private val mostGeneralType: A_Type =
			literalTokenType(ANY.o).makeShared()

		/**
		 * Answer the most general literal token type, specifically the literal
		 * token type whose literal tokens' literal values are constrained by
		 * [any][TypeDescriptor.Types.ANY].
		 *
		 * @return
		 * The most general literal token type.
		 */
		fun mostGeneralLiteralTokenType(): A_Type = mostGeneralType
	}
}
