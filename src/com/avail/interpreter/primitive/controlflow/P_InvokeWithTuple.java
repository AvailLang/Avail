/*
 * P_InvokeWithTuple.java
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
package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.IntStream;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.Invokes;
import static com.avail.interpreter.Primitive.Result.READY_TO_INVOKE;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Primitive:</strong> {@linkplain FunctionDescriptor Function}
 * evaluation, given a {@linkplain TupleDescriptor tuple} of arguments.
 * Check the {@linkplain TypeDescriptor types} dynamically to prevent
 * corruption of the type system. Fail if the arguments are not of the
 * required types.
 */
public final class P_InvokeWithTuple
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_InvokeWithTuple().init(
			2, Invokes, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final A_Function function = args.get(0);
		final A_Tuple argTuple = args.get(1);
		final A_Type functionType = function.kind();

		final int numArgs = argTuple.tupleSize();
		final A_RawFunction code = function.code();
		if (code.numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final A_Type tupleType = functionType.argsTupleType();
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject arg = argTuple.tupleAt(i);
			if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_ARGUMENT_TYPE);
			}
		}

		// The arguments and parameter types agree.  Can't fail after here, so
		// feel free to clobber the argsBuffer.
		interpreter.argsBuffer.clear();
		argTuple.forEach(interpreter.argsBuffer::add);
		interpreter.function = function;
		return READY_TO_INVOKE;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralFunctionType(),
				mostGeneralTupleType()),
			TOP.o());
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type functionType = argumentTypes.get(0);
		final A_Type argTupleType = argumentTypes.get(1);
		final A_Type paramsType = functionType.argsTupleType();
		final boolean fixedSize = argTupleType.sizeRange().upperBound().equals(
			argTupleType.sizeRange().lowerBound());
		return (fixedSize
				&& paramsType.sizeRange().equals(argTupleType.sizeRange())
				&& argTupleType.isSubtypeOf(paramsType))
			? CallSiteCannotFail
			: CallSiteCanFail;
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type functionType = argumentTypes.get(0);
		final A_Type functionReturnType =
			functionType.isSubtypeOf(mostGeneralFunctionType())
				? functionType.returnType()
				: TOP.o();
		if (functionType.instanceCount().equalsInt(1)
			&& !functionReturnType.isInstanceMeta())
		{
			final A_Function function = functionType.instance();
			final A_RawFunction code = function.code();
			final @Nullable Primitive primitive = code.primitive();
			if (primitive != null)
			{
				// See if the primitive function would always succeed with the
				// arguments that would be supplied to it.
				final int primArgCount = primitive.argCount();
				final A_Type primArgTupleType = argumentTypes.get(1);
				final A_Type primArgTupleTypeSizes =
					primArgTupleType.sizeRange();
				if (primArgTupleTypeSizes.lowerBound().equalsInt(primArgCount)
					&& primArgTupleTypeSizes.upperBound().equalsInt(
						primArgCount))
				{
					final List<A_Type> argTypes =
						IntStream.rangeClosed(1, primitive.argCount())
							.mapToObj(primArgTupleType::typeAtIndex)
							.collect(toList());
					final Fallibility fallibility =
						primitive.fallibilityForArgumentTypes(argTypes);
					if (fallibility == CallSiteCannotFail)
					{
						// The invocation of this primitive will always succeed.
						// Ask the primitive what type it guarantees to return.
						return primitive.returnTypeGuaranteedByVM(
							code, argTypes);
					}
				}
			}
			return functionReturnType;
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
	}

	/**
	 * The arguments list initially has two entries: the register holding the
	 * function to invoke, and the register holding the tuple of arguments to
	 * pass it.  If it can be determined which registers or constants provided
	 * each tuple slot, then indicate that this invocation should be transformed
	 * by answering the register holding the function after replacing the list
	 * of (two) argument registers by the list of registers that supplied
	 * entries for the tuple.
	 *
	 * <p>If, however, the exact constant function cannot be determined, and it
	 * cannot be proven that the function's type is adequate to accept the
	 * arguments (each of whose type must be known here for safety), then don't
	 * change the list of arguments, and simply return false.</p>
	 */
	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final L2ReadPointerOperand functionReg = arguments.get(0);
		final L2ReadPointerOperand tupleReg = arguments.get(1);

		// Examine the function type.
		final A_Type functionType = functionReg.type();
		final A_Type functionArgsType = functionType.argsTupleType();
		final A_Type functionTypeSizes = functionArgsType.sizeRange();
		final A_Number upperBound = functionTypeSizes.upperBound();
		if (!upperBound.isInt()
			|| !functionTypeSizes.lowerBound().equals(upperBound))
		{
			// The exact function signature is not known.  Give up.
			return false;
		}
		final int argsSize = upperBound.extractInt();

		final @Nullable List<L2ReadPointerOperand> explodedArgumentRegisters =
			translator.explodeTupleIfPossible(
				tupleReg,
				toList(functionArgsType.tupleOfTypesFromTo(1, argsSize)));
		if (explodedArgumentRegisters == null)
		{
			return false;
		}

		// Fold out the call of this primitive, replacing it with an invoke of
		// the supplied function, instead.  The client will generate any needed
		// type strengthening, so don't do it here.
		translator.generateGeneralFunctionInvocation(
			functionReg,
			explodedArgumentRegisters,
			TOP.o(),
			true,
			callSiteHelper);
		return true;
	}
}
