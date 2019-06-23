/*
 * L2_JUMP_IF_SUBTYPE_OF_CONSTANT.java
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
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operation.L2ConditionalJump.BranchReduction.*;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Conditionally jump, depending on whether the type to check is a subtype of
 * the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_SUBTYPE_OF_CONSTANT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_SUBTYPE_OF_CONSTANT}.
	 */
	private L2_JUMP_IF_SUBTYPE_OF_CONSTANT ()
	{
		super(
			READ_BOXED.is("type to check"),
			CONSTANT.is("constant type"),
			PC.is("is subtype", SUCCESS),
			PC.is("not subtype", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_SUBTYPE_OF_CONSTANT instance =
		new L2_JUMP_IF_SUBTYPE_OF_CONSTANT();

	@Override
	public BranchReduction branchReduction (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadBoxedOperand typeToCheck = instruction.operand(0);
		final L2ConstantOperand constantType = instruction.operand(1);
//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);

		final @Nullable A_BasicObject exactType = typeToCheck.constantOrNull();
		if (exactType != null)
		{
			return exactType.isInstanceOf(constantType.object)
				? AlwaysTaken
				: NeverTaken;
		}
		if (typeToCheck.type().instance().isSubtypeOf(constantType.object))
		{
			// It's a subtype, so it must always pass the type test.
			return AlwaysTaken;
		}
		final A_Type intersection =
			typeToCheck.type().instance().typeIntersection(constantType.object);
		if (intersection.isBottom())
		{
			// The types don't intersect, so it can't ever pass the type test.
			return NeverTaken;
		}
		return SometimesTaken;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand typeToCheck = instruction.operand(0);
		final L2ConstantOperand constantType = instruction.operand(1);
//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);

		assert registerSets.size() == 2;
		final RegisterSet isSubtypeSet = registerSets.get(0);
//		final RegisterSet notSubtypeSet = registerSets.get(1);

		assert isSubtypeSet.hasTypeAt(typeToCheck.register());
		if (isSubtypeSet.hasConstantAt(typeToCheck.register()))
		{
			// The *exact* type is already known.  Don't weaken it by recording
			// type information for it (a meta).
		}
		else
		{
			final A_Type existingMeta =
				isSubtypeSet.typeAt(typeToCheck.register());
			final A_Type existingType = existingMeta.instance();
			final A_Type intersectionType =
				existingType.typeIntersection(constantType.object);
			final A_Type intersectionMeta = instanceMeta(intersectionType);
			isSubtypeSet.strengthenTestedTypeAtPut(
				typeToCheck.register(), intersectionMeta);
		}
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand typeToCheck = instruction.operand(0);
		final L2ConstantOperand constantType = instruction.operand(1);
//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(typeToCheck.registerString());
		builder.append(" ⊆ ");
		builder.append(constantType.object);
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand typeToCheck = instruction.operand(0);
		final L2ConstantOperand constantType = instruction.operand(1);
		final L2PcOperand isSubtype = instruction.operand(2);
		final L2PcOperand notSubtype = instruction.operand(3);

		// :: if (type.isSubtypeOf(constant)) goto isSubtype;
		// :: else goto notSubtype;
		translator.load(method, typeToCheck.register());
		translator.literal(method, constantType.object);
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
