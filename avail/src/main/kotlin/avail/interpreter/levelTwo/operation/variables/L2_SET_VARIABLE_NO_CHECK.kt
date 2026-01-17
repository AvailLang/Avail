/*
 * L2_SET_VARIABLE_NO_CHECK.kt
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

import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.VariableSetException
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Assign a value to a [variable][VariableDescriptor] *without*
 * checking that it's of the correct type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(GLOBAL_STATE::class)
class L2_SET_VARIABLE_NO_CHECK(
	var variable: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand,
	@On(SUCCESS) var ifWriteSucceeded: L2PcOperand,
	@On(OFF_RAMP) var ifWriteFailed: L2PcOperand
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
		renderOperandsExcludingFields(
			desiredOperandTypes, ::variable, ::valueToWrite)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
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
		translator.load(method, variable)
		translator.load(method, valueToWrite)
		A_Variable.setValueNoCheckMethod.generateCall(method)
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableSetException to be pushed onto the stack. So always do the
		// jump.
		translator.jump(method, ifWriteSucceeded)
		// :: } catch (VariableSetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		translator.jumpOrFallThrough(method, ifWriteFailed)
		// :: }
	}
}
