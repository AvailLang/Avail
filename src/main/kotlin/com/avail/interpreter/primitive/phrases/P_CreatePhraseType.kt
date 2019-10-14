/*
 * P_CreatePhraseType.java
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.instanceMeta
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.exceptions.AvailErrorCode.E_BAD_YIELD_TYPE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a variation of a [ ].  In particular, create a phrase type of
 * the same [kind][PhraseKind] but with the specified expression type.
 */
object P_CreatePhraseType : Primitive(2, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val baseType = interpreter.argument(0)
		val expressionType = interpreter.argument(1)
		if (baseType.isBottom)
		{
			return interpreter.primitiveSuccess(baseType)
		}
		val kind = baseType.phraseKind()
		return if (!expressionType.isSubtypeOf(kind.mostGeneralYieldType()))
		{
			interpreter.primitiveFailure(E_BAD_YIELD_TYPE)
		}
		else interpreter.primitiveSuccess(kind.create(expressionType))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				instanceMeta(PARSE_PHRASE.mostGeneralType()),
				topMeta()),
			instanceMeta(PARSE_PHRASE.mostGeneralType()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return instanceType(E_BAD_YIELD_TYPE.numericCode())
	}

}