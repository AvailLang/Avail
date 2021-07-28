/*
 * LiteralTokenDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.tokens

import com.avail.annotations.HideFieldInDebugger
import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tokens.LiteralTokenDescriptor.IntegerSlots.Companion.LINE_NUMBER
import com.avail.descriptor.tokens.LiteralTokenDescriptor.IntegerSlots.Companion.START
import com.avail.descriptor.tokens.LiteralTokenDescriptor.ObjectSlots.LITERAL
import com.avail.descriptor.tokens.LiteralTokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO
import com.avail.descriptor.tokens.LiteralTokenDescriptor.ObjectSlots.STRING
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.literalType
import com.avail.descriptor.types.InstanceTypeDescriptor
import com.avail.descriptor.types.LiteralTokenTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * I represent a token that's a literal representation of some object.
 *
 * In addition to the state inherited from [TokenDescriptor], I add a field to
 * hold the literal value itself.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `LiteralTokenDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class LiteralTokenDescriptor private constructor(
	mutability: Mutability
) : TokenDescriptor(
	mutability,
	TypeTag.LITERAL_TOKEN_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java)
{
	/**
	 * My class's slots of type int.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * [BitField]s for the token type code, the starting byte position, and
		 * the line number.
		 */
		START_AND_LINE;

		companion object
		{
			/**
			 * The line number in the source file. Currently signed 28 bits,
			 * which should be plenty.
			 */
			val LINE_NUMBER = BitField(START_AND_LINE, 4, 28)

			/**
			 * The starting position in the source file. Currently signed 32
			 * bits, but this may change at some point -- not that we really
			 * need to parse 2GB of *Avail* source in one file, due to its
			 * deeply flexible syntax.
			 */
			@HideFieldInDebugger
			val START = BitField(START_AND_LINE, 32, 32)

			init
			{
				assert(
					TokenDescriptor.IntegerSlots
						.TOKEN_TYPE_AND_START_AND_LINE.ordinal
						== START_AND_LINE.ordinal)
				assert(TokenDescriptor.IntegerSlots.START.isSamePlaceAs(START))
				assert(
					TokenDescriptor.IntegerSlots.LINE_NUMBER
						.isSamePlaceAs(LINE_NUMBER))
			}
		}
	}

	/**
	 * My slots of type [AvailObject]. Note that they have to start the same as
	 * in my superclass [TokenDescriptor].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [string][StringDescriptor], exactly as I appeared in the source.
		 */
		STRING,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] holding the [LexingState] after
		 * this token.
		 */
		NEXT_LEXING_STATE_POJO,

		/** The actual [AvailObject] wrapped by this token. */
		LITERAL;

		companion object
		{
			init
			{
				assert(
					TokenDescriptor.ObjectSlots.STRING.ordinal
						== STRING.ordinal)
				assert(
					TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO.ordinal
						== NEXT_LEXING_STATE_POJO.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
		(e === NEXT_LEXING_STATE_POJO
			|| super.allowsImmutableToMutableReferenceInField(e))

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(
			String.format(
				"%s ",
				self.tokenType().name.lowercase().replace('_', ' ')))
		self.slot(LITERAL).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append(
			String.format(
				" (%s) @ %d:%d",
				self.slot(STRING),
				self.slot(START),
				self.slot(LINE_NUMBER)))
	}

	override fun o_TokenType(self: AvailObject): TokenType =
		TokenType.LITERAL

	override fun o_Literal(self: AvailObject): AvailObject =
		self.slot(LITERAL)

	override fun o_Kind(self: AvailObject): A_Type =
		LiteralTokenTypeDescriptor.literalTokenType(
			InstanceTypeDescriptor.instanceType(self))

	override fun o_IsInstanceOfKind(
		self: AvailObject, aType: A_Type): Boolean =
		(aType.isSupertypeOfPrimitiveTypeEnum(
			TOKEN)
			|| aType.isLiteralTokenType
			&& self.slot(LITERAL)
			.isInstanceOf(aType.literalType))

	override fun o_IsLiteralToken(self: AvailObject): Boolean = true

	override fun o_SerializerOperation(self: AvailObject)
		: SerializerOperation = SerializerOperation.LITERAL_TOKEN

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("token") }
			at("token type") {
				write(
					self.tokenType().name.lowercase().replace('_', ' '))
			}
			at("start") { write(self.slot(START)) }
			at("line number") { write(self.slot(LINE_NUMBER)) }
			at("lexeme") { self.slot(STRING).writeTo(writer) }
			at("literal") { self.slot(LITERAL).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("token") }
			at("start") { write(self.slot(START)) }
			at("line number") { write(self.slot(LINE_NUMBER)) }
			at("lexeme") { self.slot(STRING).writeTo(writer) }
			at("literal") { self.slot(LITERAL).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	// Answer the shared descriptor, since there isn't an immutable one.
	override fun immutable() = shared

	override fun shared() = shared

	companion object
	{
		/**
		 * Create and initialize a new `LiteralTokenDescriptor literal token`.
		 *
		 * @param string
		 *   The token text.
		 * @param start
		 *   The token's starting character position in the file.
		 * @param lineNumber
		 *   The line number on which the token occurred.
		 * @param literal
		 *   The literal value.
		 * @return
		 *   The new literal token.
		 */
		fun literalToken(
			string: A_String,
			start: Int,
			lineNumber: Int,
			literal: A_BasicObject
		): AvailObject = mutable.createShared {
			setSlot(STRING, string)
			setSlot(START, start)
			setSlot(LINE_NUMBER, lineNumber)
			setSlot(LITERAL, literal)
			if (literal.isInstanceOfKind(TOKEN.o)) {
				// We're wrapping another token, so share that token's
				// nextLexingState pojo, if set.
				val innerToken: A_Token = literal.traversed()
				val nextStatePojo = innerToken.nextLexingStatePojo()
				setSlot(NEXT_LEXING_STATE_POJO, nextStatePojo)
				// Also add this token to the same CompilationContext that the
				// inner token might also be inside.  Even if it isn't, the new
				// token will be cleanly disconnected from the
				// CompilationContext after finishing parsing the current
				// top-level statement.
				if (nextStatePojo.notNil)
				{
					val nextState: LexingState =
						nextStatePojo.javaObjectNotNull()
					nextState.compilationContext.recordToken(innerToken)
				}
			}
			else
			{
				setSlot(NEXT_LEXING_STATE_POJO, nil)
			}
		}

		/** The mutable [LiteralTokenDescriptor]. */
		private val mutable = LiteralTokenDescriptor(Mutability.MUTABLE)

		/** The shared [LiteralTokenDescriptor]. */
		private val shared = LiteralTokenDescriptor(Mutability.SHARED)
	}
}
