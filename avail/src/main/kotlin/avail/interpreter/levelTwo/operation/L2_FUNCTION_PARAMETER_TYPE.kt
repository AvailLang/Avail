/*
 * L2_FUNCTION_PARAMETER_TYPE.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.types.A_Type
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Given an input register containing a function (not a function type), extract
 * its Nth parameter type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_FUNCTION_PARAMETER_TYPE(
	var function: L2ReadBoxedOperand,
	var parameterIndex: L2IntImmediateOperand,
	var parameterType: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(parameterType.registerString())
		append(" ← ")
		append(function.registerString())
		append('[')
		append(parameterIndex.value)
		append(']')
	}

	/**
	 * A function's type is stored in its raw function, which is always shared,
	 * so the function type and its parameter types are therefore always shared.
	 */
	override fun propagateMutability(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>)
	{
		// The produced parameterType is always shared, and therefore immutable.
		return
	}

	/**
	 * Since the function's type is always shared, there's no need to treat this
	 * parameter type extraction as a potential destruction of the function.
	 */
	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: paramType = function.code().functionType().argsTupleType()
		// ::    .typeAtIndex(param)
		translator.load(method, function)
		FunctionDescriptor.functionCodeMethod.generateCall(method)
		A_RawFunction.functionTypeMethod.generateCall(method)
		A_Type.argsTupleTypeMethod.generateCall(method)
		translator.intConstant(method, parameterIndex.value)
		A_Type.typeAtIndexMethod.generateCall(method)
		translator.store(method, parameterType.register())
	}
}
