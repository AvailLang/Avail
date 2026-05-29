/*
 * L2Simple_PushLabel.kt
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

import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.functions.ContinuationDescriptor.Companion.createLabelContinuation
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.controlflow.P_ExitContinuationIf
import avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.performance.Statistic
import avail.performance.StatisticReport

/**
 * Construct a label for restarting or exiting the current continuation.  Note
 * that this *may* cause reification to force the call chain to be reified, but
 * the reifier block answers CONTINUE_FIBER, so no *invalidation* is possible
 * before reentry happens.  Therefore, there is no need to capture the current
 * frame's state in L1 slots of the continuation – the [A_RegisterDump] will be
 * sufficient.  The reentry is a [L2Simple_ReenterToResume], which, after
 * restoring the dummy continuation's registers, jumps back to this instruction,
 * which will detect the reified stack and create the label.
 *
 * If the label is later restarted, its caller will be the same as the current
 * reified caller continuation, and its arguments will be the same if using
 * [P_RestartContinuation], or the ones provided explicitly if using
 * [P_RestartContinuationWithArguments].
 *
 * If a label is exited via [P_ExitContinuationIf] or
 * [P_ExitContinuationWithResultIf], it's as though the current frame has been
 * returned into the (reified) caller, with either nil or a specific return
 * value, respectively.
 *
 * Note that the [stateOfL1] should have its [StateOfL1.liveSlots] set up to
 * include only the function arguments.  All live registers will still be
 * preserved, but the label itself only captures the original arguments.  The
 * [StateOfL1.pc] and [StateOfL1.stackp] are ignored.
 */
class L2Simple_PushLabel(
	nextOffset: Offset = Offset.NEXT,
	val stateOfL1: StateOfL1,
	val answer: Write
) : L2SimpleInstruction(nextOffset)
{
	override val canBePostponed get() = true

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val thisChunk = interpreter.chunk as L2SimpleChunk
		val function = registers.function
		assert(interpreter.callerIsReified())
		// Skip the reification step (or we reified and we're back again), since
		// the caller is already conveniently reified.
		val label = createLabelContinuation(
			function = function,
			caller = interpreter.getReifiedContinuation()!!.makeImmutable(),
			startingChunk = thisChunk,
			// Indicates a label.
			startingOffset = 0,
			args = registers[stateOfL1.liveSlots].asList())
		label.makeSubobjectsImmutable()
		registers[answer] = label
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_PushLabel(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1),
			answer = write(answer))

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
