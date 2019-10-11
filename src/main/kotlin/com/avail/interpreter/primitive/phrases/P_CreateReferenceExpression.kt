/*
 * P_CreateReferenceExpression.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Phrase
import com.avail.descriptor.A_Type
import com.avail.descriptor.ReferencePhraseDescriptor
import com.avail.descriptor.VariableUsePhraseDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.MODULE_VARIABLE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.REFERENCE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import com.avail.descriptor.ReferencePhraseDescriptor.referenceNodeFromUse
import com.avail.descriptor.SetDescriptor.set
import com.avail.exceptions.AvailErrorCode.E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Transform a [ ] into a [ ].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateReferenceExpression : Primitive(1, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val variableUse = interpreter.argument(0)

		val declaration = variableUse.declaration()
		if (!declaration.phraseKindIsUnder(MODULE_VARIABLE_PHRASE) && !declaration.phraseKindIsUnder(LOCAL_VARIABLE_PHRASE))
		{
			return interpreter.primitiveFailure(
				E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE)
		}
		val reference = referenceNodeFromUse(variableUse)
		return interpreter.primitiveSuccess(reference)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				VARIABLE_USE_PHRASE.mostGeneralType()),
			REFERENCE_PHRASE.mostGeneralType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE))
	}

}