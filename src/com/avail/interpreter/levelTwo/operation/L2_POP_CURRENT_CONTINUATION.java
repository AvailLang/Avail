/*
 * L2_POP_CURRENT_CONTINUATION.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Ask the {@link Interpreter} for the current continuation, writing it into the
 * provided register.  Write its caller back into the interpreter's current
 * continuation field.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_POP_CURRENT_CONTINUATION
extends L2Operation
{
	/**
	 * Construct an {@code L2_POP_CURRENT_CONTINUATION}.
	 */
	private L2_POP_CURRENT_CONTINUATION ()
	{
		super(
			WRITE_POINTER.is("current continuation"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_POP_CURRENT_CONTINUATION instance =
		new L2_POP_CURRENT_CONTINUATION();

	@Override
	public boolean hasSideEffect ()
	{
		// It updates the current continuation of the interpreter.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2Operand targetReg = instruction.operand(0);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(targetReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister targetReg =
			instruction.writeObjectRegisterAt(0).register();

		// :: continuation = interpreter.reifiedContinuation;
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"reifiedContinuation",
			getDescriptor(AvailObject.class));
		method.visitInsn(DUP);
		// :: interpreter.reifiedContinuation = continuation.caller();
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Continuation.class),
			"caller",
			getMethodDescriptor(getType(A_Continuation.class)),
			true);
		method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		translator.loadInterpreter(method);
		// Stack order is: reifiedContinuation caller interpreter ->
		// Need: reifiedContinuation interpreter caller ->
		method.visitInsn(SWAP);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"reifiedContinuation",
			getDescriptor(AvailObject.class));
		// :: target = continuation;
		translator.store(method, targetReg);
	}
}
