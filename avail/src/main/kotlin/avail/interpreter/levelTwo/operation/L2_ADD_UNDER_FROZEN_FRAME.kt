/*
 * L2_ADD_UNDER_FROZEN_FRAME.kt
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

import avail.descriptor.functions.ContinuationDescriptor.Companion.addUnderFrozenFrameMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.JVMTranslator

/**
 * In a freeze subgraph, slide the [added] continuation beneath the outermost
 * frame built so far, between that frame and its caller, and make it the
 * outermost frame built so far.  At the start of the generated chunk's
 * [freeze][ExecutableChunk.freeze] method, the outermost frame built so far
 * is the continuation being frozen.
 *
 * The added continuation is the frame of a call that was inlined around the
 * continuation being frozen, built by an [L2_CREATE_L1_CONTINUATION].  The
 * freeze subgraph builds those frames innermost first, so each slides beneath
 * the one built before it, and the last slid is the outermost frame of the
 * chunk, whose caller is the continuation that the frozen one had as its
 * caller (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * It has a side effect, which keeps the slides in order.
 *
 * @property added
 *   The mutable continuation to slide beneath the outermost frame built so
 *   far.
 */
class L2_ADD_UNDER_FROZEN_FRAME
constructor(
	var added: L2ReadBoxedOperand
): L2Instruction()
{
	override val isCold get() = true

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(added.registerString())
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: continuation = addUnderFrozenFrame(continuation, added);
		loadFrozenContinuation()
		load(added)
		generateCall(addUnderFrozenFrameMethod)
		storeFrozenContinuation()
	}
}
