/*
 * L2_SUBTRACT_INT_MINUS_INT_CONSTANT.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.INT_TYPE;

/**
 * Extract an int from the specified constant, and subtract it from an int
 * register, jumping to the target label if the result won't fit in an int.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_SUBTRACT_INT_MINUS_INT_CONSTANT
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_SUBTRACT_INT_MINUS_INT_CONSTANT}.
	 */
	private L2_SUBTRACT_INT_MINUS_INT_CONSTANT ()
	{
		super(
			READ_INT.is("minuend"),
			INT_IMMEDIATE.is("subtrahend"),
			WRITE_INT.is("difference"),
			PC.is("in range", SUCCESS),
			PC.is("out of range", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SUBTRACT_INT_MINUS_INT_CONSTANT instance =
		new L2_SUBTRACT_INT_MINUS_INT_CONSTANT();

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadIntOperand minuend = instruction.operand(0);
		final L2IntImmediateOperand subtrahend = instruction.operand(1);
		final L2WriteIntOperand difference = instruction.operand(2);
//		final L2PcOperand inRange = instruction.operand(3);
//		final L2PcOperand outOfRange = instruction.operand(4);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(difference.registerString());
		builder.append(" ← ");
		builder.append(minuend.registerString());
		builder.append(" - ");
		builder.append(subtrahend.value);
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadIntOperand minuend = instruction.operand(0);
		final L2IntImmediateOperand subtrahend = instruction.operand(1);
		final L2WriteIntOperand difference = instruction.operand(2);
		final L2PcOperand inRange = instruction.operand(3);
		final L2PcOperand outOfRange = instruction.operand(4);

		// :: longDifference = (long) minuend - (long) subtrahend;
		translator.load(method, minuend.register());
		method.visitInsn(I2L);
		translator.literal(method, subtrahend.value);
		method.visitInsn(I2L);
		method.visitInsn(LSUB);
		method.visitInsn(DUP2);
		// :: intDifference = (int) longDifference;
		method.visitInsn(L2I);
		method.visitInsn(DUP);
		final int intDifferenceLocal = translator.nextLocal(INT_TYPE);
		final Label intDifferenceStart = new Label();
		method.visitLabel(intDifferenceStart);
		method.visitVarInsn(ISTORE, intDifferenceLocal);
		// :: if (longDifference != intDifference) goto outOfRange;
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(outOfRange.offset()));
		// :: else {
		// ::    sum = intDifference;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(ILOAD, intDifferenceLocal);
		final Label intDifferenceEnd = new Label();
		method.visitLabel(intDifferenceEnd);
		method.visitLocalVariable(
			"intDifference",
			INT_TYPE.getDescriptor(),
			null,
			intDifferenceStart,
			intDifferenceEnd,
			intDifferenceLocal);
		translator.store(method, difference.register());
		translator.jump(method, instruction, inRange);
	}
}
