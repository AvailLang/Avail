/*
 * L2Simple_GetConstant.kt
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

import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.A_Variable.Companion.getValue
import avail.descriptor.representation.AvailObject
import avail.exceptions.VariableGetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Offset.Companion.REIFY_NOW
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint

/**
 * Read from a literal (module global) [A_Variable], writing its content in
 * registers[ [answer] ].
 */
class L2Simple_GetConstant(
	nextOffset: Offset,
	reentryOffset: Offset,
	stateOfL1: StateOfL1,
	val variable: AvailObject,
	val answer: Write
) : L2Simple_AbstractReifiableInstruction(
	nextOffset, reentryOffset, stateOfL1, DefaultEntryPoint.UNREACHABLE_ENTRY)
{
	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		try
		{
			registers[answer] = variable.getValue()
			return nextOffset
		}
		catch (e: VariableGetException)
		{
			handleVariableGetException(e, interpreter, registers)
			assert(interpreter.currentReifier !== null)
			return REIFY_NOW
		}
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_GetConstant(
			nextOffset = target(nextOffset),
			reentryOffset = target(reentryOffset),
			stateOfL1 = state(stateOfL1),
			variable = variable,
			answer = write(answer))
}
