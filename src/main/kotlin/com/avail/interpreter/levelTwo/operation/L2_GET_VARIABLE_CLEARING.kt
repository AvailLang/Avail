/*
 * L2_GET_VARIABLE_CLEARING.kt
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

import com.avail.descriptor.AbstractDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractAvailObject
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.Consumer

/**
 * Extract the value of a variable, while simultaneously clearing it. If the
 * variable is unassigned, then branch to the specified
 * [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_GET_VARIABLE_CLEARING : L2ControlFlowOperation(
	L2OperandType.READ_BOXED.named("variable"),
	L2OperandType.WRITE_BOXED.named("extracted value", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("read succeeded", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("read failed", L2NamedOperandType.Purpose.OFF_RAMP))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		val variableReg = instruction.operand<L2ReadBoxedOperand>(0)
		val destReg = instruction.operand<L2WriteBoxedOperand>(1)
		//		final int successIndex = instruction.pcOffsetAt(2);
//		final int failureIndex = instruction.pcOffsetAt(3);
		//TODO MvG - Rework everything related to type propagation.
		// Only update the success register set; no registers are affected if
		// the failure branch is taken.
		val registerSet = registerSets[1]
		assert(registerSet.hasTypeAt(variableReg.register()))
		val varType = registerSet.typeAt(variableReg.register())
		assert(varType.isSubtypeOf(VariableTypeDescriptor.mostGeneralVariableType()))
		registerSet.removeConstantAt(destReg.register())
		registerSet.typeAtPut(
			destReg.register(), varType.readType(), instruction)
	}

	// Subtle. Reading from a variable can fail, so don't remove this.
	// Also it clears the variable.
	override fun hasSideEffect(): Boolean = true

	override val isVariableGet: Boolean
		get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val variable = instruction.operand<L2ReadBoxedOperand>(0)
		val value = instruction.operand<L2WriteBoxedOperand>(1)
		//		final L2PcOperand success = instruction.operand(2);
//		final L2PcOperand failure = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ← ↓")
		builder.append(variable.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val variable = instruction.operand<L2ReadBoxedOperand>(0)
		val value = instruction.operand<L2WriteBoxedOperand>(1)
		val success = instruction.operand<L2PcOperand>(2)
		val failure = instruction.operand<L2PcOperand>(3)

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
		A_Variable.getValueMethod.generateCall(method)
		translator.store(method, value.register())
		// ::    if (variable.traversed().descriptor().isMutable()) {
		translator.load(method, variable.register())
		A_BasicObject.traversedMethod.generateCall(method)
		AbstractAvailObject.descriptorMethod.generateCall(method)
		AbstractDescriptor.isMutableMethod.generateCall(method)
		val elseLabel = Label()
		method.visitJumpInsn(Opcodes.IFEQ, elseLabel)
		// ::       variable.clearValue();
		translator.load(method, variable.register())
		VariableDescriptor.clearVariableMethod.generateCall(method)
		// ::       goto success;
		method.visitJumpInsn(Opcodes.GOTO, translator.labelFor(success.offset()))
		// ::    } else {
		method.visitLabel(elseLabel)
		// ::       dest.makeImmutable();
		translator.load(method, value.register())
		A_BasicObject.makeImmutableMethod.generateCall(method)
		method.visitInsn(Opcodes.POP)
		// ::       goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(Opcodes.GOTO, translator.labelFor(success.offset()))
		// :: } catch (VariableGetException|VariableSetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		translator.jump(method, instruction, failure)
		// :: }
	}
}