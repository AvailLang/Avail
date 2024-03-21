/*
 * L2_SUBTRACT_INT_MINUS_INT.kt
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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Subtract the subtrahend from the minuend, jumping to the specified target if
 * the result does not fit in an int.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_SUBTRACT_INT_MINUS_INT : L2ControlFlowOperation(
	READ_INT.named("minuend"),
	READ_INT.named("subtrahend"),
	WRITE_INT.named("difference", SUCCESS),
	PC.named("out of range", FAILURE),
	PC.named("in range", SUCCESS))
{
	// It jumps if the result doesn't fit in an int.
	override val hasSideEffect: Boolean get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val minuend = instruction.operand<L2ReadIntOperand>(0)
		val subtrahend = instruction.operand<L2ReadIntOperand>(1)
		val difference = instruction.operand<L2WriteIntOperand>(2)
		//		final L2PcOperand outOfRange = instruction.operand(3);
//		final L2PcOperand inRange = instruction.operand(4);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(difference.registerString())
		builder.append(" ← ")
		builder.append(minuend.registerString())
		builder.append(" - ")
		builder.append(subtrahend.registerString())
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val minuend = instruction.operand<L2ReadIntOperand>(0)
		val subtrahend = instruction.operand<L2ReadIntOperand>(1)
		val difference = instruction.operand<L2WriteIntOperand>(2)
		val outOfRange = instruction.operand<L2PcOperand>(3)
		val inRange = instruction.operand<L2PcOperand>(4)

		// :: longDifference = (long) minuend - (long) subtrahend;
		translator.load(method, minuend.register())
		method.visitInsn(Opcodes.I2L)
		translator.load(method, subtrahend.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LSUB)
		val longDifferenceStart = Label()
		val longDifferenceEnd = Label()
		val longDifferenceLocal = translator.nextLocal(Type.LONG_TYPE)
		method.visitLocalVariable(
			"longDifference",
			Type.LONG_TYPE.descriptor,
			null,
			longDifferenceStart,
			longDifferenceEnd,
			longDifferenceLocal)
		method.visitVarInsn(Opcodes.LSTORE, longDifferenceLocal)
		method.visitLabel(longDifferenceStart)
		// :: if (longDifference != intDifference) goto outOfRange;
		method.visitVarInsn(Opcodes.LLOAD, longDifferenceLocal)
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.I2L)
		method.visitVarInsn(Opcodes.LLOAD, longDifferenceLocal)
		method.visitInsn(Opcodes.LCMP)
		translator.jumpIf(method, Opcodes.IFNE, outOfRange)
		// :: else {
		// ::    sum = (int)longDifference;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.LLOAD, longDifferenceLocal)
		method.visitInsn(Opcodes.L2I)
		translator.store(method, difference.register())
		translator.jump(method, instruction, inRange)
		method.visitLabel(longDifferenceEnd)
		translator.endLocal(longDifferenceLocal, Type.LONG_TYPE)
	}
}
