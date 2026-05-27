/*
 * L2Simple_InvokeIfNilpotentAttemptFails.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.A_Type
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.Primitive

/**
 * Execute an arbitrary Kotlin function that was provided by a [Primitive] for a
 * particular call site.  The primitive may have chosen to bypass type checks
 * that are known to statically hold, for example.  The function supplied by the
 * primitive should either set the latestResult of the interpreter and return
 * SUCCESS, or have no side-effect and return FAILURE.
 *
 * When the instruction is executed, first the function is invoked, and if it
 * succeeds, the latestResult is written to the register at stackp, and the step
 * is done.  Otherwise the function failed, so we do a general invocation, and
 * assume the failed operation had no side-effect, so the general invocation's
 * reattempt of the primitive shouldn't be harmful.  If a failed primitive would
 * have a non-nilpotent side-effect, the primitive should answer a suitable
 * Kotlin function that avoids that, or just null.
 */
class L2Simple_InvokeIfNilpotentAttemptFails
constructor(
	nextOffset: Offset = Offset.NEXT,
	stateOfL1: StateOfL1,
	expectedType: A_Type,
	mustCheck: Boolean,
	answer: Write,
	val function: A_Function,
	val arguments: ReadArray,
	val nilpotentAttempt: (Interpreter)->A_BasicObject?
) : L2Simple_AbstractInvokerInstruction(
	nextOffset = nextOffset,
	stateOfL1 = stateOfL1,
	expectedType = expectedType,
	mustCheck = mustCheck,
	answer = answer)
{
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		interpreter.argsBuffer.run {
			clear()
			arguments.forEachIndexed { _, read ->
				add(registers[read])
			}
		}
		interpreter.function = function
		// First try the nilpotent function supplied by the primitive.
		val value = nilpotentAttempt(interpreter)
		interpreter.function = registers.function
		if (value != null)
		{
			// By far the most common case: Fast path succeeded.  Record the
			// returned value.
			registers[answer] = value
			return nextOffset
		}
		// The primitive failed, but it left the arguments in the argsBuffer, so
		// run it again.  It'll fail again, but this is a cold path and we don't
		// expect the retry to be a significant cost.  And it's idempotent.
		return invocationHelper(interpreter, registers, function)
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_InvokeIfNilpotentAttemptFails(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1),
			expectedType = expectedType,
			mustCheck = mustCheck,
			answer = write(answer),
			function = function,
			arguments = read(arguments),
			nilpotentAttempt = nilpotentAttempt)
}
