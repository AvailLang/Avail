/*
 * L2_GET_VARIABLE.kt
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.variables.A_Variable
import com.avail.exceptions.VariableGetException
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.Consumer

/**
 * Extract the value of a variable. If the variable is unassigned, then branch
 * to the specified [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_GET_VARIABLE : L2ControlFlowOperation(
	L2OperandType.READ_BOXED.`is`("variable"),
	L2OperandType.WRITE_BOXED.`is`("extracted value", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("read succeeded", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("read failed", L2NamedOperandType.Purpose.OFF_RAMP))
{
	// Subtle. Reading from a variable can fail, so don't remove this.
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
		method.visitLabel(tryStart)
		// ::    dest = variable.getValue().makeImmutable();
		translator.load(method, variable.register())
		A_Variable.getValueMethod.generateCall(method)
		A_BasicObject.makeImmutableMethod.generateCall(method)
		translator.store(method, value.register())
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(Opcodes.GOTO, translator.labelFor(success.offset()))
		// :: } catch (VariableGetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		translator.jump(method, instruction, failure)
		// :: }
	}
}