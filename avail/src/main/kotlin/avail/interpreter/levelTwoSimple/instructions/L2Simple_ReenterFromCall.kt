/*
 * L2Simple_ReenterFromCall.kt
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
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrame
import avail.descriptor.functions.RegisterDumpDescriptor.Companion.emptyRegisterDump
import avail.descriptor.representation.A_Continuation.Companion.frameAtPut
import avail.descriptor.representation.A_Continuation.Companion.registerDump
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.UNREACHABLE_ENTRY
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk

/**
 * A function was invoked, was reified, and is now returning into the caller.
 * Capture the [Interpreter.getLatestResult], check it against the
 * [expectedType] if [mustCheck], and if it conforms, write the result to
 * [answer] and continue.  If it had the wrong type, invoke the
 * [RESULT_DISAGREED_WITH_EXPECTED_TYPE] hook function, which will never return
 * (because it's ⊥-typed), but may still reify.
 *
 * The [stateOfL1] should be set up with an empty [StateOfL1.liveSlots] array,
 * since this instruction restore registers, not slots.  The [StateOfL1.pc] and
 * [StateOfL1.stackp] are unused.
 */
class L2Simple_ReenterFromCall(
	nextOffset: Offset = Offset.NEXT,
	stateOfL1: StateOfL1,
	val answer: Write,
	val expectedType: A_Type,
	val mustCheck: Boolean
) : L2Simple_AbstractReenter(nextOffset, stateOfL1)
{
	init
	{
		assert(stateOfL1.liveSlots.size == 0)
	}

	/**
	 * Capture the return value, check it if necessary, and either assign it to
	 * [answer] or invoke the [RESULT_DISAGREED_WITH_EXPECTED_TYPE] hook.
	 */
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		restoreFromDump(interpreter.popContinuation().registerDump, registers)
		val returnedValue = interpreter.getLatestResult()
		if (!mustCheck || returnedValue.isInstanceOf(expectedType))
		{
			// Almost always: The returned value had the right type.
			registers[answer] = returnedValue
			return nextOffset
		}
		// Very rare: The returned value had the wrong type.
		val wrappedReturnValue =
			newVariableWithContentType(ANY(), returnedValue)
		interpreter.argsBuffer.run {
			clear()
			add(interpreter.returningFunction as AvailObject)
			add(expectedType as AvailObject)
			add(wrappedReturnValue)
		}
		val handlerValueOrNull = interpreter.invokeFunction(
			interpreter.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE])
		// Note that the handler is ⊥-valued, so it can't return normally, but
		// it can still reify.
		assert(handlerValueOrNull === null)
		interpreter.currentReifier!!.pushAction {
			val continuation = createContinuationExceptFrame(
				registers.function,
				it,
				emptyRegisterDump(UNREACHABLE_ENTRY.offset),
				stateOfL1.pc,
				stateOfL1.stackp,
				DefaultL1Chunk,
				UNREACHABLE_ENTRY.offset)
			stateOfL1.liveSlots.forEachIndexed { zeroIndex, source ->
				continuation.frameAtPut(zeroIndex + 1, registers[source])
			}
			continuation
		}
		return REIFY_NOW
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_ReenterFromCall(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1),
			answer = write(answer),
			expectedType = expectedType,
			mustCheck = mustCheck)
}
