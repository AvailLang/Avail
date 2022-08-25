/*
 * MacroSubstitutionPhraseDescriptor.kt
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
import avail.descriptor.atoms.A_Atom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.methods.MacroDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import avail.descriptor.phrases.A_Phrase.Companion.applyStylesThen
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
import avail.descriptor.phrases.A_Phrase.Companion.bundle
import avail.descriptor.phrases.A_Phrase.Companion.childrenDo
import avail.descriptor.phrases.A_Phrase.Companion.copyConcatenating
import avail.descriptor.phrases.A_Phrase.Companion.copyWith
import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.declaredExceptions
import avail.descriptor.phrases.A_Phrase.Companion.declaredType
import avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.expression
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.A_Phrase.Companion.generateInModule
import avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import avail.descriptor.phrases.A_Phrase.Companion.isLastUse
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.list
import avail.descriptor.phrases.A_Phrase.Companion.literalObject
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.markerValue
import avail.descriptor.phrases.A_Phrase.Companion.neededVariables
import avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
import avail.descriptor.phrases.A_Phrase.Companion.permutation
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.sequence
import avail.descriptor.phrases.A_Phrase.Companion.statements
import avail.descriptor.phrases.A_Phrase.Companion.statementsDo
import avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
import avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import avail.descriptor.phrases.A_Phrase.Companion.validateLocally
import avail.descriptor.phrases.A_Phrase.Companion.variable
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.MACRO_ORIGINAL_SEND
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.OUTPUT_PHRASE
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MACRO_SUBSTITUTION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.TypeTag
import avail.interpreter.Primitive
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * A [macro&#32;substitution&#32;phrase][MacroSubstitutionPhraseDescriptor]
 * represents the result of applying a [macro][MacroDescriptor] to its
 * argument [expressions][PhraseDescriptor] to produce an output
 * [phrase][ObjectSlots.OUTPUT_PHRASE].
 *
 * It's kept around specifically to allow grammatical restrictions to operate on
 * the actual occurring macro (and method) names, not what they've turned into.
 * As such, the macro substitution phrase should be
 * [stripped][A_Phrase.stripMacro] prior to being composed into a larger parse
 * tree, whether a send phrase, another macro invocation, or direct embedding
 * within an assignment statement, variable reference, or any other hierarchical
 * parsing structure.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MacroSubstitutionPhraseDescriptor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [send&#32;phrase][SendPhraseDescriptor] prior to its
		 * transformation into the [OUTPUT_PHRASE].
		 */
		MACRO_ORIGINAL_SEND,

		/**
		 * The [phrase][PhraseDescriptor] that is the result of transforming the
		 * input phrase through a macro substitution.
		 */
		OUTPUT_PHRASE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = self.slot(OUTPUT_PHRASE).printOnAvoidingIndent(
		builder, recursionMap, indent)

	override fun o_ApparentSendName(self: AvailObject): A_Atom =
		self.slot(MACRO_ORIGINAL_SEND).apparentSendName

	override fun o_ApplyStylesThen(
		self: AvailObject,
		context: CompilationContext,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit)
	{
		// Iterate over the original's children, then the output, then style the
		// macro invocation itself.
		val original = self.macroOriginalSendNode
		context.visitAll(
			original.argumentsListNode.expressionsTuple.toList(), visitedSet
		) {
			val output = self.outputPhrase
			context.runtime.execute(FiberDescriptor.compilerPriority) {
				output.applyStylesThen(context, visitedSet) {
					context.styleSendThen(original, output, then)
				}
			}
		}
	}

	override fun o_ArgumentsListNode(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).argumentsListNode

	override fun o_ArgumentsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).argumentsTuple

	/**
	 * Reach into the output phrase.  If you want the macro name, use the
	 * apparentSendName instead.
	 */
	override fun o_Bundle(self: AvailObject): A_Bundle =
		self.slot(OUTPUT_PHRASE).bundle

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit
	) = action(self.slot(OUTPUT_PHRASE))

	/**
	 * Don't transform the original phrase, just the output phrase.
	 */
	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase
	)
	{
		self.setSlot(
			OUTPUT_PHRASE,
			transformer(self.slot(OUTPUT_PHRASE)))
	}

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag =
		self.slot(OUTPUT_PHRASE).typeTag

	/** Create a copy of the list, not this macro substitution. */
	override fun o_CopyWith(self: AvailObject, newPhrase: A_Phrase): A_Phrase =
		self.slot(OUTPUT_PHRASE).copyWith(newPhrase)

	/** Create a copy of the list, not this macro substitution. */
	override fun o_CopyConcatenating(
		self: AvailObject,
		newListPhrase: A_Phrase
	): A_Phrase = self.slot(OUTPUT_PHRASE).copyConcatenating(newListPhrase)

	override fun o_Declaration(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).declaration

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self.slot(OUTPUT_PHRASE).declaredExceptions

	override fun o_DeclaredType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PHRASE).declaredType

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens) {
		self.slot(OUTPUT_PHRASE).emitAllValuesOn(codeGenerator)
	}

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens) {
		self.slot(OUTPUT_PHRASE).emitEffectOn(codeGenerator)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens) {
		self.slot(OUTPUT_PHRASE).emitValueOn(codeGenerator)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (aPhrase.isMacroSubstitutionNode
		&& self.slot(MACRO_ORIGINAL_SEND).equalsPhrase(
			aPhrase.macroOriginalSendNode)
		&& self.slot(OUTPUT_PHRASE).equalsPhrase(aPhrase.outputPhrase))

	override fun o_Expression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).expression

	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self.slot(OUTPUT_PHRASE).expressionAt(index)

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self.slot(OUTPUT_PHRASE).expressionsSize

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).expressionsTuple

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PHRASE).phraseExpressionType

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self.slot(OUTPUT_PHRASE).flattenStatementsInto(
		accumulatedStatements)

	override fun o_GenerateInModule(
		self: AvailObject,
		module: A_Module
	): A_RawFunction = self.slot(OUTPUT_PHRASE).generateInModule(module)

	override fun o_Hash(self: AvailObject): Int = combine3(
		self.slot(MACRO_ORIGINAL_SEND).hash(),
		self.slot(OUTPUT_PHRASE).hash(),
		0x1d50d7f9)

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self.slot(OUTPUT_PHRASE).hasSuperCast

	override fun o_InitializationExpression(self: AvailObject): AvailObject =
		self.slot(OUTPUT_PHRASE).initializationExpression

	override fun o_IsLastUse(self: AvailObject, isLastUse: Boolean)
	{
		self.slot(OUTPUT_PHRASE).isLastUse = isLastUse
	}

	override fun o_IsLastUse(self: AvailObject): Boolean =
		self.slot(OUTPUT_PHRASE).isLastUse

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean = true

	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).lastExpression

	override fun o_List(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).list

	override fun o_LiteralObject(self: AvailObject): A_BasicObject =
		self.slot(OUTPUT_PHRASE).literalObject

	override fun o_MacroOriginalSendNode(self: AvailObject): A_Phrase =
		self.slot(MACRO_ORIGINAL_SEND)

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self.slot(OUTPUT_PHRASE).markerValue

	override fun o_NeededVariables(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).neededVariables

	override fun o_NeededVariables(
		self: AvailObject,
		neededVariables: A_Tuple)
	{
		self.slot(OUTPUT_PHRASE).neededVariables = neededVariables
	}

	override fun o_OutputPhrase(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE)

	/** Answer the output phrase's kind, not this macro substitution's kind. */
	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		self.slot(OUTPUT_PHRASE).phraseKind

	/** Use the output phrase's kind, not this macro substitution's kind. */
	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind
	): Boolean = self.slot(OUTPUT_PHRASE).phraseKindIsUnder(
		expectedPhraseKind)

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).permutation

	override fun o_Primitive(self: AvailObject): Primitive? =
		self.slot(OUTPUT_PHRASE).codePrimitive()

	override fun o_ResultType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PHRASE).resultType()

	override fun o_Sequence (self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).sequence

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MACRO_SUBSTITUTION_PHRASE

	override fun o_StartingLineNumber(self: AvailObject): Int =
		self.slot(OUTPUT_PHRASE).codeStartingLineNumber

	override fun o_Statements(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).statements

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase)->Unit
	) = self.slot(OUTPUT_PHRASE).statementsDo(continuation)

	override fun o_StatementsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PHRASE).statementsTuple

	override fun o_StripMacro(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE)

	override fun o_SuperUnionType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PHRASE).superUnionType

	override fun o_Token(self: AvailObject): A_Token =
		self.slot(OUTPUT_PHRASE).token

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self.slot(MACRO_ORIGINAL_SEND).tokens

	override fun o_TypeExpression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).typeExpression

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) = self.slot(OUTPUT_PHRASE).validateLocally(parent)

	override fun o_Variable(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PHRASE).variable

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("macro substitution phrase") }
			at("macro send") { self.slot(MACRO_ORIGINAL_SEND).writeTo(writer) }
			at("output phrase") { self.slot(OUTPUT_PHRASE).writeTo(writer) }
		}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Construct a new macro substitution phrase.
		 *
		 * @param macroSend
		 *   Either the send of the macro that produced this phrase, OR a
		 *   previously constructed macro substitution phrase, the original
		 *   phrase of which to use as the original phrase of the result.
		 * @param outputPhrase
		 *   The expression produced by the macro body.
		 * @return
		 *   The new macro substitution phrase.
		 */
		fun newMacroSubstitution(
			macroSend: A_Phrase,
			outputPhrase: A_Phrase
		): A_Phrase = mutable.createShared {
			val original = when (macroSend.phraseKind)
			{
				SEND_PHRASE -> macroSend
				MACRO_SUBSTITUTION_PHRASE -> macroSend.macroOriginalSendNode
				else -> throw IllegalArgumentException()
			}
			setSlot(MACRO_ORIGINAL_SEND, original)
			setSlot(OUTPUT_PHRASE, outputPhrase)
		}

		/** The mutable [MacroSubstitutionPhraseDescriptor]. */
		private val mutable =
			MacroSubstitutionPhraseDescriptor(Mutability.MUTABLE)

		/** The immutable [MacroSubstitutionPhraseDescriptor]. */
		private val immutable =
			MacroSubstitutionPhraseDescriptor(Mutability.IMMUTABLE)

		/** The immutable [MacroSubstitutionPhraseDescriptor]. */
		private val shared =
			MacroSubstitutionPhraseDescriptor(Mutability.SHARED)
	}
}

