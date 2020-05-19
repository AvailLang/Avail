/*
 * L2_INVOKE_CONSTANT_FUNCTION.kt
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

import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.*
import com.avail.interpreter.levelTwo.WritesHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import java.util.function.Consumer

/**
 * The given (constant) function is invoked.  The function may be a primitive,
 * and the primitive may succeed, fail, or replace the current continuation
 * (after reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a [StackReifier] instead of null.
 *
 * The return value can be picked up from
 * [latestResult][Interpreter.getLatestResult] in a subsequent
 * [L2_GET_LATEST_RETURN_VALUE] instruction. Note that the value that was
 * returned has not been dynamically type-checked yet, so if its validity can't
 * be proven statically by the VM, the calling function should check the type
 * against its expectation (prior to the value getting captured in any
 * continuation).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(value = [
	CURRENT_FUNCTION::class,
	CURRENT_ARGUMENTS::class,
	LATEST_RETURN_VALUE::class])
object L2_INVOKE_CONSTANT_FUNCTION : L2ControlFlowOperation(
	L2OperandType.CONSTANT.`is`("constant function"),
	L2OperandType.READ_BOXED_VECTOR.`is`("arguments"),
	L2OperandType.WRITE_BOXED.`is`(
		"result", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`(
		"on return", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`(
		"on reification", L2NamedOperandType.Purpose.OFF_RAMP))
{
	// Never remove invocations -- but inlining might make them go away.
	override fun hasSideEffect(): Boolean = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val constantFunction =
			instruction.operand<L2ConstantOperand>(0)
		val arguments =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val result =
			instruction.operand<L2WriteBoxedOperand>(2)
		//		final L2PcOperand onReturn = instruction.operand(3);
//		final L2PcOperand onReification = instruction.operand(4);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(result.registerString())
		builder.append(" ← ")
		builder.append(constantFunction.constant)
		builder.append("(")
		builder.append(arguments.elements())
		builder.append(")")
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val constantFunction =
			instruction.operand<L2ConstantOperand>(0)
		val arguments =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val result =
			instruction.operand<L2WriteBoxedOperand>(2)
		val onReturn = instruction.operand<L2PcOperand>(3)
		val onReification = instruction.operand<L2PcOperand>(4)
		translator.loadInterpreter(method)
		// :: [interpreter]
		translator.loadInterpreter(method)
		// :: [interpreter, interpreter]
		Interpreter.chunkField.generateRead(method)
		// :: [interpreter, callingChunk]
		translator.loadInterpreter(method)
		// :: [interpreter, callingChunk, interpreter]
		translator.literal(method, constantFunction.constant)
		// :: [interpreter, callingChunk, interpreter, function]
		L2_INVOKE.generatePushArgumentsAndInvoke(
			translator,
			method,
			arguments.elements(),
			result,
			onReturn,
			onReification)
	}
}