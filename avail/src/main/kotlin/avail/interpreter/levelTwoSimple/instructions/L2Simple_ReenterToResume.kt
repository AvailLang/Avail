/*
 * L2Simple_ReenterToResume.kt
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

import avail.descriptor.representation.A_Continuation.Companion.registerDump
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet

/**
 * A reification just took place to allow label creation.  Extract the registers
 * dumped in the dummy continuation, popping it, and jump to an offset suitable
 * for building the label.  In this case, the original instruction that caused
 * the reification should be fine, since we know that this time around it wall
 * successfully construct the label.
 *
 * The [stateOfL1] should be set up with an empty [StateOfL1.liveSlots] array,
 * since this instruction restore registers, not slots.  The [StateOfL1.pc] and
 * [StateOfL1.stackp] are unused.
 */
class L2Simple_ReenterToResume(
	nextOffset: Offset = Offset.NEXT,
	stateOfL1: StateOfL1
) : L2Simple_AbstractReenter(nextOffset, stateOfL1)
{
	/**
	 * A dummy continuation has resumed *immediately* after a pushLabel caused
	 * reification.  Pop the dummy continuation, using its register dump to
	 * restore the [registers], then create and push a label.  Note that we
	 * don't have to check validity of the reentering chunk, since the
	 * interpreter offered no opportunity to suspend to a safe-point, which is
	 * the only place where invalidation can happen.
	 */
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		restoreFromDump(interpreter.popContinuation().registerDump, registers)
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_ReenterToResume(
			nextOffset = target(nextOffset),
			stateOfL1 = state(stateOfL1))
}
