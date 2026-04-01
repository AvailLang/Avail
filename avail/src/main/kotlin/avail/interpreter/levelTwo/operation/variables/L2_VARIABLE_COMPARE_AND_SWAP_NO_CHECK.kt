/*
 * L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.variables

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.Mutability
import avail.descriptor.variables.A_Variable.Companion.compareAndSwapValuesNoCheckMethod
import avail.exceptions.VariableSetException
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Read the variable's value, compare it to a reference value via semantic
 * [equality][A_BasicObject.equals], and if they're equal, store a provided new
 * value into the variable and answer true. Otherwise answer false.  If the
 * variable is [shared][Mutability.SHARED], then ensure suitable locks bracket
 * this entire sequence of operations.
 *
 * Trust that the variable can type-safely hold the provided new value.
 *
 * Take either the `swap succeeded` or `swap failed` branch, depending on
 * whether the write was successful.  If the write to the variable failed due to
 * a [VariableSetException], take the `variable set exception` path.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@ReadsHiddenVariable(GLOBAL_STATE::class)
@WritesHiddenVariable(GLOBAL_STATE::class)
class L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK(
	var variable: L2ReadBoxedOperand,
	var reference: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand,
	@On(SUCCESS) var ifSwapSucceeded: L2PcOperand,
	@On(FAILURE) var ifSwapFailed: L2PcOperand,
	@On(FAILURE) var ifVariableSetException: L2PcOperand
): L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" ↓")
		append(variable.registerString())
		append(" ← ")
		append(valueToWrite.registerString())
		append(" only if it was ")
		append(reference.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::variable,
			::reference,
			::valueToWrite)
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: try {
		val tryStart = Label()
		val catchStart = Label()
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(VariableSetException::class.java))
		method.visitLabel(tryStart)
		// ::    variable.setValueNoCheck(value);
		load(variable)
		load(reference)
		load(valueToWrite)
		generateCall(compareAndSwapValuesNoCheckMethod)
		// ::    ifeq failure
		jumpIf(Opcodes.IFEQ, ifSwapFailed)
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableSetException to be pushed onto the stack. So always do the
		// jump.
		jump(ifSwapSucceeded)
		// :: } catch (VariableSetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto exception;
		jumpOrFallThrough(ifVariableSetException)
		// :: }
	}
}
