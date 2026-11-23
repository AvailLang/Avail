/*
 * L2_INLINED_FALL_BACK_TO_L1.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.inlinedFallBackToL1Method
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Inliner
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

/**
 * Fall back to the L1 [DefaultL1Chunk] for a frame embedded by [L2Inliner]
 * in the running chunk's frame, continuing the embedded frame with the data
 * provided for reconstituting it, through [Interpreter.inlinedFallBackToL1].
 *
 * Unlike [L2_FALL_BACK_TO_L1], which the embedded frame's own chunk uses, the
 * running chunk does not simply return what the L1 code answers, since its own
 * frame has more to do after the embedded one:
 * * If the embedded frame returns, the [returnedValue] is written, and control
 *   continues along [onReturn], where the embedded frame's returns lead.
 * * If the L1 code starts reifying, it has already stacked the actions that
 *   build the embedded frame and those it called, so control continues along
 *   [onReification] to build the running chunk's own frame, as a call from
 *   that frame that reified would.
 *
 * As in [L2_FALL_BACK_TO_L1], the continuation created for the embedded frame
 * has the interpreter's reified continuation as its caller, which only
 * matters if it reifies, and then its caller is replaced as the running
 * chunk's frame is built.
 *
 * @property function
 *   The function of the embedded frame.
 * @property pc
 *   The L1 program counter at which the embedded frame continues.
 * @property stackp
 *   The L1 stack pointer at which the embedded frame continues.
 * @property frameValues
 *   The values of the embedded frame's slots.
 * @property returnedValue
 *   Where to write the value the embedded frame returns.
 * @property onReturn
 *   Where to continue after the embedded frame returns.
 * @property onReification
 *   Where to continue if the embedded frame's L1 code starts reifying.
 */
@ReadsHiddenVariable(
	CURRENT_CONTINUATION::class,
	CURRENT_FUNCTION::class,
	LATEST_RETURN_VALUE::class,
	STACK_REIFIER::class)
@WritesHiddenVariable(
	CURRENT_CONTINUATION::class,
	CURRENT_FUNCTION::class,
	LATEST_RETURN_VALUE::class,
	STACK_REIFIER::class)
class L2_INLINED_FALL_BACK_TO_L1(
	var function: L2ReadBoxedOperand,
	var pc: L2IntImmediateOperand,
	var stackp: L2IntImmediateOperand,
	var frameValues: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var returnedValue: L2WriteBoxedOperand,
	@On(SUCCESS) var onReturn: L2PcOperand,
	@On(OFF_RAMP) var onReification: L2PcOperand
) : L2ControlFlowInstruction()
{
	override val isCold: Boolean get() = true

	// Never remove this.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(returnedValue.registerString())
		append(" ← function: ${function.registerString()},")
		append(" pc: ${pc.value}, stackp: ${stackp.value},")
		append("\n\tframe data:")
		frameValues.elements.joinTo(this) { "\n\t\t" + it.registerString() }
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::function,
			::pc,
			::stackp,
			::frameValues,
			::returnedValue)
	}

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: interpreter
		load(function)
		// :: interpreter, function
		intConstant(pc.intValue)
		// :: interpreter, function, pc
		intConstant(stackp.intValue)
		// :: interpreter, function, pc, stackp
		objectArray(
			frameValues.elements,
			A_BasicObject::class.java)
		// :: interpreter, function, pc, stackp, frameValues
		generateCall(inlinedFallBackToL1Method)
		// :: valueOrNull
		method.visitInsn(Opcodes.DUP)
		// :: valueOrNull, valueOrNull
		val isReification = Label()
		method.visitJumpInsn(Opcodes.IFNULL, isReification)
		// :: value
		store(returnedValue.register())
		// ::
		jump(onReturn)

		method.visitLabel(isReification)
		// :: null
		method.visitInsn(Opcodes.POP)
		// ::
		generateReificationPreamble(onReification)
	}
}
