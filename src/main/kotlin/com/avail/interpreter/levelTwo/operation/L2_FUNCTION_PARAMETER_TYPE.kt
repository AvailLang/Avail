/*
 * L2_FUNCTION_PARAMETER_TYPE.kt
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
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Given an input register containing a function (not a function type), extract
 * its Nth parameter type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_FUNCTION_PARAMETER_TYPE : L2Operation(
	L2OperandType.READ_BOXED.named("function"),
	L2OperandType.INT_IMMEDIATE.named("parameter index"),
	L2OperandType.WRITE_BOXED.named("parameter type"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val functionReg = instruction.operand<L2ReadBoxedOperand>(0)
		val paramIndex = instruction.operand<L2IntImmediateOperand>(1)
		val outputParamTypeReg = instruction.operand<L2WriteBoxedOperand>(2)

		// Function types are contravariant, so we may have to fall back on
		// just saying the parameter type must be a type and can't be top –
		// i.e., any's type.
		if (registerSet.hasConstantAt(functionReg.register()))
		{
			// Exact function is known.
			val function: A_Function =
				registerSet.constantAt(functionReg.register())
			val functionType = function.code().functionType()
			registerSet.constantAtPut(
				outputParamTypeReg.register(),
				functionType.argsTupleType().typeAtIndex(paramIndex.value),
				instruction)
			return
		}
		val sources =
			registerSet.stateForReading(functionReg.register())
				.sourceInstructions()
		if (sources.size == 1)
		{
			val source = sources[0]
			if (source.operation() === L2_CREATE_FUNCTION)
			{
				val code: A_RawFunction =
					L2_CREATE_FUNCTION.constantRawFunctionOf(source)
				val functionType = code.functionType()
				registerSet.constantAtPut(
					outputParamTypeReg.register(),
					functionType.argsTupleType().typeAtIndex(paramIndex.value),
					instruction)
				return
			}
		}
		// We don't know the exact type of the block argument, so since it's
		// contravariant we can only assume it's some non-top type.
		registerSet.typeAtPut(
			outputParamTypeReg.register(),
			InstanceMetaDescriptor.anyMeta(),
			instruction)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val parameterIndex = instruction.operand<L2IntImmediateOperand>(1)
		val parameterType = instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(parameterType.registerString())
		builder.append(" ← ")
		builder.append(function.registerString())
		builder.append('[')
		builder.append(parameterIndex.value)
		builder.append(']')
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val parameterIndex = instruction.operand<L2IntImmediateOperand>(1)
		val parameterType = instruction.operand<L2WriteBoxedOperand>(2)

		// :: paramType = function.code().functionType().argsTupleType()
		// ::    .typeAtIndex(param)
		translator.load(method, function.register())
		FunctionDescriptor.functionCodeMethod.generateCall(method)
		A_RawFunction.functionTypeMethod.generateCall(method)
		A_Type.argsTupleTypeMethod.generateCall(method)
		translator.literal(method, parameterIndex.value)
		A_Type.typeAtIndexMethod.generateCall(method)
		translator.store(method, parameterType.register())
	}
}
