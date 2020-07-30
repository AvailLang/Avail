/*
 * L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.kt
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

import com.avail.AvailRuntime
import com.avail.AvailRuntime.HookType
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Store the current [A_Function] from the [HookType.READ_UNASSIGNED_VARIABLE]
 * into the supplied [object&#32;register][L2BoxedRegister].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION : L2Operation(
	L2OperandType.WRITE_BOXED.named("unassigned variable read function"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val function = instruction.operand<L2WriteBoxedOperand>(0)
		registerSet.typeAtPut(
			function.register(),
			HookType.READ_UNASSIGNED_VARIABLE.functionType,
			instruction)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val function = instruction.operand<L2WriteBoxedOperand>(0)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(function.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val function = instruction.operand<L2WriteBoxedOperand>(0)

		// :: register = interpreter.runtime().unassignedVariableReadFunction();
		translator.loadInterpreter(method)
		Interpreter.runtimeMethod.generateCall(method)
		AvailRuntime.unassignedVariableReadFunctionMethod.generateCall(method)
		method.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
		translator.store(method, function.register())
	}
}
