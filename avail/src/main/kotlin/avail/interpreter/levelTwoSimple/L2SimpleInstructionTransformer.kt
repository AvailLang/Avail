/*
 * L2SimpleInstructionTransformer.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package avail.interpreter.levelTwoSimple

import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.levelTwoSimple.instructions.registers.WriteArray

/**
 * An [L2SimpleInstructionTransformer] provides a uniform way to transform the
 * operands of an [L2SimpleInstruction].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class L2SimpleInstructionTransformer()
{
	/** Process a [Read] operand. */
	open fun read(read: Read): Read = read

	/** Process a [ReadArray] operand. */
	fun read(reads: ReadArray): ReadArray =
		reads.map(::read)

	/** Process a [Write] operand. */
	open fun write(write: Write): Write = write

	/** Process a [WriteArray] operand. */
	fun write(indices: WriteArray): WriteArray =
		indices.map(::write)

	/** Process an [Offset] operand. */
	open fun target(offset: Offset): Offset = offset

	/** Process a [StateOfL1] operand. */
	open fun state(stateOfL1: StateOfL1): StateOfL1 =
		StateOfL1(
			pc = stateOfL1.pc,
			stackp = stateOfL1.stackp,
			liveSlots = read(stateOfL1.liveSlots),
			// DO NOT transform these, if present.
			allLiveRegisters = stateOfL1.allLiveRegisters)
}
