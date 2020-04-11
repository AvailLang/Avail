/*
 * L2_GET_TYPE.java
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

import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor;
import com.avail.descriptor.types.InstanceMetaDescriptor;
import com.avail.descriptor.types.InstanceTypeDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.types.TypeDescriptor.Types.NONTYPE;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Extract the {@link InstanceTypeDescriptor exact type} of an object in a
 * register, writing the type to another register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_GET_TYPE
extends L2Operation
{
	/**
	 * Construct an {@code L2_GET_TYPE}.
	 */
	private L2_GET_TYPE ()
	{
		super(
			READ_BOXED.is("value"),
			WRITE_BOXED.is("value's type"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_TYPE instance = new L2_GET_TYPE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand valueReg = instruction.operand(0);
		final L2WriteBoxedOperand typeReg = instruction.operand(1);

		registerSet.removeConstantAt(typeReg.register());
		if (registerSet.hasTypeAt(valueReg.register()))
		{
			final A_Type type = registerSet.typeAt(valueReg.register());
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final A_Type meta = instanceMeta(type);
			registerSet.typeAtPut(typeReg.register(), meta, instruction);
		}
		else
		{
			registerSet.typeAtPut(
				typeReg.register(), topMeta(), instruction);
		}

		if (registerSet.hasConstantAt(valueReg.register())
			&& !registerSet.constantAt(valueReg.register()).isType())
		{
			registerSet.constantAtPut(
				typeReg.register(),
				instanceTypeOrMetaOn(
					registerSet.constantAt(valueReg.register())),
				instruction);
		}
	}

	/**
	 * Extract the register providing the value whose type is to be produced.
	 *
	 * @param instruction The instruction to examine.
	 * @return The {@link L2ReadBoxedOperand} supplying the value.
	 */
	public static L2ReadBoxedOperand sourceValueOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.operand(0);
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2WriteBoxedOperand type = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(type.registerString());
		builder.append(" ← ");
		builder.append(value.registerString());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2WriteBoxedOperand type = instruction.operand(1);

		translator.load(method, value.register());
		// [value]
		if (value.restriction().containedByType(NONTYPE.o()))
		{
			// The value will *never* be a type.
			InstanceTypeDescriptor.instanceTypeMethod.generateCall(method);
		}
		else if (value.restriction().containedByType(topMeta()))
		{
			// The value will *always* be a type. Strengthen to AvailObject.
			InstanceMetaDescriptor.instanceMetaMethod.generateCall(method);
			method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		}
		else
		{
			// The value could be either a type or a non-type.
			AbstractEnumerationTypeDescriptor
				.instanceTypeOrMetaOnMethod
				.generateCall(method);
			// Strengthen to AvailObject
			method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		}
		// [type]
		translator.store(method, type.register());
	}
}
