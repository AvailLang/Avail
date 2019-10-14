/*
 * P_ExitContinuationWithResult.java
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AvailObject
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED

/**
 * **Primitive:** Exit the given [ ] (returning result to its caller).
 */
object P_ExitContinuationWithResult : Primitive(
	2,
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val con = interpreter.argument(0)
		val result = interpreter.argument(1)

		// The primitive fails if the value being returned disagrees with the
		// label continuation's function's return type.  Any stronger check, as
		// specified in a semantic restriction, will be tested in the caller.
		if (!result.isInstanceOf(
				con.function().code().functionType().returnType()))
		{
			return interpreter.primitiveFailure(
				E_CONTINUATION_EXPECTED_STRONGER_TYPE)
		}

		interpreter.reifiedContinuation = con.caller() as AvailObject
		interpreter.function = null
		interpreter.chunk = null
		interpreter.offset = Integer.MAX_VALUE
		interpreter.returnNow = true
		interpreter.latestResult(result)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralContinuationType(),
				ANY.o()),
			bottom())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CONTINUATION_EXPECTED_STRONGER_TYPE))
}