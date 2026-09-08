/*
 * L2_TUPLE_AT_UPDATE.kt
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

import avail.descriptor.representation.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_BOXED
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Synonym
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue

/**
 * Given a tuple, an immediate index, and a new value to write, create the tuple
 * with that element replaced by the new value.  Destroy or recycle the original
 * if it's mutable.  Write the output to the specified output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_TUPLE_AT_UPDATE(
	var inputTuple: L2ReadBoxedOperand,
	var updateIndex: L2IntImmediateOperand,
	var newElement: L2ReadBoxedOperand,
	var outputTuple: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(outputTuple.registerString())
		append(" ← ")
		append(inputTuple.registerString())
		append(" [ ")
		append(updateIndex.value)
		append(" ] ::= ")
		append(newElement.registerString())
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		if (newElement.constantOrNull == null)
		{
			+this@L2_TUPLE_AT_UPDATE
			return
		}
		// The element has become a constant, perhaps due to code splitting or
		// inlining, so check if there's an original template tuple that can
		// have this element pre-populated.

		val updates = mutableListOf<Pair<Int, L2ReadBoxedOperand>>()
		var trace = inputTuple.definitionSkippingMoves(currentManifest)
		while (trace is L2_TUPLE_AT_UPDATE)
		{
			// Ignore updates at the same index, since the receiver would be
			// overwriting it anyhow.
			if (trace.updateIndex.value != updateIndex.value)
			{
				updates.add(trace.updateIndex.value to trace.newElement)
			}
			trace = trace.inputTuple.definitionSkippingMoves(currentManifest)
		}
		if (trace !is L2_MOVE_CONSTANT_BOXED)
		{
			+this@L2_TUPLE_AT_UPDATE
			return
		}
		// Update the template tuple and re-apply the updates that weren't
		// for that index.
		val tupleTemplate = trace.source.constant.tupleAtPuttingCanDestroy(
			updateIndex.value, newElement.constantOrNull!!, false)
		var tupleTemp = boxedConstant(tupleTemplate)
		val typesList =
			tupleTemplate.mapTo(mutableListOf(), ::instanceTypeOrMetaOn)
		// Generate the updates for the non-constant parts (if any).
		updates.forEach { (index, elementRead) ->
			typesList[index] = elementRead.type()
			val newWrite = boxedWriteTemp(
				"with update @$index",
				restrictionForType(tupleTypeForTypesList(typesList)))
			+L2_TUPLE_AT_UPDATE(
				tupleTemp,
				L2IntImmediateOperand(index),
				elementRead,
				newWrite)
			tupleTemp = readBoxed(newWrite)
		}
		// Finally, answer a move into the same semantic values that the
		// receiver was writing to.
		move(
			tupleTemp.semanticValue(),
			outputTuple.semanticValues())
	}

	override fun L2GeneratorInterface.extractTupleElement(
		synonym: L2Synonym,
		index: Int,
		destinationSemanticValues: Set<L2SemanticValue>
	): Unit = when (index)
	{
		// Use the value that was used to update that element.
		updateIndex.value -> move(
			newElement.semanticValue(),
			destinationSemanticValues)
		// It wasn't affected by this tuple update.
		else -> extractTupleElement(
			inputTuple, index, destinationSemanticValues)
	}

	override fun JVMTranslator.translateToJVM()
	{
		load(inputTuple)
		intConstant(updateIndex.value)
		load(newElement)
		generateCall(TupleDescriptor.tupleAtPuttingMethod)
		store(outputTuple.register())
	}
}
