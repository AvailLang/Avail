/*
 * L2_FUNCTION_PARAMETER_TYPE.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.RegisterState;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Given an input register containing a function (not a function type), extract
 * its Nth parameter type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_FUNCTION_PARAMETER_TYPE
extends L2Operation
{
	/**
	 * Construct an {@code L2_FUNCTION_PARAMETER_TYPE}.
	 */
	private L2_FUNCTION_PARAMETER_TYPE ()
	{
		super(
			READ_POINTER.is("function"),
			INT_IMMEDIATE.is("parameter index"),
			WRITE_POINTER.is("parameter type"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_FUNCTION_PARAMETER_TYPE instance =
		new L2_FUNCTION_PARAMETER_TYPE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadPointerOperand functionReg =
			instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.intImmediateAt(1);
		final L2WritePointerOperand outputParamTypeReg =
			instruction.writeObjectRegisterAt(2);

		// Function types are contravariant, so we may have to fall back on
		// just saying the parameter type must be a type and can't be top –
		// i.e., any's type.
		if (registerSet.hasConstantAt(functionReg.register()))
		{
			// Exact function is known.
			final A_Function function = registerSet.constantAt(functionReg.register());
			final A_Type functionType = function.code().functionType();
			registerSet.constantAtPut(
				outputParamTypeReg.register(),
				functionType.argsTupleType().typeAtIndex(paramIndex),
				instruction);
			return;
		}
		final List<L2Instruction> sources =
			registerSet.stateForReading(functionReg.register()).sourceInstructions();
		if (sources.size() == 1)
		{
			final L2Instruction source = sources.get(0);
			if (source.operation() == L2_CREATE_FUNCTION.instance)
			{
				final A_RawFunction code = sources.get(0).constantAt(0);
				final A_Type functionType = code.functionType();
				registerSet.constantAtPut(
					outputParamTypeReg.register(),
					functionType.argsTupleType().typeAtIndex(paramIndex),
					instruction);
				return;
			}
		}
		// We don't know the exact type of the block argument, so since it's
		// contravariant we can only assume it's some non-top type.
		registerSet.typeAtPut(
			outputParamTypeReg.register(), anyMeta(), instruction);
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadPointerOperand functionReg =
			instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.intImmediateAt(1);
		final L2WritePointerOperand outputParamTypeReg =
			instruction.writeObjectRegisterAt(2);

		@Nullable A_Type functionType = null;
		if (registerSet.hasConstantAt(functionReg.register()))
		{
			final A_Function constantFunction =
				registerSet.constantAt(functionReg.register());
			functionType = constantFunction.code().functionType();
		}
		else
		{
			final RegisterState state =
				registerSet.stateForReading(functionReg.register());
			final List<L2Instruction> sources = state.sourceInstructions();
			if (sources.size() == 1)
			{
				// Exactly one instruction provides the function.
				final L2Instruction closeInstruction = sources.get(0);
				if (closeInstruction.operation() instanceof L2_CREATE_FUNCTION)
				{
					// The creation of the function is visible.  We can get to
					// the code, which gives a precise functionType (a kind, not
					// all the way down to the instance type).
					final A_RawFunction code = closeInstruction.constantAt(0);
					functionType = code.functionType();
				}
			}
		}
		if (functionType != null)
		{
			// The exact function type (at least to kind) is known statically.
			// Replace this instruction with a constant move.
			final A_Type paramType =
				functionType.argsTupleType().typeAtIndex(paramIndex);
			generator.addInstruction(
				L2_MOVE_CONSTANT.instance,
				new L2ConstantOperand(paramType),
				outputParamTypeReg);
			return true;
		}
		return super.regenerate(instruction, registerSet, generator);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2Operand function = instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.intImmediateAt(1);
		final L2ObjectRegister outputParamTypeReg =
			instruction.writeObjectRegisterAt(2).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(outputParamTypeReg);
		builder.append(" ← ");
		builder.append(function);
		builder.append('[');
		builder.append(paramIndex);
		builder.append(']');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(0).register();
		final int paramIndex = instruction.intImmediateAt(1);
		final L2ObjectRegister outputParamTypeReg =
			instruction.writeObjectRegisterAt(2).register();

		// :: paramType = function.code().functionType().argsTupleType()
		// ::    .typeAtIndex(param)
		translator.load(method, functionReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Function.class),
			"code",
			getMethodDescriptor(getType(A_RawFunction.class)),
			true);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_RawFunction.class),
			"functionType",
			getMethodDescriptor(getType(A_Type.class)),
			true);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Type.class),
			"argsTupleType",
			getMethodDescriptor(getType(A_Type.class)),
			true);
		translator.literal(method, paramIndex);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Type.class),
			"typeAtIndex",
			getMethodDescriptor(getType(A_Type.class), INT_TYPE),
			true);
		translator.store(method, outputParamTypeReg);
	}
}
