/*
 * P_Assert.java
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
package com.avail.interpreter.primitive.general;

import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationDescriptor.dumpStackThen;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.optimizer.L2Generator.edgeTo;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * <strong>Primitive:</strong> Assert the specified {@link
 * EnumerationTypeDescriptor#booleanType() predicate} or raise an {@link
 * AvailAssertionFailedException} (in Java) that contains the provided
 * {@linkplain TupleTypeDescriptor#stringType() message}.
 */
public final class P_Assert extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_Assert().init(
			2, Unknown, CanSuspend, CannotFail);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Atom predicate = interpreter.argument(0);
		final A_String failureMessage = interpreter.argument(1);

		if (predicate.extractBoolean())
		{
			return interpreter.primitiveSuccess(nil);
		}

		final A_Fiber fiber = interpreter.fiber();
		final A_Continuation continuation =
			stripNull(interpreter.reifiedContinuation);
		interpreter.primitiveSuspend(stripNull(interpreter.function));
		dumpStackThen(
			interpreter.runtime(),
			fiber.textInterface(),
			continuation,
			stack ->
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(failureMessage.asNativeString());
				for (final String frame : stack)
				{
					builder.append(format("%n\t-- %s", frame));
				}
				builder.append("\n\n");
				final AvailAssertionFailedException killer =
					new AvailAssertionFailedException(
						builder.toString());
				killer.fillInStackTrace();
				fiber.executionState(ExecutionState.ABORTED);
				fiber.failureContinuation().value(killer);
			});
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				booleanType(),
				stringType()),
			TOP.o());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type booleanType = argumentTypes.get(0);
		if (trueObject().isInstanceOf(booleanType))
		{
			// The assertion might pass, so the type is top.
			return TOP.o();
		}
		else
		{
			// The assertion can't pass, so the fiber will always terminate.
			// Thus, the type is bottom.
			return bottom();
		}
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
		assert arguments.size() == 2;
		final A_Type conditionType = argumentTypes.get(0);
		if (!falseObject().isInstanceOf(conditionType))
		{
			// The condition can't be false, so skip the call.
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(nil));
			return true;
		}
		if (!trueObject().isInstanceOf(conditionType))
		{
			// The condition can't be true, so don't optimize the call.
			return false;
		}
		// Failed assertions are rare, so avoid the cost of even the primitive
		// invocation if possible.  Actually, this is more about setting up a
		// branched control flow so that some of the computation of the message
		// string and the reification state can be pushed into the rare failure
		// path.
		final L2BasicBlock failPath =
			translator.generator.createBasicBlock("assertion failed");
		final L2BasicBlock passPath =
			translator.generator.createBasicBlock("after assertion");

		translator.addInstruction(
			L2_JUMP_IF_EQUALS_CONSTANT.instance,
			arguments.get(0),
			new L2ConstantOperand(trueObject()),
			edgeTo(passPath),
			edgeTo(failPath));

		translator.generator.startBlock(failPath);
		// Since this invocation will also be optimized, pass the constant false
		// as the condition argument to avoid infinite recursion.
		translator.generateGeneralFunctionInvocation(
			functionToCallReg,
			asList(
				translator.generator.boxedConstant(falseObject()),
				arguments.get(1)),
			true,
			callSiteHelper);

		// Happy case.  Just push nil and jump to a suitable exit point.
		translator.generator.startBlock(passPath);
		callSiteHelper.useAnswer(translator.generator.boxedConstant(nil));
		return true;
	}
}
