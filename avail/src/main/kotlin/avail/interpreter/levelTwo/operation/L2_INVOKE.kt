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

import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.cast
import org.objectweb.asm.Label
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
	LATEST_RETURN_VALUE::class,
	STACK_REIFIER::class)
class L2_INVOKE(
	var calledFunction: L2ReadBoxedOperand,
	var arguments: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var result: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReturn: L2PcOperand,
	@On(OFF_RAMP) var ifReification: L2PcOperand
): L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

	/** If it's primitive, defer to it, otherwise assume the worst. */
	override fun mightMakeEscapedVariableShared(
		manifest: L2ValueManifest
	): Boolean
	{
		calledFunction.definitionSkippingMoves(manifest)
			.getConstantCode(manifest)
			?.let { code ->
				code.codePrimitive()?.let { prim ->
					return prim.mightMakeEscapedVariableShared(
						arguments.elements.map(L2ReadBoxedOperand::type))
				}
			}
		return true
	}

	override fun L2Regenerator.regenerateForPostponement()
	{
		forcePostponedWritesToLocals()
		basicRegenerateForPostponement()
	}

	/**
	 * If the function is bottom-valued, treat the block as cold, and don't
	 * bother splitting paths that lead only to it and other cold blocks.
	 * The called function will definitely have to raise an exception, exit
	 * or restart a continuation, loop forever, or terminate the fiber, so
	 * splitting the code is not likely to have a big impact.
	 */
	override val isCold: Boolean
		get()
		{
			val functionType = calledFunction.restriction().type
			assert(functionType.isSubtypeOf(mostGeneralFunctionType))
			return functionType.returnType.isBottom
		}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		calledFunction.constantOrNull?.let { constantFunction ->
			// Rewrite it as a constant function invocation, allowing that emit
			// operation to do its own further optimizations.
			L2_INVOKE_CONSTANT_FUNCTION(
				L2ConstantOperand(constantFunction),
				arguments,
				result,
				ifReturn,
				ifReification
			).run {
				emitTransformedInstruction()
			}
			return
		}
		+this@L2_INVOKE
	}

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(result.registerString())
		append(" ← ")
		append(calledFunction.registerString())
		append("(")
		append(arguments.elements.joinToString(", "))
		append(")")
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::result,
			::calledFunction,
			::arguments)
	}

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: [interpreter]
		loadInterpreter()
		// :: [interpreter, interpreter]
		load(Interpreter.chunkField)
		// :: [interpreter, callingChunk]
		loadInterpreter()
		// :: [interpreter, callingChunk, interpreter]
		load(calledFunction)
		// :: [interpreter, callingChunk, interpreter, function]
		generatePushArgumentsAndInvoke(
			arguments.elements,
			result,
			ifReturn,
			ifReification)
	}

	companion object
	{
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
		 * @receiver
		 *   The translator on which to generate the invocation.
		 * @param argsRegsList
		 *   The [List] of [L2ReadBoxedOperand] arguments.
		 * @param result
		 *   Where to write the return result if the call returns without
		 *   reification.
		 * @param onNormalReturn
		 *   Where to jump if the call completes.
		 * @param onReification
		 *   Where to jump if reification is requested during the call.
		 */
		fun JVMTranslator.generatePushArgumentsAndInvoke(
			argsRegsList: List<L2ReadOperand<BOXED_KIND>>,
			result: L2WriteBoxedOperand,
			onNormalReturn: L2PcOperand,
			onReification: L2PcOperand)
		{
			// :: caller set up [interpreter, callingChunk, interpreter, function]
			val numArgs = argsRegsList.size
			if (numArgs < preinvokeMethods.size)
			{
				argsRegsList.forEach { load(it) }
				// :: [interpreter, callingChunk, interpreter, function, [args...]]
				generateCall(preinvokeMethods[numArgs])
			}
			else
			{
				objectArray(argsRegsList.cast(), AvailObject::class.java)
				// :: [interpreter, callingChunk, interpreter, function, argsArray]
				generateCall(Interpreter.preinvokeMethod)
			}
			// :: [interpreter, callingChunk, callingFunction]
			loadInterpreter()
			// :: [interpreter, callingChunk, callingFunction, interpreter]
			generateCall(Interpreter.interpreterRunChunkMethod)
			// :: [interpreter, callingChunk, callingFunction, reifier]
			generateCall(Interpreter.postinvokeMethod)
			// :: [reifier]
			method.visitVarInsn(Opcodes.ASTORE, reifierLocal())
			// :: []
			method.visitVarInsn(Opcodes.ALOAD, reifierLocal())
			// :: if (reifier !== null) goto onReificationPreamble;
			// :: result = interpreter.getLatestResult();
			// :: goto onNormalReturn;
			// :: onReificationPreamble: ...
			val onReificationPreamble = Label()
			method.visitJumpInsn(Opcodes.IFNONNULL, onReificationPreamble)

			loadInterpreter()
			// :: [interpreter]
			generateCall(Interpreter.getLatestResultMethod)
			// :: [latestResult]
			store(result.register())
			// :: []
			jump(onNormalReturn)

			method.visitLabel(onReificationPreamble)
			generateReificationPreamble(onReification)
		}
	}
}
