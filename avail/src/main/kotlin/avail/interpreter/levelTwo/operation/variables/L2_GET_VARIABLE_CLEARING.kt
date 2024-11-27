/*
 * L2_GET_VARIABLE_CLEARING.kt
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
import avail.descriptor.representation.A_BasicObject.Companion.traversedMethod
import avail.descriptor.representation.AbstractAvailObject.Companion.descriptorMethod
import avail.descriptor.representation.AbstractDescriptor.Companion.isMutableMethod
import avail.descriptor.variables.A_Variable.Companion.clearVariableMethod
import avail.descriptor.variables.A_Variable.Companion.getValueMethod
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Extract the value of a variable, while simultaneously clearing it. If the
 * variable is unassigned, then branch to the specified
 * [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_GET_VARIABLE_CLEARING(
	var variable: L2ReadBoxedOperand,
	@On(SUCCESS) var extractedValue: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReadSucceeded: L2PcOperand,
	@On(OFF_RAMP) var ifReadFailed: L2PcOperand
) : L2ControlFlowInstruction()
{
	// Subtle. Reading from a variable can fail, so don't remove this.
	// Also it clears the variable.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(extractedValue.registerString())
		append(" ← ↓")
		append(variable.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::variable, ::extractedValue)
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
			Type.getInternalName(VariableGetException::class.java))
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(VariableSetException::class.java))
		method.visitLabel(tryStart)
		// ::    dest = variable.getValue();
		translator.load(method, variable.register())
		getValueMethod.generateCall(method)
		translator.store(method, extractedValue.register())
		// ::    if (variable.traversed().descriptor().isMutable()) {
		translator.load(method, variable.register())
		traversedMethod.generateCall(method)
		descriptorMethod.generateCall(method)
		isMutableMethod.generateCall(method)
		val elseLabel = Label()
		method.visitJumpInsn(Opcodes.IFEQ, elseLabel)
		// ::       variable.clearValue();
		translator.load(method, variable.register())
		clearVariableMethod.generateCall(method)
		// ::       goto success;
		translator.jump(method, ifReadSucceeded)
		// ::    } else {
		method.visitLabel(elseLabel)
		// ::       dest.makeImmutable();
		translator.load(method, extractedValue.register())
		A_BasicObject.makeImmutableMethod.generateCall(method)
		method.visitInsn(Opcodes.POP)
		// ::       goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		translator.jump(method, ifReadSucceeded)
		// :: } catch (VariableGetException|VariableSetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		translator.jumpOrFallThrough(method, ifReadFailed)
		// :: }
	}
}
