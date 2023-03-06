/*
 * LiteralPhraseDescriptor.kt
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
package avail.descriptor.phrases
import avail.compiler.AvailCodeGenerator
import avail.compiler.CompilationContext
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromBigInteger
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.phrases.A_Phrase.Companion.applyStylesThen
import avail.descriptor.phrases.A_Phrase.Companion.equalsPhrase
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.LiteralPhraseDescriptor.ObjectSlots.TOKEN
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.LiteralTokenDescriptor
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.interpreter.levelOne.L1Decompiler
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.math.BigInteger
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
	ObjectSlots::class.java)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The token that was transformed into this literal.
		 */
		TOKEN
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(self.token.string().asNativeString())
	}

	override fun o_ApplyStylesThen(
		self: AvailObject,
		context: CompilationContext,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit)
	{
		if (!visitedSet.add(self)) return then()
		val token = self.token
		var literal = token.literal()
		// As a nicety, drill into a literal phrase whose value is a literal
		// token.  This is how the compiler prepares '…#' arguments.
		if (literal.isLiteralToken())
		{
			literal = literal.literal()
		}
		val generatingPhrase = token.generatingPhrase
		// To help understand this chain of continuations, consider the order of
		// these operations:  First, it runs the superclass's styling, allowing
		// all subphrases (if any) to be styled. Second, it runs then3, styling
		// literals by the literal's type, and recursing if the literal is
		// itself a phrase. Third, it runs then2, which styles the generating
		// phrase, if any, of the literal.  Fourth, it runs the original
		// continuation.
		val then2: ()->Unit = {
			when
			{
				generatingPhrase.notNil -> generatingPhrase.applyStylesThen(
					context, visitedSet, then)
				else -> then()
			}
		}

		val then3: ()->Unit = {
			when
			{
				// When a macro name includes "_!", a call site will wrap that
				// argument *phrase* inside a synthetic literal (i.e., the value
				// of that literal will be a phrase.  We should traverse into
				// such a phrase.
				literal.isInstanceOf(PARSE_PHRASE.mostGeneralType) ->
					literal.applyStylesThen(context, visitedSet, then2)
				else -> then2()
			}
		}
		super.o_ApplyStylesThen(self, context, visitedSet, then3)
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit)
	{
		// Do nothing.
	}

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase)
	{
		// Do nothing.
	}

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator)
	{
		// Do nothing.
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.emitPushLiteral(
		tuple(self.token), self[TOKEN].literal())

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean
	{
		if (aPhrase.isMacroSubstitutionNode) return false
		if (self.phraseKind != aPhrase.phraseKind) return false
		val literal1 = self.token.literal()
		val literal2 = aPhrase.token.literal()
		return when {
			!literal1.isInstanceOf(PARSE_PHRASE.mostGeneralType) ->
				literal1.equals(literal2)
			!literal2.isInstanceOf(PARSE_PHRASE.mostGeneralType) -> false
			else -> literal1.equalsPhrase(literal2)
		}
	}

	/**
	 * Simplify decompilation by pretending a literal phrase holding tuple is
	 * actually a list phrase of literal phrases for each element.
	 *
	 * This only comes into play if the [L1Decompiler] was about to fail anyhow.
	 */
	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		tupleFromList(self.map(::syntheticLiteralNodeFor))

	override fun o_PhraseExpressionType(self: AvailObject): A_Type {
		val token: A_Token = self[TOKEN]
		assert(token.tokenType() === TokenType.LITERAL)
		return instanceTypeOrMetaOn(token.literal()).makeImmutable()
	}

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.LITERAL_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.LITERAL_PHRASE

	override fun o_Token(self: AvailObject): A_Token = self[TOKEN]

	override fun o_Tokens(self: AvailObject): A_Tuple
	{
		val token: A_Token = self[TOKEN]
		val literal = token.literal()
		return when
		{
			// The literal phrase's token's literal is also a token.
			// Answer both.
			literal.isInstanceOf(Types.TOKEN.o) -> tuple(token, literal)
			// Answer just the literal phrase's token.
			else -> tuple(token)
		}
	}

	override fun o_TokenIndicesInName(self: AvailObject): A_Tuple =
		generateIntTupleFrom(self.tokens.tupleSize) { 0 }

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("literal phrase") }
			at("token") { self[TOKEN].writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("literal phrase") }
			at("token") { self[TOKEN].writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun immutable() = immutable

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
				initHash()
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
				initHash()
			}
		}

		private val safePrintRange = run {
			val googol =
				fromBigInteger(
					BigInteger("1" + String.format("%0100d", 0))
				).makeShared()
			inclusive(zero.minusCanDestroy(googol, false), googol).makeShared()
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
		 * @param optionalGeneratingPhrase
		 *   The optional [A_Phrase] from which this literal node was created.
		 * @return
		 *   The new literal phrase.
		 */
		fun syntheticLiteralNodeFor(
			literalValue: A_BasicObject,
			literalAsString: A_String = when
				{
					literalValue.isString -> literalValue as A_String
					literalValue.isInstanceOf(safePrintRange) ->
						stringFrom(literalValue.toString())
					literalValue.isInstanceOf(integers) ->
						stringFrom("(a big integer)")
					else -> stringFrom(literalValue.toString())
				},
			optionalGeneratingPhrase: A_Phrase = nil
		): A_Phrase = literalNodeFromToken(
			literalToken(
				literalAsString,
				0,
				0,
				literalValue,
				nil,
				optionalGeneratingPhrase))

		/** The mutable [LiteralPhraseDescriptor]. */
		private val mutable = LiteralPhraseDescriptor(Mutability.MUTABLE)

		/** The immutable [LiteralPhraseDescriptor]. */
		private val immutable = LiteralPhraseDescriptor(Mutability.IMMUTABLE)

		/** The shared [LiteralPhraseDescriptor]. */
		private val shared = LiteralPhraseDescriptor(Mutability.SHARED)
	}
}
