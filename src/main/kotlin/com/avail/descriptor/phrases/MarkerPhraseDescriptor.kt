/*
 * MarkerPhraseDescriptor.kt
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
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import com.avail.descriptor.phrases.A_Phrase.Companion.markerValue
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import com.avail.descriptor.phrases.MarkerPhraseDescriptor.ObjectSlots.MARKER_VALUE
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import java.util.*

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
		MARKER_VALUE
	}

	/**
	 * An [Enum] whose ordinals can be used as marker values in
	 * [marker&#32;phrases][MarkerPhraseDescriptor].
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
		val marker: A_Phrase = newMarkerNode(fromInt(ordinal))
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("Marker(")
		builder.append(self.markerValue())
		builder.append(")")
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	): Unit = unsupportedOperation()

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	): Unit = unsupportedOperation()

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	): Unit = unsupportedOperation()

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.markerValue().equals(aPhrase.markerValue()))

	/** This shouldn't make a difference. */
	override fun o_ExpressionType(self: AvailObject): A_Type = Types.TOP.o()

	override fun o_Hash(self: AvailObject): Int =
		self.markerValue().hash() xor -0x34353534

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self.slot(MARKER_VALUE)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.MARKER_PHRASE

	/**
	 * There's currently no reason to serialize a marker phrase.  This may
	 * change at some point.
	 */
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		 unsupportedOperation()

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupportedOperation()

	override fun o_Tokens(self: AvailObject): A_Tuple = emptyTuple()

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	): Unit = unsupportedOperation()

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a marker phrase wrapping the given [A_BasicObject].
		 *
		 * @param markerValue
		 *   The arbitrary value to wrap.
		 * @return
		 *   A new immutable marker phrase.
		 */
		fun newMarkerNode(markerValue: A_BasicObject?): AvailObject =
			mutable.create().apply {
				setSlot(MARKER_VALUE, markerValue!!)
				makeShared()
			}

		/** The mutable [MarkerPhraseDescriptor].  */
		private val mutable = MarkerPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [MarkerPhraseDescriptor].  */
		private val shared = MarkerPhraseDescriptor(Mutability.SHARED)
	}
}
