/*
 * L2_CREATE_OBJECT.kt
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

import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.ARBITRARY_CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import avail.utility.cast
import org.objectweb.asm.MethodVisitor

/**
 * Create an object using a constant pojo holding an [ObjectLayoutVariant]
 * and a vector of values, in the order the variant lays them out as fields.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_OBJECT : L2Operation(
	ARBITRARY_CONSTANT.named("variant"),
	READ_BOXED_VECTOR.named("field values"),
	WRITE_BOXED.named("new object"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit)
	{
		val variantOperand = instruction.operand<L2ArbitraryConstantOperand>(0)
		val fieldsVector = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val newObject = instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(newObject.registerString())
		builder.append(" ← {")
		val variant: ObjectLayoutVariant = variantOperand.constant.cast()
		val realSlots = variant.realSlots
		val fieldSources = fieldsVector.elements
		assert(realSlots.size == fieldSources.size)
		var i = 0
		realSlots.joinTo(builder, ",")
		{ key -> "$key: ${fieldSources[i++].registerString()}" }
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val variantOperand = instruction.operand<L2ArbitraryConstantOperand>(0)
		val fieldsVector = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val newObject = instruction.operand<L2WriteBoxedOperand>(2)
		val variant: ObjectLayoutVariant = variantOperand.constant.cast()
		translator.literal(method, variant)
		ObjectDescriptor.createUninitializedObjectMethod.generateCall(method)
		val fieldSources = fieldsVector.elements
		val limit = fieldSources.size
		for (i in 0 until limit)
		{
			translator.intConstant(method, i + 1)
			translator.load(method, fieldSources[i].register())
			ObjectDescriptor.setFieldMethod.generateCall(method) // Returns object for chaining.
		}
		translator.store(method, newObject.register())
	}
}
