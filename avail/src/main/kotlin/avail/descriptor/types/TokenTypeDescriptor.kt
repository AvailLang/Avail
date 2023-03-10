/*
 * TokenTypeDescriptor.kt
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
package avail.descriptor.types

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tokens.TokenDescriptor.TokenType.Companion.lookupTokenType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfTokenType
import avail.descriptor.types.A_Type.Companion.tokenType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfTokenType
import avail.descriptor.types.A_Type.Companion.typeUnionOfTokenType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.TokenTypeDescriptor.IntegerSlots.TOKEN_TYPE_CODE
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * I represent the type of some [tokens][TokenDescriptor]. Like any object, a
 * particular token has an exact [instance type][InstanceTypeDescriptor], but
 * `TokenTypeDescriptor` covariantly constrains a token's type by its
 * [TokenType].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [TokenTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class TokenTypeDescriptor
private constructor(
	mutability: Mutability
) : TypeDescriptor(
	mutability,
	TypeTag.TOKEN_TYPE_TAG,
	TypeTag.TOKEN_TAG,
	null,
	IntegerSlots::class.java)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The [type][TokenDescriptor.TokenType] constraint on a token's value.
		 */
		TOKEN_TYPE_CODE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(String.format(
			"%s token",
			self.tokenType().name.lowercase().replace('_', ' ')))
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsTokenType(self)

	override fun o_EqualsTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean =
			self.tokenType() === aTokenType.tokenType

	override fun o_Hash(self: AvailObject): Int =
		combine2(self[TOKEN_TYPE_CODE].toInt(), -0x32659c49)

	override fun o_IsTokenType(self: AvailObject): Boolean = true

	// Check if object (a type) is a subtype of aType (should also be a type).
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfTokenType(self)

	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean =
			self.tokenType() === aTokenType.tokenType

	override fun o_TokenType(self: AvailObject): TokenType =
		lookupTokenType(self[TOKEN_TYPE_CODE].toInt())

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.TOKEN_TYPE

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.equals(another) -> self
			self.isSubtypeOf(another) -> self
			another.isSubtypeOf(self) -> another
			else -> another.typeIntersectionOfTokenType(self)
		}

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type =
			if (self.tokenType() === aTokenType.tokenType) self
			else bottom

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			if (TOKEN.superTests[primitiveTypeEnum.ordinal]) self
			else bottom

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfTokenType(self)
		}

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type =
			if (self.tokenType() === aTokenType.tokenType) self
			else TOKEN.o

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			TOKEN.unionTypes[primitiveTypeEnum.ordinal]!!

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("token type")
		writer.write("token type")
		writer.write(self.tokenType().name.lowercase().replace('_', ' '))
		writer.endObject()
	}

	override fun mutable(): TokenTypeDescriptor = mutable

	override fun immutable(): TokenTypeDescriptor = immutable

	override fun shared(): TokenTypeDescriptor = shared

	companion object
	{
		/**
		 * Create a new token type whose values comply with the given
		 * [TokenType].
		 *
		 * @param tokenType
		 *   The type with which to constrain values.
		 * @return
		 *   A [token type][TokenTypeDescriptor].
		 */
		fun tokenType(tokenType: TokenType): AvailObject =
			mutable.create {
				setSlot(TOKEN_TYPE_CODE, tokenType.ordinal.toLong())
			}

		/** The mutable [TokenTypeDescriptor]. */
		private val mutable = TokenTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [TokenTypeDescriptor]. */
		private val immutable = TokenTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [TokenTypeDescriptor]. */
		private val shared = TokenTypeDescriptor(Mutability.SHARED)
	}
}
