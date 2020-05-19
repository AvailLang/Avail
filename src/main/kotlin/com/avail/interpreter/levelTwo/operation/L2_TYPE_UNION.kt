/*
 * L2_TYPE_UNION.java
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

import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import java.util.function.Consumer

/**
 * Given two input types in registers, compute their union and write it to the
 * output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_TYPE_UNION : L2Operation(
	L2OperandType.READ_BOXED.named("first type"),
	L2OperandType.READ_BOXED.named("second type"),
	L2OperandType.WRITE_BOXED.named("union type"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val firstType = instruction.operand<L2ReadBoxedOperand>(0)
		val secondType = instruction.operand<L2ReadBoxedOperand>(1)
		val outputType = instruction.operand<L2WriteBoxedOperand>(2)
		val firstMeta = registerSet.typeAt(firstType.register())
		val secondMeta = registerSet.typeAt(secondType.register())
		val unionMeta = firstMeta.typeUnion(secondMeta)
		registerSet.typeAtPut(
			outputType.register(), unionMeta, instruction)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val firstType = instruction.operand<L2ReadBoxedOperand>(0)
		val secondType = instruction.operand<L2ReadBoxedOperand>(1)
		val outputType = instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(outputType.registerString())
		builder.append(" ← ")
		builder.append(firstType.registerString())
		builder.append(" ∪ ")
		builder.append(secondType.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val firstType = instruction.operand<L2ReadBoxedOperand>(0)
		val secondType = instruction.operand<L2ReadBoxedOperand>(1)
		val outputType = instruction.operand<L2WriteBoxedOperand>(2)

		// :: unionType = firstInputType.typeUnion(secondInputType);
		translator.load(method, firstType.register())
		translator.load(method, secondType.register())
		A_Type.typeUnionMethod.generateCall(method)
		translator.store(method, outputType.register())
	}
}