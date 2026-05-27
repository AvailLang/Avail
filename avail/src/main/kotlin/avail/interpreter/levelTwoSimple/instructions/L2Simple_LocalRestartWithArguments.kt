/*
 * L2Simple_LocalRestartWithArguments.kt
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

import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleExecutableChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet

/**
 * Restart the current function frame using new arguments read from explicit
 * source slots, in place of a P_RestartContinuationWithArguments invocation
 * that would otherwise have built (and resumed into) a label continuation
 * for this frame.  All locals are reinitialized to fresh variables, mirroring
 * the function-entry setup performed by [L2SimpleExecutableChunk.runChunk].
 *
 * This instruction is emitted only for functions without a `codePrimitive`,
 * so the restart does not have to re-attempt a primitive that lives at the
 * function's entry — there is none to re-attempt.
 *
 * @property nextOffset
 *   The [Offset] that restarts the frame (consuming arguments and creating
 *   locals).
 * @property argSources
 *   Register slots from which to read the new argument values, in argument
 *   order.  May overlap the destination slots (1..numArgs), so values are
 *   captured into a temporary array before being written back.
 */
class L2Simple_LocalRestartWithArguments(
	nextOffset: Offset,
	val argSources: ReadArray
) : L2SimpleInstruction(nextOffset)
{
	init { assert(nextOffset.value == 0) }

	override val canBePostponed: Boolean
		get() = false

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		// The argument capture (and locals creation) happens in generated
		// L3SimpleCode now, so just put them into the argsBuffer.
		interpreter.argsBuffer.run {
			clear()
			argSources.forEachIndexed { _, read ->
				add(registers[read])
			}
		}
		// Should be offset zero.
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_LocalRestartWithArguments(
			nextOffset = target(nextOffset),
			argSources = read(argSources))
}
