/*
 * P_SpecialObject.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.bootstrap

import com.avail.AvailRuntime
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.exceptions.AvailErrorCode.E_NO_SPECIAL_OBJECT
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Retrieve the [special&#32;object][AvailRuntime.specialObject]
 * with the specified ordinal.
 */
@Suppress("unused")
object P_SpecialObject : Primitive(1, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val ordinalLiteral = interpreter.argument(0)
		val ordinal = ordinalLiteral.token().literal()
		if (!ordinal.isInt)
		{
			return interpreter.primitiveFailure(E_NO_SPECIAL_OBJECT)
		}
		val i = ordinal.extractInt()
		val result: AvailObject
		try
		{
			result = AvailRuntime.specialObject(i)
		}
		catch (e: ArrayIndexOutOfBoundsException)
		{
			return interpreter.primitiveFailure(E_NO_SPECIAL_OBJECT)
		}

		if (result.equalsNil())
		{
			return interpreter.primitiveFailure(E_NO_SPECIAL_OBJECT)
		}

		return interpreter.primitiveSuccess(syntheticLiteralNodeFor(result))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(LITERAL_PHRASE.create(naturalNumbers)),
			LITERAL_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NO_SPECIAL_OBJECT))
}
