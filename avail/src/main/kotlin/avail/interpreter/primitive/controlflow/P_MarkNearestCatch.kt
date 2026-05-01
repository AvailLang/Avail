/*
 * P_MarkNearestCatch.kt
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

package avail.interpreter.primitive.controlflow

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.setValueNoCheck
import avail.descriptor.variables.A_Variable.Companion.value
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_CANNOT_MARK_HANDLER_FRAME
import avail.exceptions.AvailErrorCode.E_NO_HANDLER_FRAME
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.debugL2
import avail.interpreter.execution.Interpreter.Companion.debugPrimitives
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.execution.Interpreter.Companion.loggerDebugL2
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.interpreter.primitive.Primitive1
import avail.interpreter.primitive.controlflow.P_CatchException.handlerSentinel
import avail.interpreter.primitive.controlflow.P_CatchException.unwindSentinel
import java.util.logging.Level

/**
 * **Primitive:** Mark the nearest frame corresponding to an invocation of
 * [P_CatchException] as ineligible to handle exceptions any longer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_MarkNearestCatch : Primitive1(Unknown)
{
	override fun attempt1(
		interpreter: Interpreter,
		arg1: AvailObject
	): A_BasicObject?
	{
		val code = arg1
		return interpreter.reifyForPrimitive(true) {
			when (val failureCode = interpreter.markNearestGuard(code))
			{
				null -> succeed(nil)
				else ->
				{
					if (debugPrimitives)
					{
						Interpreter.log(
							interpreter.fiber(),
							Interpreter.loggerDebugPrimitives,
							Level.FINER,
							"{0}Marking nearest catch for {1} FAILED: {2}",
							interpreter.debugModeString,
							code,
							failureCode)
					}
					fail(failureCode)
				}
			}
		}
	}

	/**
	 * Assume the entire stack has been reified.  Scan the stack of
	 * continuations until one is found for a function whose code specifies
	 * [P_CatchException]. Write the specified marker into its primitive failure
	 * variable to indicate the current exception handling state.
	 *
	 * @param marker
	 *   An exception handling state marker.
	 * @return
	 *   The failure code for the [P_MarkNearestCatch] primitive if it is to
	 *   fail, otherwise `null` to indicate success.
	 */
	fun Interpreter.markNearestGuard(
		marker: A_Number
	): AvailErrorCode?
	{
		assert(callerIsReified())
		var continuation: A_Continuation = getReifiedContinuation()!!
		var depth = 0
		while (continuation.notNil)
		{
			val code = continuation.function.code()
			if (code.codePrimitive() == P_CatchException)
			{
				assert(code.numArgs() == 3)
				assert(code.numLocals > 0)
				// The frame layout is:
				//   1. arg: body
				//   2. arg: handlers
				//   3. arg: unwind
				//   4. first local variable: guardVariable
				//   [...potentially other variables...]
				//   ≥5. first local slot: primitive failure slot
				// Note that even though variable elision postpones the creation
				// of the variable in slot (≥)5, by the time we're searching the
				// stack, the frames have become immutable, which forces the
				// variables to be created (and affected frames to jump to L1
				// interpretation).
				val guardVariable: A_Variable = continuation.frameAt(
					P_CatchException.slotIndexOfGuardVariable)
				val oldState = guardVariable.value()
				// Only allow certain state transitions.
				when
				{
					marker.equals(handlerSentinel)
						&& oldState != zero ->
						return E_CANNOT_MARK_HANDLER_FRAME
					marker.equals(unwindSentinel)
						&& oldState != handlerSentinel ->
						return E_CANNOT_MARK_HANDLER_FRAME
				}
				// Mark this frame: we don't want it to handle exceptions
				// anymore.
				guardVariable.setValueNoCheck(marker)
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Marked {1} at depth {2}",
						debugModeString,
						marker,
						depth)
				}
				return null // success
			}
			continuation = continuation.caller
			depth++
		}
		return E_NO_HANDLER_FRAME
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				enumerationWith(set(handlerSentinel, unwindSentinel))),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CANNOT_MARK_HANDLER_FRAME, E_NO_HANDLER_FRAME))

	override val canDestroyArguments get() = false
}
