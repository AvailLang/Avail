/*
 * L2_MAKE_SUBOBJECTS_IMMUTABLE.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Mark as immutable all objects referred to from the specified object.
 * Copying a continuation as part of the {@link
 * L1Operation#L1Ext_doPushLabel} can make good use of this peculiar
 * instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MAKE_SUBOBJECTS_IMMUTABLE
extends L2Operation
{
	/**
	 * Construct an {@code L2_MAKE_SUBOBJECTS_IMMUTABLE}.
	 */
	private L2_MAKE_SUBOBJECTS_IMMUTABLE ()
	{
		super(
			READ_BOXED.is("input"),
			WRITE_BOXED.is("output"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_MAKE_SUBOBJECTS_IMMUTABLE instance =
		new L2_MAKE_SUBOBJECTS_IMMUTABLE();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final String inputReg =
			instruction.readBoxedRegisterAt(0).registerString();
		final String outputReg =
			instruction.writeBoxedRegisterAt(1).registerString();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(outputReg);
		builder.append(" ← ");
		builder.append(inputReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2BoxedRegister inputReg =
			instruction.readBoxedRegisterAt(0).register();
		final L2BoxedRegister outputReg =
			instruction.writeBoxedRegisterAt(1).register();

		// :: output = input.makeSubobjectsImmutable();
		translator.load(method, inputReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"makeSubobjectsImmutable",
			getMethodDescriptor(getType(AvailObject.class)),
			true);
		translator.store(method, outputReg);
	}
}
