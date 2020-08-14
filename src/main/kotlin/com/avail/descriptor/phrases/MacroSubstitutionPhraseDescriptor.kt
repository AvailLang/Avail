/*
 * MacroSubstitutionPhraseDescriptor.kt
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
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.methods.MacroDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.bundle
import com.avail.descriptor.phrases.A_Phrase.Companion.copyConcatenating
import com.avail.descriptor.phrases.A_Phrase.Companion.copyWith
import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
import com.avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import com.avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import com.avail.descriptor.phrases.A_Phrase.Companion.expression
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import com.avail.descriptor.phrases.A_Phrase.Companion.generateInModule
import com.avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import com.avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.isLastUse
import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import com.avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.list
import com.avail.descriptor.phrases.A_Phrase.Companion.literalObject
import com.avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import com.avail.descriptor.phrases.A_Phrase.Companion.markerValue
import com.avail.descriptor.phrases.A_Phrase.Companion.neededVariables
import com.avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
import com.avail.descriptor.phrases.A_Phrase.Companion.permutation
import com.avail.descriptor.phrases.A_Phrase.Companion.statements
import com.avail.descriptor.phrases.A_Phrase.Companion.statementsDo
import com.avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.validateLocally
import com.avail.descriptor.phrases.A_Phrase.Companion.variable
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.MACRO_ORIGINAL_SEND
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.OUTPUT_PARSE_NODE
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.Primitive
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * A [macro&#32;substitution&#32;phrase][MacroSubstitutionPhraseDescriptor]
 * represents the result of applying a [macro][MacroDescriptor] to its
 * argument [expressions][PhraseDescriptor] to produce an output
 * [phrase][ObjectSlots.OUTPUT_PARSE_NODE].
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
	TypeTag.MACRO_SUBSTITUTION_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [send&#32;phrase][SendPhraseDescriptor] prior to its
		 * transformation into the [OUTPUT_PARSE_NODE].
		 */
		MACRO_ORIGINAL_SEND,

		/**
		 * The [phrase][PhraseDescriptor] that is the result of transforming the
		 * input phrase through a macro substitution.
		 */
		OUTPUT_PARSE_NODE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = self.slot(OUTPUT_PARSE_NODE).printOnAvoidingIndent(
		builder, recursionMap, indent)

	override fun o_ApparentSendName(self: AvailObject): A_Atom =
		self.slot(MACRO_ORIGINAL_SEND).apparentSendName()

	override fun o_ArgumentsListNode(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).argumentsListNode()

	override fun o_ArgumentsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).argumentsTuple()

	/**
	 * Reach into the output phrase.  If you want the macro name, use the
	 * apparentSendName instead.
	 */
	override fun o_Bundle(self: AvailObject): A_Bundle =
		self.slot(OUTPUT_PARSE_NODE).bundle()

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(OUTPUT_PARSE_NODE))

	/**
	 * Don't transform the original phrase, just the output phrase.
	 */
	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) {
		self.setSlot(
			OUTPUT_PARSE_NODE,
			transformer(self.slot(OUTPUT_PARSE_NODE)))
	}

	/** Create a copy of the list, not this macro substitution. */
	override fun o_CopyWith(self: AvailObject, newPhrase: A_Phrase): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).copyWith(newPhrase)

	/** Create a copy of the list, not this macro substitution. */
	override fun o_CopyConcatenating(
		self: AvailObject,
		newListPhrase: A_Phrase
	): A_Phrase = self.slot(OUTPUT_PARSE_NODE).copyConcatenating(newListPhrase)

	override fun o_Declaration(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).declaration()

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self.slot(OUTPUT_PARSE_NODE).declaredExceptions()

	override fun o_DeclaredType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PARSE_NODE).declaredType()

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens()) {
		self.slot(OUTPUT_PARSE_NODE).emitAllValuesOn(codeGenerator)
	}

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens()) {
		self.slot(OUTPUT_PARSE_NODE).emitEffectOn(codeGenerator)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = codeGenerator.setTokensWhile(self.slot(MACRO_ORIGINAL_SEND).tokens()) {
		self.slot(OUTPUT_PARSE_NODE).emitValueOn(codeGenerator)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (aPhrase.isMacroSubstitutionNode()
		&& self.slot(MACRO_ORIGINAL_SEND).equals(
			aPhrase.macroOriginalSendNode())
		&& self.slot(OUTPUT_PARSE_NODE).equals(
			aPhrase.outputPhrase()))

	override fun o_Expression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).expression()

	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).expressionAt(index)

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self.slot(OUTPUT_PARSE_NODE).expressionsSize()

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).expressionsTuple()

	override fun o_ExpressionType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PARSE_NODE).expressionType()

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self.slot(OUTPUT_PARSE_NODE).flattenStatementsInto(
		accumulatedStatements)

	override fun o_GenerateInModule(
		self: AvailObject,
		module: A_Module
	): A_RawFunction = self.slot(OUTPUT_PARSE_NODE).generateInModule(module)

	override fun o_Hash(self: AvailObject): Int =
		(self.slot(MACRO_ORIGINAL_SEND).hash() * multiplier
			+ (self.slot(OUTPUT_PARSE_NODE).hash() xor 0x1d50d7f9))

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self.slot(OUTPUT_PARSE_NODE).hasSuperCast()

	override fun o_InitializationExpression(self: AvailObject): AvailObject =
		self.slot(OUTPUT_PARSE_NODE).initializationExpression()

	override fun o_IsLastUse(self: AvailObject, isLastUse: Boolean) =
		self.slot(OUTPUT_PARSE_NODE).isLastUse(isLastUse)

	override fun o_IsLastUse(self: AvailObject): Boolean =
		self.slot(OUTPUT_PARSE_NODE).isLastUse()

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean = true

	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).lastExpression()

	override fun o_List(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).list()

	override fun o_LiteralObject(self: AvailObject): A_BasicObject =
		self.slot(OUTPUT_PARSE_NODE).literalObject()

	override fun o_MacroOriginalSendNode(self: AvailObject): A_Phrase =
		self.slot(MACRO_ORIGINAL_SEND)

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self.slot(OUTPUT_PARSE_NODE).markerValue()

	override fun o_NeededVariables(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).neededVariables()

	override fun o_NeededVariables(
		self: AvailObject,
		neededVariables: A_Tuple
	) = self.slot(OUTPUT_PARSE_NODE).neededVariables(neededVariables)

	override fun o_OutputPhrase(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE)

	/** Answer the output phrase's kind, not this macro substitution's kind. */
	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		self.slot(OUTPUT_PARSE_NODE).phraseKind()

	/** Use the output phrase's kind, not this macro substitution's kind. */
	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind
	): Boolean = self.slot(OUTPUT_PARSE_NODE).phraseKindIsUnder(
		expectedPhraseKind)

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).permutation()

	override fun o_Primitive(self: AvailObject): Primitive? =
		self.slot(OUTPUT_PARSE_NODE).primitive()

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MACRO_SUBSTITUTION_PHRASE

	override fun o_StartingLineNumber(self: AvailObject): Int =
		self.slot(OUTPUT_PARSE_NODE).startingLineNumber()

	override fun o_Statements(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).statements()

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = self.slot(OUTPUT_PARSE_NODE).statementsDo(continuation)

	override fun o_StatementsTuple(self: AvailObject): A_Tuple =
		self.slot(OUTPUT_PARSE_NODE).statementsTuple()

	override fun o_StripMacro(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE)

	override fun o_SuperUnionType(self: AvailObject): A_Type =
		self.slot(OUTPUT_PARSE_NODE).superUnionType()

	override fun o_Token(self: AvailObject): A_Token =
		self.slot(OUTPUT_PARSE_NODE).token()

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self.slot(MACRO_ORIGINAL_SEND).tokens()

	override fun o_TypeExpression(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).typeExpression()

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) = self.slot(OUTPUT_PARSE_NODE).validateLocally(parent)

	override fun o_Variable(self: AvailObject): A_Phrase =
		self.slot(OUTPUT_PARSE_NODE).variable()

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("macro substitution phrase") }
			at("macro send") { self.slot(MACRO_ORIGINAL_SEND).writeTo(writer) }
			at("output phrase") { self.slot(OUTPUT_PARSE_NODE).writeTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Construct a new macro substitution phrase.
		 *
		 * @param macroSend
		 *   The send of the macro that produced this phrase.
		 * @param outputPhrase
		 *   The expression produced by the macro body.
		 * @return
		 *   The new macro substitution phrase.
		 */
		fun newMacroSubstitution(
			macroSend: A_Phrase,
			outputPhrase: A_Phrase
		): A_Phrase = mutable.createShared {
			setSlot(MACRO_ORIGINAL_SEND, macroSend)
			setSlot(OUTPUT_PARSE_NODE, outputPhrase)
		}

		/** The mutable [MacroSubstitutionPhraseDescriptor].  */
		private val mutable =
			MacroSubstitutionPhraseDescriptor(Mutability.MUTABLE)

		/** The immutable [MacroSubstitutionPhraseDescriptor].  */
		private val shared =
			MacroSubstitutionPhraseDescriptor(Mutability.SHARED)
	}
}

