/*
 * P_CreateAssignmentStatement.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.newAssignment
import com.avail.descriptor.phrases.SequencePhraseDescriptor
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ASSIGNMENT_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MODULE_VARIABLE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import com.avail.exceptions.AvailErrorCode.E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Transform a variable reference and an expression into an
 * [assignment][AssignmentPhraseDescriptor] statement. Such a phrase has type
 * [top][Types.TOP] and cannot be embedded as a subexpression.
 *
 * Note that because we can have "inner" assignment phrases (i.e., assignments
 * used as subexpressions), we actually produce a
 * [sequence&#32;phrase][SequencePhraseDescriptor] here, consisting of the
 * assignment phrase proper (whose output is effectively discarded) and a
 * literal [null&#32;value][NilDescriptor.nil].
 */
@Suppress("unused")
object P_CreateAssignmentStatement : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variable = interpreter.argument(0)
		val expression = interpreter.argument(1)

		val declaration = variable.declaration()
		if (!declaration.phraseKindIsUnder(MODULE_VARIABLE_PHRASE)
			&& !declaration.phraseKindIsUnder(LOCAL_VARIABLE_PHRASE))
		{
			return interpreter.primitiveFailure(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT)
		}
		if (!expression.phraseExpressionType().isSubtypeOf(
				variable.phraseExpressionType()))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		val assignment = newAssignment(
			variable, expression, emptyTuple, false)
		return interpreter.primitiveSuccess(assignment)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				VARIABLE_USE_PHRASE.mostGeneralType(),
				EXPRESSION_PHRASE.create(ANY.o)),
			ASSIGNMENT_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE))
}
