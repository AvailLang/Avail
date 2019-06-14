/*
 * L2_UNBOX_FLOAT.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_FLOAT;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Unbox an {@code float} from an {@link AvailObject}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_UNBOX_FLOAT
extends L2Operation
{
	/**
	 * Construct an {@code L2_UNBOX_FLOAT}.
	 */
	private L2_UNBOX_FLOAT ()
	{
		super(
			READ_BOXED.is("source"),
			WRITE_FLOAT.is("destination"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_UNBOX_FLOAT instance = new L2_UNBOX_FLOAT();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final String sourceReg =
			instruction.readBoxedRegisterAt(0).registerString();
		final String destinationReg =
			instruction.writeFloatRegisterAt(1).registerString();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationReg);
		builder.append(" ← ");
		builder.append(sourceReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2BoxedRegister sourceReg =
			instruction.readBoxedRegisterAt(0).register();
		final L2FloatRegister destinationReg =
			instruction.writeFloatRegisterAt(1).register();

		// :: destination = source.extractDouble();
		translator.load(method, sourceReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Number.class),
			"extractDouble",
			getMethodDescriptor(DOUBLE_TYPE),
			true);
		translator.store(method, destinationReg);
	}
}
