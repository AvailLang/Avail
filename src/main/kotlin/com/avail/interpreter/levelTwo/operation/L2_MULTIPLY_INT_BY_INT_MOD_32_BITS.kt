/*
 * L2_MULTIPLY_INT_BY_INT_MOD_32_BITS.java
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

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Multiply the value in one int register by the value in another int register,
 * truncating it to the low 32 bits and storing it back in the second register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_MULTIPLY_INT_BY_INT_MOD_32_BITS : L2Operation(
	L2OperandType.READ_INT.`is`("multiplicand"),
	L2OperandType.READ_INT.`is`("multiplier"),
	L2OperandType.WRITE_INT.`is`("product"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val multiplicand = instruction.operand<L2ReadIntOperand>(0)
		val multiplier = instruction.operand<L2ReadIntOperand>(1)
		val product = instruction.operand<L2WriteIntOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(product.registerString())
		builder.append(" ← ")
		builder.append(multiplicand.registerString())
		builder.append(" × ")
		builder.append(multiplier.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val multiplicand = instruction.operand<L2ReadIntOperand>(0)
		val multiplier = instruction.operand<L2ReadIntOperand>(1)
		val product = instruction.operand<L2WriteIntOperand>(2)

		// :: product = multiplicand * multiplier;
		translator.load(method, multiplicand.register())
		translator.load(method, multiplier.register())
		method.visitInsn(Opcodes.IMUL)
		translator.store(method, product.register())
	}
}