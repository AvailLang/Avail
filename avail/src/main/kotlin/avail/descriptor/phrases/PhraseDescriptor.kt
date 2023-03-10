/*
 * PhraseDescriptor.kt
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

import avail.AvailRuntimeSupport
import avail.compiler.AvailCodeGenerator
import avail.compiler.CompilationContext
import avail.descriptor.atoms.A_Atom
import avail.descriptor.phrases.A_Phrase.Companion.childrenDo
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.equalsPhrase
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.PhraseDescriptor.IntegerSlots
import avail.descriptor.phrases.PhraseDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.phraseKind
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ASSIGNMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation

/**
 * I'm used to implement the abstract notion of phrases.  All concrete phrase
 * kinds are below me in the hierarchy.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout.  The [IntegerSlots] class defined below is
 *   used if none is specified.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class PhraseDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum> =
		IntegerSlots::class.java
) : Descriptor(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * My slots of type [int][Integer].
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * A slot containing multiple [BitField]s, potentially.
		 */
		HASH_AND_MORE;

		companion object {
			/** The random hash of this object. */
			val HASH = BitField(HASH_AND_MORE, 0, 32) { null }
		}
	}

	override fun maximumIndent(): Int = Int.MAX_VALUE

	/**
	 * The `apparentSendName` of something that isn't a
	 * [send][SendPhraseDescriptor] or
	 * [macro][MacroSubstitutionPhraseDescriptor] is always [nil].
	 */
	override fun o_ApparentSendName(self: AvailObject): A_Atom = nil

	override fun o_ApplyStylesThen(
		self: AvailObject,
		context: CompilationContext,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit)
	{
		if (!visitedSet.add(self)) return then()
		// By default, just visit the children and do nothing else with this
		// phrase.  Subclasses may invoke the super behavior with an altered
		// 'then'.
		styleDescendantsThen(self, context, visitedSet, then)
	}

	/**
	 * Visit every phrase constituting this parse tree, invoking the passed
	 * consumer with each.
	 *
	 * @param self
	 *   The [phrase][A_Phrase] to traverse.
	 * @param action
	 *   The action to perform with each child phrase.
	 */
	abstract override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit
	)

	/**
	 * Visit and transform the direct descendants of this phrase.  Map this
	 * phrase's children through the (destructive) transformation specified by
	 * aBlock, assigning them back into my slots.
	 *
	 * @param self
	 *   The phrase to transform.
	 * @param transformer
	 *   The transformer through which to recursively map the phrase.
	 */
	abstract override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase
	)

	/**
	 * If the receiver is immutable, make an equivalent mutable copy of that
	 * phrase.  Otherwise, answer the receiver itself.
	 *
	 * @param self
	 *   The [A_Phrase] of which to create a mutable copy.
	 * @return
	 *   A mutable [A_Phrase] equivalent to the passed phrase, possibly the same
	 *   object.
	 */
	override fun o_CopyMutablePhrase(self: AvailObject): A_Phrase
	{
		self.makeSubobjectsImmutable()
		return when {
			isMutable -> self
			else -> newLike(mutable(), self, 0, 0)
		}
	}

	/**
	 * Emit the effect of this phrase.  By default that means to emit the value
	 * of the phrase, then to pop the unwanted value from the stack.
	 *
	 * @param self
	 *   The phrase.
	 * @param codeGenerator
	 *   Where to emit the code.
	 */
	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self.emitValueOn(codeGenerator)
		codeGenerator.emitPop()
	}

	/**
	 * Emit the value of this phrase.  That means emit a sequence of
	 * instructions that will cause this phrase's value to end up on the stack.
	 *
	 * @param self
	 *   The phrase.
	 * @param codeGenerator
	 *   Where to emit the code.
	 */
	abstract override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator)

	/**
	 * [Phrases][A_Phrase] compare with [A_Phrase.equalsPhrase].
	 *
	 * @param self
	 *   The phrase.
	 * @param another
	 *   An object to compare it against.
	 * @return
	 *   Whether they are equal.
	 */
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.traversed().sameAddressAs(self)

	/**
	 * Note: This is only used to eliminate duplicate equivalent phrases
	 * produced by the parallel parsing plan mechanism.  Each definition has to
	 * be parsed as a separate potential entity in the message bundle tree, and
	 * multiple applicable sites might be found for the same bundle, but
	 * different definitions.
	 */
	abstract override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean

	/**
	 * Return the phrase's expression type, which is the type of object that
	 * will be produced by this phrase.
	 *
	 * @return
	 *   The [type][TypeDescriptor] of the [AvailObject] that will be produced
	 *   by evaluating this phrase.
	 */
	abstract override fun o_PhraseExpressionType(self: AvailObject): A_Type

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) {
		accumulatedStatements.add(self)
	}

	override fun o_Hash(self: AvailObject) = self[HASH]

	/**
	 * Terminate the recursion through the recursive list structure.  If this
	 * isn't overridden in a subclass then it must be a bottom-level argument to
	 * a send.
	*/
	override fun o_HasSuperCast(self: AvailObject): Boolean = false

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	) = when {
		PARSE_PHRASE.mostGeneralType.isSubtypeOf(aType) -> true
		!aType.isSubtypeOf(PARSE_PHRASE.mostGeneralType) -> false
		!self.phraseKindIsUnder(aType.phraseKind) -> false
		else -> self.phraseExpressionType.isSubtypeOf(
			aType.phraseTypeExpressionType)
	}

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean = false

	override fun o_Kind(self: AvailObject): A_Type =
		self.phraseKind.create(self.phraseExpressionType)

	/**
	 * Return the [PhraseKind] that this phrase's type implements.
	 *
	 * @return
	 *   The [PhraseKind] of phrase that the object's type would be.
	 */
	abstract override fun o_PhraseKind(self: AvailObject): PhraseKind

	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind
	): Boolean = self.phraseKind.isSubkindOf(expectedPhraseKind)

	abstract override fun o_SerializerOperation(
		self: AvailObject
	): SerializerOperation

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	abstract override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit)

	override fun o_StripMacro(self: AvailObject): A_Phrase = self

	override fun o_SuperUnionType(self: AvailObject): A_Type = bottom

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple

	override fun o_TokenIndicesInName(self: AvailObject): A_Tuple = emptyTuple

	/**
	 * Validate this phrase, throwing an exception if there is a problem.  Do
	 * not recurse into its subphrases, as those have already been validated.
	 *
	 * @param self
	 *   The [A_Phrase] to validate.
	 */
	override fun o_ValidateLocally(
		self: AvailObject)
	{
		// Do nothing by default.
	}

	/**
	 * Subclasses do not have an immutable descriptor, so use the shared one
	 * instead.
	 */
	abstract override fun immutable(): PhraseDescriptor

	abstract override fun shared(): PhraseDescriptor

	companion object
	{
		/**
		 * Initialize the phrase's hash value during construction.
		 */
		fun AvailObject.initHash(): Unit =
			setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())

		/**
		 * Visit the entire tree with the given consumer, children before
		 * parents.  The block takes two arguments: the phrase and its parent.
		 *
		 * @param self
		 *   The current [A_Phrase].
		 * @param parentNode
		 *   This phrase's parent, or `null`. Defaults to `null`.
		 * @param aBlock
		 *   What to do with each descendant.
		 */
		fun treeDoWithParent(
			self: A_Phrase,
			parentNode: A_Phrase? = null,
			children: (A_Phrase, (A_Phrase)->Unit)->Unit =
				{ phrase, withChild -> phrase.childrenDo(withChild) },
			aBlock: (A_Phrase, parent: A_Phrase?)->Unit)
		{
			TreeIterator(children, aBlock).visitAll(self, parentNode)
		}

		/**
		 * A helper for traversing a phrase tree without recursion.
		 *
		 * @property childrenExtractor
		 *   A function that takes a phrase and a function that accepts a
		 *   phrase, and evaluates the passed function with each child of the
		 *   phrase.
		 * @property aBlock
		 *   What to execute with each phrase in the tree and its optional (only
		 *   for the top phrase) parent.
		 */
		class TreeIterator
		constructor(
			val childrenExtractor: (A_Phrase, (A_Phrase)->Unit)->Unit,
			val aBlock: (A_Phrase, parent: A_Phrase?)->Unit)
		{
			/** The work stack of nullary actions to run. */
			private val stack = ArrayDeque<()->Unit>()

			/** Visit a phrase, with the provided optional parent. */
			private fun traceOne(phrase: A_Phrase, parent: A_Phrase?)
			{
				// This will run after the children are processed.
				stack.add { aBlock(phrase, parent) }
				// Process the children.  Reverse the order of the children on
				// the stack, since we will be popping values from the end of
				// the deque.
				val temp = mutableListOf<()->Unit>()
				childrenExtractor(phrase) { child ->
					temp.add { traceOne(child, phrase) }
				}
				stack.addAll(temp.asReversed())
			}

			/**
			 * Visit all phrases in the tree, in bottom-up order, invoking
			 * aBlock for each, and also passing the phrase's optional parent.
			 */
			fun visitAll(phrase: A_Phrase, parent: A_Phrase?)
			{
				traceOne(phrase, parent)
				while (stack.isNotEmpty())
				{
					stack.removeLast()()
				}
			}
		}

		/**
		 * Does the specified [flat][A_Phrase.Companion.flattenStatementsInto]
		 * [list][List] of [phrases][PhraseDescriptor] contain only statements?
		 *
		 * TODO MvG - REVISIT to make this work sensibly.  Probably only allow
		 * statements in a sequence/first-of-sequence, and have blocks hold an
		 * optional final *expression*.
		 *
		 * @param flat
		 *   A flattened list of statements.
		 * @param resultType
		 *   The result type of the sequence. Use
		 *   [top][PrimitiveTypeDescriptor.Types.TOP] if unconcerned about
		 *   result type.
		 * @return
		 *   `true` if the list contains only statements, `false` otherwise.
		 */
		fun containsOnlyStatements(
			flat: List<A_Phrase>,
			resultType: A_Type): Boolean
		{
			val statementCount = flat.size
			for (i in 0 until statementCount)
			{
				val statement = flat[i]
				assert(!statement.phraseKindIsUnder(SEQUENCE_PHRASE))
				val valid: Boolean = when
				{
					i >= statementCount - 1 ->
					{
						statement.phraseExpressionType.isSubtypeOf(resultType)
					}
					else ->
					{
						((statement.phraseKindIsUnder(STATEMENT_PHRASE)
							||statement.phraseKindIsUnder(ASSIGNMENT_PHRASE)
							|| statement.phraseKindIsUnder(SEND_PHRASE))
							&& statement.phraseExpressionType.isTop)
					}
				}
				if (!valid)
				{
					return false
				}
			}
			return true
		}

		/**
		 * Given two [Iterable]s of [A_Phrase]s, check that they have the same
		 * length, and that their corresponding elements are
		 * [A_Phrase.equalsPhrase].
		 *
		 * @param phrases1
		 *   The first source of phrases.
		 * @param phrases2
		 *   The other source of phrases.
		 * @return
		 *   Whether the phrases were all equivalent.
		 */
		fun equalPhrases(
			phrases1: Iterable<A_Phrase>,
			phrases2: Iterable<A_Phrase>): Boolean
		{
			val list1 = phrases1.toList()
			val list2 = phrases2.toList()
			if (list1.size != list2.size) return false
			return (list1 zip list2).all { (phrase1, phrase2) ->
				phrase1.equalsPhrase(phrase2)
			}
		}

		/**
		 * Recursively style all descendants of the given phrase.
		 *
		 */
		fun styleDescendantsThen(
			phrase: A_Phrase,
			context: CompilationContext,
			visitedSet: MutableSet<A_Phrase>,
			then: ()->Unit)
		{
			val children = mutableListOf<A_Phrase>()
			phrase.childrenDo(children::add)
			context.visitAll(children, visitedSet, then)
		}
	}
}
