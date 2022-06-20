/*
 * SequencePhraseDescriptor.kt
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
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.statements
import avail.descriptor.phrases.SequencePhraseDescriptor.ObjectSlots.STATEMENTS
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter

/**
 * My instances represent a sequence of [phrases][PhraseDescriptor] to
 * be treated as statements, except possibly the last one.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SequencePhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.SEQUENCE_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [statements][PhraseDescriptor] that should be considered to
		 * execute sequentially, discarding each result except possibly for that
		 * of the last statement.
		 */
		STATEMENTS
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = self.slot(STATEMENTS).forEach(action)

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(
		STATEMENTS,
		tupleFromList(self.slot(STATEMENTS).map(transformer)))

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(STATEMENTS).forEach {
		it.emitEffectOn(codeGenerator)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val statements: A_Tuple = self.slot(STATEMENTS)
		val statementsCount = statements.tupleSize
		for (i in 1 until statementsCount) {
			statements.tupleAt(i).emitEffectOn(codeGenerator)
		}
		if (statementsCount > 0) {
			statements.tupleAt(statementsCount).emitValueOn(codeGenerator)
		}
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.slot(STATEMENTS).equals(aPhrase.statements))

	override fun o_PhraseExpressionType(self: AvailObject): A_Type {
		val statements: A_Tuple = self.slot(STATEMENTS)
		return when(statements.tupleSize) {
			0 -> TOP.o
			else -> statements.tupleAt(statements.tupleSize)
				.phraseExpressionType
		}
	}

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self.slot(STATEMENTS).forEach {
		it.flattenStatementsInto(accumulatedStatements)
	}

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.slot(STATEMENTS).hash(), -0x1c7ebf36)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.SEQUENCE_PHRASE

	override fun o_Statements(self: AvailObject): A_Tuple = self.slot(STATEMENTS)

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = self.slot(STATEMENTS).forEach(continuation)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SEQUENCE_PHRASE

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("sequence phrase") }
			at("statements") { self.slot(STATEMENTS).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("sequence phrase") }
			at("statements") { self.slot(STATEMENTS).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new [sequence][SequencePhraseDescriptor] phrase from the
		 * given [tuple][TupleDescriptor] of [statements][PhraseDescriptor].
		 *
		 * @param statements
		 *   The expressions to assemble into a
		 *   [sequence][SequencePhraseDescriptor] phrase.
		 * @return
		 *   The resulting sequence phrase.
		 */
		fun newSequence(statements: A_Tuple): A_Phrase =
			mutable.createShared {
				setSlot(STATEMENTS, statements)
			}

		/** The mutable [SequencePhraseDescriptor]. */
		private val mutable = SequencePhraseDescriptor(Mutability.MUTABLE)

		/** The shared [SequencePhraseDescriptor]. */
		private val shared = SequencePhraseDescriptor(Mutability.SHARED)
	}
}
