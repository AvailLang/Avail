/*
 * AssignmentPhraseDescriptor.kt
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
import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.expression
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.A_Phrase.Companion.variable
import avail.descriptor.phrases.AssignmentPhraseDescriptor.IntegerSlots.Companion.IS_INLINE
import avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.EXPRESSION
import avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.TOKENS
import avail.descriptor.phrases.AssignmentPhraseDescriptor.ObjectSlots.VARIABLE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LABEL
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.PRIMITIVE_FAILURE_REASON
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.AvailObject.Companion.error
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

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
		builder.append(self.slot(VARIABLE).token.string().asNativeString())
		builder.append(" := ")
		self.slot(EXPRESSION).printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
	}

	override fun o_Variable(self: AvailObject): A_Phrase = self.slot(VARIABLE)

	override fun o_Expression(self: AvailObject): A_Phrase =
		self.slot(EXPRESSION)

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		when {
			isInline(self) -> self.slot(EXPRESSION).phraseExpressionType
			else -> TOP.o
		}

	override fun o_Hash(self: AvailObject) = combine3(
		self.variable.hash(),
		self.expression.hash(),
		-0x58e157ac)

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.slot(VARIABLE).equals(aPhrase.variable)
		&& self.slot(EXPRESSION).equals(aPhrase.expression)
		&& self.slot(TOKENS).equals(aPhrase.tokens))

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration = self.slot(VARIABLE).declaration
		val declarationKind = declaration.declarationKind()
		assert(declarationKind.isVariable)
		self.slot(EXPRESSION).emitValueOn(codeGenerator)
		declarationKind.emitVariableAssignmentForOn(
			self.tokens, declaration, codeGenerator)
	}

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val declaration = self.slot(VARIABLE).declaration
		val declarationKind = declaration.declarationKind()
		assert(declarationKind.isVariable)
		self.slot(EXPRESSION).emitValueOn(codeGenerator)
		when {
			isInline(self) -> {
				codeGenerator.emitDuplicate()
				declarationKind.emitVariableAssignmentForOn(
					self.tokens, declaration, codeGenerator)
			}
			else -> {
				// This assignment is the last statement in a sequence.  Don't
				// leak the assigned value, since it's *not* an inlined
				// assignment.
				declarationKind.emitVariableAssignmentForOn(
					self.tokens, declaration, codeGenerator)
				codeGenerator.emitPushLiteral(emptyTuple, nil)
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
	) = when (self.slot(VARIABLE).declaration.declarationKind()) {
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

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("assignment phrase") }
			at("target") { self.slot(VARIABLE).writeTo(writer) }
			at("expression") { self.slot(EXPRESSION).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("assignment phrase") }
			at("target") { self.slot(VARIABLE).writeSummaryTo(writer) }
			at("expression") { self.slot(EXPRESSION).writeSummaryTo(writer) }
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
		): A_Phrase = mutable.createShared {
			setSlot(VARIABLE, variableUse)
			setSlot(EXPRESSION, expression)
			setSlot(TOKENS, tokens)
			setSlot(IS_INLINE, if (isInline) 1 else 0)
		}

		/** The mutable [AssignmentPhraseDescriptor]. */
		private val mutable = AssignmentPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [AssignmentPhraseDescriptor]. */
		private val shared = AssignmentPhraseDescriptor(Mutability.SHARED)
	}
}
