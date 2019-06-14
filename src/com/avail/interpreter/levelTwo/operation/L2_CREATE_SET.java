/*
 * L2_CREATE_SET.java
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
import com.avail.descriptor.A_Set;
import com.avail.descriptor.SetDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * Create a set from the values in the specified vector of object registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_SET
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_SET}.
	 */
	private L2_CREATE_SET ()
	{
		super(
			READ_BOXED_VECTOR.is("values"),
			WRITE_BOXED.is("new set"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_SET instance = new L2_CREATE_SET();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final List<L2ReadBoxedOperand> elements =
			instruction.readVectorRegisterAt(0);
		final String destinationSetReg =
			instruction.writeBoxedRegisterAt(1).registerString();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationSetReg);
		builder.append(" ← {");
		for (int i = 0, limit = elements.size(); i < limit; i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			final L2ReadBoxedOperand element = elements.get(i);
			builder.append(element);
		}
		builder.append('}');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final List<L2ReadBoxedOperand> elements =
			instruction.readVectorRegisterAt(0);
		final L2BoxedRegister destinationSetReg =
			instruction.writeBoxedRegisterAt(1).register();

		// :: set = SetDescriptor.emptySet();
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(SetDescriptor.class),
			"emptySet",
			getMethodDescriptor(getType(A_Set.class)),
			false);
		for (final L2ReadBoxedOperand operand : elements)
		{
			// :: set = set.setWithElementCanDestroy(«register», true);
			translator.load(method, operand.register());
			translator.intConstant(method, 1);
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(A_Set.class),
				"setWithElementCanDestroy",
				getMethodDescriptor(
					getType(A_Set.class),
					getType(A_BasicObject.class),
					BOOLEAN_TYPE),
				true);
		}
		// :: destinationSet = set;
		translator.store(method, destinationSetReg);
	}
}
