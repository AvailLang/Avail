/*
 * L2_CREATE_VARIABLE.java
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

import com.avail.descriptor.types.VariableTypeDescriptor;
import com.avail.descriptor.variables.VariableDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;

/**
 * Create a new {@linkplain VariableDescriptor variable object} of the
 * specified {@link VariableTypeDescriptor variable type}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_VARIABLE
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_VARIABLE}.
	 */
	private L2_CREATE_VARIABLE ()
	{
		super(
			CONSTANT.is("outerType"),
			WRITE_BOXED.is("variable"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_VARIABLE instance = new L2_CREATE_VARIABLE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ConstantOperand outerType = instruction.operand(0);
		final L2WriteBoxedOperand variable = instruction.operand(1);

		// Not a constant, but we know the type...
		registerSet.removeConstantAt(variable.register());
		registerSet.typeAtPut(
			variable.register(), outerType.object, instruction);
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ConstantOperand outerType = instruction.operand(0);
		final L2WriteBoxedOperand variable = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(variable.registerString());
		builder.append(" ← new ");
		builder.append(outerType.object);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ConstantOperand outerType = instruction.operand(0);
		final L2WriteBoxedOperand variable = instruction.operand(1);

		// :: newVar = newVariableWithOuterType(outerType);
		translator.literal(method, outerType.object);
		VariableDescriptor.newVariableWithOuterTypeMethod.generateCall(method);
		translator.store(method, variable.register());
	}
}
