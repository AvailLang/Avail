/*
 * SequenceAsExpressionPhraseDescriptor.kt
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
import avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.sequence
import avail.descriptor.phrases.A_Phrase.Companion.statements
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.SequenceAsExpressionPhraseDescriptor.ObjectSlots.SEQUENCE
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import avail.utility.Strings.newlineTab
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances adapt a sequence of statements, the last one potentially
 * producing a non-⊤ value, as an expression.  The two currently supported
 * examples are ⊤-value message sends and assignments.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SequenceAsExpressionPhraseDescriptor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.SEQUENCE_AS_EXPRESSION_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [sequence][PhraseKind.SEQUENCE_PHRASE] being wrapped to be an
		 * [expression][PhraseKind.EXPRESSION_PHRASE].
		 */
		SEQUENCE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("sequence-as-expression(")
		self.slot(SEQUENCE).statements.forEach { statement ->
			builder.newlineTab(indent)
			statement.printOnAvoidingIndent(builder, recursionMap, indent + 1)
			builder.append(";")
		}
		builder.append(")")
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(SEQUENCE))

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.updateSlot(SEQUENCE, transformer)

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(SEQUENCE).emitEffectOn(codeGenerator)

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(SEQUENCE).emitValueOn(codeGenerator)

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.slot(SEQUENCE).equals(aPhrase.sequence))

	override fun o_Sequence(self: AvailObject): A_Phrase =
		self.slot(SEQUENCE)

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.slot(SEQUENCE).phraseExpressionType

	override fun o_Hash(self: AvailObject) =
		combine2(self.slot(SEQUENCE).hash(), 0x46D8127F)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.SEQUENCE_AS_EXPRESSION_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SEQUENCE_AS_EXPRESSION_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = continuation(self)

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self.slot(SEQUENCE).tokens

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("sequence as expression phrase") }
			at("sequence") { self.slot(SEQUENCE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("sequence as expression phrase") }
			at("sequence") { self.slot(SEQUENCE).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new sequence-as-expression phrase from the given sequence
		 * phrase.
		 *
		 * @param sequence
		 *   A [sequence][SequencePhraseDescriptor] phrase.
		 * @return
		 *   The new
		 *   [sequence-as-expression][SequenceAsExpressionPhraseDescriptor]
		 *   phrase.
		 */
		fun newSequenceAsExpression(sequence: A_Phrase): A_Phrase =
			mutable.createShared {
				setSlot(SEQUENCE, sequence)
			}

		/** The mutable [SequenceAsExpressionPhraseDescriptor]. */
		private val mutable =
			SequenceAsExpressionPhraseDescriptor(Mutability.MUTABLE)

		/** The immutable [SequenceAsExpressionPhraseDescriptor]. */
		private val immutable =
			SequenceAsExpressionPhraseDescriptor(Mutability.IMMUTABLE)

		/** The shared [SequenceAsExpressionPhraseDescriptor]. */
		private val shared =
			SequenceAsExpressionPhraseDescriptor(Mutability.SHARED)
	}
}
