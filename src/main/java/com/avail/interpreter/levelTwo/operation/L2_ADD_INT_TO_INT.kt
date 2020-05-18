/*
 * L2_ADD_INT_TO_INT.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.Consumer

/**
 * Add the value in one int register to another int register, jumping to the
 * specified target if the result does not fit in an int.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_ADD_INT_TO_INT : L2ControlFlowOperation(
	L2OperandType.READ_INT.`is`("augend"),
	L2OperandType.READ_INT.`is`("addend"),
	L2OperandType.WRITE_INT.`is`("sum", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("out of range", L2NamedOperandType.Purpose.FAILURE),
	L2OperandType.PC.`is`("in range", L2NamedOperandType.Purpose.SUCCESS))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		//		final L2ReadIntOperand augendReg = instruction.operand(0);
//		final L2ReadIntOperand addendReg = instruction.operand(1);
		val sumReg = instruction.operand<L2WriteIntOperand>(2)
		//		final L2PcOperand outOfRange = instruction.operand(3);
		val inRange = instruction.operand<L2PcOperand>(4)
		super.instructionWasAdded(instruction, manifest)
		inRange.manifest().intersectType(
			sumReg.pickSemanticValue(), IntegerRangeTypeDescriptor.int32())
	}

	// It jumps if the result doesn't fit in an int.
	override fun hasSideEffect(): Boolean = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val augend = instruction.operand<L2ReadIntOperand>(0)
		val addend = instruction.operand<L2ReadIntOperand>(1)
		val sum = instruction.operand<L2WriteIntOperand>(2)
		//		final L2PcOperand outOfRange = instruction.operand(3);
//		final L2PcOperand inRange = instruction.operand(4);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(sum.registerString())
		builder.append(" ← ")
		builder.append(augend.registerString())
		builder.append(" + ")
		builder.append(addend.registerString())
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val augendReg = instruction.operand<L2ReadIntOperand>(0)
		val addendReg = instruction.operand<L2ReadIntOperand>(1)
		val sumReg = instruction.operand<L2WriteIntOperand>(2)
		val outOfRange = instruction.operand<L2PcOperand>(3)
		val inRange = instruction.operand<L2PcOperand>(4)

		// :: longSum = (long) augend + (long) addend;
		translator.load(method, augendReg.register())
		method.visitInsn(Opcodes.I2L)
		translator.load(method, addendReg.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LADD)
		method.visitInsn(Opcodes.DUP2)
		// :: intSum = (int) longSum;
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.DUP)
		val intSumLocal = translator.nextLocal(Type.INT_TYPE)
		val intSumStart = Label()
		method.visitLabel(intSumStart)
		method.visitVarInsn(Opcodes.ISTORE, intSumLocal)
		// :: if (longSum != intSum) goto outOfRange;
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LCMP)
		method.visitJumpInsn(
			Opcodes.IFNE, translator.labelFor(outOfRange.offset()))
		// :: else {
		// ::    sum = intSum;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.ILOAD, intSumLocal)
		val intSumEnd = Label()
		method.visitLabel(intSumEnd)
		method.visitLocalVariable(
			"intSum",
			Type.INT_TYPE.descriptor,
			null,
			intSumStart,
			intSumEnd,
			intSumLocal)
		translator.store(method, sumReg.register())
		translator.jump(method, instruction, inRange)
	}
}