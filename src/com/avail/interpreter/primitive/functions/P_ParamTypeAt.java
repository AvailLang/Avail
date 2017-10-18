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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE;
import com.avail.optimizer.L1Translator;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionMeta;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.*;

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
				return interpreter.primitiveSuccess(bottom());
			}
			return interpreter.primitiveSuccess(parametersType.defaultType());
		}
		final int index = indexObject.extractInt();
		final A_Type argumentType =
			functionType.argsTupleType().typeAtIndex(index);
		return interpreter.primitiveSuccess(argumentType);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				functionMeta(),
				naturalNumbers()),
			anyMeta());
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
//		final A_Type functionType = argumentTypes.get(0);
		final A_Type indexType = argumentTypes.get(1);
		return indexType.isSubtypeOf(int32())
			? CallSiteCannotFail
			: CallSiteCanFail;
	}

	@Override
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		// Transform the invocation of this P_ParamTypeAt primitive to use the
		// L2_FUNCTION_PARAMETER_TYPE instruction if possible.
		final L2ReadPointerOperand functionTypeReg = arguments.get(0);
		final L2ReadPointerOperand parameterIndexReg = arguments.get(1);

		final A_Type indexType = parameterIndexReg.type();
		if (indexType.lowerBound().equals(indexType.upperBound())
			&& indexType.lowerBound().isInt())
		{
			final int index = indexType.lowerBound().extractInt();
			assert index >= 1;
			final @Nullable A_Type functionType =
				(A_Type) functionToCallReg.constantOrNull();
			if (functionType != null)
			{
				// The exact function type is known (i.e., not just the function
				// meta that bounds it).  Fold it, although this probably would
				// already have been folded during initial code generation.
				final A_Type argsTupleType = functionType.argsTupleType();
				if (indexType.isSubtypeOf(argsTupleType.sizeRange()))
				{
					return translator.constantRegister(
						argsTupleType.typeAtIndex(index));
				}
				return translator.constantRegister(bottom());
			}
			// Since the index is known, we can still emit an
			// L2_FUNCTION_PARAMETER_TYPE.
			final L2WritePointerOperand outputReg =
				translator.newObjectRegisterWriter(ANY.o(), null);
			translator.addInstruction(
				L2_FUNCTION_PARAMETER_TYPE.instance,
				functionTypeReg,
				new L2ImmediateOperand(index),
				outputReg);
			return outputReg.read();
		}
		return super.tryToGenerateSpecialInvocation(
			functionToCallReg, arguments, argumentTypes, translator);
	}
}
