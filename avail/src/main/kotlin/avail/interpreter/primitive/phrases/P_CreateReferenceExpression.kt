/*
 * P_CreateReferenceExpression.kt
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
package avail.interpreter.primitive.phrases

import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.ReferencePhraseDescriptor
import avail.descriptor.phrases.ReferencePhraseDescriptor.Companion.referenceNodeFromUse
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MODULE_VARIABLE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.REFERENCE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import avail.exceptions.AvailErrorCode.E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive1

/**
* **Primitive:** Transform a [variable&#32;use][VariableUsePhraseDescriptor]
 * into a [reference][ReferencePhraseDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateReferenceExpression : Primitive1(CanInline)
{
	override fun attempt1(
		interpreter: Interpreter,
		arg1: AvailObject
	): A_BasicObject?
	{
		val variableUse = arg1

		val declaration = variableUse.declaration
		if (!declaration.phraseKindIsUnder(MODULE_VARIABLE_PHRASE)
			&& !declaration.phraseKindIsUnder(LOCAL_VARIABLE_PHRASE))
		{
			return interpreter.fail(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE)
		}
		return referenceNodeFromUse(variableUse)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				VARIABLE_USE_PHRASE.mostGeneralType),
			REFERENCE_PHRASE.mostGeneralType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE))
}
