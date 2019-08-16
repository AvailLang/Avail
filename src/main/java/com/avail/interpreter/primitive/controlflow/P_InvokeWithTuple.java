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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.Invokes;
import static com.avail.interpreter.Primitive.Result.READY_TO_INVOKE;

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
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Function function = interpreter.argument(0);
		final A_Tuple argTuple = interpreter.argument(1);
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
		for (final AvailObject arg : argTuple)
		{
			interpreter.argsBuffer.add(arg);
		}
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
		final A_Type argTupleType = argumentTypes.get(1);
		final A_Type paramsType = functionType.argsTupleType();
		final boolean fixedSize = argTupleType.sizeRange().upperBound().equals(
			argTupleType.sizeRange().lowerBound());
		if (fixedSize
			&& paramsType.sizeRange().equals(argTupleType.sizeRange())
			&& argTupleType.isSubtypeOf(paramsType))
		{
			// The argument types are hereby guaranteed to be compatible.
			// Therefore the invoke itself will succeed, so we can rely on the
			// invoked function's return type at least.  See if we can do even
			// better if we know the exact function being invoked.
			if (functionType.instanceCount().equalsInt(1))
			{
				// The actual function being invoked is known.
				final A_Function function = functionType.instance();
				final A_RawFunction code = function.code();
				final @Nullable Primitive primitive = code.primitive();
				if (primitive != null)
				{
					// The function being invoked is itself a primitive. Dig
					// deeper to find out whether that primitive would itself
					// always succeed, and if so, what type it guarantees.
					final int primArgCount = primitive.argCount();
					final A_Type primArgSizes = argTupleType.sizeRange();
					if (primArgSizes.lowerBound().equalsInt(primArgCount)
						&& primArgSizes.upperBound().equalsInt(primArgCount))
					{
						final List<A_Type> innerArgTypes =
							new ArrayList<>(primArgCount);
						for (int i = 1; i <= primArgCount; i++)
						{
							innerArgTypes.add(argTupleType.typeAtIndex(i));
						}
						final Fallibility fallibility =
							primitive.fallibilityForArgumentTypes(
								innerArgTypes);
						if (fallibility == CallSiteCannotFail)
						{
							// The inner invocation of the primitive function
							// will always succeed. Ask the primitive what type
							// it guarantees to return.
							return primitive.returnTypeGuaranteedByVM(
								code, innerArgTypes);
						}
						// The inner primitive might fail, and its failure code
						// can return something as general as the primitive
						// function's return type.
						return functionType.returnType();
					}
					// The invocation of the inner function might not have the
					// right number of arguments. Fall through.
				}
				// The invoked inner function is not a primitive. Fall through.
			}
			// The exact function being invoked is not known. Fall through.
		}
		// The arguments that will be supplied to the inner function might not
		// have the right count.
		return functionType.returnType();
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
		final L2ReadBoxedOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadBoxedOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final L2ReadBoxedOperand functionReg = arguments.get(0);
		final L2ReadBoxedOperand tupleReg = arguments.get(1);

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

		final @Nullable List<L2ReadBoxedOperand> explodedArgumentRegisters =
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
			functionReg, explodedArgumentRegisters, true, callSiteHelper);
		return true;
	}
}
