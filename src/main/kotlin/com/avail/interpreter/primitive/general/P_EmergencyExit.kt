/*
 * P_EmergencyExit.kt
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
package com.avail.interpreter.primitive.general

import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.dumpStackThen
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailEmergencyExitException
import com.avail.exceptions.AvailErrorCode
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.AlwaysSwitchesContinuation
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.CanSwitchContinuations
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.utility.cast
import java.lang.String.format

/**
 * **Primitive:** Exit the current [fiber][FiberDescriptor]. The specified
 * argument will be converted internally into a `string` and used to report an
 * error message.
 *
 * It's marked with [CanSwitchContinuations] to force the stack to
 * be reified, for debugging convenience.
 */
@Suppress("unused")
object P_EmergencyExit : Primitive(
	1,
	Unknown,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation,
	CanSuspend,
	CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val errorMessageProducer = interpreter.argument(0)
		val fiber = interpreter.fiber()
		val continuation = interpreter.getReifiedContinuation()!!
		interpreter.primitiveSuspend(interpreter.function!!)
		dumpStackThen(
			interpreter.runtime, fiber.textInterface(), continuation
		) { stack ->
			val builder = StringBuilder()
			builder.append(format(
				"A fiber (%s) has exited: %s",
				fiber.fiberName(),
				errorMessageProducer))
			if (errorMessageProducer.isInt)
			{
				val errorNumber: A_Number = errorMessageProducer.cast()
				val intValue = errorNumber.extractInt()
				val code = AvailErrorCode.byNumericCode(intValue)
				if (code !== null)
				{
					builder.append(format(" (= %s)", code.name))
				}
			}
			for (frame in stack)
			{
				builder.append(format("%n\t-- %s", frame))
			}
			builder.append("\n\n")
			val killer = AvailEmergencyExitException(builder.toString())
			killer.fillInStackTrace()
			fiber.setExecutionState(ExecutionState.ABORTED)
			fiber.failureContinuation()(killer)
			// If we're still here, the handler didn't do anything with the
			// exception.  Output it and throw it as a runtime exception.
			System.err.print(builder)
			throw RuntimeException(killer)
		}
		return Result.FIBER_SUSPENDED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ANY.o), bottom)

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// Never inline.  Ensure the caller reifies the stack before calling it.
		return false
	}
}
