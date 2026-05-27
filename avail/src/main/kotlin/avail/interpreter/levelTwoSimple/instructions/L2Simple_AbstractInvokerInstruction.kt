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
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.functions.A_Continuation.Companion.registerDump
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
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk

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
	stateOfL1: StateOfL1,
	val expectedType: A_Type,
	val mustCheck: Boolean,
	val answer: Write
): L2Simple_AbstractReifiableInstruction(nextOffset, stateOfL1)
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
		interpreter.chunk = thisChunk
		interpreter.function = registers.function
		if (valueOrNull === null)
		{
			// It reified inside the call.
			val reifier = interpreter.currentReifier!!
			// Ensure the current frame is added to the reified call stack.
			if (reifier.actuallyReify)
			{
				reifier.pushAction {
					createContinuation(it, registers, thisChunk, expectedType)
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
		assert(interpreter.currentReifier != null)
		return REIFY_NOW
	}

	override fun defaultL1EntryPointIfInvalid() = REENTRY_FROM_REIFIED_CALL

	/**
	 * This is called when the invocation for this step had to reify, and now
	 * we've finished the actual Avail call and we're attempting to continue
	 * where we left off.  We have to check the returned value and either
	 * capture it on the stack or invoke the result check failure function.
	 *
	 * Note that at this time, only registers.function has been set up, so if this
	 * reentry succeeds (i.e., the return value satisfies the expectedType), we
	 * need to transfer the top continuation's slots into the registers array
	 * and pop that continuation off the call stack.
	 *
	 * Also, if the current chunk has become invalid, we will alter the
	 * interpreter's current chunk to the [DefaultL1Chunk] to indicate this,
	 * allowing the (Kotlin) caller to immediately return to the interpreter
	 * loop for L1 interpretation.
	 */
	override fun reenter(
		registers: RegisterSet,
		interpreter: Interpreter
	): Boolean
	{
		val result = interpreter.getLatestResult()
		val con = interpreter.getReifiedContinuation()!!
		val thisChunk = con.levelTwoChunk
		if (!mustCheck || result.isInstanceOf(expectedType))
		{
			// Passed the return check, or didn't need to check.  This is the
			// fastest path.  Restore the registers from the continuation and
			// pop it.
			assert(con.frameAt(stateOfL1.stackp).equals(expectedType))
				// Restore the registers from the continuation.
			restoreFromDump(con.registerDump, registers)
			// Now replace the top-of-stack register with the (correctly typed)
			// returned result.
			registers[answer] = result
			interpreter.popContinuation()
			return true
		}
		// Rare - the return check failed, so we need to invoke the return
		// check failure function.  It's ⊥-valued, so it won't return, but
		// it will eventually reify.
		val wrappedReturnValue = VariableDescriptor.newVariableWithContentType(
			PrimitiveTypeDescriptor.Types.ANY(),
			result)
		interpreter.argsBuffer.run {
			clear()
			add(registers.function)
			add(expectedType as AvailObject)
			add(wrappedReturnValue)
		}
		val valueOrNull = interpreter.invokeFunction(
			interpreter.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE])
		// The handler is ⊥-valued, so it can't return normally.
		assert(valueOrNull === null)
		// We're reifying either the original call or the return check failure.
		val reifier = interpreter.currentReifier!!
		if (reifier.actuallyReify)
		{
			reifier.pushAction { currentContinuation ->
				createContinuation(
					currentContinuation, registers, thisChunk, expectedType)
			}
		}
		return false
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
				createContinuation(it, registers, thisChunk, bottom)
			}
		}
		return REIFY_NOW
	}
}
