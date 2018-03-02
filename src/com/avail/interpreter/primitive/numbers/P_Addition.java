/*
 * P_Addition.java
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
package com.avail.interpreter.primitive.numbers;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AbstractNumberDescriptor;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operation.L2_ADD_INT_TO_INT;
import com.avail.interpreter.levelTwo.operation.L2_ADD_INT_TO_INT_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_ADD_INT_TO_INT_MOD_32_BITS;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AbstractNumberDescriptor
	.binaryNumericOperationTypeBound;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.exceptions.AvailErrorCode
	.E_CANNOT_ADD_UNLIKE_INFINITIES;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Add two {@linkplain
 * AbstractNumberDescriptor numbers}.
 */
@SuppressWarnings("unused")
public final class P_Addition
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_Addition().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Number a = interpreter.argument(0);
		final A_Number b = interpreter.argument(1);
		try
		{
			return interpreter.primitiveSuccess(a.plusCanDestroy(b, true));
		}
		catch (final ArithmeticException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				NUMBER.o(),
				NUMBER.o()),
			NUMBER.o());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		try
		{
			if (aType.isEnumeration() && bType.isEnumeration())
			{
				final A_Set aInstances = aType.instances();
				final A_Set bInstances = bType.instances();
				// Compute the Cartesian product as an enumeration if there will
				// be few enough entries.
				if (aInstances.setSize() * (long) bInstances.setSize() < 100)
				{
					A_Set answers = emptySet();
					for (final A_Number aInstance : aInstances)
					{
						for (final A_Number bInstance : bInstances)
						{
							answers = answers.setWithElementCanDestroy(
								aInstance.plusCanDestroy(bInstance, false),
								false);
						}
					}
					return enumerationWith(answers);
				}
			}
			if (aType.isIntegerRangeType() && bType.isIntegerRangeType())
			{
				final A_Number low = aType.lowerBound().plusCanDestroy(
					bType.lowerBound(), false);
				final A_Number high = aType.upperBound().plusCanDestroy(
					bType.upperBound(), false);
				final boolean includesNegativeInfinity =
					negativeInfinity().isInstanceOf(aType)
						|| negativeInfinity().isInstanceOf(bType);
				final boolean includesInfinity =
					positiveInfinity().isInstanceOf(aType)
						|| positiveInfinity().isInstanceOf(bType);
				return integerRangeType(
					low.minusCanDestroy(one(), false),
					includesNegativeInfinity,
					high.plusCanDestroy(one(), false),
					includesInfinity);
			}
		}
		catch (final ArithmeticException e)
		{
			// $FALL-THROUGH$
		}
		return binaryNumericOperationTypeBound(aType, bType);
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		final boolean aTypeIncludesNegativeInfinity =
			negativeInfinity().isInstanceOf(aType);
		final boolean aTypeIncludesInfinity =
			positiveInfinity().isInstanceOf(aType);
		final boolean bTypeIncludesNegativeInfinity =
			negativeInfinity().isInstanceOf(bType);
		final boolean bTypeIncludesInfinity =
			positiveInfinity().isInstanceOf(bType);
		if ((aTypeIncludesInfinity && bTypeIncludesNegativeInfinity)
			|| (aTypeIncludesNegativeInfinity && bTypeIncludesInfinity))
		{
			return CallSiteCanFail;
		}
		return CallSiteCannotFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_CANNOT_ADD_UNLIKE_INFINITIES));
	}

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		if (aType.typeIntersection(int32()).isBottom() ||
			bType.typeIntersection(int32()).isBottom())
		{
			return false;
		}

		// Attempt to unbox the arguments.
		final L2BasicBlock unboxedArg1Block =
			new L2BasicBlock("unboxed arg#1");
		final L2BasicBlock unboxedAddition =
			new L2BasicBlock("unboxed addition");
		final L2BasicBlock boxedAddition =
			new L2BasicBlock("fall back to boxed addition");
		final L2ReadIntOperand a = translator.unboxIntoIntRegister(
			arguments.get(0),
			aType,
			unboxedArg1Block,
			boxedAddition);
		// unboxedArg1Block has been started, if necessary.
		final L2ReadIntOperand b = translator.unboxIntoIntRegister(
			arguments.get(1),
			bType,
			unboxedAddition,
			boxedAddition);
		// unboxedAddition has been started, if necessary.

		// Emit the most efficient available unboxed arithmetic.
		final A_Type returnType = returnTypeGuaranteedByVM(
			rawFunction, argumentTypes);
		final L2WriteIntOperand sum = translator.newIntRegisterWriter(
			returnType, null);
		if (returnType.isSubtypeOf(int32()))
		{
			// The result is guaranteed not to overflow, so emit an instruction
			// that won't bother with an overflow check.
			translator.addInstruction(
				L2_ADD_INT_TO_INT_MOD_32_BITS.instance,
				a,
				b,
				sum);
			final L2ReadPointerOperand boxed =
				translator.box(sum.read(), returnType);
			callSiteHelper.useAnswer(boxed);
		}
		else
		{
			// The result may overflow, so we will need to emit an instruction
			// that deals with overflow (by falling back on the original
			// primitive invocation mechanism).
			final L2Operation operation;
			final L2ReadIntOperand op1;
			final L2Operand op2;
			if (a.constantOrNull() != null || b.constantOrNull() != null)
			{
				// One of the arguments is a constant, so emit an instruction
				// that takes an immediate.
				if (a.constantOrNull() == null)
				{
					final int value =
						stripNull(b.constantOrNull()).extractInt();
					if (value == 0)
					{
						// If the immediate is zero, then we can avoid emitting
						// any arithmetic altogether and just answer the
						// non-immediate.
						callSiteHelper.useAnswer(arguments.get(0));
						return true;
					}
					op1 = a;
					op2 = new L2IntImmediateOperand(value);
				}
				else
				{
					final int value =
						stripNull(a.constantOrNull()).extractInt();
					if (value == 0)
					{
						// If the immediate is zero, then we can avoid emitting
						// any arithmetic altogether and just answer the
						// non-immediate.
						callSiteHelper.useAnswer(arguments.get(1));
						return true;
					}
					op1 = b;
					op2 = new L2IntImmediateOperand(value);
				}
				operation = L2_ADD_INT_TO_INT_CONSTANT.instance;
			}
			else
			{
				// Neither of the arguments is a constant, so emit an
				// instruction that takes two readers.
				operation = L2_ADD_INT_TO_INT.instance;
				op1 = a;
				op2 = b;
			}

			// We need two successors, the happy one that has successfully
			// performed the unboxed arithmetic and the sad one that needs to
			// fall back to the full primitive invocation mechanism.
			final L2BasicBlock boxUpSum = new L2BasicBlock("box sum");
			translator.addInstruction(
				operation,
				op1,
				op2,
				sum,
				translator.edgeTo(boxUpSum),
				translator.edgeTo(boxedAddition));

			// Here we've succeeded at performing unboxed arithmetic, so we need
			// to arrange to box the result up again for delivery.
			translator.startBlock(boxUpSum);
			final L2ReadPointerOperand boxed =
				translator.box(sum.read(), returnType);
			callSiteHelper.useAnswer(boxed);

			// Here we've failed at performing unboxed arithmetic, so we need to
			// fall back to primitive invocation.
			translator.startBlock(boxedAddition);
			translator.generateGeneralFunctionInvocation(
				functionToCallReg,
				arguments,
				returnType,
				false,
				callSiteHelper);
		}

		return true;
	}
}
