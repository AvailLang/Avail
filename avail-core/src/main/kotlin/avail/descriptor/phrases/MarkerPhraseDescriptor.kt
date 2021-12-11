/*
 * MarkerPhraseDescriptor.kt
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
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.markerValue
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.MarkerPhraseDescriptor.ObjectSlots.EXPRESSION_TYPE
import avail.descriptor.phrases.MarkerPhraseDescriptor.ObjectSlots.MARKER_VALUE
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import avail.serialization.SerializerOperation.MARKER_PHRASE
import java.util.IdentityHashMap

/**
 * My instances represent a parsing marker that can be pushed onto the parse
 * stack.  It should never occur as part of a composite
 * [phrase][PhraseDescriptor], and is not capable of emitting code.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MarkerPhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.MARKER_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [marker][MarkerPhraseDescriptor] being wrapped in a form suitable
		 * for the parse stack.
		 */
		MARKER_VALUE,

		/**
		 * The marker phrase's expression type.  This is useful when a marker is
		 * produced for quoting/unquoting phrases.
		 */
		EXPRESSION_TYPE
	}

	/**
	 * An [Enum] whose ordinals can be used as marker values in
	 * [marker&#32;phrases][MarkerPhraseDescriptor] during decompilation.
	 */
	enum class MarkerTypes {
		/**
		 * A marker standing for a duplicate of some value that was on the
		 * stack.
		 */
		DUP,

		/**
		 * A marker indicating the value below it has been permuted, and should
		 * be checked by a subsequent call operation;
		 */
		PERMUTE;

		/**
		 * A pre-built marker for this enumeration value.
		 */
		val marker: A_Phrase =
			newMarkerNode(fromInt(ordinal), TOP.o).makeShared()
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("Marker(")
		builder.append(self.markerValue)
		builder.append(" → ")
		builder.append(self.phraseExpressionType)
		builder.append(")")
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	): Unit = Unit

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	): Unit = Unit

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	): Unit = unsupported

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.markerValue.equals(aPhrase.markerValue))

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.slot(EXPRESSION_TYPE)

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.markerValue.hash(), -0x34353534)

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self.slot(MARKER_VALUE)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.MARKER_PHRASE

	/**
	 * There's currently no reason to serialize a marker phrase.  This may
	 * change at some point.
	 */
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		MARKER_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	): Unit = unsupported

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a marker phrase wrapping the given [A_BasicObject].
		 *
		 * @param markerValue
		 *   The arbitrary value to wrap.
		 * @param expressionType
		 *   The type that this marker ostensibly yields, although a marker
		 *   phrase has to be replaced by some other phrase before code
		 *   generation.
		 * @return
		 *   A new immutable marker phrase.
		 */
		fun newMarkerNode(
			markerValue: A_BasicObject,
			expressionType: A_Type
		): AvailObject = mutable.createShared {
			setSlot(MARKER_VALUE, markerValue)
			setSlot(EXPRESSION_TYPE, expressionType)
		}

		/** The mutable [MarkerPhraseDescriptor]. */
		private val mutable = MarkerPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [MarkerPhraseDescriptor]. */
		private val shared = MarkerPhraseDescriptor(Mutability.SHARED)
	}
}
