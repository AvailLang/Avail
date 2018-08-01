/*
 * L2_GET_TYPE.java
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
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AbstractEnumerationTypeDescriptor;
import com.avail.descriptor.InstanceTypeDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

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
			READ_POINTER.is("value"),
			WRITE_POINTER.is("value's type"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_TYPE instance = new L2_GET_TYPE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2WritePointerOperand typeReg =
			instruction.writeObjectRegisterAt(1);

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
	 * @return The {@link L2ReadPointerOperand} supplying the value.
	 */
	public static L2ReadPointerOperand sourceValueOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.readObjectRegisterAt(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ObjectRegister valueReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister typeReg =
			instruction.writeObjectRegisterAt(1).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(typeReg);
		builder.append(" ← ");
		builder.append(valueReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister valueReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister typeReg =
			instruction.writeObjectRegisterAt(1).register();

		// :: type = instanceTypeOfMetaOn(value);
		translator.load(method, valueReg);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(AbstractEnumerationTypeDescriptor.class),
			"instanceTypeOrMetaOn",
			getMethodDescriptor(
				getType(A_Type.class),
				getType(A_BasicObject.class)),
			false);
		translator.store(method, typeReg);
	}
}
