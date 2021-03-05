/*
 * LiteralPhraseDescriptor.kt
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
package com.avail.descriptor.phrases
import com.avail.compiler.AvailCodeGenerator
import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.ObjectSlots.TOKEN
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.LiteralTokenDescriptor
import com.avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelOne.L1Decompiler
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances are occurrences of literals parsed from Avail source code.  At
 * the moment only strings and non-negative numbers are supported.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class LiteralPhraseDescriptor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.LITERAL_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The token that was transformed into this literal.
		 */
		TOKEN
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append(self.token().string().asNativeString())
	}

	override fun o_ChildrenDo(self: AvailObject, action: (A_Phrase) -> Unit) {
		// Do nothing.
	}

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) {
		// Do nothing.
	}

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		// Do nothing.
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.emitPushLiteral(
		tuple(self.token()), self.slot(TOKEN).literal())

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.slot(TOKEN).equals(aPhrase.token()))

	/**
	 * Simplify decompilation by pretending a literal phrase holding tuple is
	 * actually a list phrase of literal phrases for each element.
	 *
	 * This only comes into play if the [L1Decompiler] was about to fail anyhow.
	 */
	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		tupleFromList(self.map { syntheticLiteralNodeFor(it) })

	override fun o_PhraseExpressionType(self: AvailObject): A_Type {
		val token: A_Token = self.slot(TOKEN)
		assert(token.tokenType() === TokenType.LITERAL)
		return instanceTypeOrMetaOn(token.literal()).makeImmutable()
	}

	override fun o_Hash(self: AvailObject): Int =
		self.token().hash() xor -0x6379f3f3

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.LITERAL_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.LITERAL_PHRASE

	override fun o_Token(self: AvailObject): A_Token = self.slot(TOKEN)

	override fun o_Tokens(self: AvailObject): A_Tuple = tuple(self.slot(TOKEN))

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("literal phrase") }
			at("token") { self.slot(TOKEN).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("literal phrase") }
			at("token") { self.slot(TOKEN).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a literal phrase from a [literal][LiteralTokenDescriptor].
		 *
		 * @param token
		 *   The token that describes the literal.
		 * @return
		 *   The new literal phrase.
		 */
		fun fromTokenForDecompiler(token: A_Token): A_Phrase =
			mutable.createShared {
				setSlot(TOKEN, token)
			}

		/**
		 * Create a literal phrase from a [literal][LiteralTokenDescriptor].
		 *
		 * @param token
		 *   The token that describes the literal.
		 * @return
		 *   The new literal phrase.
		 */
		fun literalNodeFromToken(token: A_Token): A_Phrase {
			assert(token.isInstanceOfKind(mostGeneralLiteralTokenType()))
			return mutable.createShared {
				setSlot(TOKEN, token)
			}
		}

		/**
		 * Create a literal phrase from an [AvailObject], the literal value
		 * itself.  Automatically wrap the value inside a synthetic literal
		 * token. Use the specified `literalAsString` as the string form of the
		 * literal for printing.
		 *
		 * @param literalValue
		 *   The value that this literal phrase should produce.
		 * @param literalAsString
		 *   The optional [A_String] used to describe this literal.
		 * @return
		 *   The new literal phrase.
		 */
		@JvmOverloads
		fun syntheticLiteralNodeFor(
			literalValue: A_BasicObject,
			literalAsString: A_String =
				if (literalValue.isString) literalValue as A_String
				else stringFrom(literalValue.toString())
		): A_Phrase = literalNodeFromToken(
			literalToken(literalAsString, 0, 0, literalValue))

		/** The mutable [LiteralPhraseDescriptor].  */
		private val mutable = LiteralPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [LiteralPhraseDescriptor].  */
		private val shared = LiteralPhraseDescriptor(Mutability.SHARED)
	}
}
