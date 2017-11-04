/**
 * P_Assert.java
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
package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationDescriptor.dumpStackThen;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * <strong>Primitive:</strong> Assert the specified {@link
 * EnumerationTypeDescriptor#booleanType() predicate} or raise an
 * {@link AvailAssertionFailedException} (in Java) that contains the
 * provided {@linkplain TupleTypeDescriptor#stringType() message}.
 *
 * <p>It's marked with {@link Flag#SwitchesContinuation} to force the stack to
 * be reified, for debugging convenience.</p>
 */
public final class P_Assert extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_Assert().init(
			2, Unknown, CanSuspend, SwitchesContinuation, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Atom predicate = args.get(0);
		final A_String failureMessage = args.get(1);
		if (!predicate.extractBoolean())
		{
			final A_Fiber fiber = interpreter.fiber();
			final A_Continuation continuation = interpreter.reifiedContinuation;
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
		return interpreter.primitiveSuccess(nil);
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
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		assert arguments.size() == 2;
		final A_Type conditionType = argumentTypes.get(0);
		if (!falseObject().isInstanceOf(conditionType))
		{
			// The condition can't be false, so skip the call.
			return translator.constantRegister(nil);
		}
		if (!trueObject().isInstanceOf(conditionType))
		{
			// The condition can't be true, so don't optimize the call.
			return null;
		}
		// Failed assertions are rare, so avoid the cost of even the primitive
		// invocation if possible.  Actually, this is more about setting up a
		// branched control flow so that some of the computation of the message
		// string and the reification state can be pushed into the rare failure
		// path.
		final L2BasicBlock failPath = translator.createBasicBlock(
			"assertion failed");
		final L2BasicBlock afterPath = translator.createBasicBlock(
			"after assertion");

		translator.addInstruction(
			L2_JUMP_IF_EQUALS_CONSTANT.instance,
			arguments.get(0),
			new L2ConstantOperand(trueObject()),
			new L2PcOperand(afterPath, translator.slotRegisters()),
			new L2PcOperand(failPath, translator.slotRegisters()));

		translator.startBlock(failPath);
		// Since this invocation will also be optimized, pass the constant false
		// as the condition argument to avoid infinite recursion.
		translator.generateGeneralFunctionInvocation(
			functionToCallReg,
			asList(
				translator.constantRegister(falseObject()),
				arguments.get(1)),
			TOP.o(),
			true,
			translator.slotRegisters(),
			"assertion failure");
		translator.addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(afterPath, translator.slotRegisters()));

		// And converge back here.  This assertion primitive always answers nil.
		translator.startBlock(afterPath);
		return translator.constantRegister(nil);
	}
}
