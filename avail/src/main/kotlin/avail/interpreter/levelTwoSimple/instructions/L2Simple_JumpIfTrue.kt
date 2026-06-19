/*
 * L2Simple_JumpIfTrue.kt
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

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.representation.A_Atom.Companion.extractBoolean
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet

/**
 * Read the condition.  If it's the Avail object [trueObject], jump to the
 * [ifTrueOffset], otherwise (it must be the [falseObject], jump to the
 * [nextOffset].
 */
class L2Simple_JumpIfTrue(
	nextOffset: Offset,
	val ifTrueOffset: Offset,
	val condition: Read
) : L2SimpleInstruction(nextOffset)
{
	override val canBePostponed: Boolean get() = false

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset = when
	{
		registers[condition].extractBoolean -> ifTrueOffset
		else -> nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_JumpIfTrue(
			nextOffset = target(nextOffset),
			ifTrueOffset = target(ifTrueOffset),
			condition = read(condition))
}
