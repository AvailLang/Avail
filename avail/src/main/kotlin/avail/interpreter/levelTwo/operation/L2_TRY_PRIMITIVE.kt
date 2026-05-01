/*
 * L2_TRY_PRIMITIVE.kt
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

import avail.exceptions.unsupported
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.attemptPrimitiveMethod
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.primitive.Primitive
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

/**
 * Attempt the given [primitive].  If it succeeds, return from the frame with
 * the primitive's output.  If the primitive attempts to reify, return `null`.
 * If the primitive failed, it will have recorded the failure code for
 * subsequent use, so fall through to the remainder of the chunk.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(
	CURRENT_CONTINUATION::class,
	CURRENT_FUNCTION::class,
	LATEST_RETURN_VALUE::class)
class L2_TRY_PRIMITIVE(
	var primitive: L2ArbitraryConstantOperand<Primitive>
): L2Instruction()
{
	override val isEntryPoint get() = true

	// It could fail and jump.
	override val hasSideEffect get() = true

	override fun equivalentTo(other: L2Instruction) = unsupported

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// interpreter
		method.visitInsn(Opcodes.DUP)
		// interpreter, interpreter
		load(Interpreter.interpreterFunctionField)
		// interpreter, fn
		loadLiteralObject(primitive.constant)
		// interpreter, fn, prim
		generateCall(attemptPrimitiveMethod)
		// :: valueOrNull
		method.visitInsn(Opcodes.DUP)
		// :: valueOrNull, valueOrNull
		val notSuccess = Label()
		method.visitJumpInsn(Opcodes.IFNULL, notSuccess)
		// :: valueOrNull(!null)
		method.visitInsn(Opcodes.ARETURN)

		method.visitLabel(notSuccess)
		// :: valueOrNull(=null)
		loadInterpreter()
		// :: valueOrNull(=null), interpreter
		load(Interpreter.currentReifierField)
		// :: valueOrNull(=null), reifier
		val notReifying = Label()
		method.visitJumpInsn(Opcodes.IFNULL, notReifying)
		// :: valueOrNull(=null)
		method.visitInsn(Opcodes.ARETURN)
		method.visitLabel(notReifying)
		// ::
		// Fall through for the case of a failed primitive.
	}
}
