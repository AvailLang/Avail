/*
 * L2_INVOKE.kt
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
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.STACK_REIFIER
import com.avail.interpreter.levelTwo.WritesHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * The given function is invoked.  The function may be a primitive, and the
 * primitive may succeed, fail, or replace the current continuation (after
 * reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a [StackReifier] instead of null.
 *
 * The return value can be picked up from [Interpreter.getLatestResult] in a
 * subsequent [L2_GET_LATEST_RETURN_VALUE] instruction. Note that the value that
 * was returned has not been dynamically type-checked yet, so if its validity
 * can't be proven statically by the VM, the calling function should check the
 * type against its expectation (prior to the value getting captured in any
 * continuation).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(
	CURRENT_ARGUMENTS::class,
	LATEST_RETURN_VALUE::class,
	STACK_REIFIER::class)
object L2_INVOKE : L2ControlFlowOperation(
	L2OperandType.READ_BOXED.named("called function"),
	L2OperandType.READ_BOXED_VECTOR.named("arguments"),
	L2OperandType.WRITE_BOXED.named("result", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("on return", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("on reification", L2NamedOperandType.Purpose.OFF_RAMP))
{
	override fun hasSideEffect(): Boolean
	{
		// Never remove invocations -- but inlining might make them go away.
		return true
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val result = instruction.operand<L2WriteBoxedOperand>(2)
		//		final L2PcOperand onReturn = instruction.operand(3);
//		final L2PcOperand onReification = instruction.operand(4);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(result.registerString())
		builder.append(" ← ")
		builder.append(function.registerString())
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
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val result = instruction.operand<L2WriteBoxedOperand>(2)
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
		translator.load(method, function.register())
		// :: [interpreter, callingChunk, interpreter, function]
		generatePushArgumentsAndInvoke(
			translator,
			method,
			arguments.elements(),
			result,
			onReturn,
			onReification)
	}
	/**
	 * An array of [Interpreter.preinvokeMethod] variants, where the
	 * index in the array is the number of arguments.
	 */
	private val preinvokeMethods = arrayOf(
		Interpreter.preinvoke0Method,
		Interpreter.preinvoke1Method,
		Interpreter.preinvoke2Method,
		Interpreter.preinvoke3Method
	)

	/**
	 * Generate code to push the arguments and invoke.  This expects the stack
	 * to already contain the [Interpreter], the calling [L2Chunk],
	 * another occurrence of the [Interpreter], and the [A_Function]
	 * to be invoked.
	 *
	 * @param translator
	 * The translator on which to generate the invocation.
	 * @param method
	 * The [MethodVisitor] controlling the method being written.
	 * @param argsRegsList
	 * The [List] of [L2ReadBoxedOperand] arguments.
	 * @param result
	 * Where to write the return result if the call returns without reification.
	 * @param onNormalReturn
	 * Where to jump if the call completes.
	 * @param onReification
	 * Where to jump if reification is requested during the call.
	 */
	fun generatePushArgumentsAndInvoke(
		translator: JVMTranslator,
		method: MethodVisitor,
		argsRegsList: List<L2ReadBoxedOperand>,
		result: L2WriteBoxedOperand,
		onNormalReturn: L2PcOperand,
		onReification: L2PcOperand)
	{
		// :: caller set up [interpreter, callingChunk, interpreter, function]
		val numArgs = argsRegsList.size
		if (numArgs < preinvokeMethods.size)
		{
			argsRegsList.forEach { translator.load(method, it.register()) }
			// :: [interpreter, callingChunk, interpreter, function, [args...]]
			preinvokeMethods[numArgs].generateCall(method)
		}
		else
		{
			translator.objectArray(
				method, argsRegsList, AvailObject::class.java)
			// :: [interpreter, callingChunk, interpreter, function, argsArray]
			Interpreter.preinvokeMethod.generateCall(method)
		}
		// :: [interpreter, callingChunk, callingFunction]
		translator.loadInterpreter(method)
		// :: [interpreter, callingChunk, callingFunction, interpreter]
		Interpreter.interpreterRunChunkMethod.generateCall(method)
		// :: [interpreter, callingChunk, callingFunction, reifier]
		Interpreter.postinvokeMethod.generateCall(method)
		// :: [reifier]
		method.visitVarInsn(Opcodes.ASTORE, translator.reifierLocal())
		// :: []
		method.visitVarInsn(Opcodes.ALOAD, translator.reifierLocal())
		// :: if (reifier !== null) goto onReificationPreamble;
		// :: result = interpreter.getLatestResult();
		// :: goto onNormalReturn;
		// :: onReificationPreamble: ...
		val onReificationPreamble = Label()
		method.visitJumpInsn(Opcodes.IFNONNULL, onReificationPreamble)

		translator.loadInterpreter(method)
		// :: [interpreter]
		Interpreter.getLatestResultMethod.generateCall(method)
		// :: [latestResult]
		translator.store(method, result.register())
		// :: []
		translator.jump(method, onNormalReturn)

		method.visitLabel(onReificationPreamble)
		translator.generateReificationPreamble(method, onReification)
	}
}
