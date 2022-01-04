/*
 * L2_GET_OBJECT_FIELD.kt
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

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Extract the specified field of the object.
 *
 * TODO - Eventually we should generate code to collect stats on which variants
 * occur, then at reoptimization time inline tests for the likely ones, and use
 * the field indices directly for those variants.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_GET_OBJECT_FIELD : L2Operation(
	READ_BOXED.named("object"),
	CONSTANT.named("field atom"),
	WRITE_BOXED.named("field value"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val objectRead = instruction.operand<L2ReadBoxedOperand>(0)
		val fieldAtom = instruction.operand<L2ConstantOperand>(1)
		val fieldValue = instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(fieldValue.registerString())
		builder.append(" ← ")
		builder.append(objectRead)
		builder.append("[")
		builder.append(fieldAtom)
		builder.append("]")
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		assert(this == instruction.operation)
		val objectRead = instruction.operand<L2ReadBoxedOperand>(0)
		val fieldAtom = instruction.operand<L2ConstantOperand>(1)
		val fieldValue = instruction.operand<L2WriteBoxedOperand>(2)

		translator.load(method, objectRead.register())
		val variants = objectRead.restriction().positiveGroup.objectVariants
		if (variants.notNullAnd { map { it.variantId }.toSet().size == 1 })
		{
			// The field index is the same for every variant possible at this
			// point.  Get the field by index.
			val variant = variants!!.first()
			val fieldIndex = variant.fieldToSlotIndex[fieldAtom.constant]!!
			translator.intConstant(method, fieldIndex)
			AvailObject.fieldAtIndexMethod.generateCall(method)
		}
		else
		{
			translator.literal(method, fieldAtom.constant)
			AvailObject.fieldAtMethod.generateCall(method)
		}
		translator.store(method, fieldValue.register())
	}
}
