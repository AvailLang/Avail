/*
 * L2Simple_Jump.kt
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
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.HIGHEST_LEGAL_OFFSET
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.RETURN_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet

/**
 * Jump to the instruction with the indicated offset.  Note that the offset
 * can be a special value > [HIGHEST_LEGAL_OFFSET] to indicate an exit of
 * the [A_Function] is requested, in particular [RETURN_NOW] to return the
 * last value in the register array, or [REIFY_NOW] to return `null`,
 * indicating a reification is in progress.
 */
class L2Simple_Jump(
	nextOffset: Offset
) : L2SimpleInstruction(nextOffset)
{
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset  = nextOffset

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_Jump(
			nextOffset = target(nextOffset))
}
