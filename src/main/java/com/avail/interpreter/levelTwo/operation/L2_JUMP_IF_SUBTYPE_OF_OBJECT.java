/*
 * L2_JUMP_IF_SUBTYPE_OF_OBJECT.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.operation.L2ConditionalJump.BranchReduction.*;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Conditionally jump, depending on whether the first type is a subtype of the
 * second type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_SUBTYPE_OF_OBJECT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_SUBTYPE_OF_Object}.
	 */
	private L2_JUMP_IF_SUBTYPE_OF_OBJECT ()
	{
		super(
			READ_BOXED.is("first type"),
			READ_BOXED.is("second type"),
			PC.is("is subtype", SUCCESS),
			PC.is("not subtype", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_SUBTYPE_OF_OBJECT instance =
		new L2_JUMP_IF_SUBTYPE_OF_OBJECT();

	@Override
	public BranchReduction branchReduction (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadBoxedOperand firstReg = instruction.operand(0);
		final L2ReadBoxedOperand secondReg = instruction.operand(1);
//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);

		final @Nullable A_Type exactFirstType =
			firstReg.constantOrNull();
		final @Nullable A_Type exactSecondType =
			secondReg.constantOrNull();
		if (exactSecondType != null)
		{
			if (firstReg.type().isSubtypeOf(secondReg.type()))
			{
				return AlwaysTaken;
			}
		}
		else
		{
			for (final A_Type excludedSecondMeta
				: secondReg.restriction().excludedTypes)
			{
				if (firstReg.type().isSubtypeOf(excludedSecondMeta))
				{
					// The first type falls entirely in a type tree excluded
					// from the second restriction.
					return NeverTaken;
				}
			}
		}
		return SometimesTaken;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand firstReg = instruction.operand(0);
		final L2ReadBoxedOperand secondReg = instruction.operand(1);
//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(firstReg.registerString());
		builder.append(" ⊆ ");
		builder.append(secondReg.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand firstReg = instruction.operand(0);
		final L2ReadBoxedOperand secondReg = instruction.operand(1);
		final L2PcOperand isSubtype = instruction.operand(2);
		final L2PcOperand notSubtype = instruction.operand(3);

		// :: if (first.isSubtypeOf(second)) goto isSubtype;
		// :: else goto notSubtype;
		translator.load(method, firstReg.register());
		translator.load(method, secondReg.register());
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Type.class),
			"isSubtypeOf",
			getMethodDescriptor(BOOLEAN_TYPE, getType(A_Type.class)),
			true);
		emitBranch(
			translator, method, instruction, IFNE, isSubtype, notSubtype);
	}
}
