/*
 * VariableUsePhraseDescriptor.kt
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
 import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
 import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
 import com.avail.descriptor.phrases.A_Phrase.Companion.isLastUse
 import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
 import com.avail.descriptor.phrases.A_Phrase.Companion.token
 import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
 import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.IntegerSlots.Companion.LAST_USE
 import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.IntegerSlots.FLAGS
 import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.ObjectSlots.DECLARATION
 import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.ObjectSlots.USE_TOKEN
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
 import com.avail.descriptor.representation.AbstractSlotsEnum
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.AvailObject.Companion.multiplier
 import com.avail.descriptor.representation.BitField
 import com.avail.descriptor.representation.IntegerSlotsEnum
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.tokens.A_Token
 import com.avail.descriptor.tokens.TokenDescriptor
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
 import com.avail.descriptor.types.TypeDescriptor.Types
 import com.avail.descriptor.types.TypeTag
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.json.JSONWriter
 import java.util.*

/**
 * My instances represent the use of some
 * [declaration][DeclarationPhraseDescriptor].
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class VariableUsePhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.VARIABLE_USE_PHRASE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * My slots of type [int][Integer].
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * Currently just a single BitField, [LAST_USE].
		 */
		FLAGS;

		companion object {
			/**
			 * A flag indicating (with 0/1) whether this is the last use of the
			 * mentioned entity.  This gets set during code generation, even if
			 * the phrase is immutable.  It should not be made visible to the
			 * Avail language.
			 */
			val LAST_USE = BitField(FLAGS, 0, 1)
		}
	}

	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [token][TokenDescriptor] that is a mention of the entity in
		 * question.
		 */
		USE_TOKEN,

		/**
		 * The [declaration][DeclarationPhraseDescriptor] of the entity that is
		 * being mentioned.
		 */
		DECLARATION
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === FLAGS

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(DECLARATION))

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(DECLARATION, transformer(self.slot(DECLARATION)))

	override fun o_Declaration(self: AvailObject): A_Phrase =
		self.slot(DECLARATION)

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration: A_Phrase = self.slot(DECLARATION)
		declaration.declarationKind().emitVariableValueForOn(
			self.tokens(), declaration, codeGenerator)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.slot(USE_TOKEN).equals(aPhrase.token())
		&& self.slot(DECLARATION).equals(aPhrase.declaration())
		&& self.isLastUse() == aPhrase.isLastUse())

	override fun o_ExpressionType(self: AvailObject): A_Type =
		self.slot(DECLARATION).declaredType()

	override fun o_Hash(self: AvailObject): Int =
		(self.slot(USE_TOKEN).hash() * multiplier
			+ self.slot(DECLARATION).hash()
			xor 0x62CE7BA2)

	override fun o_IsLastUse(
		self: AvailObject,
		isLastUse: Boolean
	) = self.synchronizeIf(isShared) {
		self.setSlot(LAST_USE, if (isLastUse) 1 else 0)
	}

	override fun o_IsLastUse(self: AvailObject): Boolean =
		self.synchronizeIf(isShared) {
			self.slot(LAST_USE) != 0
		}

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.VARIABLE_USE_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.VARIABLE_USE_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_Token(self: AvailObject): A_Token = self.slot(USE_TOKEN)

	override fun o_Tokens(self: AvailObject): A_Tuple =
		tuple(self.slot(USE_TOKEN))

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("variable use phrase")
		writer.write("token")
		self.slot(USE_TOKEN).writeTo(writer)
		writer.write("declaration")
		self.slot(DECLARATION).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("variable use phrase")
		writer.write("token")
		self.slot(USE_TOKEN).writeSummaryTo(writer)
		writer.write("declaration")
		self.slot(DECLARATION).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append(self.slot(USE_TOKEN).string().asNativeString())
	}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Construct a new variable use phrase.
		 *
		 * @param theToken
		 *   The token which is the use of the variable in the source.
		 * @param declaration
		 *   The declaration which is being used.
		 * @return
		 *   A new variable use phrase.
		 */
		fun newUse(
			theToken: A_Token,
			declaration: A_Phrase
		): A_Phrase {
			assert(theToken.isInstanceOfKind(Types.TOKEN.o()))
			assert(declaration.isInstanceOfKind(
				PhraseKind.DECLARATION_PHRASE.mostGeneralType()))
			return mutable.create().apply {
				setSlot(USE_TOKEN, theToken)
				setSlot(DECLARATION, declaration)
				setSlot(FLAGS, 0)
				makeShared()
			}
		}

		/** The mutable [VariableUsePhraseDescriptor].  */
		private val mutable = VariableUsePhraseDescriptor(Mutability.MUTABLE)

		/** The shared [VariableUsePhraseDescriptor].  */
		private val shared = VariableUsePhraseDescriptor(Mutability.IMMUTABLE)
	}
}
