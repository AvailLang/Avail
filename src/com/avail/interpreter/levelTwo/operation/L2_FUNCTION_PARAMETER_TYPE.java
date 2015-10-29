/**
 * L2_FUNCTION_PARAMETER_TYPE.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.RegisterState;

/**
 * Given an input register containing a function (not a function type), extract
 * its Nth parameter type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_FUNCTION_PARAMETER_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_FUNCTION_PARAMETER_TYPE().init(
			READ_POINTER.is("function"),
			IMMEDIATE.is("parameter index"),
			WRITE_POINTER.is("parameter type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.immediateAt(1);
		final L2ObjectRegister outputParamTypeReg =
			instruction.readObjectRegisterAt(2);

		final A_Function function = functionReg.in(interpreter);
		final A_Type paramType =
			function.code().functionType().argsTupleType().typeAtIndex(
				paramIndex);
		outputParamTypeReg.set(paramType, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.immediateAt(1);
		final L2ObjectRegister outputParamTypeReg =
			instruction.writeObjectRegisterAt(2);

		// Function types are contravariant, so we may have to fall back on
		// just saying the parameter type must be a type and can't be top –
		// i.e., any's type.
		if (registerSet.hasConstantAt(functionReg))
		{
			// Exact function is known.
			final A_Function function = registerSet.constantAt(functionReg);
			final A_Type functionType = function.code().functionType();
			registerSet.constantAtPut(
				outputParamTypeReg,
				functionType.argsTupleType().typeAtIndex(paramIndex),
				instruction);
			return;
		}
		final List<L2Instruction> sources =
			registerSet.stateForReading(functionReg).sourceInstructions();
		if (sources.size() == 1)
		{
			final L2Instruction source = sources.get(0);
			if (source.operation == L2_CREATE_FUNCTION.instance)
			{
				final A_RawFunction code = sources.get(0).constantAt(0);
				final A_Type functionType = code.functionType();
				registerSet.constantAtPut(
					outputParamTypeReg,
					functionType.argsTupleType().typeAtIndex(paramIndex),
					instruction);
				return;
			}
		}
		// We don't know the exact type of the block argument, so since it's
		// contravariant we can only assume it's some non-top type.
		registerSet.typeAtPut(
			outputParamTypeReg, InstanceMetaDescriptor.anyMeta(), instruction);
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final L1NaiveTranslator naiveTranslator,
		final RegisterSet registerSet)
	{
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(0);
		final int paramIndex = instruction.immediateAt(1);
		final L2ObjectRegister outputParamTypeReg =
			instruction.writeObjectRegisterAt(2);

		@Nullable A_Type functionType = null;
		if (registerSet.hasConstantAt(functionReg))
		{
			final A_Function constantFunction =
				registerSet.constantAt(functionReg);
			functionType = constantFunction.code().functionType();
		}
		else
		{
			final RegisterState state =
				registerSet.stateForReading(functionReg);
			final List<L2Instruction> sources = state.sourceInstructions();
			if (sources.size() == 1)
			{
				// Exactly one instruction provides the function.
				final L2Instruction closeInstruction = sources.get(0);
				if (closeInstruction.operation instanceof L2_CREATE_FUNCTION)
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
			naiveTranslator.addInstruction(
				L2_MOVE_CONSTANT.instance,
				new L2ConstantOperand(paramType),
				new L2WritePointerOperand(outputParamTypeReg));
			return true;
		}
		return super.regenerate(instruction, naiveTranslator, registerSet);
	}
}
