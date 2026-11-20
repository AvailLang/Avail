/*
 * L2_CREATE_LABEL.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import avail.descriptor.functions.LabelDescriptor
import avail.descriptor.functions.LabelDescriptor.Companion.createLabelMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator

/**
 * Create a [label][LabelDescriptor] from a function and the reified
 * continuation that called it.  This is the last step of the code that
 * replaces an [L2_VIRTUAL_CREATE_LABEL], after the caller has been reified.
 *
 * @property function
 *   The function whose invocation the label names.
 * @property caller
 *   The reified continuation that invoked the function.
 * @property outputLabel
 *   Where to write the new label.
 */
class L2_CREATE_LABEL(
	var function: L2ReadBoxedOperand,
	var caller: L2ReadBoxedOperand,
	var outputLabel: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(outputLabel.registerString())
		append(" ← label(")
		append(function.registerString())
		append(", caller = ")
		append(caller.registerString())
		append(")")
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: label = createLabel(function, caller);
		load(function)
		load(caller)
		generateCall(createLabelMethod)
		store(outputLabel.register())
	}
}
