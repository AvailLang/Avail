/*
 * L2Simple_ReifyForPushLabel.kt
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

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.ContinuationDescriptor.Companion.createDummyContinuation
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.StackReifier
import avail.performance.Statistic
import avail.performance.StatisticReport

/**
 * If the current continuation is not reified, reify it and arrange to
 * immediately reenter at the [reentryOffset], which will restore the registers
 * and fall through to the next instruction.  If the current continuation was
 * already reified, simply jump past the reentry instruction to the
 * [L2Simple_PushLabel] instruction that follows it.
 *
 * Note that in the event of reification, the [reentryOffset] will be reached
 * immediately, with no possibility of invalidation, so the L1 state of slots
 * does not have to be captured in the continuation (this is called a dummy
 * continuation).  Only the L2Simple registers need to be saved/restored.
 *
 * The [stateOfL1] should have its [StateOfL1.liveSlots] set up to be empty. All
 * live registers will still be preserved.  The [StateOfL1.pc] and
 * [StateOfL1.stackp] are ignored.
 */
class L2Simple_ReifyForPushLabel(
	nextOffset: Offset,
	reentryOffset: Offset,
	stateOfL1: StateOfL1
) : L2Simple_AbstractReifiableInstruction(
	nextOffset, reentryOffset, stateOfL1, DefaultEntryPoint.TRANSIENT)
{
	init
	{
		assert(stateOfL1.liveSlots.size == 0)
	}

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val thisChunk = interpreter.chunk as L2SimpleChunk
		val function = registers.function
		if (interpreter.callerIsReified())
		{
			// Skip the reification step, since the caller is already
			// conveniently reified.
			return nextOffset
		}
		// Slow path.  Reify the call stack, arranging to continue running at
		// the reentryOffset.  There will be an L2Simple_ReenterToResume
		// instruction there that will restore the registers and fall through to
		// label creation (which is also at my nextOffset).
		interpreter.currentReifier =
			StackReifier(true, reificationBeforeLabelCreationStat) {
				val caller =
					interpreter.getReifiedContinuation()!!.makeImmutable()
				// Create a *dummy* continuation capturing the registers,
				// and push it on the frame stack.  When this instruction
				// immediately reenters, it will restore the register state
				// from the dummy continuation's register dump, then push a
				// label.
				var dummyContinuation: A_Continuation =
					createDummyContinuation(
						function = function,
						registerDump = makeRegisterDump(registers),
						levelTwoChunk = thisChunk,
						levelTwoOffset = nextOffset.value)
				dummyContinuation =
					dummyContinuation.replacingCaller(caller)
				interpreter.setReifiedContinuation(dummyContinuation)
				// Now we tell the interpreter to reenter the dummy
				// continuation, which will extract the register dump back
				// into the new RegisterSet, and continue running the chunk
				// at the next offset.
				interpreter.function = function
				interpreter.chunk = thisChunk
				interpreter.offset = reentryOffset.value
				StackReifier.AfterReification.CONTINUE_FIBER
			}
		return REIFY_NOW
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_ReifyForPushLabel(
			nextOffset = target(nextOffset),
			reentryOffset = target(reentryOffset),
			stateOfL1 = state(stateOfL1))

	companion object
	{
		/**
		 * Statistic for when a pushLabel in an L2Simple chunk is forced to
		 * perform reification (so that the label's caller is correct).
		 */
		private val reificationBeforeLabelCreationStat = Statistic(
			StatisticReport.REIFICATIONS,
			"L2Simple reification before label creation")
	}
}
