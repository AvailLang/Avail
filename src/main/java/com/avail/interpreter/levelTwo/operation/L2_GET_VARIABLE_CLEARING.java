/*
 * L2_GET_VARIABLE_CLEARING.java
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
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.representation.AbstractAvailObject;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.variables.A_Variable;
import com.avail.descriptor.variables.VariableDescriptor;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.A_BasicObject.makeImmutableMethod;
import static com.avail.descriptor.A_BasicObject.traversedMethod;
import static com.avail.descriptor.AbstractDescriptor.isMutableMethod;
import static com.avail.descriptor.representation.AbstractAvailObject.descriptorMethod;
import static com.avail.descriptor.types.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.descriptor.variables.A_Variable.getValueMethod;
import static com.avail.descriptor.variables.VariableDescriptor.clearVariableMethod;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Extract the value of a variable, while simultaneously clearing it. If the
 * variable is unassigned, then branch to the specified {@linkplain
 * Interpreter#offset(int) offset}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_GET_VARIABLE_CLEARING
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_GET_VARIABLE_CLEARING}.
	 */
	private L2_GET_VARIABLE_CLEARING ()
	{
		super(
			READ_BOXED.is("variable"),
			WRITE_BOXED.is("extracted value", SUCCESS),
			PC.is("read succeeded", SUCCESS),
			PC.is("read failed", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_VARIABLE_CLEARING instance =
		new L2_GET_VARIABLE_CLEARING();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand variableReg = instruction.operand(0);
		final L2WriteBoxedOperand destReg = instruction.operand(1);
//		final int successIndex = instruction.pcOffsetAt(2);
//		final int failureIndex = instruction.pcOffsetAt(3);
		//TODO MvG - Rework everything related to type propagation.
		// Only update the success register set; no registers are affected if
		// the failure branch is taken.
		final RegisterSet registerSet = registerSets.get(1);
		// If we haven't already guaranteed that this is a variable then we
		// are probably not doing things right.
		assert registerSet.hasTypeAt(variableReg.register());
		final A_Type varType = registerSet.typeAt(variableReg.register());
		assert varType.isSubtypeOf(mostGeneralVariableType());
		registerSet.removeConstantAt(destReg.register());
		registerSet.typeAtPut(
			destReg.register(), varType.readType(), instruction);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Subtle. Reading from a variable can fail, so don't remove this.
		// Also it clears the variable.
		return true;
	}

	@Override
	public boolean isVariableGet ()
	{
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
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
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(VariableSetException.class));
		method.visitLabel(tryStart);
		// ::    dest = variable.getValue();
		translator.load(method, variable.register());
		getValueMethod.generateCall(method);
		translator.store(method, value.register());
		// ::    if (variable.traversed().descriptor().isMutable()) {
		translator.load(method, variable.register());
		traversedMethod.generateCall(method);
		descriptorMethod.generateCall(method);
		isMutableMethod.generateCall(method);
		final Label elseLabel = new Label();
		method.visitJumpInsn(IFEQ, elseLabel);
		// ::       variable.clearValue();
		translator.load(method, variable.register());
		clearVariableMethod.generateCall(method);
		// ::       goto success;
		method.visitJumpInsn(GOTO, translator.labelFor(success.offset()));
		// ::    } else {
		method.visitLabel(elseLabel);
		// ::       dest.makeImmutable();
		translator.load(method, value.register());
		makeImmutableMethod.generateCall(method);
		method.visitInsn(POP);
		// ::       goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(GOTO, translator.labelFor(success.offset()));
		// :: } catch (VariableGetException|VariableSetException e) {
		method.visitLabel(catchStart);
		method.visitInsn(POP);
		// ::    goto failure;
		translator.jump(method, instruction, failure);
		// :: }
	}
}
