/*
 * L2_MOVE_OUTER_VARIABLE.kt
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION
import com.avail.interpreter.levelTwo.ReadsHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Extract a captured "outer" variable from a function.  If the outer
 * variable is an actual [variable][VariableDescriptor] then the
 * variable itself is what gets moved into the destination register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(theValue = [CURRENT_FUNCTION::class])
object L2_MOVE_OUTER_VARIABLE : L2Operation(
	L2OperandType.INT_IMMEDIATE.named("outer index"),
	L2OperandType.READ_BOXED.named("function"),
	L2OperandType.WRITE_BOXED.named("destination"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val outerIndex =
			instruction.operand<L2IntImmediateOperand>(0)
		val function =
			instruction.operand<L2ReadBoxedOperand>(1)
		val destination =
			instruction.operand<L2WriteBoxedOperand>(2)
		if (registerSet.hasConstantAt(function.register()))
		{
			// The exact function is known.
			val fn: A_Function = registerSet.constantAt(function.register())
			val value = fn.outerVarAt(outerIndex.value)
			registerSet.constantAtPut(
				destination.register(), value, instruction)
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val outerIndex =
			instruction.operand<L2IntImmediateOperand>(0)
		val function =
			instruction.operand<L2ReadBoxedOperand>(1)
		val destination =
			instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destination.registerString())
		builder.append(" ← ")
		builder.append(function.registerString())
		builder.append('[')
		builder.append(outerIndex.value)
		builder.append(']')
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val outerIndex = instruction.operand<L2IntImmediateOperand>(0)
		val function = instruction.operand<L2ReadBoxedOperand>(1)
		val destination = instruction.operand<L2WriteBoxedOperand>(2)

		// :: destination = function.outerVarAt(outerIndex);
		translator.load(method, function.register())
		translator.literal(method, outerIndex.value)
		FunctionDescriptor.outerVarAtMethod.generateCall(method)
		translator.store(method, destination.register())
	}
}
