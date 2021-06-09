/*
 * L2_DIVIDE_OBJECT_BY_OBJECT.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.numbers.A_Number
import com.avail.exceptions.ArithmeticException
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2OperandType.PC
import com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Divide the dividend value by the divisor value.  If the calculation causes an
 * [ArithmeticException], jump to the specified label, otherwise set the
 * quotient and remainder registers and continue with the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_DIVIDE_OBJECT_BY_OBJECT : L2ControlFlowOperation(
	READ_BOXED.named("dividend"),
	READ_BOXED.named("divisor"),
	WRITE_BOXED.named("quotient", Purpose.SUCCESS),
	WRITE_BOXED.named("remainder", Purpose.SUCCESS),
	PC.named("if undefined", Purpose.OFF_RAMP),
	PC.named("success", Purpose.SUCCESS))
{
	override fun hasSideEffect(): Boolean
	{
		// It jumps for division by zero.
		return true
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val dividend = instruction.operand<L2ReadBoxedOperand>(0)
		val divisor = instruction.operand<L2ReadBoxedOperand>(1)
		val quotient = instruction.operand<L2WriteBoxedOperand>(2)
		val remainder = instruction.operand<L2WriteBoxedOperand>(3)
		//		final L2PcOperand undefined = instruction.operand(4);
//		final L2PcOperand success = instruction.operand(5);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(quotient.registerString())
		builder.append(", ")
		builder.append(remainder.registerString())
		builder.append(" ← ")
		builder.append(dividend.registerString())
		builder.append(" ÷ ")
		builder.append(divisor.registerString())
		renderOperandsStartingAt(instruction, 4, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val dividend = instruction.operand<L2ReadBoxedOperand>(0)
		val divisor = instruction.operand<L2ReadBoxedOperand>(1)
		val quotient = instruction.operand<L2WriteBoxedOperand>(2)
		val remainder = instruction.operand<L2WriteBoxedOperand>(3)
		val undefined = instruction.operand<L2PcOperand>(4)
		val success = instruction.operand<L2PcOperand>(5)

		// :: try {
		val tryStart = Label()
		val catchStart = Label()
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(ArithmeticException::class.java))
		method.visitLabel(tryStart)
		// ::    quotient = dividend.divideCanDestroy(divisor, false);
		translator.load(method, dividend.register())
		method.visitInsn(Opcodes.DUP)
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.ICONST_0)
		A_Number.divideMethod.generateCall(method)
		method.visitInsn(Opcodes.DUP)
		translator.store(method, quotient.register())
		// ::    remainder = dividend.minusCanDestroy(
		// ::       quotient.timesCanDestroy(divisor, false),
		// ::       false);
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.ICONST_0)
		A_Number.timesCanDestroyMethod.generateCall(method)
		method.visitInsn(Opcodes.ICONST_0)
		A_Number.minusCanDestroyMethod.generateCall(method)
		translator.store(method, remainder.register())
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// ArithmeticException to be pushed onto the stack. So always do the
		// jump.
		translator.jump(method, success)
		// :: } catch (ArithmeticException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto undefined;
		translator.jump(method, instruction, undefined)
		// :: }
	}
}
