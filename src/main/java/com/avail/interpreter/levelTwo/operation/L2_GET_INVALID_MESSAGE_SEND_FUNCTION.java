/*
 * L2_GET_INVALID_MESSAGE_SEND_FUNCTION.java
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Function;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.AvailRuntime.HookType.INVALID_MESSAGE_SEND;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.*;

/**
 * Store the {@linkplain AvailRuntime#invalidMessageSendFunction() invalid
 * message send function} into the supplied {@linkplain L2BoxedRegister
 * object register}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;todd@availlang.org&gt;
 */
public final class L2_GET_INVALID_MESSAGE_SEND_FUNCTION
extends L2Operation
{
	/**
	 * Construct an {@code L2_GET_INVALID_MESSAGE_SEND_FUNCTION}.
	 */
	private L2_GET_INVALID_MESSAGE_SEND_FUNCTION ()
	{
		super(
			WRITE_BOXED.is("invalid message send function"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_INVALID_MESSAGE_SEND_FUNCTION instance =
		new L2_GET_INVALID_MESSAGE_SEND_FUNCTION();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2WriteBoxedOperand function = instruction.operand(0);
		registerSet.typeAtPut(
			function.register(),
			INVALID_MESSAGE_SEND.functionType,
			instruction);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2WriteBoxedOperand function = instruction.operand(0);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(function.registerString());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2WriteBoxedOperand function = instruction.operand(0);

		// :: destination = interpreter.runtime().invalidMessageSendFunction();
		translator.loadInterpreter(method);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"runtime",
			getMethodDescriptor(getType(AvailRuntime.class)),
			false);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(AvailRuntime.class),
			"invalidMessageSendFunction",
			getMethodDescriptor(getType(A_Function.class)),
			false);
		translator.store(method, function.register());
	}
}
