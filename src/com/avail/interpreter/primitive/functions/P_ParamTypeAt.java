/**
 * P_ParamTypeAt.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.functions;

import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Fallibility.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

/**
 * <strong>Primitive:</strong> Answer the type of the parameter at the
 * given index within the given functionType.
 */
public final class P_ParamTypeAt
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_ParamTypeAt().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Type functionType = args.get(0);
		final A_Number indexObject = args.get(1);
		final A_Type parametersType = functionType.argsTupleType();
		if (!indexObject.isInt())
		{
			// Note that it's already restricted statically to natural numbers.
			if (parametersType.upperBound().lessThan(indexObject))
			{
				return interpreter.primitiveSuccess(
					BottomTypeDescriptor.bottom());
			}
			return interpreter.primitiveSuccess(parametersType.defaultType());
		}
		final int index = indexObject.extractInt();
		final A_Type argumentType =
			functionType.argsTupleType().typeAtIndex(index);
		return interpreter.primitiveSuccess(argumentType);
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final L1NaiveTranslator naiveTranslator,
		final RegisterSet registerSet)
	{
		// Inline the invocation of this P_ParamTypeAt primitive, specifically
		// to use the L2_FUNCTION_PARAMETER_TYPE instruction if possible.

//		final L2ObjectRegister functionTypeReg =
//			instruction.readObjectRegisterAt(0);
//		final L2ObjectRegister invokerFunctionReg =
//			instruction.readObjectRegisterAt(1);
		final L2RegisterVector invokerArgumentsVector =
			instruction.readVectorRegisterAt(2);
//		final int skipCheck = instruction.immediateAt(3);

		// Separate the arguments to the primitive: the function type and the
		// (boxed) index.
		final List<L2ObjectRegister> arguments =
			invokerArgumentsVector.registers();
		final L2ObjectRegister actualFunctionTypeReg = arguments.get(0);
		final L2ObjectRegister parameterIndexReg = arguments.get(1);

		if (registerSet.hasConstantAt(parameterIndexReg))
		{
			final A_Number parameterIndexBoxed =
				registerSet.constantAt(parameterIndexReg);
			if (parameterIndexBoxed.isInt())
			{
				final int parameterIndex = parameterIndexBoxed.extractInt();
				assert registerSet.hasTypeAt(actualFunctionTypeReg);
				final A_Type functionMeta =
					registerSet.typeAt(actualFunctionTypeReg);
				final A_Type functionType = functionMeta.instance();
				final A_Type argsType = functionType.argsTupleType();
				final A_Type argsSizeRange = argsType.sizeRange();
				if (parameterIndexBoxed.isInstanceOf(argsSizeRange))
				{
					final L2ObjectRegister outputReg =
						instruction.operation.primitiveResultRegister(
							instruction);
					naiveTranslator.addInstruction(
						L2_FUNCTION_PARAMETER_TYPE.instance,
						new L2ReadPointerOperand(actualFunctionTypeReg),
						new L2ImmediateOperand(parameterIndex),
						new L2WritePointerOperand(outputReg));
					return true;
				}
			}
		}
		return super.regenerate(instruction, naiveTranslator, registerSet);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				FunctionTypeDescriptor.meta(),
				IntegerRangeTypeDescriptor.naturalNumbers()),
			InstanceMetaDescriptor.anyMeta());
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
//		final A_Type functionMeta = argumentTypes.get(0);
		final A_Type indexType = argumentTypes.get(1);
		return indexType.isSubtypeOf(IntegerRangeTypeDescriptor.int32())
			? CallSiteCannotFail
			: CallSiteCanFail;
	}
}
