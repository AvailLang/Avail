/*
 * L2_FLOAT_TO_INT.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.READ_FLOAT
import avail.interpreter.levelTwo.L2OperandType.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Convert a `double` to an [Int].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_FLOAT_TO_INT : L2Operation(
	READ_FLOAT.named("source"),
	WRITE_INT.named("destination"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val source = instruction.operand<L2ReadFloatOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		assert(this == instruction.operation)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destination.registerString())
		builder.append(" ← ")
		builder.append(source.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source = instruction.operand<L2ReadFloatOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)

		// :: destination = (int) source;
		translator.load(method, source.register())
		method.visitInsn(Opcodes.D2I)
		translator.store(method, destination.register())
	}
}
