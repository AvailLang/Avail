/*
 * PhraseDescriptor.kt
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
 import com.avail.descriptor.phrases.A_Phrase.Companion.childrenDo
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.AbstractDescriptor
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
 import com.avail.descriptor.representation.Descriptor
 import com.avail.descriptor.representation.IntegerSlotsEnum
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.NilDescriptor.Companion.nil
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.BottomTypeDescriptor.bottom
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
 import com.avail.descriptor.types.TypeDescriptor
 import com.avail.descriptor.types.TypeTag
 import com.avail.serialization.SerializerOperation

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
 *   object's object slots layout, or `null` if there are no integer slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class PhraseDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(
	mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass
) {
	override fun maximumIndent(): Int = Int.MAX_VALUE

	/**
	 * The `apparentSendName` of something that isn't a
	 * [send][SendPhraseDescriptor] or
	 * [macro][MacroSubstitutionPhraseDescriptor] is always [nil].
	 */
	override fun o_ApparentSendName(self: AvailObject): A_Atom = nil

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
		action: (A_Phrase) -> Unit)

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
		transformer: (A_Phrase) -> A_Phrase)

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
	 * [phrases][A_Phrase] compare with [A_Phrase.equalsPhrase].
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
	): Boolean = another.equalsPhrase(self)

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
	abstract override fun o_ExpressionType(self: AvailObject): A_Type

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) {
		accumulatedStatements.add(self)
	}

	/**
	 * [Phrases][A_Phrase] must implement this.
	 *
	 * @param self
	 *   The phrase.
	 * @return
	 *   The hash of the phrase.
	 */
	abstract override fun o_Hash(self: AvailObject): Int

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
		PARSE_PHRASE.mostGeneralType().isSubtypeOf(aType) -> true
		!aType.isSubtypeOf(PARSE_PHRASE.mostGeneralType()) -> false
		!self.phraseKindIsUnder(aType.phraseKind()) -> false
		else -> self.expressionType().isSubtypeOf(aType.expressionType())
	}

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean = false

	override fun o_Kind(self: AvailObject): A_Type =
		self.phraseKind().create(self.expressionType())

	/**
	 * None of the subclasses define an immutable descriptor, so make the
	 * argument shared instead.
	 */
	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable) self.makeShared() else self

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
	): Boolean = self.phraseKind().isSubkindOf(expectedPhraseKind)

	abstract override fun o_SerializerOperation(
		self: AvailObject
	): SerializerOperation

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	abstract override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit)

	override fun o_StripMacro(self: AvailObject): A_Phrase = self

	override fun o_SuperUnionType(self: AvailObject): A_Type = bottom()

	abstract override fun o_Tokens(self: AvailObject): A_Tuple

	/**
	 * Validate this phrase, throwing an exception if there is a problem.
	 *
	 * @param self
	 *   The [A_Phrase] to validate.
	 * @param parent
	 *   The optional [A_Phrase] which contains the phrase to validate as its
	 *   sub-phrase.
	 */
	abstract override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?)

	/**
	 * Subclasses do not have an immutable descriptor, so use the shared one
	 * instead.
	 */
	override fun immutable() = shared()

	abstract override fun shared(): AbstractDescriptor

	companion object {
		/**
		 * Visit the entire tree with the given consumer, children before
		 * parents.  The block takes two arguments: the phrase and its parent.
		 *
		 * @param self
		 *   The current [A_Phrase].
		 * @param aBlock
		 *   What to do with each descendant.
		 * @param parentNode
		 *   This phrase's parent, or `null`.
		 */
		fun treeDoWithParent(
			self: A_Phrase,
			aBlock: (A_Phrase, A_Phrase?) -> Unit,
			parentNode: A_Phrase?
		) {
			self.childrenDo { child -> treeDoWithParent(child, aBlock, self) }
			aBlock(self, parentNode)
		}
	}
}
