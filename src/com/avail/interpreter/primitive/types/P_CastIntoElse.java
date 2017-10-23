/**
 * P_CastIntoElse.java
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
package com.avail.interpreter.primitive.types;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.optimizer.L1Translator.readVector;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> If the second argument, a {@linkplain A_Function
 * function}, accepts the first argument as its parameter, do the invocation.
 * Otherwise invoke the third argument, a zero-argument function.
 */
public final class P_CastIntoElse extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CastIntoElse().init(
			3, Invokes, CanInline, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final AvailObject value = args.get(0);
		final A_Function castFunction = args.get(1);
		final A_Function elseFunction = args.get(2);

		interpreter.argsBuffer.clear();
		if (value.isInstanceOf(
			castFunction.code().functionType().argsTupleType().typeAtIndex(1)))
		{
			// "Jump" into the castFunction, to keep this frame from showing up.
			interpreter.argsBuffer.add(value);
			interpreter.function = castFunction;
			return Result.READY_TO_INVOKE;
		}
		// "Jump" into the elseFunction, to keep this frame from showing up.
		interpreter.function = elseFunction;
		return Result.READY_TO_INVOKE;
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		// Keep it simple.
		final A_Type castFunctionType = argumentTypes.get(1);
		final A_Type elseFunctionType = argumentTypes.get(2);
		return castFunctionType.returnType().typeUnion(
			elseFunctionType.returnType());
	}

	/**
	 * If we can determine the exact type that the value will be compared
	 * against (i.e., the intoBlock's argument type), then answer it.  Otherwise
	 * answer {@code null}.
	 *
	 * @param initialFunctionReg
	 *        The register that holds the intoFunction.
	 * @return Either null or an exact type to compare the value against in
	 *         order to determine whether the intoBlock or the elseBlock will be
	 *         invoked.
	 */
	private static @Nullable A_Type exactArgumentTypeFor (
		final L2ReadPointerOperand initialFunctionReg)
	{
		L2ReadPointerOperand functionReg = initialFunctionReg;
		while (true)
		{
			final @Nullable A_Function constantFunction =
				A_Function.class.cast(functionReg.constantOrNull());
			if (constantFunction != null)
			{
				// Function is a constant.
				final A_Type functionType =
					constantFunction.code().functionType();
				return functionType.argsTupleType().typeAtIndex(1);
			}
			final L2Instruction originOfFunction =
				functionReg.register().definition();
			if (originOfFunction.operation instanceof L2_MOVE_CONSTANT)
			{
				final A_Function function = originOfFunction.constantAt(0);
				final A_Type functionType = function.code().functionType();
				return functionType.argsTupleType().typeAtIndex(1);
			}
			if (originOfFunction.operation instanceof L2_CREATE_FUNCTION)
			{
				final A_RawFunction code = originOfFunction.constantAt(0);
				final A_Type functionType = code.functionType();
				return functionType.argsTupleType().typeAtIndex(1);
			}
			if (originOfFunction.operation instanceof L2_MOVE)
			{
				// Trace it back through moves.
				functionReg = originOfFunction.readObjectRegisterAt(0);
			}
			else
			{
				return null;
			}
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ANY.o(),
				functionType(
					tuple(
						bottom()),
					TOP.o()),
				functionType(
					emptyTuple(),
					TOP.o())),
			TOP.o());
	}

	@Override
	public L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		// Inline the invocation of this P_CastIntoElse primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.
		final L2ReadPointerOperand valueReg = arguments.get(0);
		final L2ReadPointerOperand castFunctionReg = arguments.get(1);
		final L2ReadPointerOperand elseFunctionReg = arguments.get(2);

		final L2BasicBlock castBlock = translator.createBasicBlock(
			"cast type matched");
		final L2BasicBlock elseBlock = translator.createBasicBlock(
			"cast type did not match");
		final L2BasicBlock mergeBlock = translator.createBasicBlock(
			"merge after cast");

		final @Nullable A_Type typeTest = exactArgumentTypeFor(castFunctionReg);
		if (typeTest != null)
		{
			// By tracing where the castBlock came from, we were able to
			// determine the exact type to compare the value against.  This is
			// the usual case for casts, typically where the castBlock phrase is
			// simply a function closure.  First see if we can eliminate the
			// runtime test entirely.
			boolean bypassTesting = true;
			final boolean passedTest;
			final @Nullable A_BasicObject constant = valueReg.constantOrNull();
			if (constant != null)
			{
				passedTest = constant.isInstanceOf(typeTest);
			}
			else if (valueReg.type().isSubtypeOf(typeTest))
			{
				passedTest = true;
			}
			else if (valueReg.type().typeIntersection(typeTest).isBottom())
			{
				passedTest = false;
			}
			else
			{
				bypassTesting = false;
				passedTest = false;  // Keep compiler happy below.
			}
			if (bypassTesting)
			{
				// Run the castBlock or elseBlock without having to do the
				// runtime type test (since we just did it).  Don't do a type
				// check on the result, because the client will deal with it.
				return translator.generateGeneralFunctionInvocation(
					passedTest ? castFunctionReg : elseFunctionReg,
					passedTest ? singletonList(valueReg) : emptyList(),
					TOP.o(),
					true,
					translator.slotRegisters(),
					passedTest
						? "no-check cast body"
						: "no-check cast failure");
			}

			// We know the exact type to compare the value against, but we
			// couldn't statically eliminate the type test.  Emit a branch.
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				valueReg,
				new L2ConstantOperand(typeTest),
				new L2PcOperand(
					castBlock,
					translator.slotRegisters(),
					valueReg.restrictedTo(typeTest, null)),
				new L2PcOperand(
					elseBlock,
					translator.slotRegisters(),
					valueReg.restrictedWithoutType(typeTest)));
		}
		else
		{
			// We don't statically know the type to compare the value against,
			// but we can get it at runtime by extracting the actual
			// castFunction's argument type.  Note that we can't phi-strengthen
			// the valueReg along the branches, since we don't statically know
			// the type that it was compared to.
			final L2WritePointerOperand parameterTypeWrite =
				translator.newObjectRegisterWriter(anyMeta(), null);
			translator.addInstruction(
				L2_FUNCTION_PARAMETER_TYPE.instance,
				castFunctionReg,
				new L2ImmediateOperand(1),
				parameterTypeWrite);
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_OBJECT.instance,
				valueReg,
				parameterTypeWrite.read(),
				new L2PcOperand(castBlock, translator.slotRegisters()),
				new L2PcOperand(elseBlock, translator.slotRegisters()));
		}

		// We couldn't skip the runtime type check, which takes us to either
		// castBlock or elseBlock, after which we merge the control flow back.
		// Start by generating the invocation of castFunction.
		translator.startBlock(castBlock);
		final L2ReadPointerOperand castResultReg =
			translator.generateGeneralFunctionInvocation(
				castFunctionReg,
				singletonList(valueReg),
				TOP.o(),
				true,
				translator.slotRegisters(),
				"cast body");
		translator.addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(mergeBlock, translator.slotRegisters()));

		translator.startBlock(elseBlock);
		final L2ReadPointerOperand elseResultReg =
			translator.generateGeneralFunctionInvocation(
				castFunctionReg,
				singletonList(valueReg),
				TOP.o(),
				true,
				translator.slotRegisters(),
				"cast else");
		translator.addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(mergeBlock, translator.slotRegisters()));

		translator.startBlock(mergeBlock);
		final TypeRestriction mergeRestriction =
			castResultReg.restriction().union(elseResultReg.restriction());
		final L2WritePointerOperand mergeReg =
			translator.newObjectRegisterWriter(
				mergeRestriction.type,
				mergeRestriction.constantOrNull);
		translator.addInstruction(
			L2_PHI_PSEUDO_OPERATION.instance,
			readVector(asList(castResultReg, elseResultReg)),
			mergeReg);
		return mergeReg.read();
	}
}
