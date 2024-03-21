/*
 * L2_CONCATENATE_TUPLES.kt
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

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.TupleDescriptor.Companion.concatenateTupleMethod
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
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
	READ_BOXED_VECTOR.named("tuples to concatenate"),
	WRITE_BOXED.named("concatenated tuple"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit)
	{
		val tuples = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val output = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(output.registerString())
		builder.append(" ← ")
		tuples.elements.joinTo(builder, " ++ ") { it.registerString() }
	}

	override fun extractTupleElement(
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand
	{
		// If we can tell (1) which subtuple we're getting the value from, and
		// (2) the index within that subtuple, then extract the value from the
		// subtuple instead of the concatenation.
		val instruction = tupleReg.definition().instruction
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		// val tuple = instruction.operand<L2WriteBoxedOperand>(1)

		var residualIndex = index
		for (elementRead in values.elements)
		{
			assert(residualIndex >= 1)
			val sizeRange = elementRead.type()
			val lowerBound = sizeRange.lowerBound
			if (!lowerBound.isInt)
			{
				// Should be impossible, other than abnormal intermediate types.
				break
			}
			val lowerBoundInt = lowerBound.extractInt
			if (residualIndex <= lowerBoundInt)
			{
				// It's definitely in this subtuple.
				return generator.extractTupleElement(elementRead, residualIndex)
			}
			if (!lowerBound.equals(sizeRange.upperBound))
			{
				// This subtuple's size is not fixed, so don't look at any more
				// subtuples.
				break
			}
			residualIndex -= lowerBoundInt
		}
		// It fell back, so do the default tuple element extraction.
		return super.extractTupleElement(tupleReg, index, generator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val tuples = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val output = instruction.operand<L2WriteBoxedOperand>(1)
		val elements = tuples.elements
		val tupleCount = elements.size
		assert(tupleCount > 0)
		translator.load(method, elements[0].register())
		for (i in 1 until tupleCount)
		{
			translator.load(method, elements[i].register())
			concatenateTupleMethod.generateCall(method)
		}
		// Strengthen the final result to AvailObject.
		method.visitTypeInsn(
			Opcodes.CHECKCAST,
			Type.getInternalName(AvailObject::class.java))
		translator.store(method, output.register())
	}
}
