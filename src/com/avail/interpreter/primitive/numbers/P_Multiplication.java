/*
 * P_Multiplication.java
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
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operation.L2_MULTIPLY_INT_BY_INT;
import com.avail.interpreter.levelTwo.operation.L2_MULTIPLY_INT_BY_INT_MOD_32_BITS;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractNumberDescriptor.binaryNumericOperationTypeBound;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.optimizer.L2Generator.edgeTo;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Primitive:</strong> Multiply two extended integers.
 */
public final class P_Multiplication
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_Multiplication().init(
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
			return interpreter.primitiveSuccess(a.timesCanDestroy(b, true));
		}
		catch (final ArithmeticException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(tuple(NUMBER.o(), NUMBER.o()), NUMBER.o());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		aType.makeImmutable();
		bType.makeImmutable();

		if (aType.isEnumeration() && bType.isEnumeration())
		{
			final A_Set aValues = aType.instances();
			final A_Set bValues = bType.instances();
			// Compute the Cartesian product as an enumeration if there will
			// be few enough entries.
			if (aValues.setSize() * (long) bValues.setSize() < 100)
			{
				A_Set answers = emptySet();
				for (final A_Number aValue : aValues)
				{
					for (final A_Number bValue : bValues)
					{
						try
						{
							answers = answers.setWithElementCanDestroy(
								aValue.timesCanDestroy(bValue, false),
								false);
						}
						catch (final ArithmeticException e)
						{
							// Ignore that combination of inputs, as it will
							// fail rather than return a value.
						}
					}
				}
				return enumerationWith(answers);
			}
		}
		if (aType.isIntegerRangeType() && bType.isIntegerRangeType())
		{
			return new BoundCalculator(aType, bType).process();
		}
		return binaryNumericOperationTypeBound(aType, bType);
	}

	/** A helper class for computing precise bounds. */
	public static final class BoundCalculator
	{
		/** The input ranges. */
		private final A_Type aType, bType;

		/** Accumulate the range. */
		private A_Type union = bottom();

		/** The infinities that should be included in the result. */
		private final Set<A_Number> includedInfinities = new HashSet<>(2);

		/** Partition the integers by sign. */
		private static final List<A_Type> interestingRanges = asList(
			inclusive(negativeInfinity(), negativeOne()),
			inclusive(zero(), zero()),
			inclusive(one(), positiveInfinity()));

		/**
		 * Partition the integer range into negatives, zero, and positives,
		 * omitting any empty regions.
		 */
		private static List<A_Type> split (final A_Type type)
		{
			return interestingRanges.stream()
				.map(type::typeIntersection)
				.filter(subrange -> !subrange.isBottom())
				.collect(toList());
		}

		/**
		 * Given an element from aType and an element from bType, extend the
		 * union to include their product, while also capturing information
		 * about whether an infinity should be included in the result.
		 *
		 * @param a
		 *        An extended integer from {@link #aType}, not necessarily
		 *        inclusive.
		 * @param b
		 *        An extended integer from {@link #bType}, not necessarily
		 *        inclusive.
		 */
		private void processPair (final A_Number a, final A_Number b)
		{
			if ((!a.equalsInt(0) || b.isFinite())
				&& (!b.equalsInt(0) || a.isFinite()))
			{
				// It's not 0 × ±∞, so include this product in the range.
				// Always include infinities for now, and trim them out later.
				final A_Number product = a.timesCanDestroy(b, false);
				product.makeImmutable();
				union = union.typeUnion(inclusive(product, product));
				if (!product.isFinite()
					&& a.isInstanceOf(aType)
					&& b.isInstanceOf(bType))
				{
					// Both inputs are inclusive, and the product is infinite.
					// Include the product in the output.
					includedInfinities.add(product);
				}
			}
		}

		/**
		 * Set up a new {@code BoundCalculator} for computing the bound of the
		 * product of elements from two integer range types.
		 *
		 * @param aType An integer range type.
		 * @param bType Another integer range type.
		 */
		BoundCalculator (final A_Type aType, final A_Type bType)
		{
			this.aType = aType;
			this.bType = bType;
		}

		/**
		 * Compute the bound for the product of the two integer range types that
		 * were supplied to the constructor.
		 *
		 * @return The bound of the product.
		 */
		A_Type process ()
		{
			final List<A_Type> aRanges = split(aType);
			final List<A_Type> bRanges = split(bType);
			for (final A_Type aRange : aRanges)
			{
				final A_Number aMin = aRange.lowerBound();
				final A_Number aMax = aRange.upperBound();
				for (final A_Type bRange : bRanges)
				{
					final A_Number bMin = bRange.lowerBound();
					final A_Number bMax = bRange.upperBound();
					processPair(aMin, bMin);
					processPair(aMin, bMax);
					processPair(aMax, bMin);
					processPair(aMax, bMax);
				}
			}
			// Trim off the infinities for now...
			union = union.typeIntersection(integers());
			// ...and add them back if needed.
			for (final A_Number infinity : includedInfinities)
			{
				union = union.typeUnion(inclusive(infinity, infinity));
			}
			return union;
		}
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		final boolean aTypeIncludesZero =
			zero().isInstanceOf(aType);
		final boolean aTypeIncludesInfinity =
			negativeInfinity().isInstanceOf(aType)
				|| positiveInfinity().isInstanceOf(aType);
		final boolean bTypeIncludesZero =
			zero().isInstanceOf(bType);
		final boolean bTypeIncludesInfinity =
			negativeInfinity().isInstanceOf(bType)
				|| positiveInfinity().isInstanceOf(bType);
		if ((aTypeIncludesZero && bTypeIncludesInfinity)
				|| (aTypeIncludesInfinity && bTypeIncludesZero))
		{
			return CallSiteCanFail;
		}
		return CallSiteCannotFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_CANNOT_MULTIPLY_ZERO_AND_INFINITY));
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
		final L2ReadBoxedOperand a = arguments.get(0);
		final L2ReadBoxedOperand b = arguments.get(1);
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
			"fall back to boxed multiplication");
		final L2ReadIntOperand intA =
			generator.readInt(a.semanticValue(), fallback);
		final L2ReadIntOperand intB =
			generator.readInt(b.semanticValue(), fallback);
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.
			final A_Type returnTypeIfInts = returnTypeGuaranteedByVM(
				rawFunction,
				argumentTypes.stream()
					.map(t -> t.typeIntersection(int32()))
					.collect(toList()));
			final L2SemanticValue semanticTemp =
				generator.topFrame.temp(generator.nextUnique());
			final L2WriteIntOperand tempWriter = generator.intWrite(
				semanticTemp,
				restrictionForType(returnTypeIfInts, UNBOXED_INT));
			if (returnTypeIfInts.isSubtypeOf(int32()))
			{
				// The result is guaranteed not to overflow, so emit an
				// instruction that won't bother with an overflow check.  Note
				// that both the unboxed and boxed registers end up in the same
				// synonym, so subsequent uses of the result might use either
				// register, depending whether an unboxed value is desired.
				translator.addInstruction(
					L2_MULTIPLY_INT_BY_INT_MOD_32_BITS.instance,
					intA,
					intB,
					tempWriter);
			}
			else
			{
				// The result could exceed an int32.
				final L2BasicBlock success =
					generator.createBasicBlock("product is in range");
				translator.addInstruction(
					L2_MULTIPLY_INT_BY_INT.instance,
					intA,
					intB,
					tempWriter,
					edgeTo(success),
					edgeTo(fallback));
				generator.startBlock(success);
			}
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp));
		}
		if (fallback.predecessorEdgesCount() > 0)
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the product.
			generator.startBlock(fallback);
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper);
		}
		return true;
	}
}
