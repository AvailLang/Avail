/*
 * L2_JUMP_IF_KIND_OF_CONSTANT.java
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
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Jump to the target if the object is an instance of the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_KIND_OF_CONSTANT
extends L2Operation
{
	/**
	 * Construct an {@code L2_JUMP_IF_KIND_OF_CONSTANT}.
	 */
	private L2_JUMP_IF_KIND_OF_CONSTANT ()
	{
		super(
			READ_POINTER.is("value"),
			CONSTANT.is("constant type"),
			PC.is("is kind", SUCCESS),
			PC.is("is not kind", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_KIND_OF_CONSTANT instance =
		new L2_JUMP_IF_KIND_OF_CONSTANT();

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final A_Type type = instruction.constantAt(1);
		final L2PcOperand isKind = instruction.pcAt(2);
		final L2PcOperand notKind = instruction.pcAt(3);

		final @Nullable A_BasicObject constant = valueReg.constantOrNull();
		if (constant != null)
		{
			translator.addInstruction(
				L2_JUMP.instance,
				constant.isInstanceOf(type) ? isKind : notKind);
			return true;
		}
		final A_Type knownType = valueReg.type();
		if (knownType.isSubtypeOf(type))
		{
			// It's a subtype, so it must always pass the type test.
			translator.addInstruction(L2_JUMP.instance, isKind);
			return true;
		}
		final A_Type intersection = type.typeIntersection(knownType);
		if (intersection.isBottom())
		{
			// The types don't intersect, so it can't ever pass the type test.
			translator.addInstruction(L2_JUMP.instance, notKind);
			return true;
		}
		// The branch direction isn't known statically.  However, since it's
		// already known to be an X and we're testing for Y, we might be better
		// off testing for an X∩Y instead.  Let's just assume it's quicker for
		// now.  Eventually we can extend this idea to testing things other than
		// types, such as if we know we have a tuple but we want to dispatch
		// based on the tuple's size.
		if (!intersection.equals(type))
		{
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				valueReg,
				new L2ConstantOperand(intersection),
				isKind,
				notKind);
			return true;
		}
		// The test could not be eliminated or improved.
		return super.regenerate(instruction, registerSet, translator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final A_Type type = instruction.constantAt(1);
//		final L2PcOperand isKind = instruction.pcAt(2);
//		final L2PcOperand notKind = instruction.pcAt(3);

		assert registerSets.size() == 2;
		final RegisterSet isKindSet = registerSets.get(0);
//		final RegisterSet notKindSet = registerSets.get(1);

		final A_Type existingType = isKindSet.typeAt(valueReg.register());
		final A_Type intersection = existingType.typeIntersection(type);
		isKindSet.strengthenTestedTypeAtPut(valueReg.register(), intersection);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		final L2Operand[] operands = instruction.operands;
		final L2ObjectRegister valueRegister =
			instruction.readObjectRegisterAt(0).register();
		final L2Operand constant = operands[1];
//		final L2PcOperand isKind = instruction.pcAt(2);
//		final L2PcOperand notKind = instruction.pcAt(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(valueRegister);
		builder.append(" ∈ ");
		builder.append(constant);
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister valueRegister =
			instruction.readObjectRegisterAt(0).register();
		final A_Type type = instruction.constantAt(1);
		final L2PcOperand isKind = instruction.pcAt(2);
		final L2PcOperand notKind = instruction.pcAt(3);

		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto notKind;
		translator.load(method, valueRegister);
		translator.literal(method, type);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"isInstanceOf",
			getMethodDescriptor(BOOLEAN_TYPE, getType(A_Type.class)),
			true);
		translator.branch(method, instruction, IFNE, isKind, notKind);
	}
}
