/*
 * L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION.kt
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

import avail.AvailRuntime
import avail.descriptor.functions.A_Continuation
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.reportWrongReturnTypeMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Invoke the [AvailRuntime.resultDisagreedWithExpectedTypeFunction] handler
 * function, via [Interpreter.reportWrongReturnType], which takes responsibility
 * for assembling an [A_Continuation] in the event of reification.
 *
 * Note that it implicitly uses the [Interpreter.function] and
 * [Interpreter.returningFunction].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(CURRENT_FUNCTION::class)
@WritesHiddenVariable(CURRENT_FUNCTION::class)
object L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION : L2ControlFlowOperation(
	READ_BOXED.named("returned value"),
	CONSTANT.named("expected type"),
	INT_IMMEDIATE.named("pc"),
	INT_IMMEDIATE.named("stackp"),
	READ_BOXED_VECTOR.named("frame values"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val returnedValue = instruction.operand<L2ReadBoxedOperand>(0)
		val expectedType = instruction.operand<L2ConstantOperand>(1)
		val pc = instruction.operand<L2IntImmediateOperand>(2)
		val stackp = instruction.operand<L2IntImmediateOperand>(3)
		val frameValues = instruction.operand<L2ReadBoxedVectorOperand>(4)
		renderPreamble(instruction, builder)
		builder.append(" got: ")
		builder.append(returnedValue.registerString())
		builder.append(", expected: ")
		builder.append(expectedType.constant.typeTag)
		builder.append(", pc: ")
		builder.append(pc.value)
		builder.append(", stackp: ")
		builder.append(stackp.value)
		builder.append("\n\tframe data: ")
		frameValues.elements.joinTo(builder, limit = 5) { it.registerString() }
	}

	// Never remove this.
	override val hasSideEffect get() = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val returnedValue = instruction.operand<L2ReadBoxedOperand>(0)
		val expectedType = instruction.operand<L2ConstantOperand>(1)
		val pc = instruction.operand<L2IntImmediateOperand>(2)
		val stackp = instruction.operand<L2IntImmediateOperand>(3)
		val frameValues = instruction.operand<L2ReadBoxedVectorOperand>(4)

		translator.loadInterpreter(method)
		// :: interpreter
		translator.load(method, returnedValue.register())
		translator.literal(method, expectedType.constant)
		translator.intConstant(method, pc.value)
		translator.intConstant(method, stackp.value)
		// :: interpreter, value, expected, pc, stackp
		translator.objectArray(
			method, frameValues.elements, AvailObject::class.java)
		// :: interpreter, value, expected, pc, stackp, frameArray
		reportWrongReturnTypeMethod.generateCall(method)
		// :: stackReifier
		// Note that the above call took responsibility for creating the
		// reified continuation as needed.
		method.visitInsn(Opcodes.ARETURN)
	}
}
