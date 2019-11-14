/*
 * L2_JUMP_IF_EQUALS_CONSTANT.java
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

import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForConstant;
import static com.avail.interpreter.levelTwo.operation.L2ConditionalJump.BranchReduction.*;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Jump to {@code "if equal"} if the value equals the constant, otherwise jump
 * to {@code "if unequal"}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_EQUALS_CONSTANT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_EQUALS_CONSTANT}.
	 */
	private L2_JUMP_IF_EQUALS_CONSTANT ()
	{
		super(
			READ_BOXED.is("value"),
			CONSTANT.is("constant"),
			PC.is("if equal", SUCCESS),
			PC.is("if unequal", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_EQUALS_CONSTANT instance =
		new L2_JUMP_IF_EQUALS_CONSTANT();

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand reader = instruction.operand(0);
		final L2ConstantOperand constant = instruction.operand(1);
		final L2PcOperand ifEqual = instruction.operand(2);
		final L2PcOperand ifNotEqual = instruction.operand(3);

		// Ensure the new write ends up in the same synonym as the source.
		reader.instructionWasAdded(instruction, manifest);
		constant.instructionWasAdded(instruction, manifest);
		ifEqual.instructionWasAdded(instruction, manifest);
		ifNotEqual.instructionWasAdded(instruction, manifest);

		// Restrict the value to the constant along the ifEqual branch, and
		// exclude the constant along the ifNotEqual branch.
		final TypeRestriction oldRestriction = reader.restriction();
		ifEqual.manifest().setRestriction(
			reader.semanticValue(),
			restrictionForConstant(constant.object, BOXED));
		ifNotEqual.manifest().setRestriction(
			reader.semanticValue(),
			oldRestriction.minusValue(constant.object));
	}

	@Override
	public BranchReduction branchReduction (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ConstantOperand constant = instruction.operand(1);

		final @Nullable A_BasicObject valueOrNull = value.constantOrNull();
		if (valueOrNull != null)
		{
			// Compare them right now.
			return valueOrNull.equals(constant.object)
				? AlwaysTaken
				: NeverTaken;
		}
		if (!constant.object.isInstanceOf(value.type()))
		{
			// They can't be equal.
			return NeverTaken;
		}
		// Otherwise it's still contingent.
		return SometimesTaken;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ConstantOperand constant = instruction.operand(1);
//		final L2PcOperand ifEqual = instruction.operand(2);
//		final L2PcOperand ifUnequal = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(value.registerString());
		builder.append(" = ");
		builder.append(constant.object);
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ConstantOperand constant = instruction.operand(1);
		final L2PcOperand ifEqual = instruction.operand(2);
		final L2PcOperand ifUnequal = instruction.operand(3);

		// :: if (value.equals(constant)) goto ifEqual;
		// :: else goto ifUnequal;
		translator.load(method, value.register());
		translator.literal(method, constant.object);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"equals",
			getMethodDescriptor(BOOLEAN_TYPE, getType(A_BasicObject.class)),
			true);
		emitBranch(translator, method, instruction, IFNE, ifEqual, ifUnequal);
	}
}
