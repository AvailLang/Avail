/*
 * PermutedListPhraseDescriptor.kt
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
package com.avail.descriptor.phrases
import com.avail.compiler.AvailCodeGenerator
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import com.avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.list
import com.avail.descriptor.phrases.A_Phrase.Companion.permutation
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import com.avail.descriptor.phrases.A_Phrase.Companion.stripMacro
import com.avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.EXPRESSION_TYPE
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.LIST
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.PERMUTATION
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelOne.L1Operation
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances represent [phrases][PhraseDescriptor] which will generate
 * *permuted* tuples at runtime.  The elements still have to be generated in
 * their lexical order, but an [L1Operation.L1Ext_doPermute] changes their order
 * while they're still on the stack (before being made into a tuple or passed as
 * the top level arguments in a send).
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class PermutedListPhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.PERMUTED_LIST_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [list&#32;phrase][ListPhraseDescriptor] to permute when
		 * generating level one nybblecodes.
		 */
		LIST,

		/**
		 * The permutation to apply to the list phrase when generating level one
		 * nybblecodes.
		 */
		PERMUTATION,

		/**
		 * A cache of the permuted list's type.
		 */
		EXPRESSION_TYPE
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === EXPRESSION_TYPE

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("Permute(")
		builder.append(self.slot(LIST))
		builder.append(", ")
		builder.append(self.slot(PERMUTATION))
		builder.append(")")
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(LIST))

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(LIST, transformer(self.slot(LIST)))

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self.slot(LIST).emitAllValuesOn(codeGenerator)
		codeGenerator.emitPermute(self.tokens, self.permutation)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self.slot(LIST).emitAllValuesOn(codeGenerator)
		val permutation: A_Tuple = self.slot(PERMUTATION)
		codeGenerator.emitPermute(self.tokens, permutation)
		codeGenerator.emitMakeTuple(self.tokens, permutation.tupleSize)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.list.equals(aPhrase.list)
		&& self.permutation.equals(aPhrase.permutation))

	/** DON'T transform the index. */
	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self.slot(LIST).expressionAt(index)

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self.slot(LIST).expressionsSize

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self.slot(LIST).expressionsTuple

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { computeExpressionType(self) }

	override fun o_Hash(self: AvailObject): Int =
		((self.slot(LIST).hash() xor -0x3703d84e)
			+ self.slot(PERMUTATION).hash())

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self.slot(LIST).hasSuperCast

	/** DON'T transform the index. */
	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self.slot(LIST).lastExpression

	override fun o_List(self: AvailObject): A_Phrase = self.slot(LIST)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.PERMUTED_LIST_PHRASE

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self.slot(PERMUTATION)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.PERMUTED_LIST_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	/**
	 * Strip away macro substitution phrases inside my recursive list structure.
	 * This has to be done recursively over list phrases because of the way the
	 * "leaf" phrases are checked for grammatical restrictions, but the "root"
	 * phrases are what get passed into functions.
	 */
	override fun o_StripMacro(self: AvailObject): A_Phrase
	{
		val originalList: A_Phrase = self.slot(LIST)
		val strippedList = originalList.stripMacro
		return when {
			strippedList.sameAddressAs(originalList) -> {
				// Nothing changed, so return the original permuted list.
				self
			}
			else -> newPermutedListNode(strippedList, self.slot(PERMUTATION))
		}
	}

	override fun o_SuperUnionType(self: AvailObject): A_Type
	{
		val list: A_Phrase = self.slot(LIST)
		val listSuperUnionType = list.superUnionType
		if (listSuperUnionType.isBottom) {
			// It doesn't contain a supercast, so answer bottom.
			return listSuperUnionType
		}
		val permutation: A_Tuple = self.slot(PERMUTATION)
		val size = list.expressionsSize
		val types = Array<A_Type>(size) { nil }
		(1..size).forEach { i ->
			val t = listSuperUnionType.typeAtIndex(i)
			val index = permutation.tupleIntAt(i)
			types[index - 1] = t
		}
		return tupleTypeForTypes(*types)
	}

	override fun o_Tokens(self: AvailObject): A_Tuple = self.slot(LIST).tokens

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("permuted list phrase") }
			at("list") { self.slot(LIST).writeTo(writer) }
			at("permutation") { self.slot(PERMUTATION).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("permuted list phrase") }
			at("list") { self.slot(LIST).writeSummaryTo(writer) }
			at("permutation") { self.slot(PERMUTATION).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Lazily compute and install the expression type of the specified
		 * permuted list phrase.
		 *
		 * @param self
		 *   A permuted list phrase.
		 * @return
		 *   The tuple type that this phrase will produce.
		 */
		private fun computeExpressionType(self: AvailObject): A_Type {
			var expressionType: A_Type = self.mutableSlot(EXPRESSION_TYPE)
			if (expressionType.notNil) return expressionType
			val originalTupleType = self.slot(LIST).phraseExpressionType
			val permutation: A_Tuple = self.slot(PERMUTATION)
			val size = permutation.tupleSize
			assert(originalTupleType.sizeRange.lowerBound.extractInt
				== size)
			val adjustedTypes = Array<A_Type>(size) { nil }
			for (i in 1..size) {
				adjustedTypes[permutation.tupleIntAt(i) - 1] =
					originalTupleType.typeAtIndex(i)
			}
			expressionType = tupleTypeForTypes(*adjustedTypes)
			self.setMutableSlot(EXPRESSION_TYPE, expressionType.makeShared())
			return expressionType
		}

		/**
		 * Create a new permuted list phrase from the given
		 * [list&#32;phrase][ListPhraseDescriptor] and
		 * [permutation][TupleDescriptor]. The permutation is a tuple of all
		 * integers between 1 and the tuple size, but *not* in ascending order.
		 *
		 * @param list
		 *   The list phrase to wrap.
		 * @param permutation
		 *   The permutation to perform on the list phrase's elements.
		 * @return
		 *   The resulting permuted list phrase.
		 */
		fun newPermutedListNode(
			list: A_Phrase,
			permutation: A_Tuple
		): A_Phrase = mutable.createShared {
			setSlot(LIST, list)
			setSlot(PERMUTATION, permutation)
			setSlot(EXPRESSION_TYPE, nil)
		}

		/** The mutable [PermutedListPhraseDescriptor].  */
		private val mutable = PermutedListPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [PermutedListPhraseDescriptor].  */
		private val shared = PermutedListPhraseDescriptor(Mutability.SHARED)
	}
}
