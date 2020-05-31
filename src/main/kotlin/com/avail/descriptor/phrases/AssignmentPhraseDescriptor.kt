/*
 * AssignmentPhraseDescriptor.kt
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

 import com.avail.compiler.AvailCodeGenerator
 import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.expression
 import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKind
 import com.avail.descriptor.phrases.A_Phrase.Companion.token
 import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
 import com.avail.descriptor.phrases.A_Phrase.Companion.variable
 import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.IntegerSlots.Companion.IS_INLINE
 import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.EXPRESSION
 import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.TOKENS
 import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.VARIABLE
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LABEL
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.PRIMITIVE_FAILURE_REASON
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.AbstractDescriptor
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.AvailObject.Companion.error
 import com.avail.descriptor.representation.AvailObject.Companion.multiplier
 import com.avail.descriptor.representation.BitField
 import com.avail.descriptor.representation.IntegerSlotsEnum
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.NilDescriptor.Companion.nil
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.tokens.A_Token
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
 import com.avail.descriptor.types.TypeDescriptor.Types
 import com.avail.descriptor.types.TypeTag
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.json.JSONWriter
 import java.util.*

/**
 * My instances represent assignment statements.
 *
 * @constructor
 *
 * @param mutability
 * The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AssignmentPhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.ASSIGNMENT_PHRASE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * My integer slots.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The [assignment&#32;phrase][AssignmentPhraseDescriptor]'s flags.
		 */
		FLAGS;

		companion object {
			/**
			 * Is this an inline [assignment][AssignmentPhraseDescriptor]?
			 */
			val IS_INLINE = BitField(FLAGS, 0, 1)
		}
	}

	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [variable][VariableUsePhraseDescriptor] being assigned.
		 */
		VARIABLE,

		/**
		 * The actual [expression][PhraseDescriptor] providing the value to
		 * assign.
		 */
		EXPRESSION,

		/**
		 * The [A_Tuple] of [A_Token]s, if any, from which the assignment was
		 * constructed.
		 */
		TOKENS
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append(self.slot(VARIABLE).token().string().asNativeString())
		builder.append(" := ")
		self.slot(EXPRESSION).printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
	}

	override fun o_Variable(self: AvailObject): A_Phrase = self.slot(VARIABLE)

	override fun o_Expression(self: AvailObject): A_Phrase =
		self.slot(EXPRESSION)

	override fun o_ExpressionType(self: AvailObject): A_Type =
		when {
			isInline(self) -> self.slot(EXPRESSION).expressionType()
			else -> Types.TOP.o()
		}

	override fun o_Hash(self: AvailObject) =
		(self.variable().hash() * multiplier
			+ self.expression().hash()
			xor -0x58e157ac)

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode()
		&& self.phraseKind() == aPhrase.phraseKind()
		&& self.slot(VARIABLE).equals(aPhrase.variable())
		&& self.slot(EXPRESSION).equals(aPhrase.expression())
		&& self.slot(TOKENS).equals(aPhrase.tokens()))

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration = self.slot(VARIABLE).declaration()
		val declarationKind = declaration.declarationKind()
		assert(declarationKind.isVariable)
		self.slot(EXPRESSION).emitValueOn(codeGenerator)
		declarationKind.emitVariableAssignmentForOn(
			self.tokens(), declaration, codeGenerator)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration = self.slot(VARIABLE).declaration()
		val declarationKind = declaration.declarationKind()
		assert(declarationKind.isVariable)
		self.slot(EXPRESSION).emitValueOn(codeGenerator)
		when {
			isInline(self) -> {
				codeGenerator.emitDuplicate()
				declarationKind.emitVariableAssignmentForOn(
					self.tokens(), declaration, codeGenerator)
			}
			else -> {
				// This assignment is the last statement in a sequence.  Don't
				// leak the assigned value, since it's *not* an inlined
				// assignment.
				declarationKind.emitVariableAssignmentForOn(
					self.tokens(), declaration, codeGenerator)
				codeGenerator.emitPushLiteral(emptyTuple(), nil)
			}
		}
	}

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) {
		self.setSlot(EXPRESSION, transformer(self.slot(EXPRESSION)))
		self.setSlot(VARIABLE, transformer(self.slot(VARIABLE)))
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) {
		action(self.slot(EXPRESSION))
		action(self.slot(VARIABLE))
	}

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = continuation(self)

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) = when (self.slot(VARIABLE).declaration().declarationKind()) {
		ARGUMENT -> error("Can't assign to argument")
		LABEL -> error("Can't assign to label")
		LOCAL_CONSTANT,
		MODULE_CONSTANT,
		PRIMITIVE_FAILURE_REASON -> error("Can't assign to constant")
		LOCAL_VARIABLE,
		MODULE_VARIABLE -> { }
	}

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.ASSIGNMENT_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.ASSIGNMENT_PHRASE

	override fun o_Tokens(self: AvailObject): A_Tuple = self.slot(TOKENS)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("assignment phrase")
		writer.write("target")
		self.slot(VARIABLE).writeTo(writer)
		writer.write("expression")
		self.slot(EXPRESSION).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("assignment phrase")
		writer.write("target")
		self.slot(VARIABLE).writeSummaryTo(writer)
		writer.write("expression")
		self.slot(EXPRESSION).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Does the [object][AvailObject] represent an inline assignment?
		 *
		 * @param self
		 *   An object.
		 * @return
		 *   `true` if the object represents an inline assignment, `false`
		 *   otherwise.
		 */
		fun isInline(self: AvailObject): Boolean = self.slot(IS_INLINE) != 0

		/**
		 * Create a new assignment phrase using the given
		 * [variable&#32;use][VariableUsePhraseDescriptor] and
		 * [expression][PhraseDescriptor].  Also indicate whether the assignment
		 * is inline (produces a value) or not (must be a statement).
		 *
		 * @param variableUse
		 *   A use of the variable into which to assign.
		 * @param expression
		 *   The expression whose value should be assigned to the variable.
		 * @param tokens
		 *   The tuple of tokens that formed this assignment.
		 * @param isInline
		 *   `true` to create an inline assignment, `false` otherwise.
		 * @return
		 *   The new assignment phrase.
		 */
		fun newAssignment(
			variableUse: A_Phrase,
			expression: A_Phrase,
			tokens: A_Tuple,
			isInline: Boolean
		): A_Phrase = mutable.create().apply {
			setSlot(VARIABLE, variableUse)
			setSlot(EXPRESSION, expression)
			setSlot(TOKENS, tokens)
			setSlot(IS_INLINE, if (isInline) 1 else 0)
			makeShared()
		}

		/** The mutable [AssignmentPhraseDescriptor].  */
		private val mutable = AssignmentPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [AssignmentPhraseDescriptor].  */
		private val shared = AssignmentPhraseDescriptor(Mutability.SHARED)
	}
}
