/*
 * P_RaiseException.kt
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
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions.exceptionType
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions.stackDumpAtom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.setValueNoCheck
import avail.descriptor.variables.A_Variable.Companion.value
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.debugL2
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.execution.Interpreter.Companion.loggerDebugPrimitives
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive1
import avail.interpreter.primitive.controlflow.P_CatchException.handlerSentinel
import avail.optimizer.StackReifier
import avail.optimizer.StackReifier.AfterReification.CONTINUE_FIBER
import java.util.logging.Level

/**
 * **Primitive:** Raise an exception. Scan the stack of
 * [continuations][ContinuationDescriptor] until one is found for a
 * [function][FunctionDescriptor] whose [code][A_RawFunction] is
 * [P_CatchException]. Get that continuation's second argument (a tuple of
 * handler functions of one argument), and check if any of the handler functions
 * will accept `exceptionValue`. If not, keep looking. If it will accept it,
 * unwind the stack so that the [P_CatchException] continuation is the top
 * entry, and invoke the handler block with `exceptionValue`. If there is no
 * suitable handler block, then fail this primitive (with the unhandled
 * exception).
 */
@Suppress("unused")
object P_RaiseException : Primitive1(CanSuspend, CanSwitchContinuations)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val exception = arg1

		val raiseFunction = function!!
		assert(raiseFunction.code().codePrimitive() == P_RaiseException)

		currentReifier = StackReifier(true, reificationForNoninlineStat) {
			// The call stack must have been reified now.
			assert(callerIsReified())

			// Attach the current continuation to the exception, so that a stack
			// dump can be obtained later.
			val newException = exception.fieldAtPuttingCanDestroy(
				stackDumpAtom,
				getReifiedContinuation()!!.makeImmutable(),
				false)
			// Search for an applicable exception handler, leaving the
			// interpreter in a state from which it can continue after this
			// post-reification is done.
			if (!searchForExceptionHandler(newException))
			{
				// Search failed, so fail the primitive.
				val raiseChunk = raiseFunction.code().startingChunk
				function = raiseFunction
				chunk = raiseChunk
				offset = raiseChunk.offsetAfterInitialTryPrimitive
				// The exception itself is the failure value.
				setLatestResult(newException)
				// Set up the argument as well.
				argsBuffer.run {
					assert(size == 1)
					set(0, arg1)
				}
			}
			CONTINUE_FIBER
		}
		return null
	}


	/**
	 * Raise an exception. Scan the stack of continuations (which must have been
	 * reified already) until one is found for a function whose code specifies
	 * [P_CatchException]. Get that continuation's second argument (a handler
	 * block of one argument), and check if that handler block will accept the
	 * exceptionValue. If not, keep looking. If it accepts it, unwind the
	 * continuation stack so that the primitive catch method is the top entry,
	 * and invoke the handler block with exceptionValue. If there is no suitable
	 * handler block, fail the primitive.
	 *
	 * Note: Don't do either invocation directly here – set it up so that the
	 * [run] loop will be able to invoke either the handler block or the failure
	 * code, once reification has complete.
	 *
	 * @param exceptionValue
	 *   The exception object being raised.
	 */
	private fun Interpreter.searchForExceptionHandler(
		exceptionValue: AvailObject
	): Boolean
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
				val stateVariable: A_Variable = continuation.frameAt(
					P_CatchException.slotIndexOfGuardVariable)
				val state = stateVariable.value()
				if (!state.equalsInt(0))
				{
					if (debugL2)
					{
						log(
							loggerDebugPrimitives,
							Level.FINER,
							"{0}Skip catch at depth {1} with state {2}",
							debugModeString,
							depth,
							state)
					}
				}
				else
				{
					// Scan a currently unmarked frame.
					val handlerTuple: A_Tuple = continuation.frameAt(
						P_CatchException.slotIndexOfHandlersTuple)
					assert(handlerTuple.isTuple)
					for (handler in handlerTuple)
					{
						if (exceptionValue.isInstanceOf(
								handler.kind().argsTupleType.typeAtIndex(1)))
						{
							if (debugL2)
							{
								log(
									loggerDebugPrimitives,
									Level.FINER,
									"{0}Raised (->handler) at depth {1}",
									debugModeString,
									depth)
							}
							// Mark this frame: we don't want it to handle an
							// exception raised from within one of its handlers.
							stateVariable.setValueNoCheck(handlerSentinel)
							// Run the handler.  Since the JVM stack has been
							// fully reified, simply jump into the chunk.  Note
							// that the argsBuffer was already set up with just
							// the exceptionValue.
							setReifiedContinuation(continuation)
							clearLatestResult()
							function = handler
							chunk = handler.code().startingChunk
							assert(chunk!!.isValid)
							offset = 0
							// Replace the contents of the argument buffer with
							// "exceptionValue", an exception augmented with
							// stack information.
							assert(argsBuffer.size == 1)
							argsBuffer[0] = exceptionValue
							return true
						}
					}
				}
			}
			continuation = continuation.caller
			depth++
		}
		if (debugL2)
		{
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"{0}Handler not found (max depth {1})",
				debugModeString,
				depth)
		}
		// Ro handler was found, so fail the primitive.
		return false
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(exceptionType), bottom)

	override fun privateFailureVariableType(): A_Type = exceptionType
}
