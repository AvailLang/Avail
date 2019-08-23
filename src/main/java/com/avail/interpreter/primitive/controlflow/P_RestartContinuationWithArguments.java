/*
 * P_RestartContinuationWithArguments.java
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
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION_WITH_ARGUMENTS;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;

/**
 * <strong>Primitive:</strong> Restart the given {@linkplain
 * ContinuationDescriptor continuation}, but passing in the given {@linkplain
 * TupleDescriptor tuple} of arguments. Make sure it's a label-like continuation
 * rather than a call-like, because a call-like continuation has the expected
 * return type already pushed on the stack, and requires the return value, after
 * checking against that type, to overwrite the type in the stack (without
 * affecting the stack depth). Fail if the continuation's {@linkplain
 * FunctionDescriptor function} is not capable of accepting the given arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_RestartContinuationWithArguments
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_RestartContinuationWithArguments().init(
			2,
			CanInline,
			CanSwitchContinuations,
			AlwaysSwitchesContinuation);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Continuation originalCon = interpreter.argument(0);
		final A_Tuple arguments = interpreter.argument(1);

		final A_RawFunction code = originalCon.function().code();
		//TODO MvG - This should be a primitive failure.
		assert originalCon.stackp() == code.numSlots() + 1
			: "Continuation should have been a label- rather than "
				+ "call-continuation";
		assert originalCon.pc() == 0
			: "Continuation should have been a label- rather than "
				+ "call-continuation";

		final int numArgs = code.numArgs();
		if (numArgs != arguments.tupleSize())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// Check the argument types.
		if (!code.functionType().acceptsTupleOfArguments(arguments))
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		// Move the arguments into interpreter.argsBuffer.
		interpreter.argsBuffer.clear();
		for (final AvailObject arg : arguments)
		{
			interpreter.argsBuffer.add(arg);
		}
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		interpreter.reifiedContinuation = (AvailObject) originalCon.caller();
		interpreter.function = originalCon.function();
		interpreter.chunk = code.startingChunk();
		interpreter.offset = 0;
		interpreter.returnNow = false;
		interpreter.latestResult(null);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralContinuationType(),
				mostGeneralTupleType()),
			bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_INCORRECT_ARGUMENT_TYPE));
	}

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadBoxedOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadBoxedOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final L2ReadBoxedOperand continuationReg = arguments.get(0);
		final L2ReadBoxedOperand argumentsTupleReg = arguments.get(1);

		// A restart works with every continuation that is created by a label.
		// First, pop out of the Java stack frames back into the outer L2 run
		// loop (which saves/restores the current frame and continues at the
		// next L2 instruction).  Extract the tuple of arguments back into a
		// vector of individual registers, aborting code generation of this
		// special invocation if it's not possible.

		// Examine the continuation's function's type.
		final A_Type continuationType = continuationReg.type();
		final A_Type functionType = continuationType.functionType();
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
		final @Nullable List<L2ReadBoxedOperand> explodedArgumentRegs =
			translator.explodeTupleIfPossible(
				argumentsTupleReg,
				toList(functionArgsType.tupleOfTypesFromTo(1, argsSize)));
		if (explodedArgumentRegs == null)
		{
			return false;
		}

		translator.addInstruction(
			L2_RESTART_CONTINUATION_WITH_ARGUMENTS.instance,
			continuationReg,
			new L2ReadBoxedVectorOperand(explodedArgumentRegs));
		assert !translator.generator.currentlyReachable();
		translator.generator.startBlock(translator.generator.createBasicBlock(
			"unreachable after L2_RESTART_CONTINUATION_WITH_ARGUMENTS"));
		return true;
	}
}
