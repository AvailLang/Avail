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
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_TUPLE;
import com.avail.interpreter.levelTwo.operation.L2_EXPLODE_TUPLE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT;
import com.avail.optimizer.L1Translator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor
	.mostGeneralFunctionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
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
import static com.avail.optimizer.L1Translator.writeVector;
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
						return primitive.returnTypeGuaranteedByVM(argTypes);
					}
				}
			}
			return functionReturnType;
		}
		return super.returnTypeGuaranteedByVM(argumentTypes);
	}

	/**
	 * The arguments list initially has two entries: the register holding the
	 * function to invoke, and the register holding the tuple of arguments to
	 * pass it.  If it can be determined which registers or constants provided
	 * each tuple slot, then indicate that this invocation should be transformed
	 * by answering the register holding the function after replacing the list
	 * of (two) argument registers by the list of registers that supplied
	 * entries for the tuple.  If some tuple slots were populated from
	 * constants, emit suitable constant moves into fresh registers.
	 *
	 * <p>If, however, the exact constant function cannot be determined, and it
	 * cannot be proven that the function's type is adequate to accept the
	 * arguments (each of whose type must be known here for safety), then don't
	 * change the list of arguments, and simple return null.</p>
	 */
	@Override
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		final L2ReadPointerOperand functionReg = arguments.get(0);
		final L2ReadPointerOperand tupleReg = arguments.get(1);

		// First see if there's enough type information available about the
		// tuple of arguments.
		final A_Type tupleType = tupleReg.type();
		final A_Type tupleTypeSizes = tupleType.sizeRange();
		if (!tupleTypeSizes.upperBound().isInt()
			|| !tupleTypeSizes.lowerBound().equals(
				tupleTypeSizes.upperBound()))
		{
			// The exact tuple size is not known.  Give up.
			return null;
		}
		final int argsSize = tupleTypeSizes.upperBound().extractInt();

		// Now examine the function type.
		final A_Type functionType = functionReg.type();
		final A_Type functionArgsType = functionType.argsTupleType();
		final A_Type functionTypeSizes = functionArgsType.sizeRange();
		if (!functionTypeSizes.equals(singleInt(argsSize)))
		{
			// The argument count of the function is not known to be exactly the
			// right size for the arguments tuple.  Give up.
			return null;
		}

		// Check if the (same-sized) tuple element types agree with the types
		// the function will expect.
		final List<L2WritePointerOperand> argsTupleWriters =
			new ArrayList<>(argsSize);
		for (int i = 1; i <= argsSize; i++)
		{
			final A_Type argType = tupleType.typeAtIndex(i);
			if (!argType.isSubtypeOf(functionArgsType.typeAtIndex(i)))
			{
				// A tuple element is not strong enough to guarantee successful
				// invocation of the function.
				return null;
			}
			final L2WritePointerOperand newReg =
				translator.newObjectRegisterWriter(argType, null);
			argsTupleWriters.add(newReg);
		}
		// At this point we know the invocation will succeed.  The function it
		// invokes may be a primitive which could fail, but that's someone
		// else's problem.

		// Emit code to get the tuple slots into separate registers for the
		// invocation, *extracting* them from the tuple if necessary.
		L2Instruction tupleDefinitionInstruction =
			tupleReg.register().definition();
		while (tupleDefinitionInstruction.operation instanceof L2_MOVE)
		{
			final L2ReadPointerOperand sourceReg =
				L2_MOVE.sourceOf(tupleDefinitionInstruction);
			tupleDefinitionInstruction = sourceReg.register().definition();
		}
		final List<L2ReadPointerOperand> argsTupleReaders;
		if (tupleDefinitionInstruction.operation instanceof L2_CREATE_TUPLE)
		{
			argsTupleReaders = L2_CREATE_TUPLE.tupleSourceRegistersOf(
				tupleDefinitionInstruction);
		}
		else if (tupleDefinitionInstruction.operation
			instanceof L2_MOVE_CONSTANT)
		{
			final A_Tuple constantTuple =
				L2_MOVE_CONSTANT.constantOf(tupleDefinitionInstruction);
			argsTupleReaders =
				IntStream.rangeClosed(1, argsSize)
					.mapToObj(constantTuple::tupleAt)
					.map(translator::constantRegister)
					.collect(toList());
		}
		else
		{
			translator.addInstruction(
				L2_EXPLODE_TUPLE.instance,
				tupleReg,
				writeVector(argsTupleWriters));
			argsTupleReaders =
				argsTupleWriters.stream()
					.map(L2WritePointerOperand::read)
					.collect(toList());
		}

		// Fold out the call of this primitive, replacing it with an invoke of
		// the supplied function, instead.  The client will generate any needed
		// type strengthening, so don't do it here.
		return translator.generateGeneralFunctionInvocation(
			functionReg,    // the function to directly invoke.
			argsTupleReaders,   // the arguments, no longer in a tuple.
			TOP.o(),
			true,
			translator.slotRegisters(),
			"general invocation");
	}
}
