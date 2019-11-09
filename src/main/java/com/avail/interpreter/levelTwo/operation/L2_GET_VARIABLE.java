/*
 * L2_GET_VARIABLE.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.exceptions.VariableGetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Extract the value of a variable. If the variable is unassigned, then branch
 * to the specified {@linkplain Interpreter#offset(int) offset}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_GET_VARIABLE
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_GET_VARIABLE}.
	 */
	private L2_GET_VARIABLE ()
	{
		super(
			READ_BOXED.is("variable"),
			WRITE_BOXED.is("extracted value"),
			PC.is("read succeeded", SUCCESS),
			PC.is("read failed", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_VARIABLE instance = new L2_GET_VARIABLE();

	@Override
	public boolean hasSideEffect ()
	{
		// Subtle. Reading from a variable can fail, so don't remove this.
		return true;
	}

	@Override
	public boolean isVariableGet ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand variable = instruction.operand(0);
		final L2WriteBoxedOperand value = instruction.operand(1);
//		final L2PcOperand success = instruction.operand(2);
//		final L2PcOperand failure = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(value.registerString());
		builder.append(" ← ↓");
		builder.append(variable.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand variable = instruction.operand(0);
		final L2WriteBoxedOperand value = instruction.operand(1);
		final L2PcOperand success = instruction.operand(2);
		final L2PcOperand failure = instruction.operand(3);

		// :: try {
		final Label tryStart = new Label();
		final Label catchStart = new Label();
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(VariableGetException.class));
		method.visitLabel(tryStart);
		// ::    dest = variable.getValue().makeImmutable();
		translator.load(method, variable.register());
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Variable.class),
			"getValue",
			getMethodDescriptor(getType(AvailObject.class)),
			true);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"makeImmutable",
			getMethodDescriptor(getType(AvailObject.class)),
			true);
		translator.store(method, value.register());
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(GOTO, translator.labelFor(success.offset()));
		// :: } catch (VariableGetException e) {
		method.visitLabel(catchStart);
		method.visitInsn(POP);
		// ::    goto failure;
		translator.jump(method, instruction, failure);
		// :: }
	}
}
