/*
 * L2_CREATE_L1_CONTINUATION.kt
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

import avail.descriptor.functions.ContinuationDescriptor.Companion.createL1CallContinuationExceptFrameMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.jvm.JVMTranslator
import avail.utility.isNullOr

/**
 * Create a continuation for a frame stopped at a call, that resumes only in
 * the [DefaultL1Chunk], at
 * [DefaultEntryPoint.REENTRY_FROM_REIFIED_CALL].  It has no register dump and
 * no caller.  This is the continuation the L1 interpreter would have built
 * when reifying that frame.
 *
 * A freeze subgraph uses it to build the frame of a call that was inlined
 * around the continuation being frozen, and an [L2_ADD_UNDER_FROZEN_FRAME]
 * then slides it beneath the frames built so far, which gives it its caller.
 * Reading each slot value forces any postponed creation of the variable it
 * holds, so the variable exists by the time the frame does.
 *
 * @property function
 *   The function whose frame the continuation represents.
 * @property levelOnePc
 *   The L1 program counter just after the call.
 * @property levelOneStackp
 *   The L1 stack pointer, which indexes the slot holding the call's expected
 *   type.
 * @property slotValues
 *   The values of the continuation's frame slots, with the call's expected
 *   type at [levelOneStackp].
 * @property destination
 *   Where to write the new continuation.
 */
class L2_CREATE_L1_CONTINUATION
constructor(
	var function: L2ReadBoxedOperand,
	var levelOnePc: L2IntImmediateOperand,
	var levelOneStackp: L2IntImmediateOperand,
	var slotValues: L2ReadBoxedVectorOperand,
	var destination: L2WriteBoxedOperand
): L2Instruction()
{
	override val isCold get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ← L1 frame of ")
		append(function.registerString())
		append(" at pc=")
		append(levelOnePc.intValue)
		append(", stackp=")
		append(levelOneStackp.intValue)
		slotValues.elements.forEachIndexed { zeroIndex, read ->
			append("\n\tslot#${zeroIndex + 1} ← ")
			append(read.registerString())
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: continuation = createL1CallContinuationExceptFrame(
		// ::    function, levelOnePc, levelOneStackp);
		load(function)
		intConstant(levelOnePc.intValue)
		intConstant(levelOneStackp.intValue)
		generateCall(createL1CallContinuationExceptFrameMethod)
		// Skip slots that are always nil, since the continuation was already
		// initialized with nils.
		// :: [continuation]
		frameAtPutAll(
			slotValues.elements
				.mapIndexed { zeroIndex, read -> zeroIndex + 1 to read }
				.filter { (_, read) ->
					read.register().constant.isNullOr { notNil }
				})
		// :: []
		store(destination.register())
	}
}
