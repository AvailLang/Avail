/*
 * L2_JUMP_IF_COMPARE_INT.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static org.objectweb.asm.Opcodes.*;

/**
 * Jump to the target if int1 is less than int2.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_COMPARE_INT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_LESS_THAN_CONSTANT}.
	 * @param opcode
	 *        The opcode number for this compare-and-branch.
	 * @param opcodeName
	 *        The symbolic name of the opcode for this compare-and-branch.
	 */
	private L2_JUMP_IF_COMPARE_INT (
		final int opcode,
		final String opcodeName)
	{
		super(
			READ_INT.is("int1"),
			READ_INT.is("int2"),
			PC.is("if true", SUCCESS),
			PC.is("if false", FAILURE));
		this.opcode = opcode;
		this.opcodeName = opcodeName;
	}

	/** An instance for testing whether a < b. */
	public static final L2_JUMP_IF_COMPARE_INT less =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPLT, "<");

	/** An instance for testing whether a > b. */
	public static final L2_JUMP_IF_COMPARE_INT greater =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPGT, ">");

	/** An instance for testing whether a ≤ b. */
	public static final L2_JUMP_IF_COMPARE_INT lessOrEqual =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPLE, "≤");

	/** An instance for testing whether a ≥ b. */
	public static final L2_JUMP_IF_COMPARE_INT greaterOrEqual =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPGE, "≥");

	/** An instance for testing whether a = b. */
	public static final L2_JUMP_IF_COMPARE_INT equal =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPEQ, "=");

	/** An instance for testing whether a ≠ b. */
	public static final L2_JUMP_IF_COMPARE_INT notEqual =
		new L2_JUMP_IF_COMPARE_INT(IF_ICMPNE, "≠");

	/**
	 * The opcode that compares and branches to the success case.
	 */
	private final int opcode;

	/**
	 * The symbolic name of the opcode that compares and branches to the success
	 * case.
	 */
	private final String opcodeName;

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadIntOperand int1Reg = instruction.operand(0);
		final L2ReadIntOperand int2Reg = instruction.operand(1);
//		final L2PcOperand ifTrue = instruction.operand(2);
//		final L2PcOperand ifFalse = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(int1Reg.registerString());
		builder.append(" ");
		builder.append(opcodeName);
		builder.append(" ");
		builder.append(int2Reg.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public String toString ()
	{
		return super.toString() + "(" + opcodeName + ")";
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadIntOperand int1Reg = instruction.operand(0);
		final L2ReadIntOperand int2Reg = instruction.operand(1);
		final L2PcOperand ifTrue = instruction.operand(2);
		final L2PcOperand ifFalse = instruction.operand(3);

		// :: if (int1 op int2) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, int1Reg.register());
		translator.load(method, int2Reg.register());
		emitBranch(translator, method, instruction, opcode, ifTrue, ifFalse);
	}
}
