/*
 * L2_TUPLE_AT_UPDATE.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.tuples.TupleDescriptor.Companion.tupleAtPuttingMethod
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.new.L2NewInstruction
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_MOVE.L2_MOVE_BOXED
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

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
): L2NewInstruction()
{
	override fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(outputTuple.registerString())
		builder.append(" ← ")
		builder.append(inputTuple.registerString())
		builder.append(" [ ")
		builder.append(updateIndex.value)
		builder.append(" ] ::= ")
		builder.append(newElement.registerString())
	}

	override fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
		index: Int,
		write: L2WriteBoxedOperand,
		generator: L2Generator)
	{
		if (index == updateIndex.value)
		{
			// Use the value that was used to update that element.
			generator.addInstruction(L2_MOVE_BOXED(newElement, write))
		}
		else
		{
			// It wasn't affected by this tuple update.
			generator.extractTupleElement(inputTuple, index, write)
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		translator.load(method, inputTuple.register())
		translator.intConstant(method, updateIndex.value)
		translator.load(method, newElement.register())
		tupleAtPuttingMethod.generateCall(method)
		translator.store(method, outputTuple.register())
	}
}
