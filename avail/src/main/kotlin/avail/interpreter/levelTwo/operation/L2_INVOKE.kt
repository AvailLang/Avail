/*
 * L2_INVOKE.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.cast
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
	READ_BOXED.named("called function"),
	READ_BOXED_VECTOR.named("arguments"),
	WRITE_BOXED.named("result", SUCCESS),
	PC.named("on return", SUCCESS),
	PC.named("on reification", OFF_RAMP))
{
	override val hasSideEffect: Boolean
		get() = true

	override fun isCold(instruction: L2Instruction): Boolean
	{
		assert(this == instruction.operation)
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		//val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)
		//val result = instruction.operand<L2WriteBoxedOperand>(2)
		//val onReturn = instruction.operand<L2PcOperand>(3);
		//val onReification = instruction.operand<L2PcOperand>(4);

		// If the function is bottom-valued, treat the block as cold, and don't
		// bother splitting paths that lead only to it and other cold blocks.
		// The called function will definitely have to raise an exception, exit
		// or restart a continuation, loop forever, or terminate the fiber, so
		// splitting the code is not likely to have a big impact.
		val functionType = function.restriction().type
		assert(functionType.isSubtypeOf(mostGeneralFunctionType()))
		return functionType.returnType.isBottom
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val function = transformedOperands[0] as L2ReadBoxedOperand
		val arguments = transformedOperands[1] as L2ReadBoxedVectorOperand
		val result = transformedOperands[2] as L2WriteBoxedOperand
		val onNormalReturn = transformedOperands[3] as L2PcOperand
		val onReification = transformedOperands[4] as L2PcOperand

		function.restriction().constantOrNull?.let { constantFunction ->
			// Rewrite it as a constant function invocation, allowing that emit
			// operation to do its own further optimizations.
			L2_INVOKE_CONSTANT_FUNCTION.emitTransformedInstruction(
				arrayOf(
					L2ConstantOperand(constantFunction),
					arguments,
					result,
					onNormalReturn,
					onReification),
				regenerator)
			return
		}
		super.emitTransformedInstruction(transformedOperands, regenerator)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val result = instruction.operand<L2WriteBoxedOperand>(2)
		//val onReturn = instruction.operand<L2PcOperand>(3);
		//val onReification = instruction.operand<L2PcOperand>(4);

		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(result.registerString())
		builder.append(" ← ")
		builder.append(function.registerString())
		builder.append("(")
		builder.append(arguments.elements)
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
			arguments.elements,
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
		Interpreter.preinvoke3Method)

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
		argsRegsList: List<L2ReadOperand<BOXED_KIND>>,
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
				method, argsRegsList.cast(), AvailObject::class.java)
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
