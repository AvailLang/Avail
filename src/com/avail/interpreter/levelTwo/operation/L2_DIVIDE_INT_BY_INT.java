/*
 * L2_DIVIDE_INT_BY_INT.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
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
public class L2_DIVIDE_INT_BY_INT
extends L2Operation
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
		assert this == instruction.operation;
		final L2IntRegister dividendReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister divisorReg =
			instruction.readIntRegisterAt(1).register();
		final L2IntRegister quotientReg =
			instruction.writeIntRegisterAt(2).register();
		final L2IntRegister remainderReg =
			instruction.writeIntRegisterAt(3).register();
//		final L2PcOperand undefined = instruction.pcAt(4);
//		final int successIndex = instruction.pcOffsetAt(5);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(quotientReg);
		builder.append(", ");
		builder.append(remainderReg);
		builder.append(" ← ");
		builder.append(dividendReg);
		builder.append(" ÷ ");
		builder.append(divisorReg);
		renderOperandsStartingAt(instruction, 4, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntRegister dividendReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister divisorReg =
			instruction.readIntRegisterAt(1).register();
		final L2IntRegister intQuotientReg =
			instruction.writeIntRegisterAt(2).register();
		final L2IntRegister intRemainderReg =
			instruction.writeIntRegisterAt(3).register();
		final int outOfRangeIndex = instruction.pcOffsetAt(4);
		final int zeroDivisorIndex = instruction.pcOffsetAt(5);
		final int successIndex = instruction.pcOffsetAt(6);

		// :: if (divisor == 0) goto zeroDivisorIndex;
		translator.load(method, divisorReg);
		method.visitJumpInsn(IFEQ, translator.labelFor(zeroDivisorIndex));

		// a/b for b<0:  use -(a/-b)
		// :: if (divisor < 0) quotient = -(dividend / -divisor);
		translator.load(method, divisorReg);
		final Label checkDividend = new Label();
		method.visitJumpInsn(IFGE, checkDividend);
		translator.load(method, dividendReg);
		method.visitInsn(I2L);
		translator.load(method, divisorReg);
		method.visitInsn(I2L);
		method.visitInsn(LNEG);
		method.visitInsn(LDIV);
		method.visitInsn(LNEG);
		final int quotient = translator.nextLocal(LONG_TYPE);
		method.visitVarInsn(LSTORE, quotient);
		final Label quotientComputed = new Label();
		method.visitJumpInsn(GOTO, quotientComputed);

		// a/b for a<0, b>0:  use -1-(-1-a)/b
		// :: else if (dividend < 0)
		// ::    quotient = -1L - ((-1L - dividend) / divisor);
		translator.load(method, dividendReg);
		final Label bothNonNegative = new Label();
		method.visitJumpInsn(IFGE, bothNonNegative);
		translator.longConstant(method, -1);
		method.visitInsn(DUP);
		translator.load(method, dividendReg);
		method.visitInsn(I2L);
		method.visitInsn(LSUB);
		translator.load(method, divisorReg);
		method.visitInsn(I2L);
		method.visitInsn(LDIV);
		method.visitInsn(LSUB);
		method.visitVarInsn(LSTORE, quotient);
		method.visitJumpInsn(GOTO, quotientComputed);

		// :: else quotient = dividend / divisor;
		translator.load(method, dividendReg);
		method.visitInsn(I2L);
		translator.load(method, divisorReg);
		method.visitInsn(I2L);
		method.visitInsn(LDIV);
		method.visitVarInsn(LSTORE, quotient);

		// Remainder is always non-negative.
		// :: remainder = dividend - (quotient * divisor);
		method.visitLabel(quotientComputed);
		translator.load(method, dividendReg);
		method.visitInsn(I2L);
		method.visitVarInsn(LLOAD, quotient);
		translator.load(method, divisorReg);
		method.visitInsn(I2L);
		method.visitInsn(LMUL);
		method.visitInsn(LSUB);
		final int remainder = translator.nextLocal(LONG_TYPE);
		method.visitVarInsn(LSTORE, remainder);
		
		// :: if (quotient != (int) quotient) goto outOfRange;
		method.visitVarInsn(LLOAD, quotient);
		method.visitInsn(DUP);
		method.visitInsn(L2I);
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(outOfRangeIndex));
		// ::  if (remainder != (int) remainder) goto outOfRange;
		method.visitVarInsn(LLOAD, remainder);
		method.visitInsn(DUP);
		method.visitInsn(L2I);
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(outOfRangeIndex));
		// ::    intQuotient = (int) quotient;
		method.visitVarInsn(LLOAD, quotient);
		method.visitInsn(L2I);
		translator.store(method, intQuotientReg);
		// ::    intRemainder = (int) remainder;
		method.visitVarInsn(LLOAD, remainder);
		method.visitInsn(L2I);
		translator.store(method, intRemainderReg);
		// ::    goto success;
		method.visitJumpInsn(GOTO, translator.labelFor(successIndex));
	}
}
