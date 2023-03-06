/*
 * PermutedListPhraseDescriptor.kt
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
import avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import avail.descriptor.phrases.A_Phrase.Companion.equalsPhrase
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.list
import avail.descriptor.phrases.A_Phrase.Companion.permutation
import avail.descriptor.phrases.A_Phrase.Companion.permutedPhrases
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.stripMacro
import avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.EXPRESSION_TYPE
import avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.LIST
import avail.descriptor.phrases.PermutedListPhraseDescriptor.ObjectSlots.PERMUTATION
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.descriptor.types.TypeTag
import avail.interpreter.levelOne.L1Operation
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
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
	ObjectSlots::class.java)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
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
		builder.append(self[LIST])
		builder.append(", ")
		builder.append(self[PERMUTATION])
		builder.append(")")
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit
	) = action(self[LIST])

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase
	) = self.updateSlot(LIST, transformer)

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self[LIST].emitAllValuesOn(codeGenerator)
		codeGenerator.emitPermute(self.tokens, self.permutation)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self[LIST].emitAllValuesOn(codeGenerator)
		val permutation: A_Tuple = self[PERMUTATION]
		codeGenerator.emitPermute(self.tokens, permutation)
		codeGenerator.emitMakeTuple(self.tokens, permutation.tupleSize)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.list.equalsPhrase(aPhrase.list)
		&& self.permutation.equals(aPhrase.permutation))

	/** DON'T transform the index. */
	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self[LIST].expressionAt(index)

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self[LIST].expressionsSize

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self[LIST].expressionsTuple

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { computeExpressionType(self) }

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self[LIST].hasSuperCast

	/** DON'T transform the index. */
	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self[LIST].lastExpression

	override fun o_List(self: AvailObject): A_Phrase = self[LIST]

	override fun o_PermutedPhrases(self: AvailObject): List<A_Phrase>
	{
		// Note: This DOES NOT permute any sublists.  It's only for uses where a
		// method call site has to have some processing like semantic
		// restrictions or macros, and the phrases have to be collected into a
		// Kotlin list for transformation prior to invoking the method body as a
		// function.
		val originalExpressions = self[LIST].expressionsTuple.makeShared()
		val permutation = self[PERMUTATION]
		val size = permutation.tupleSize
		val permutedPhrases = MutableList<A_Phrase>(size) { nil }
		for (i in 1 .. size)
		{
			permutedPhrases[permutation.tupleIntAt(i) - 1] =
				originalExpressions.tupleAt(i)
		}
		return permutedPhrases
	}

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.PERMUTED_LIST_PHRASE

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self[PERMUTATION]

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
		val originalList: A_Phrase = self[LIST]
		val strippedList = originalList.stripMacro
		return when {
			// Nothing changed, so return the original permuted list.
			strippedList.sameAddressAs(originalList) -> self
			else -> newPermutedListNode(strippedList, self[PERMUTATION])
		}
	}

	/**
	 * Note that this permutes the arguments, so that they agree with a call
	 * site's runtime argument order.
	 */
	override fun o_SuperUnionType(self: AvailObject): A_Type
	{
		val list: A_Phrase = self[LIST]
		val listSuperUnionType = list.superUnionType
		if (listSuperUnionType.isBottom) {
			// It doesn't contain a supercast, so answer bottom.
			return listSuperUnionType
		}
		val permutation: A_Tuple = self[PERMUTATION]
		val size = list.expressionsSize
		val types = Array<A_Type>(size) { nil }
		(1..size).forEach { i ->
			val t = listSuperUnionType.typeAtIndex(i)
			val index = permutation.tupleIntAt(i)
			types[index - 1] = t
		}
		return tupleTypeForTypes(*types)
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("permuted list phrase") }
			at("list") { self[LIST].writeTo(writer) }
			at("permutation") { self[PERMUTATION].writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("permuted list phrase") }
			at("list") { self[LIST].writeSummaryTo(writer) }
			at("permutation") { self[PERMUTATION].writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Lazily compute and install the expression type of the specified
		 * permuted list phrase.
		 *
		 * @param self
		 *   A permuted list phrase.
		 * @return
		 *   The tuple type that this phrase will produce.
		 */
		private fun computeExpressionType(self: AvailObject): A_Type
		{
			val originalExpressionType: A_Type =
				self.mutableSlot(EXPRESSION_TYPE)
			if (originalExpressionType.notNil) return originalExpressionType
			val permutedTypes = self.permutedPhrases.map { phrase ->
				val subphraseType = phrase.phraseExpressionType
				if (subphraseType.isBottom)
				{
					self.setMutableSlot(EXPRESSION_TYPE, bottom)
					return bottom
				}
				subphraseType
			}
			val expressionType = tupleTypeForTypesList(permutedTypes)
			self.setMutableSlot(EXPRESSION_TYPE, expressionType)
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
			initHash()
		}

		/** The mutable [PermutedListPhraseDescriptor]. */
		private val mutable = PermutedListPhraseDescriptor(Mutability.MUTABLE)

		/** The immutable [PermutedListPhraseDescriptor]. */
		private val immutable =
			PermutedListPhraseDescriptor(Mutability.IMMUTABLE)

		/** The shared [PermutedListPhraseDescriptor]. */
		private val shared = PermutedListPhraseDescriptor(Mutability.SHARED)
	}
}
