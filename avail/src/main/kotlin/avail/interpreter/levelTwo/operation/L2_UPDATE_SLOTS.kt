/*
 * L2_UPDATE_SLOTS.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operation

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Opcodes

/**
 * Write each of the [variables] into the corresponding frame slot of the
 * continuation being frozen, in a freeze subgraph, which starts with an
 * [L2_ENTER_FREEZE] and ends with an [L2_EXIT_FREEZE].  Reading the variables
 * forces the postponed creations (and assignments) of the elided variables, so
 * they happen in the freeze subgraph and nowhere else along this path
 * (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * The continuation written is the one passed to the generated chunk's
 * [freeze][ExecutableChunk.freeze] method.  This instruction occurs only in
 * the block that a freeze edge leads to, before any
 * [L2_ADD_UNDER_FROZEN_FRAME] has slid another frame beneath that
 * continuation and made that frame the one the method holds.
 *
 * @property slotIndices
 *   The one-based frame slot indices to write, parallel to [variables].
 * @property variables
 *   The variables to write into those frame slots.
 */
class L2_UPDATE_SLOTS
constructor(
	var slotIndices: L2ArbitraryConstantOperand<IntArray>,
	var variables: L2ReadBoxedVectorOperand
): L2Instruction()
{
	init
	{
		assert(slotIndices.constant.size == variables.elements.size)
	}

	override val isCold get() = true

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		slotIndices.constant.zip(variables.elements).forEach { (slot, read) ->
			append("\n\tslot#$slot ← ")
			append(read.registerString())
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: [continuation]
		loadFrozenContinuation()
		frameAtPutAll(slotIndices.constant.zip(variables.elements))
		// :: [continuation]
		method.visitInsn(Opcodes.POP)
		// :: []
	}
}
