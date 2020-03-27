/*
 * L2_ADD_INT_TO_INT.java
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
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.int32;
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
			WRITE_INT.is("sum", SUCCESS),
			PC.is("out of range", FAILURE),
			PC.is("in range", SUCCESS));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_ADD_INT_TO_INT instance = new L2_ADD_INT_TO_INT();

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
//		final L2ReadIntOperand augendReg = instruction.operand(0);
//		final L2ReadIntOperand addendReg = instruction.operand(1);
		final L2WriteIntOperand sumReg = instruction.operand(2);
//		final L2PcOperand outOfRange = instruction.operand(3);
		final L2PcOperand inRange = instruction.operand(4);

		super.instructionWasAdded(instruction, manifest);
		inRange.manifest().intersectType(sumReg.pickSemanticValue(), int32());
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps if the result doesn't fit in an int.
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadIntOperand augend = instruction.operand(0);
		final L2ReadIntOperand addend = instruction.operand(1);
		final L2WriteIntOperand sum = instruction.operand(2);
//		final L2PcOperand outOfRange = instruction.operand(3);
//		final L2PcOperand inRange = instruction.operand(4);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(sum.registerString());
		builder.append(" ← ");
		builder.append(augend.registerString());
		builder.append(" + ");
		builder.append(addend.registerString());
		renderOperandsStartingAt(instruction, 3, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadIntOperand augendReg = instruction.operand(0);
		final L2ReadIntOperand addendReg = instruction.operand(1);
		final L2WriteIntOperand sumReg = instruction.operand(2);
		final L2PcOperand outOfRange = instruction.operand(3);
		final L2PcOperand inRange = instruction.operand(4);

		// :: longSum = (long) augend + (long) addend;
		translator.load(method, augendReg.register());
		method.visitInsn(I2L);
		translator.load(method, addendReg.register());
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
		method.visitJumpInsn(
			IFNE, translator.labelFor(outOfRange.offset()));
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
		translator.store(method, sumReg.register());
		translator.jump(method, instruction, inRange);
	}
}
