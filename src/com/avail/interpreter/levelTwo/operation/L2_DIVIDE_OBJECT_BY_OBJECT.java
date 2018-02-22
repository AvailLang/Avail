/*
 * L2_DIVIDE_OBJECT_BY_OBJECT.java
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
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Divide the dividend value by the divisor value.  If the calculation causes an
 * {@link ArithmeticException}, jump to the specified label, otherwise set the
 * quotient and remainder registers and continue with the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_DIVIDE_OBJECT_BY_OBJECT
extends L2Operation
{
	/**
	 * Construct an {@code L2_DIVIDE_OBJECT_BY_OBJECT}.
	 */
	private L2_DIVIDE_OBJECT_BY_OBJECT ()
	{
		super(
			READ_POINTER.is("dividend"),
			READ_POINTER.is("divisor"),
			WRITE_POINTER.is("quotient"),
			WRITE_POINTER.is("remainder"),
			PC.is("if undefined", OFF_RAMP),
			PC.is("success", SUCCESS));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_DIVIDE_OBJECT_BY_OBJECT instance =
		new L2_DIVIDE_OBJECT_BY_OBJECT();

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps for division by zero.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		final L2ObjectRegister dividendReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister divisorReg =
			instruction.readObjectRegisterAt(1).register();
		final L2ObjectRegister quotientReg =
			instruction.writeObjectRegisterAt(2).register();
		final L2ObjectRegister remainderReg =
			instruction.writeObjectRegisterAt(3).register();
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
		final L2ObjectRegister dividendReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister divisorReg =
			instruction.readObjectRegisterAt(1).register();
		final L2ObjectRegister quotientReg =
			instruction.writeObjectRegisterAt(2).register();
		final L2ObjectRegister remainderReg =
			instruction.writeObjectRegisterAt(3).register();
		final L2PcOperand undefined = instruction.pcAt(4);
		final int successIndex = instruction.pcOffsetAt(5);

		// :: try {
		final Label tryStart = new Label();
		final Label catchStart = new Label();
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(ArithmeticException.class));
		method.visitLabel(tryStart);
		// ::    quotient = dividend.divideCanDestroy(divisor, false);
		translator.load(method, dividendReg);
		method.visitInsn(DUP);
		translator.load(method, divisorReg);
		method.visitInsn(ICONST_0);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"divideCanDestroy",
			getMethodDescriptor(
				getType(A_Number.class),
				getType(A_Number.class),
				BOOLEAN_TYPE),
			true);
		method.visitInsn(DUP);
		translator.store(method, quotientReg);
		// ::    remainder = dividend.minusCanDestroy(
		// ::       quotient.timesCanDestroy(divisor, false),
		// ::       false);
		translator.load(method, divisorReg);
		method.visitInsn(ICONST_0);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"timesCanDestroy",
			getMethodDescriptor(
				getType(A_Number.class),
				getType(A_Number.class),
				BOOLEAN_TYPE),
			true);
		method.visitInsn(ICONST_0);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"minusCanDestroy",
			getMethodDescriptor(
				getType(A_Number.class),
				getType(A_Number.class),
				BOOLEAN_TYPE),
			true);
		translator.store(method, remainderReg);
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// ArithmeticException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(GOTO, translator.labelFor(successIndex));
		// :: } catch (ArithmeticException e) {
		method.visitLabel(catchStart);
		method.visitInsn(POP);
		// ::    goto undefined;
		translator.branch(method, instruction, undefined);
		// :: }
	}
}
