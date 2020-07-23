/*
 * ExpressionAsStatementPhraseDescriptor.kt
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
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.expression
 import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
 import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
 import com.avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.ObjectSlots.EXPRESSION
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
 import com.avail.descriptor.types.TypeDescriptor.Types
 import com.avail.descriptor.types.TypeDescriptor.Types.TOP
 import com.avail.descriptor.types.TypeTag
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.json.JSONWriter
 import java.util.*

/**
 * My instances adapt expressions to be statements.  The two currently supported
 * examples are ⊤-value message sends and assignments.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ExpressionAsStatementPhraseDescriptor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/** The expression being wrapped to be a statement.  */
		EXPRESSION
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = self.slot(EXPRESSION).printOnAvoidingIndent(
		builder, recursionMap, indent)

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(EXPRESSION))

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(EXPRESSION, transformer(self.slot(EXPRESSION)))

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(EXPRESSION).emitEffectOn(codeGenerator)

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(EXPRESSION).emitValueOn(codeGenerator)

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.slot(EXPRESSION).equals(aPhrase.expression()))

	override fun o_Expression(self: AvailObject): A_Phrase =
		self.slot(EXPRESSION)

	/** Statements are always ⊤-valued. */
	override fun o_ExpressionType(self: AvailObject): A_Type = TOP.o

	override fun o_Hash(self: AvailObject) =
		self.slot(EXPRESSION).hash() + -0x6f773228

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.EXPRESSION_AS_STATEMENT_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = continuation(self)

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self.slot(EXPRESSION).tokens()

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("expression as statement phrase") }
			at("expression") { self.slot(EXPRESSION).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("expression as statement phrase") }
			at("expression") { self.slot(EXPRESSION).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new expression-as-statement phrase from the given expression
		 * phrase.
		 *
		 * @param expression
		 *   An expression (see [PhraseKind.EXPRESSION_PHRASE]).
		 * @return
		 *   The new expression-as-statement phrase (see
		 *   [PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE]).
		 */
		fun newExpressionAsStatement(expression: A_Phrase): A_Phrase =
			mutable.createShared {
				setSlot(EXPRESSION, expression)
			}

		/** The mutable [ExpressionAsStatementPhraseDescriptor].  */
		private val mutable =
			ExpressionAsStatementPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [ExpressionAsStatementPhraseDescriptor].  */
		private val shared =
			ExpressionAsStatementPhraseDescriptor(Mutability.SHARED)
	}
}
