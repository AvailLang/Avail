/*
 * L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.AvailRuntime
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.types.*
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.GLOBAL_STATE
import com.avail.interpreter.levelTwo.ReadsHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.Consumer

/**
 * Store the [invalid result
 * function][AvailRuntime.resultDisagreedWithExpectedTypeFunction] into the
 * supplied [object register][L2BoxedRegister].
 *
 *
 * The function is invoked by the VM whenever an attempt is made to return a
 * value that doesn't satisfy the call site's expected return type.  The
 * function is passed the returning function, the expected return type, and the
 * actual value that it was attempting to return.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(theValue = arrayOf(GLOBAL_STATE::class))
object L2_GET_INVALID_MESSAGE_RESULT_FUNCTION : L2Operation(
	L2OperandType.WRITE_BOXED.`is`("invalid message result function"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val function =
			instruction.operand<L2WriteBoxedOperand>(0)
		registerSet.typeAtPut(
			function.register(),
			FunctionTypeDescriptor.functionType(
				ObjectTupleDescriptor.tuple(
					FunctionTypeDescriptor.mostGeneralFunctionType(),
					InstanceMetaDescriptor.topMeta(),
					VariableTypeDescriptor.variableTypeFor(
						TypeDescriptor.Types.ANY.o())),
				BottomTypeDescriptor.bottom()),
			instruction)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val function =
			instruction.operand<L2WriteBoxedOperand>(0)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(function.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val function =
			instruction.operand<L2WriteBoxedOperand>(0)

		// :: destination = interpreter.runtime()
		// ::    .resultDisagreedWithExpectedTypeFunction();
		translator.loadInterpreter(method)
		Interpreter.runtimeMethod.generateCall(method)
		AvailRuntime.resultDisagreedWithExpectedTypeFunctionMethod
			.generateCall(method)
		method.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
		translator.store(method, function.register())
	}
}