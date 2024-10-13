/*
 * L2_CHECK_ESCAPED_LOCALS.kt
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

import avail.descriptor.variables.A_Variable.Companion.checkForSharedOrReactorsMethod
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Check each of the locals, which must contain a variable.  If any has become
 * shared or has had a reactor added to it, fall back to L1 [unoptimizedChunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@ReadsHiddenVariable(CURRENT_FUNCTION::class)
@WritesHiddenVariable(CURRENT_FUNCTION::class)
class L2_CHECK_ESCAPED_LOCALS(
	var localsToCheck: L2ReadBoxedVectorOperand,
	var ifSafe: L2PcOperand,
	var ifFallBack: L2PcOperand
) : L2ControlFlowInstruction()
{
	// This instruction must not be removed.
	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(" check locals: ")
		localsToCheck.elements.joinTo(builder) { it.registerString() }
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		localsToCheck.elements.forEach { local ->
			translator.load(method, local.register())
			// :: local-variable
			checkForSharedOrReactorsMethod.generateCall(method)
			// :: local-shared-or-has-reactor
			method.visitJumpInsn(
				Opcodes.IFEQ, translator.labelFor(ifFallBack.offset()))
		}
		method.visitJumpInsn(Opcodes.GOTO, translator.labelFor(ifSafe.offset()))
	}
}
