/*
 * L2_SET_VARIABLE_NO_CHECK.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.VariableDescriptor;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Assign a value to a {@linkplain VariableDescriptor variable} <em>without</em>
 * checking that it's of the correct type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_SET_VARIABLE_NO_CHECK
extends L2Operation
{
	/**
	 * Construct an {@code L2_SET_VARIABLE_NO_CHECK}.
	 */
	private L2_SET_VARIABLE_NO_CHECK ()
	{
		super(
			READ_POINTER.is("variable"),
			READ_POINTER.is("value to write"),
			PC.is("write succeeded", SUCCESS),
			PC.is("write failed", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SET_VARIABLE_NO_CHECK instance =
		new L2_SET_VARIABLE_NO_CHECK();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(1);
//		final int succeeded = instruction.pcAt(2);
//		final int failed = instruction.pcAt(3);

		// The two register sets are clones, so only cross-check one of them.
		final RegisterSet registerSet = registerSets.get(0);
		assert registerSet.hasTypeAt(variableReg.register());
		final A_Type varType = registerSet.typeAt(variableReg.register());
		assert varType.isSubtypeOf(mostGeneralVariableType());
		assert registerSet.hasTypeAt(valueReg.register());
		final A_Type valueType = registerSet.typeAt(valueReg.register());
		assert valueType.isSubtypeOf(varType.writeType());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean isVariableSet ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister valueReg =
			instruction.readObjectRegisterAt(1).register();
//		final int successIndex = instruction.pcOffsetAt(2);
//		final L2PcOperand failure = instruction.pcAt(3);

		assert this == instruction.operation;
		renderPreamble(instruction, builder);
		builder.append(" ↓");
		builder.append(variableReg);
		builder.append(" ← ");
		builder.append(valueReg);
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister valueReg =
			instruction.readObjectRegisterAt(1).register();
		final int successIndex = instruction.pcOffsetAt(2);
		final L2PcOperand failure = instruction.pcAt(3);

		// :: try {
		final Label tryStart = new Label();
		final Label catchStart = new Label();
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(VariableSetException.class));
		method.visitLabel(tryStart);
		// ::    variable.setValueNoCheck(value);
		translator.load(method, variableReg);
		translator.load(method, valueReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Variable.class),
			"setValueNoCheck",
			getMethodDescriptor(VOID_TYPE, getType(A_BasicObject.class)),
			true);
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableSetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(GOTO, translator.labelFor(successIndex));
		// :: } catch (VariableSetException e) {
		method.visitLabel(catchStart);
		method.visitInsn(POP);
		// ::    goto failure;
		translator.branch(method, instruction, failure);
		// :: }
	}
}
