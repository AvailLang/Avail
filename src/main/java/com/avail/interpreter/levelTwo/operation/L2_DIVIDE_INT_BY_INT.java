/*
 * L2_DIVIDE_INT_BY_INT.java
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
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.L2I;
import static org.objectweb.asm.Opcodes.LCMP;
import static org.objectweb.asm.Opcodes.LDIV;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LMUL;
import static org.objectweb.asm.Opcodes.LNEG;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.LSUB;
import static org.objectweb.asm.Type.LONG_TYPE;

/**
 * If the divisor is zero, then jump to the zero divisor label.  Otherwise
 * divide the dividend int by the divisor int.  If the quotient and remainder
 * each fit in an {@code int}, then store them and continue, otherwise jump to
 * an out-of-range label.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_DIVIDE_INT_BY_INT
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_DIVIDE_INT_BY_INT}.
	 */
	private L2_DIVIDE_INT_BY_INT ()
	{
		super(
			READ_INT.is("dividend"),
			READ_INT.is("divisor"),
			WRITE_INT.is("quotient"),
			WRITE_INT.is("remainder"),
			PC.is("out of range", FAILURE),
			PC.is("zero divisor", OFF_RAMP),
			PC.is("success", SUCCESS));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_DIVIDE_INT_BY_INT instance =
		new L2_DIVIDE_INT_BY_INT();

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps for division by zero or out-of-range.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadIntOperand dividend = instruction.operand(0);
		final L2ReadIntOperand divisor = instruction.operand(1);
		final L2WriteIntOperand quotient = instruction.operand(2);
		final L2WriteIntOperand remainder = instruction.operand(3);
//		final L2PcOperand zeroDivisor = instruction.operand(4);
//		final L2PcOperand success = instruction.operand(5);

		renderPreamble(instruction, builder);
		builder.append("quo=");
		builder.append(quotient.registerString());
		builder.append(", rem=");
		builder.append(remainder.registerString());
		builder.append(" ← ");
		builder.append(dividend.registerString());
		builder.append(" ÷ ");
		builder.append(divisor.registerString());
		renderOperandsStartingAt(instruction, 4, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadIntOperand dividend = instruction.operand(0);
		final L2ReadIntOperand divisor = instruction.operand(1);
		final L2WriteIntOperand quotient = instruction.operand(2);
		final L2WriteIntOperand remainder = instruction.operand(3);
		final L2PcOperand zeroDivisor = instruction.operand(4);
		final L2PcOperand success = instruction.operand(5);

		// :: if (divisor == 0) goto zeroDivisorIndex;
		translator.load(method, divisor.register());
		method.visitJumpInsn(IFEQ, translator.labelFor(zeroDivisor.offset()));

		// a/b for b<0:  use -(a/-b)
		// :: if (divisor < 0) quotient = -(dividend / -divisor);
		translator.load(method, divisor.register());
		final Label checkDividend = new Label();
		method.visitJumpInsn(IFGE, checkDividend);
		translator.load(method, dividend.register());
		method.visitInsn(I2L);
		translator.load(method, divisor.register());
		method.visitInsn(I2L);
		method.visitInsn(LNEG);
		method.visitInsn(LDIV);
		method.visitInsn(LNEG);
		final int quotientLong = translator.nextLocal(LONG_TYPE);
		method.visitVarInsn(LSTORE, quotientLong);
		final Label quotientComputed = new Label();
		method.visitJumpInsn(GOTO, quotientComputed);

		// a/b for a<0, b>0:  use -1-(-1-a)/b
		// :: else if (dividend < 0)
		// ::    quotient = -1L - ((-1L - dividend) / divisor);
		translator.load(method, dividend.register());
		final Label bothNonNegative = new Label();
		method.visitJumpInsn(IFGE, bothNonNegative);
		translator.longConstant(method, -1);
		method.visitInsn(DUP);
		translator.load(method, dividend.register());
		method.visitInsn(I2L);
		method.visitInsn(LSUB);
		translator.load(method, divisor.register());
		method.visitInsn(I2L);
		method.visitInsn(LDIV);
		method.visitInsn(LSUB);
		method.visitVarInsn(LSTORE, quotientLong);
		method.visitJumpInsn(GOTO, quotientComputed);

		// :: else quotient = dividend / divisor;
		translator.load(method, dividend.register());
		method.visitInsn(I2L);
		translator.load(method, divisor.register());
		method.visitInsn(I2L);
		method.visitInsn(LDIV);
		method.visitVarInsn(LSTORE, quotientLong);

		// Remainder is always non-negative.
		// :: remainder = dividend - (quotient * divisor);
		method.visitLabel(quotientComputed);
		translator.load(method, dividend.register());
		method.visitInsn(I2L);
		method.visitVarInsn(LLOAD, quotientLong);
		translator.load(method, divisor.register());
		method.visitInsn(I2L);
		method.visitInsn(LMUL);
		method.visitInsn(LSUB);
		final int remainderLong = translator.nextLocal(LONG_TYPE);
		method.visitVarInsn(LSTORE, remainderLong);

		// :: if (quotient != (int) quotient) goto outOfRange;
		method.visitVarInsn(LLOAD, quotientLong);
		method.visitInsn(DUP);
		method.visitInsn(L2I);
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(zeroDivisor.offset()));
		// ::  if (remainder != (int) remainder) goto outOfRange;
		method.visitVarInsn(LLOAD, remainderLong);
		method.visitInsn(DUP);
		method.visitInsn(L2I);
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(zeroDivisor.offset()));
		// ::    intQuotient = (int) quotient;
		method.visitVarInsn(LLOAD, quotientLong);
		method.visitInsn(L2I);
		translator.store(method, quotient.register());
		// ::    intRemainder = (int) remainder;
		method.visitVarInsn(LLOAD, remainderLong);
		method.visitInsn(L2I);
		translator.store(method, remainder.register());
		// ::    goto success;
		method.visitJumpInsn(GOTO, translator.labelFor(success.offset()));
	}
}
