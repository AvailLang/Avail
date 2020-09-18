/*
 * L2_CREATE_OBJECT.kt
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

import com.avail.descriptor.objects.ObjectDescriptor
import com.avail.descriptor.objects.ObjectLayoutVariant
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Create an object using a constant pojo holding an [ObjectLayoutVariant]
 * and a vector of values, in the order the variant lays them out as fields.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_OBJECT : L2Operation(
	L2OperandType.CONSTANT.named("variant pojo"),
	L2OperandType.READ_BOXED_VECTOR.named("field values"),
	L2OperandType.WRITE_BOXED.named("new object"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val variantOperand =
			instruction.operand<L2ConstantOperand>(0)
		val fieldsVector =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val `object` =
			instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(`object`.registerString())
		builder.append(" ← {")
		val variant =
			variantOperand.constant.javaObjectNotNull<ObjectLayoutVariant>()
		val realSlots = variant.realSlots
		val fieldSources = fieldsVector.elements()
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
		assert(this == instruction.operation())
		val variantOperand =
			instruction.operand<L2ConstantOperand>(0)
		val fieldsVector =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val `object` =
			instruction.operand<L2WriteBoxedOperand>(2)
		val variant =
			variantOperand.constant.javaObjectNotNull<ObjectLayoutVariant>()
		method.visitLdcInsn(variant)
		ObjectDescriptor.createUninitializedObjectMethod.generateCall(method)
		val fieldSources = fieldsVector.elements()
		val limit = fieldSources.size
		for (i in 0 until limit)
		{
			method.visitLdcInsn(i + 1)
			translator.load(method, fieldSources[i].register())
			ObjectDescriptor.setFieldMethod.generateCall(method) // Returns object for chaining.
		}
		translator.store(method, `object`.register())
	}
}
