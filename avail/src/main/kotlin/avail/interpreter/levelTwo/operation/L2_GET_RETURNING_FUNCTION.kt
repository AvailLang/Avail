/*
 * L2_GET_RETURNING_FUNCTION.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.CallSiteHelper
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Ask the [Interpreter] for its [Interpreter.returningFunction], the function
 * that most recently returned a value to this chunk.  A call site that checks
 * the returned value against its expected type does this after a call that
 * actually returned, so that a failed check can name the function responsible
 * (see [CallSiteHelper.returningFunction]).  A callee embedded in the chunk
 * never returns, so its function is supplied directly instead.
 *
 * @property returningFunction
 *   Where to write the function that returned.
 */
@ReadsHiddenVariable(LATEST_RETURN_VALUE::class)
class L2_GET_RETURNING_FUNCTION(
	var returningFunction: L2WriteBoxedOperand
): L2Instruction()
{
	/**
	 * Like [L2_GET_LATEST_RETURN_VALUE], it reads a field that every return
	 * updates, so it must stay where it is.
	 */
	override val hasSideEffect: Boolean get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(returningFunction.registerString())
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: target = interpreter.returningFunction;
		loadInterpreter()
		load(Interpreter.returningFunctionField)
		method.visitTypeInsn(
			Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
		store(returningFunction.register())
	}
}
