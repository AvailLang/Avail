/*
 * L2Simple_AbstractInvokerInstruction.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwoSimple.instructions

import avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.functions.A_Function
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.MethodDefinitionException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.REENTRY_FROM_REIFIED_CALL

/**
 * An abstract class for instructions whose primary purpose is to invoke some
 * other function.  The [expectedType] indicates what type should be pushed on
 * the stack if reification happens.  If [mustCheck] is true, the [expectedType]
 * is also used to check that the eventually returned value is of the correct
 * type.
 */
abstract class L2Simple_AbstractInvokerInstruction
constructor(
	nextOffset: Offset,
	reentryOffset: Offset,
	stateOfL1: StateOfL1,
	val expectedType: A_Type,
	val mustCheck: Boolean,
	val answer: Write
): L2Simple_AbstractReifiableInstruction(
	nextOffset, reentryOffset, stateOfL1, REENTRY_FROM_REIFIED_CALL)
{
	/**
	 * A utility for invoking a given function, handling reification and return
	 * type checking as needed.
	 */
	protected fun invocationHelper(
		interpreter: Interpreter,
		registers: RegisterSet,
		function: A_Function
	): Offset
	{
		val thisChunk = interpreter.chunk!!
		//assert(function.code().functionType().acceptsListOfArgValues(
		//	interpreter.argsBuffer))
		var valueOrNull = interpreter.invokeFunction(function)
		assert(interpreter.chunk === thisChunk)
		assert(interpreter.function === registers.function)
		if (valueOrNull === null)
		{
			// It reified inside the call.
			val reifier = interpreter.currentReifier!!
			// Ensure the current frame is added to the reified call stack.
			if (reifier.actuallyReify)
			{
				reifier.pushAction {
					createContinuation(
						it, registers, thisChunk, expectedType, reentryOffset)
				}
			}
			interpreter.currentReifier = reifier
			return REIFY_NOW
		}
		// We returned normally from the call, which is the fast path.
		if (!mustCheck || valueOrNull.isInstanceOf(expectedType))
		{
			// Passed the return check, or didn't need to check.  This is
			// the fastest path.
			registers[answer] = valueOrNull
			return nextOffset
		}
		// Rare - the result did not conform to the expected type.
		val wrappedReturnValue =
			VariableDescriptor.newVariableWithContentType(
				PrimitiveTypeDescriptor.Types.ANY(),
				valueOrNull)
		interpreter.argsBuffer.run {
			clear()
			add(function as AvailObject)
			add(expectedType as AvailObject)
			add(wrappedReturnValue)
		}
		val handlerValueOrNull = interpreter.invokeFunction(
			interpreter.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE])
		// Note that the handler is ⊥-valued, so it can't return normally.
		assert(handlerValueOrNull === null)
		interpreter.currentReifier!!.pushAction {
			// Using the reentryOffset doesn't matter, since it can't continue.
			createContinuation(
				it, registers, thisChunk, expectedType, reentryOffset)
		}
		return REIFY_NOW
	}

	/**
	 * A runtime lookup failed.  Invoke the invalidMessageSendFunction, while
	 * supporting reification inside the call.
	 */
	protected fun handleFailedLookup(
		interpreter: Interpreter,
		args: MutableList<AvailObject>,
		e: MethodDefinitionException,
		registers: RegisterSet,
		bundle: A_Bundle
	): Offset
	{
		val thisChunk = interpreter.chunk!!
		val argumentsTuple = tupleFromList(args)
		args.clear()
		args.add(e.errorCode.numericCode() as AvailObject)
		args.add(bundle.bundleMethod as AvailObject)
		args.add(argumentsTuple as AvailObject)
		val valueOrNull = interpreter.invokeFunction(
			interpreter.runtime.invalidMessageSendFunction())
		assert(valueOrNull === null)
		// The function cannot return, so it's reifying.
		val reifier = interpreter.currentReifier!!
		if (reifier.actuallyReify)
		{
			reifier.pushAction {
				registers[answer] = expectedType as AvailObject
				createContinuation(
					it, registers, thisChunk, bottom, Offset.UNREACHABLE)
			}
		}
		return REIFY_NOW
	}
}
