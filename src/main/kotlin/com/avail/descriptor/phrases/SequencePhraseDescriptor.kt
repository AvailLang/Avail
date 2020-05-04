/*
 * SequencePhraseDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

import com.avail.annotations.AvailMethod
import com.avail.compiler.AvailCodeGenerator
import com.avail.descriptor.phrases.SequencePhraseDescriptor.ObjectSlots.STATEMENTS
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.evaluation.Continuation1NotNull
import com.avail.utility.json.JSONWriter
import java.util.function.Consumer
import java.util.function.UnaryOperator

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

	@AvailMethod
	override fun o_ChildrenDo(
		self: AvailObject,
		action: Consumer<A_Phrase>
	) = self.slot(STATEMENTS).forEach(action::accept)

	@AvailMethod
	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: UnaryOperator<A_Phrase>
	) = self.setSlot(
		STATEMENTS,
		tupleFromList(self.slot(STATEMENTS).map { transformer.apply(it) }))

	@AvailMethod
	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.slot(STATEMENTS).forEach {
		it.emitEffectOn(codeGenerator)
	}

	@AvailMethod
	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val statements: A_Tuple = self.slot(STATEMENTS)
		val statementsCount = statements.tupleSize()
		for (i in 1 until statementsCount) {
			statements.tupleAt(i).emitEffectOn(codeGenerator)
		}
		if (statementsCount > 0) {
			statements.tupleAt(statementsCount).emitValueOn(codeGenerator)
		}
	}

	@AvailMethod
	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.slot(STATEMENTS).equals(aPhrase.statements()))

	@AvailMethod
	override fun o_ExpressionType(self: AvailObject): A_Type {
		val statements: A_Tuple = self.slot(STATEMENTS)
		return when(statements.tupleSize()) {
			0 -> Types.TOP.o()
			else -> statements.tupleAt(statements.tupleSize()).expressionType()
		}
	}

	@AvailMethod
	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self.slot(STATEMENTS).forEach {
		it.flattenStatementsInto(accumulatedStatements)
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int =
		self.slot(STATEMENTS).hash() + -0x1c7ebf36

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.SEQUENCE_PHRASE

	@AvailMethod
	override fun o_Statements(self: AvailObject): A_Tuple = self.slot(STATEMENTS)

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: Continuation1NotNull<A_Phrase>
	) = self.slot(STATEMENTS).forEach(continuation::value)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SEQUENCE_PHRASE

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple()

	@AvailMethod
	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("sequence phrase")
		writer.write("statements")
		self.slot(STATEMENTS).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("sequence phrase")
		writer.write("statements")
		self.slot(STATEMENTS).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable(): SequencePhraseDescriptor = mutable

	override fun shared(): SequencePhraseDescriptor = shared

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
			mutable.create().apply {
				setSlot(STATEMENTS, statements)
				makeShared()
			}

		/** The mutable [SequencePhraseDescriptor].  */
		private val mutable = SequencePhraseDescriptor(Mutability.MUTABLE)

		/** The shared [SequencePhraseDescriptor].  */
		private val shared = SequencePhraseDescriptor(Mutability.SHARED)
	}
}