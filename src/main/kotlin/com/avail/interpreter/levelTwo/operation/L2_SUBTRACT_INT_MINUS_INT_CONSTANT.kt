/*
 * L2_SUBTRACT_INT_MINUS_INT_CONSTANT.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Extract an int from the specified constant, and subtract it from an int
 * register, jumping to the target label if the result won't fit in an int.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_SUBTRACT_INT_MINUS_INT_CONSTANT : L2ControlFlowOperation(
	L2OperandType.READ_INT.named("minuend"),
	L2OperandType.INT_IMMEDIATE.named("subtrahend"),
	L2OperandType.WRITE_INT.named("difference", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("in range", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("out of range", L2NamedOperandType.Purpose.FAILURE))
{
	override fun hasSideEffect() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val minuend = instruction.operand<L2ReadIntOperand>(0)
		val subtrahend = instruction.operand<L2IntImmediateOperand>(1)
		val difference = instruction.operand<L2WriteIntOperand>(2)
		//		final L2PcOperand inRange = instruction.operand(3);
//		final L2PcOperand outOfRange = instruction.operand(4);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(difference.registerString())
		builder.append(" ← ")
		builder.append(minuend.registerString())
		builder.append(" - ")
		builder.append(subtrahend.value)
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val minuend = instruction.operand<L2ReadIntOperand>(0)
		val subtrahend = instruction.operand<L2IntImmediateOperand>(1)
		val difference = instruction.operand<L2WriteIntOperand>(2)
		val inRange = instruction.operand<L2PcOperand>(3)
		val outOfRange = instruction.operand<L2PcOperand>(4)

		// :: longDifference = (long) minuend - (long) subtrahend;
		translator.load(method, minuend.register())
		method.visitInsn(Opcodes.I2L)
		translator.literal(method, subtrahend.value)
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LSUB)
		method.visitInsn(Opcodes.DUP2)
		// :: intDifference = (int) longDifference;
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.DUP)
		val intDifferenceLocal = translator.nextLocal(Type.INT_TYPE)
		val intDifferenceStart = Label()
		method.visitLabel(intDifferenceStart)
		method.visitVarInsn(Opcodes.ISTORE, intDifferenceLocal)
		// :: if (longDifference != intDifference) goto outOfRange;
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LCMP)
		method.visitJumpInsn(Opcodes.IFNE, translator.labelFor(outOfRange.offset()))
		// :: else {
		// ::    sum = intDifference;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.ILOAD, intDifferenceLocal)
		val intDifferenceEnd = Label()
		method.visitLabel(intDifferenceEnd)
		method.visitLocalVariable(
			"intDifference",
			Type.INT_TYPE.descriptor,
			null,
			intDifferenceStart,
			intDifferenceEnd,
			intDifferenceLocal)
		translator.store(method, difference.register())
		translator.jump(method, instruction, inRange)
	}
}
