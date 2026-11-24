/*
 * L2_ENTER_FREEZE.kt
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

import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.JVMTranslator

/**
 * The start of a freeze subgraph, the target of an [L2_CREATE_CONTINUATION]'s
 * [freeze][L2_CREATE_CONTINUATION.freeze] edge.  Control never flows along
 * that edge.  Instead, the generated chunk's [freeze][ExecutableChunk.freeze]
 * method starts here when the continuation becomes immutable or shared, so
 * that the subgraph can create the variables whose creation was postponed
 * past the continuation's creation, write them into the continuation's frame
 * with an [L2_UPDATE_SLOTS], and finish with an [L2_EXIT_FREEZE].
 *
 * In the graph, the edge behaves as a jump, carrying the manifest and its
 * postponed instructions into the subgraph.  During JVM translation, the
 * [L2_CREATE_CONTINUATION] saves the registers that are live along the edge
 * into the room reserved for them in the continuation's [A_RegisterDump], and
 * this instruction restores them into the same registers
 * (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 */
class L2_ENTER_FREEZE : L2Instruction()
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
		val layout = freezeDumpLayouts[offset]!!
		if (layout.isEmpty) return
		// :: [continuation]
		loadFrozenContinuation()
		// :: [registerDump]
		generateCall(AvailObject.registerDumpMethod)
		restoreRegistersFromDump(layout)
		// :: []
	}
}
