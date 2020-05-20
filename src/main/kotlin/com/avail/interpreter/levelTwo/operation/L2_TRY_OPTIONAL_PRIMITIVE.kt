/*
 * L2_TRY_OPTIONAL_PRIMITIVE.kt
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.*
import com.avail.interpreter.levelTwo.ReadsHiddenVariable
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Expect the [AvailObject] (pointers) array and int array to still reflect the
 * caller.  Expect [Interpreter.argsBuffer] to have been loaded with the
 * arguments to this possible primitive function, and expect the
 * code/function/chunk to have been updated for this primitive function. Try to
 * execute a potential primitive, setting the [Interpreter.returnNow] flag and
 * [latestResult][Interpreter.setLatestResult] if successful.  The caller always
 * has the responsibility of checking the return value, if applicable at that
 * call site.  Used only by the [L2Chunk.unoptimizedChunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(theValue = [
	CURRENT_CONTINUATION::class,
	CURRENT_FUNCTION::class,
	//	CURRENT_ARGUMENTS.class,
	LATEST_RETURN_VALUE::class])
object L2_TRY_OPTIONAL_PRIMITIVE : L2Operation()
{
	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		// This instruction should only be used in the L1 interpreter loop.
		throw UnsupportedOperationException()
	}

	// It could fail and jump.
	override fun hasSideEffect(): Boolean = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// :: if (interpreter.function.code().primitive() == null)
		// ::    goto noPrimitive;
		translator.loadInterpreter(method)
		method.visitInsn(Opcodes.DUP)
		Interpreter.interpreterFunctionField.generateRead(method)
		method.visitInsn(Opcodes.DUP)
		FunctionDescriptor.functionCodeMethod.generateCall(method)
		CompiledCodeDescriptor.codePrimitiveMethod.generateCall(method)
		method.visitInsn(Opcodes.DUP)
		val noPrimitive = Label()
		method.visitJumpInsn(Opcodes.IFNULL, noPrimitive)
		// :: return L2_TRY_OPTIONAL_PRIMITIVE.attemptThePrimitive(
		// ::    interpreter, function, primitive);
		attemptThePrimitiveMethod.generateCall(method)
		method.visitInsn(Opcodes.ARETURN)
		method.visitLabel(noPrimitive)
		// Pop the three Category-1 arguments that were waiting for
		// attemptThePrimitive().
		method.visitInsn(Opcodes.POP2)
		method.visitInsn(Opcodes.POP)
	}

	/**
	 * Attempt the [primitive][Primitive], dynamically checking
	 * whether it is an [inlineable][Primitive.Flag.CanInline] primitive.
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @param function
	 *   The [A_Function].
	 * @param primitive
	 *   The [Primitive].
	 * @return
	 *   The [StackReifier], if any.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	fun attemptThePrimitive(
		interpreter: Interpreter,
		function: A_Function,
		primitive: Primitive): StackReifier?
	{
		return if (primitive.hasFlag(Primitive.Flag.CanInline))
		{
			L2_TRY_PRIMITIVE.attemptInlinePrimitive(
				interpreter, function, primitive)
		}
		else
		{
			L2_TRY_PRIMITIVE.attemptNonInlinePrimitive(
				interpreter, function, primitive)
		}
	}

	/**
	 * The [CheckedMethod] for invoking [attemptThePrimitive].
	 */
	val attemptThePrimitiveMethod =
		CheckedMethod.staticMethod(
			L2_TRY_OPTIONAL_PRIMITIVE::class.java,
			::attemptThePrimitive.name,
			StackReifier::class.java,
			Interpreter::class.java,
			A_Function::class.java,
			Primitive::class.java)
}