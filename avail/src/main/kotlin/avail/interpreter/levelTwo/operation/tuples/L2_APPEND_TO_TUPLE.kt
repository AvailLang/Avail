/*
 * L2_APPEND_TO_TUPLE.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation.tuples

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticBoxedValue
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Append an element to a tuple, producing a longer tuple.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_APPEND_TO_TUPLE(
	var inputTuple: L2ReadBoxedOperand,
	var elementToAppend: L2ReadBoxedOperand,
	var outputTuple: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(outputTuple.registerString())
		append(" ← ")
		append(inputTuple.registerString())
		append(" + ")
		append(elementToAppend.registerString())
	}

	override fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>,
		generator: L2Generator)
	{
		// If the index is between 1 and the lower bound of the inputTuple's
		// size, we can just extract the element from the inputTuple.  If the
		// inputTuple is fixed size and the index is just beyond the end, just
		// use the elementToAppend.
		val sizeRange = inputTuple.type()
		val lowerBound = sizeRange.lowerBound
		if (lowerBound.isInt)
		{
			val lowerBoundInt = lowerBound.extractInt
			if (index <= lowerBoundInt)
			{
				// It's definitely in the inputTuple.
				generator.extractTupleElement(
					inputTuple, index, destinationSemanticValues)
				return
			}
			if (lowerBound.equals(sizeRange.upperBound)
				&& index == lowerBoundInt + 1)
			{
				// It's definitely the elementToAppend.
				generator.moveBoxedRegister(
					elementToAppend.semanticValue(),
					destinationSemanticValues)
				return
			}
		}
		// Fall back to the default tuple element extraction.
		super.extractTupleElement(
			tupleRead, index, destinationSemanticValues, generator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		translator.load(method, inputTuple.register())
		translator.load(method, elementToAppend.register())
		TupleDescriptor.appendToTupleMethod.generateCall(method)
		// Strengthen the final result to AvailObject.
		method.visitTypeInsn(
			Opcodes.CHECKCAST,
			Type.getInternalName(AvailObject::class.java))
		translator.store(method, outputTuple.register())
	}
}
