/*
 * FirstOfSequencePhraseDescriptor.kt
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
package avail.descriptor.phrases
import avail.compiler.AvailCodeGenerator
import avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.statements
import avail.descriptor.phrases.A_Phrase.Companion.statementsDo
import avail.descriptor.phrases.FirstOfSequencePhraseDescriptor.ObjectSlots.STATEMENTS
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter

/**
 * My instances represent a sequence of [phrases][PhraseDescriptor] to be
 * treated as statements, except the *first* one. All phrases are executed, and
 * all results except the one from the first phrase are discarded. The
 * [first-of-sequence][FirstOfSequencePhraseDescriptor] phrase's effective value
 * is the value produced by the first phrase.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class FirstOfSequencePhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.FIRST_OF_SEQUENCE_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [A_Tuple] of [expressions][PhraseDescriptor] that should be
		 * considered to execute sequentially, discarding each result except for
		 * that of the *first* expression. There must be at least one
		 * expression.  All expressions but the first must be typed as ⊤.  The
		 * first one is also allowed to be typed as ⊤, but even if so, *if* the
		 * actual value produced is more specific (i.e., not [nil], then that is
		 * what the [first-of-sequence][FirstOfSequencePhraseDescriptor]
		 * phrase's effective value will be.
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
	) {
		self.setSlot(
			STATEMENTS,
			tupleFromList(self.slot(STATEMENTS).map(transformer)))
	}

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		// It's unclear under what circumstances this construct would be asked
		// to emit itself only for effect.  Regardless, keep the first
		// expression's value on the stack until the other statements have all
		// executed... then pop it.  There will be no significant runtime
		// difference, and it will make disassembly more faithful.
		self.emitValueOn(codeGenerator)
		// Pop the first expression's value.
		codeGenerator.emitPop()
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val statements: A_Tuple = self.slot(STATEMENTS)
		val statementsCount = statements.tupleSize
		assert(statements.tupleSize > 0)
		// Leave the first statement's value on the stack while evaluating the
		// subsequent statements.
		statements.tupleAt(1).emitValueOn(codeGenerator)
		(2..statementsCount).forEach {
			statements.tupleAt(it).emitEffectOn(codeGenerator)
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
		assert(statements.tupleSize > 0)
		return statements.tupleAt(1).phraseExpressionType
	}

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) {
		val statements: A_Tuple = self.slot(STATEMENTS)
		// Process the first expression, then grab the final value-producing
		// expression back *off* the list.
		statements.tupleAt(1).flattenStatementsInto(accumulatedStatements)
		val valueProducer = accumulatedStatements.removeAt(
			accumulatedStatements.size - 1)
		val myFlatStatements = mutableListOf(valueProducer)
		(2..statements.tupleSize).forEach {
			statements.tupleAt(it).flattenStatementsInto(myFlatStatements)
		}
		when (myFlatStatements.size) {
			1 -> {
				accumulatedStatements.add(myFlatStatements[0])
			}
			else -> {
				accumulatedStatements.add(
					newFirstOfSequenceNode(tupleFromList(myFlatStatements)))
			}
		}
	}

	override fun o_Hash(self: AvailObject) =
		combine2(self.slot(STATEMENTS).hash(), 0x70EDD231)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.FIRST_OF_SEQUENCE_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.FIRST_OF_SEQUENCE_PHRASE

	override fun o_Statements(self: AvailObject): A_Tuple =
		self.slot(STATEMENTS)

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = self.slot(STATEMENTS).forEach { it.statementsDo(continuation) }

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("first-of-sequence phrase") }
			at("statements") { self.slot(STATEMENTS).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("first-of-sequence phrase") }
			at("statements") { self.slot(STATEMENTS).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new first-of-sequence phrase from the given [A_Tuple] of
		 * statements.
		 *
		 * @param statements
		 *   The expressions to assemble into a first-of-sequence phrase, the
		 *   *first* of which provides the value.
		 * @return
		 *   The resulting first-of-sequence phrase.
		 */
		fun newFirstOfSequenceNode(statements: A_Tuple): A_Phrase {
			assert(statements.tupleSize > 1)
			return mutable.createShared {
				setSlot(STATEMENTS, statements)
			}
		}

		/** The mutable [FirstOfSequencePhraseDescriptor]. */
		private val mutable =
			FirstOfSequencePhraseDescriptor(Mutability.MUTABLE)

		/** The shared [FirstOfSequencePhraseDescriptor]. */
		private val shared =
			FirstOfSequencePhraseDescriptor(Mutability.SHARED)
	}
}
