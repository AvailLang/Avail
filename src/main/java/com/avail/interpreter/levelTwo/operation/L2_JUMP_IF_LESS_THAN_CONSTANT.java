/*
 * L2_JUMP_IF_LESS_THAN_CONSTANT.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Jump to the target if the object is numerically less than the constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_LESS_THAN_CONSTANT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_LESS_THAN_CONSTANT}.
	 */
	private L2_JUMP_IF_LESS_THAN_CONSTANT ()
	{
		super(
			READ_BOXED.is("value"),
			CONSTANT.is("constant"),
			PC.is("if less", SUCCESS),
			PC.is("if greater or equal", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_LESS_THAN_CONSTANT instance =
		new L2_JUMP_IF_LESS_THAN_CONSTANT();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ConstantOperand constant = instruction.operand(1);
		final L2PcOperand ifLess = instruction.operand(2);
		final L2PcOperand ifNotLess = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(value.registerString());
		builder.append(" < ");
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
		final L2PcOperand ifLess = instruction.operand(2);
		final L2PcOperand ifNotLess = instruction.operand(3);

		// :: comparison = first.numericCompare(second);
		translator.load(method, value.register());
		translator.literal(method, constant.object);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"numericCompare",
			getMethodDescriptor(getType(Order.class), getType(A_Number.class)),
			true);
		// :: if (comparison.isLess()) goto ifTrue;
		// :: else goto ifFalse;
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Order.class),
			"isLess",
			getMethodDescriptor(BOOLEAN_TYPE),
			false);
		emitBranch(translator, method, instruction, IFNE, ifLess, ifNotLess);
	}
}
