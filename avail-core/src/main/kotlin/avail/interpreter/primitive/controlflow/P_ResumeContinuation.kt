/*
 * P_ResumeContinuation.kt
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

package avail.interpreter.primitive.controlflow

import avail.descriptor.functions.A_Continuation
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.AlwaysSwitchesContinuation
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Resume the specified [continuation][A_Continuation].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_ResumeContinuation : Primitive(
	1,
	Private,
	CannotFail,
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val con = interpreter.argument(0)

		interpreter.setReifiedContinuation(con)
		interpreter.function = con.function()
		interpreter.chunk = con.levelTwoChunk()
		interpreter.offset = con.levelTwoOffset()
		interpreter.returnNow = false
		interpreter.setLatestResult(null)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralContinuationType), bottom)
}
