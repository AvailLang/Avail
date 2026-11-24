/*
 * L2_EXIT_FREEZE.kt
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

import avail.interpreter.levelTwo.L2OperandType
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Opcodes

/**
 * The end of a freeze subgraph, which starts with an [L2_ENTER_FREEZE].
 * Return from the generated chunk's [freeze][ExecutableChunk.freeze] method.
 * The continuation being frozen has had its slots written by
 * [L2_UPDATE_SLOTS], and the frames of any enclosing inlined calls slid
 * beneath it by [L2_ADD_UNDER_FROZEN_FRAME], so there is nothing left to do
 * (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * When a callee's graph is embedded in a caller, each of the callee's exits
 * becomes a jump to the call site's freeze block, which builds the caller's
 * frame, slides it beneath the frames built so far, and ends with the
 * caller's own exit.
 */
class L2_EXIT_FREEZE : L2ControlFlowInstruction()
{
	override val isCold get() = true

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: return
		method.visitInsn(Opcodes.RETURN)
	}
}
