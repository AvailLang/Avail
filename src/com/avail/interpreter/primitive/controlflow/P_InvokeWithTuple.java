/**
 * P_InvokeWithTuple.java
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
package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteVectorOperand;
import com.avail.interpreter.levelTwo.operation.L2_EXPLODE_TUPLE;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor
	.mostGeneralFunctionType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode
	.E_INCORRECT_NUMBER_OF_ARGUMENTS;
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
	public static final Primitive instance =
		new P_InvokeWithTuple().init(
			2, Invokes, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Function function = args.get(0);
		final A_Tuple argTuple = args.get(1);
		final A_Type blockType = function.kind();

		final int numArgs = argTuple.tupleSize();
		final A_RawFunction code = function.code();
		if (code.numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final A_Type tupleType = blockType.argsTupleType();
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

	/**
	 * The arguments list initially has two entries: the register holding the
	 * function to invoke, and the register holding the tuple of arguments to
	 * pass it.  If it can be determined which registers or constants provided
	 * each tuple slot, then indicate that this invocation should be transformed
	 * by answering the register holding the function after replacing the list
	 * of (two) argument registers by the list of registers that supplied
	 * entries for the tuple.  If some tuple slots were populated from
Í	 * constants, emit suitable constant moves into fresh registers.
	 *
	 * If, however, the exact constant function cannot be determined, and it
	 * cannot be proven that the function's type is adequate to accept the
	 * arguments (each of whose type must be known here for safety), then don't
	 * change the list of arguments, and simple return null.
	 */
	@Override
	public @Nullable L2ObjectRegister foldOutInvoker (
		final List<L2ObjectRegister> args,
		final L1NaiveTranslator naiveTranslator)
	{
		assert hasFlag(Invokes);
		assert !hasFlag(Flag.CanInline);
		assert !hasFlag(Flag.CanFold);

		final L2ObjectRegister functionReg = args.get(0);
		final L2ObjectRegister argsRegister = args.get(1);

		// First see if there's enough type information available about the
		// tuple of arguments.
		final A_Type argsTupleType =
			naiveTranslator.naiveRegisters().typeAt(argsRegister);
		final A_Type argsTupleTypeSizes = argsTupleType.sizeRange();
		if ((!argsTupleTypeSizes.lowerBound().equals(
				argsTupleTypeSizes.upperBound()))
			|| !argsTupleTypeSizes.lowerBound().isInt())
		{
			// The exact tuple size is not known (or enormous).  Give up.
			return null;
		}
		final int argsSize = argsTupleTypeSizes.lowerBound().extractInt();

		// Now examine the function type.
		final A_Type functionType =
			naiveTranslator.naiveRegisters().typeAt(functionReg);
		final A_Type functionArgsType = functionType.argsTupleType();
		final A_Type functionTypeSizes = functionArgsType.sizeRange();

		if ((!functionTypeSizes.lowerBound().equals(
				functionTypeSizes.upperBound()))
			|| (!functionTypeSizes.lowerBound().isInt())
			|| (functionTypeSizes.lowerBound().extractInt() != argsSize))
		{
			// The exact argument count of the function is not known, is
			// enormous, or disagrees with the tuple size.
			return null;
		}

		// Check if the (same-sized) tuple element types agree with the types
		// the function will expect.
		for (int i = 0; i < argsSize; i++)
		{
			final A_Type argType = argsTupleType.typeAtIndex(i);
			if (!argType.isSubtypeOf(functionArgsType.typeAtIndex(i)))
			{
				// A tuple element is not strong enough to guarantee successful
				// invocation of the function.
				return null;
			}
		}
		// At this point we know the invocation will succeed.  The function it
		// invokes may be a primitive which could fail, but that's someone
		// else's problem.

		// Emit code to get the tuple slots into separate registers for the
		// invocation, *extracting* them from the tuple if necessary.
		final List<L2ObjectRegister> argsTupleRegisterList =
			new ArrayList<>(argsSize);
		for (int i = 0; i < argsSize; i++)
		{
			final L2ObjectRegister newReg = naiveTranslator.newObjectRegister();
			argsTupleRegisterList.add(newReg);
		}
		naiveTranslator.addInstruction(
			L2_EXPLODE_TUPLE.instance,
			new L2ReadPointerOperand(argsRegister),
			new L2WriteVectorOperand(
				naiveTranslator.createVector(argsTupleRegisterList)));
		// Replace the arguments with the newly allocated registers.
		args.clear();
		args.addAll(argsTupleRegisterList);
		return functionReg;
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
		final A_Type blockType = argumentTypes.get(0);
		final A_Type argTupleType = argumentTypes.get(1);
		final A_Type paramsType = blockType.argsTupleType();
		final boolean fixedSize = argTupleType.sizeRange().upperBound().equals(
			argTupleType.sizeRange().lowerBound());
		return (fixedSize
				&& paramsType.sizeRange().equals(argTupleType.sizeRange())
				&& argTupleType.isSubtypeOf(paramsType))
			? CallSiteCannotFail
			: CallSiteCanFail;
	}
}
