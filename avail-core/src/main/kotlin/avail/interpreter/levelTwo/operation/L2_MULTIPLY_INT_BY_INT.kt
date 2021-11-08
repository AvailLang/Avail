/*
 * L2_MULTIPLY_INT_BY_INT.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Multiply the value in one int register by the value in another int register,
 * storing back in the second if the result fits in an int without overflow.
 * Otherwise jump to the specified target.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_MULTIPLY_INT_BY_INT : L2ControlFlowOperation(
	L2OperandType.READ_INT.named("multiplicand"),
	L2OperandType.READ_INT.named("multiplier"),
	L2OperandType.WRITE_INT.named("product", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("out of range", L2NamedOperandType.Purpose.FAILURE),
	L2OperandType.PC.named("in range", L2NamedOperandType.Purpose.SUCCESS))
{
	// It jumps if the result doesn't fit in an int.
	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val multiplicandReg = instruction.operand<L2ReadIntOperand>(0)
		val multiplierReg = instruction.operand<L2ReadIntOperand>(1)
		val productReg = instruction.operand<L2WriteIntOperand>(2)
		//val outOfRange = instruction.operand<L2WriteIntOperand>(3)
		//val inRange = instruction.operand<L2WriteIntOperand>(4)

		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(productReg.registerString())
		builder.append(" ← ")
		builder.append(multiplicandReg.registerString())
		builder.append(" × ")
		builder.append(multiplierReg.registerString())
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val multiplicandReg = instruction.operand<L2ReadIntOperand>(0)
		val multiplierReg = instruction.operand<L2ReadIntOperand>(1)
		val productReg = instruction.operand<L2WriteIntOperand>(2)
		val outOfRange = instruction.operand<L2PcOperand>(3)
		val inRange = instruction.operand<L2PcOperand>(4)

		// :: longProduct = (long) multiplicand * (long) multiplier;
		translator.load(method, multiplicandReg.register())
		method.visitInsn(Opcodes.I2L)
		translator.load(method, multiplierReg.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LMUL)
		method.visitInsn(Opcodes.DUP2)
		// :: intProduct = (int) longProduct;
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.DUP)
		val intProductLocal = translator.nextLocal(Type.INT_TYPE)
		val intProductStart = Label()
		method.visitLabel(intProductStart)
		method.visitVarInsn(Opcodes.ISTORE, intProductLocal)
		// :: if (longProduct != intProduct) goto outOfRange;
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LCMP)
		method.visitJumpInsn(
			Opcodes.IFNE, translator.labelFor(outOfRange.offset()))
		// :: else {
		// ::    product = intProduct;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.ILOAD, intProductLocal)
		val intProductEnd = Label()
		method.visitLabel(intProductEnd)
		method.visitLocalVariable(
			"intProduct",
			Type.INT_TYPE.descriptor,
			null,
			intProductStart,
			intProductEnd,
			intProductLocal)
		translator.store(method, productReg.register())
		translator.jump(method, instruction, inRange)
	}
}
