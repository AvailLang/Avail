/*
 * L2_CONCATENATE_TUPLES.kt
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

import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.TupleDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Synonym
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Concatenate the tuples in the vector of object registers to produce a single
 * tuple in an output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_CONCATENATE_TUPLES(
	var tuples: L2ReadBoxedVectorOperand,
	var concatenatedTuple: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(concatenatedTuple.registerString())
		append(" ← ")
		tuples.elements.joinTo(this, " ++ ") { it.registerString() }
	}

	override fun L2GeneratorInterface.extractTupleElement(
		synonym: L2Synonym,
		index: Int,
		destinationSemanticValues: Set<L2SemanticValue>)
	{
		// If we can tell (1) which subtuple we're getting the value from, and
		// (2) the index within that subtuple, then extract the value from the
		// subtuple instead of the concatenation.
		var residualIndex = index
		for (elementRead in tuples.elements)
		{
			assert(residualIndex >= 1)
			val sizeRange = elementRead.type()
			val lowerBound = sizeRange.lowerBound
			if (!lowerBound.isInt)
			{
				// Should be impossible, other than abnormal intermediate types.
				addUnreachableCode()
				return
			}
			val lowerBoundInt = lowerBound.extractInt
			if (residualIndex <= lowerBoundInt)
			{
				// It's definitely in this subtuple.
				extractTupleElement(
					elementRead, residualIndex, destinationSemanticValues)
				return
			}
			if (!lowerBound.equals(sizeRange.upperBound))
			{
				// This subtuple's size is not fixed, so don't look at any more
				// subtuples.
				break
			}
			residualIndex -= lowerBoundInt
		}
		// Fall back to the default tuple element extraction.
		+L2_TUPLE_AT_CONSTANT(
			readBoxed(synonym.pickSemanticValue()),
			L2IntImmediateOperand(index),
			boxedWrite(
				destinationSemanticValues,
				restrictionForType(
					concatenatedTuple.restriction().type.typeAtIndex(index))))
	}

	override fun JVMTranslator.translateToJVM()
	{
		val elements = tuples.elements
		val tupleCount = elements.size
		assert(tupleCount > 0)
		load(elements[0])
		for (i in 1 until tupleCount)
		{
			load(elements[i])
			generateCall(TupleDescriptor.concatenateTupleMethod)
		}
		// Strengthen the final result to AvailObject.
		method.visitTypeInsn(
			Opcodes.CHECKCAST,
			Type.getInternalName(AvailObject::class.java))
		store(concatenatedTuple.register())
	}
}
