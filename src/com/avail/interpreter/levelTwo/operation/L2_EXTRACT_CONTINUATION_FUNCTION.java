/*
 * L2_EXTRACT_CONTINUATION_FUNCTION.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Extract the {@link A_Function} from an {@link A_Continuation}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_EXTRACT_CONTINUATION_FUNCTION
extends L2Operation
{
	/**
	 * Construct an {@code L2_EXTRACT_CONTINUATION_FUNCTION}.
	 */
	private L2_EXTRACT_CONTINUATION_FUNCTION ()
	{
		super(
			READ_POINTER.is("continuation"),
			WRITE_POINTER.is("extracted function"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_EXTRACT_CONTINUATION_FUNCTION instance =
		new L2_EXTRACT_CONTINUATION_FUNCTION();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister functionReg =
			instruction.writeObjectRegisterAt(1).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(functionReg);
		builder.append(" ← ");
		builder.append(continuationReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// Extract the function from the given continuation.
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister functionReg =
			instruction.writeObjectRegisterAt(1).register();

		// :: function = continuation.function();
		translator.load(method, continuationReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Continuation.class),
			"function",
			getMethodDescriptor(getType(AvailObject.class)),
			true);
		translator.store(method, functionReg);
	}
}
