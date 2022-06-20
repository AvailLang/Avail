/*
 * L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.AbstractNumberDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.PC
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the object is numerically greater than or equal to the
 * constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT : L2ConditionalJump(
	READ_BOXED.named("value"),
	CONSTANT.named("constant"),
	PC.named("if greater or equal", SUCCESS),
	PC.named("if less", FAILURE))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifGreaterOrEqual = instruction.operand(2);
//		final L2PcOperand ifNotGreaterOrEqual = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ≥ ")
		builder.append(constant.constant)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifGreaterOrEqual = instruction.operand<L2PcOperand>(2)
		val ifNotGreaterOrEqual = instruction.operand<L2PcOperand>(3)

		// :: comparison = first.numericCompare(second);
		translator.load(method, value.register())
		translator.literal(method, constant.constant)
		A_Number.numericCompareMethod.generateCall(method)
		// :: if (comparison.isMoreOrEqual()) goto ifTrue;
		// :: else goto ifFalse;
		AbstractNumberDescriptor.Order.isMoreOrEqualMethod.generateCall(method)
		emitBranch(
			translator,
			method,
			instruction,
			Opcodes.IFNE,
			ifGreaterOrEqual,
			ifNotGreaterOrEqual)
	}
}
