/*
 * ReferencePhraseDescriptor.kt
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
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import com.avail.descriptor.phrases.A_Phrase.Companion.literalObject
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.A_Phrase.Companion.variable
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LABEL
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.PRIMITIVE_FAILURE_REASON
import com.avail.descriptor.phrases.ReferencePhraseDescriptor.ObjectSlots.VARIABLE
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.error
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
 import java.util.*

/**
 * My instances represent a reference-taking expression.  A variable itself is
 * to be pushed on the stack.  Note that this does not work for arguments or
 * constants or labels, as no actual variable object is created for those.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ReferencePhraseDescriptor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.REFERENCE_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [variable&#32;use&#32;phrase][VariableUsePhraseDescriptor] for
		 * which the [reference][ReferencePhraseDescriptor] is being taken.
		 */
		VARIABLE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("↑")
		builder.append(self.slot(VARIABLE).token().string().asNativeString())
	}

	override fun o_Variable(self: AvailObject): A_Phrase = self.slot(VARIABLE)

	/**
	 * The value I represent is a variable itself.  Answer an appropriate
	 * variable type.
	 */
	override fun o_ExpressionType(self: AvailObject): A_Type {
		val variable: A_Phrase = self.slot(VARIABLE)
		val declaration = variable.declaration()
		return when (declaration.declarationKind()) {
			MODULE_VARIABLE -> instanceType(declaration.literalObject())
			else -> variableTypeFor(variable.expressionType())
		}
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean {
		return (!aPhrase.isMacroSubstitutionNode()
			&& self.phraseKind() == aPhrase.phraseKind()
			&& self.slot(VARIABLE).equals(aPhrase.variable()))
	}

	override fun o_Hash(self: AvailObject): Int =
		self.variable().hash() xor -0x180564c1

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration = self.slot(VARIABLE).declaration()
		declaration.declarationKind().emitVariableReferenceForOn(
			self.tokens(), declaration, codeGenerator)
	}

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(VARIABLE, transformer(self.slot(VARIABLE)))

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(VARIABLE))

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		val decl: A_BasicObject = self.slot(VARIABLE).declaration()
		when (decl.declarationKind()) {
			ARGUMENT -> error("You can't take the reference of an argument")
			LABEL -> error("You can't take the reference of a label")
			LOCAL_CONSTANT,
			MODULE_CONSTANT,
			PRIMITIVE_FAILURE_REASON ->
				error("You can't take the reference of a constant")
			LOCAL_VARIABLE,
			MODULE_VARIABLE -> { }
		}
	}

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.REFERENCE_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.REFERENCE_PHRASE

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self.slot(VARIABLE).tokens()

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable reference phrase") }
			at("referent") { self.slot(VARIABLE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable reference phrase") }
			at("referent") { self.slot(VARIABLE).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new [reference&#32;phrase][ReferencePhraseDescriptor] from
		 * the given [variable&#32;use&#32;phrase][VariableUsePhraseDescriptor].
		 *
		 * @param variableUse
		 *   A variable use phrase for which to construct a reference phrase.
		 * @return
		 *   The new reference phrase.
		 */
		fun referenceNodeFromUse(variableUse: A_Phrase): A_Phrase =
			mutable.createShared {
				setSlot(VARIABLE, variableUse)
			}

		/** The mutable [ReferencePhraseDescriptor].  */
		private val mutable = ReferencePhraseDescriptor(Mutability.MUTABLE)

		/** The shared [ReferencePhraseDescriptor].  */
		private val shared = ReferencePhraseDescriptor(Mutability.SHARED)
	}
}
