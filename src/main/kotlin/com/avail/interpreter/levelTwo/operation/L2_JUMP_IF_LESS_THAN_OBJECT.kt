/*
 * L2_JUMP_IF_LESS_THAN_OBJECT.kt
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

import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.AbstractNumberDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Jump to the target if the first value is numerically less than the second
 * value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_LESS_THAN_OBJECT : L2ConditionalJump(
	L2OperandType.READ_BOXED.`is`("first value"),
	L2OperandType.READ_BOXED.`is`("second value"),
	L2OperandType.PC.`is`("if less", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("if greater or equal", L2NamedOperandType.Purpose.FAILURE))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		//		final L2PcOperand ifLess = instruction.operand(2);
//		final L2PcOperand ifNotLess = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(first.registerString())
		builder.append(" < ")
		builder.append(second.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		val ifLess = instruction.operand<L2PcOperand>(2)
		val ifNotLess = instruction.operand<L2PcOperand>(3)

		// :: comparison = first.numericCompare(second);
		translator.load(method, first.register())
		translator.load(method, second.register())
		A_Number.numericCompareMethod.generateCall(method)
		// :: if (comparison.isLess()) goto ifTrue;
		// :: else goto ifFalse;
		AbstractNumberDescriptor.Order.isLessMethod.generateCall(method)
		emitBranch(translator, method, instruction, Opcodes.IFNE, ifLess, ifNotLess)
	}
}