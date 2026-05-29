/*
 * L2Simple_CheckForInterrupt.kt
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

import avail.descriptor.functions.A_Continuation.Companion.frameAtPut
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrame
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleExecutableChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.StackReifier

/**
 * Poll to see if an interrupt has been requested.  The bulk of this is handled
 * directly by [L2SimpleExecutableChunk], but reentry still has to be handled
 * here.
 */
class L2Simple_CheckForInterrupt(
	nextOffset: Offset,
	reentryOffset: Offset,
	stateOfL1: StateOfL1
) : L2Simple_AbstractReifiableInstruction(
	nextOffset, reentryOffset, stateOfL1, DefaultEntryPoint.RESUME)
{
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val statistic =  interpreter.statisticForRequestedInterrupt
		if (statistic === null)
			return nextOffset
		// An interrupt has been requested.  Reify and process it.
		val function = registers.function
		val thisChunk = interpreter.chunk!!
		interpreter.currentReifier = StackReifier(true, statistic) {
			val caller = interpreter.getReifiedContinuation()!!
			val continuation = createContinuationExceptFrame(
				function,
				caller,
				makeRegisterDump(registers),
				stateOfL1.pc,
				stateOfL1.stackp,
				thisChunk,
				reentryOffset.value)
			stateOfL1.liveSlots.forEachIndexed { zeroIndex, source ->
				continuation.frameAtPut(
					zeroIndex + 1,
					if (source.value == 0) nil else registers[source])
			}
			interpreter.setReifiedContinuation(continuation)
			interpreter.function = function
			interpreter.chunk = thisChunk
			interpreter.offset = reentryOffset.value
			interpreter.processInterrupt(continuation)
			StackReifier.AfterReification.SWITCH_FROM_FIBER
		}
		return REIFY_NOW
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_CheckForInterrupt(
			nextOffset = target(nextOffset),
			reentryOffset = target(reentryOffset),
			stateOfL1 = state(stateOfL1))
}
