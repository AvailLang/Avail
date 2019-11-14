/*
 * L2_MOVE_OUTER_VARIABLE.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Extract a captured "outer" variable from a function.  If the outer
 * variable is an actual {@linkplain VariableDescriptor variable} then the
 * variable itself is what gets moved into the destination register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MOVE_OUTER_VARIABLE
extends L2Operation
{
	/**
	 * Construct an {@code L2_MOVE_OUTER_VARIABLE}.
	 */
	private L2_MOVE_OUTER_VARIABLE ()
	{
		super(
			INT_IMMEDIATE.is("outer index"),
			READ_BOXED.is("function"),
			WRITE_BOXED.is("destination"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_MOVE_OUTER_VARIABLE instance =
		new L2_MOVE_OUTER_VARIABLE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2IntImmediateOperand outerIndex = instruction.operand(0);
		final L2ReadBoxedOperand function = instruction.operand(1);
		final L2WriteBoxedOperand destination = instruction.operand(2);

		if (registerSet.hasConstantAt(function.register()))
		{
			// The exact function is known.
			final A_Function fn =
				registerSet.constantAt(function.register());
			final AvailObject value = fn.outerVarAt(outerIndex.value);
			registerSet.constantAtPut(
				destination.register(), value, instruction);
		}
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2IntImmediateOperand outerIndex = instruction.operand(0);
		final L2ReadBoxedOperand function = instruction.operand(1);
		final L2WriteBoxedOperand destination = instruction.operand(2);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destination.registerString());
		builder.append(" ← ");
		builder.append(function.registerString());
		builder.append('[');
		builder.append(outerIndex.value);
		builder.append(']');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntImmediateOperand outerIndex = instruction.operand(0);
		final L2ReadBoxedOperand function = instruction.operand(1);
		final L2WriteBoxedOperand destination = instruction.operand(2);

		// :: destination = function.outerVarAt(outerIndex);
		translator.load(method, function.register());
		translator.literal(method, outerIndex.value);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Function.class),
			"outerVarAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			true);
		translator.store(method, destination.register());
	}
}
