/*
 * P_Subtraction.java
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
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operation.L2_SUBTRACT_INT_MINUS_INT;
import com.avail.interpreter.levelTwo.operation.L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractNumberDescriptor.binaryNumericOperationTypeBound;
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
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_SUBTRACT_LIKE_INFINITIES;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restriction;

/**
 * <strong>Primitive:</strong> Subtract {@linkplain
 * AbstractNumberDescriptor number} b from a.
 */
public final class P_Subtraction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_Subtraction().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Number a = interpreter.argument(0);
		final A_Number b = interpreter.argument(1);
		try
		{
			return interpreter.primitiveSuccess(a.minusCanDestroy(b, true));
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
								aInstance.minusCanDestroy(bInstance, false),
								false);
						}
					}
					return
						enumerationWith(answers);
				}
			}
			if (aType.isIntegerRangeType() && bType.isIntegerRangeType())
			{
				final A_Number low = aType.lowerBound().minusCanDestroy(
					bType.upperBound(),
					false);
				final A_Number high = aType.upperBound().minusCanDestroy(
					bType.lowerBound(),
					false);
				final boolean includesNegativeInfinity =
					negativeInfinity().isInstanceOf(aType)
						|| positiveInfinity().isInstanceOf(bType);
				final boolean includesInfinity =
					positiveInfinity().isInstanceOf(aType)
						|| negativeInfinity().isInstanceOf(bType);
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
		if ((aTypeIncludesNegativeInfinity && bTypeIncludesNegativeInfinity)
				|| (aTypeIncludesInfinity && bTypeIncludesInfinity))
		{
			return CallSiteCanFail;
		}
		return CallSiteCannotFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_CANNOT_SUBTRACT_LIKE_INFINITIES));
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
		final L2ReadPointerOperand a = arguments.get(0);
		final L2ReadPointerOperand b = arguments.get(1);
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
		final L2Generator generator = translator.generator;
		final L2BasicBlock fallback = generator.createBasicBlock(
			"fall back to boxed subtraction");
		final L2ReadIntOperand intA = generator.readIntRegister(
			a.register(), a.restriction(), fallback);
		final L2ReadIntOperand intB = generator.readIntRegister(
			b.register(), b.restriction(), fallback);
		final A_Type returnType = returnTypeGuaranteedByVM(
			rawFunction, argumentTypes);
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.
			final L2WriteIntOperand difference =
				generator.newIntRegisterWriter(restriction(returnType));
			if (returnType.isSubtypeOf(int32()))
			{
				// The result is guaranteed not to overflow, so emit an
				// instruction that won't bother with an overflow check.  Note
				// that both the unboxed and boxed registers end up in the same
				// synonym, so subsequent uses of the result might use either
				// register, depending whether an unboxed value is desired.
				translator.addInstruction(
					L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS.instance,
					intA,
					intB,
					difference);
			}
			else
			{
				// The result could exceed an int32.
				final L2BasicBlock success =
					generator.createBasicBlock("difference is in range");
				translator.addInstruction(
					L2_SUBTRACT_INT_MINUS_INT.instance,
					intA,
					intB,
					difference,
					translator.edgeTo(
						success,
						difference.read().restrictedToType(int32())),
					translator.edgeTo(
						fallback,
						difference.read().restrictedWithoutType(int32())));
				generator.startBlock(success);
			}
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			final L2ReadPointerOperand boxed = generator.readBoxedRegister(
				difference.register(), restriction(returnType));
			callSiteHelper.useAnswer(boxed);
		}
		if (fallback.predecessorEdgesCount() > 0)
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the sum.
			generator.startBlock(fallback);
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
