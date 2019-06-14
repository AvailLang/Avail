/*
 * L2_ADD_INT_TO_INT.java
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
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
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
 * Add the value in one int register to another int register, jumping to the
 * specified target if the result does not fit in an int.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_ADD_INT_TO_INT
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_ADD_INT_TO_INT}.
	 */
	private L2_ADD_INT_TO_INT ()
	{
		super(
			READ_INT.is("augend"),
			READ_INT.is("addend"),
			WRITE_INT.is("sum"),
			PC.is("in range", SUCCESS),
			PC.is("out of range", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_ADD_INT_TO_INT instance = new L2_ADD_INT_TO_INT();

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps if the result doesn't fit in an int.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final String augendReg =
			instruction.readIntRegisterAt(0).registerString();
		final String addendReg =
			instruction.readIntRegisterAt(1).registerString();
		final String sumReg =
			instruction.writeIntRegisterAt(2).registerString();
//		final L2PcOperand inRange = instruction.pcAt(3);
//		final int outOfRangeOffset = instruction.pcOffsetAt(4);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(sumReg);
		builder.append(" ← ");
		builder.append(augendReg);
		builder.append(" + ");
		builder.append(addendReg);
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntRegister augendReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister addendReg =
			instruction.readIntRegisterAt(1).register();
		final L2IntRegister sumReg =
			instruction.writeIntRegisterAt(2).register();
		final L2PcOperand inRange = instruction.pcAt(3);
		final int outOfRangeOffset = instruction.pcOffsetAt(4);

		// :: longSum = (long) augend + (long) addend;
		translator.load(method, augendReg);
		method.visitInsn(I2L);
		translator.load(method, addendReg);
		method.visitInsn(I2L);
		method.visitInsn(LADD);
		method.visitInsn(DUP2);
		// :: intSum = (int) longSum;
		method.visitInsn(L2I);
		method.visitInsn(DUP);
		final int intSumLocal = translator.nextLocal(INT_TYPE);
		final Label intSumStart = new Label();
		method.visitLabel(intSumStart);
		method.visitVarInsn(ISTORE, intSumLocal);
		// :: if (longSum != intSum) goto outOfRange;
		method.visitInsn(I2L);
		method.visitInsn(LCMP);
		method.visitJumpInsn(IFNE, translator.labelFor(outOfRangeOffset));
		// :: else {
		// ::    sum = intSum;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(ILOAD, intSumLocal);
		final Label intSumEnd = new Label();
		method.visitLabel(intSumEnd);
		method.visitLocalVariable(
			"intSum",
			INT_TYPE.getDescriptor(),
			null,
			intSumStart,
			intSumEnd,
			intSumLocal);
		translator.store(method, sumReg);
		translator.jump(method, instruction, inRange);
	}
}
