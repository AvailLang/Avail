/*
 * L2_CONCATENATE_TUPLES.kt
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

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.TupleDescriptor.Companion.concatenateTupleMethod
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.ConcatenatedTupleTypeDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Concatenate the tuples in the vector of object registers to produce a single
 * tuple in an output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CONCATENATE_TUPLES : L2Operation(
	L2OperandType.READ_BOXED_VECTOR.named("tuples to concatenate"),
	L2OperandType.WRITE_BOXED.named("concatenated tuple"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		// Approximate it for now.  If testing the return type dynamically
		// becomes a bottleneck, we can improve this bound.
		val tuples = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val output = instruction.operand<L2WriteBoxedOperand>(1)
		if (tuples.elements().isEmpty())
		{
			registerSet.constantAtPut(
				output.register(),
				emptyTuple,
				instruction)
			return
		}
		var index = tuples.elements().size - 1
		var resultType: A_Type = tuples.elements()[index].type()
		while (--index >= 0)
		{
			resultType = ConcatenatedTupleTypeDescriptor.concatenatingAnd(
				tuples.elements()[index].type(), resultType)
		}
		registerSet.constantAtPut(
			output.register(), resultType, instruction)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val tuples = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val output = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(output.registerString())
		builder.append(" ← ")
		var i = 0
		val limit = tuples.elements().size
		while (i < limit)
		{
			if (i > 0)
			{
				builder.append(" ++ ")
			}
			val element = tuples.elements()[i]
			builder.append(element.registerString())
			i++
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val tuples = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val output = instruction.operand<L2WriteBoxedOperand>(1)
		val elements = tuples.elements()
		val tupleCount = elements.size
		if (tupleCount == 0)
		{
			translator.literal(method, emptyTuple)
		}
		else
		{
			translator.load(method, elements[0].register())
			for (i in 1 until tupleCount)
			{
				translator.load(method, elements[i].register())
				translator.intConstant(method, 1) // canDestroy = true
				concatenateTupleMethod.generateCall(method)
			}
			// Strengthen the final result to AvailObject.
			method.visitTypeInsn(
				Opcodes.CHECKCAST,
				Type.getInternalName(AvailObject::class.java))
		}
		translator.store(method, output.register())
	}
}
