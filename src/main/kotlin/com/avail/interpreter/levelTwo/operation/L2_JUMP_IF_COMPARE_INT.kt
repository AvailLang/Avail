/*
 * L2_JUMP_IF_COMPARE_INT.kt
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
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if int1 is less than int2.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property opcode
 *   The opcode that compares and branches to the success case.
 * @property opcodeName
 *   The symbolic name of the opcode that compares and branches to the success
 *   case.
 * @constructor
 * Construct an `L2_JUMP_IF_LESS_THAN_CONSTANT`.
 *
 * @param opcode
 *   The opcode number for this compare-and-branch.
 * @param opcodeName
 *   The symbolic name of the opcode for this compare-and-branch.
 */
class L2_JUMP_IF_COMPARE_INT private constructor(
		private val opcode: Int,
		private val opcodeName: String) :
	L2ConditionalJump(
		L2OperandType.READ_INT.named("int1"),
		L2OperandType.READ_INT.named("int2"),
		L2OperandType.PC.named("if true", L2NamedOperandType.Purpose.SUCCESS),
		L2OperandType.PC.named("if false", L2NamedOperandType.Purpose.FAILURE))
{

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		//		final L2PcOperand ifTrue = instruction.operand(2);
//		final L2PcOperand ifFalse = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(int1Reg.registerString())
		builder.append(" ")
		builder.append(opcodeName)
		builder.append(" ")
		builder.append(int2Reg.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun toString(): String
	{
		return super.toString() + "(" + opcodeName + ")"
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		// :: if (int1 op int2) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, int1Reg.register())
		translator.load(method, int2Reg.register())
		emitBranch(translator, method, instruction, opcode, ifTrue, ifFalse)
	}

	companion object
	{
		/** An instance for testing whether a < b.  */
		val less = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPLT, "<")

		/** An instance for testing whether a > b.  */
		val greater = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPGT, ">")

		/** An instance for testing whether a ≤ b.  */
		val lessOrEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPLE, "≤")

		/** An instance for testing whether a ≥ b.  */
		val greaterOrEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPGE, "≥")

		/** An instance for testing whether a = b.  */
		val equal = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPEQ, "=")

		/** An instance for testing whether a ≠ b.  */
		val notEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPNE, "≠")
	}

}
