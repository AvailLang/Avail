/*
 * L2_RESTART_CONTINUATION.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.A_Continuation
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Restart the given [continuation][A_Continuation], which already has the
 * correct program counter and level two offset (in case the [L2Chunk] is
 * still valid).  The function will start at the beginning, using the same
 * arguments that were captured within the continuation.
 *
 * This operation does the same thing as running [P_RestartContinuation], but
 * avoids the need for a reified calling continuation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_RESTART_CONTINUATION : L2ControlFlowOperation(
	READ_BOXED.named("continuation to restart"))
{
	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val continuation = instruction.operand<L2ReadBoxedOperand>(0)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(continuation.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val continuation = instruction.operand<L2ReadBoxedOperand>(0)

		// :: return interpreter.reifierToRestart(continuation);
		translator.loadInterpreter(method)
		translator.load(method, continuation.register())
		Interpreter.reifierToRestartMethod.generateCall(method)
		method.visitInsn(Opcodes.ARETURN)
	}
}
