/*
 * L2_JUMP_IF_GREATER_THAN_OBJECT.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Jump to the target if the first value is numerically greater than the second
 * value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_JUMP_IF_GREATER_THAN_OBJECT
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_GREATER_THAN_OBJECT().init(
			READ_POINTER.is("first value"),
			READ_POINTER.is("second value"),
			PC.is("if greater", SUCCESS),
			PC.is("if less or equal", FAILURE));

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister firstReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister secondReg =
			instruction.readObjectRegisterAt(1).register();
		final L2PcOperand ifTrue = instruction.pcAt(2);
		final L2PcOperand ifFalse = instruction.pcAt(3);

		// :: comparison = first.numericCompare(second);
		translator.load(method, firstReg);
		translator.load(method, secondReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"numericCompare",
			getMethodDescriptor(getType(Order.class), getType(A_Number.class)),
			true);
		// :: if (comparison.isMore()) goto ifTrue;
		// :: else goto ifFalse;
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Order.class),
			"isMore",
			getMethodDescriptor(BOOLEAN_TYPE),
			false);
		translator.branch(method, instruction, IFNE, ifTrue, ifFalse);
	}
}
