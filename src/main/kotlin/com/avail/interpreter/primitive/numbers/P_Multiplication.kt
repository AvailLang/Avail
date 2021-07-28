/*
 * P_Multiplication.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.numbers

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.A_Number.Companion.equalsInt
import com.avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Companion.binaryNumericOperationTypeBound
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import com.avail.descriptor.types.TypeDescriptor.Types.NUMBER
import com.avail.exceptions.ArithmeticException
import com.avail.exceptions.AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import com.avail.interpreter.levelTwo.operation.L2_MULTIPLY_INT_BY_INT
import com.avail.interpreter.levelTwo.operation.L2_MULTIPLY_INT_BY_INT_MOD_32_BITS
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo
import com.avail.optimizer.values.L2SemanticUnboxedInt
import com.avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation

/**
 * **Primitive:** Multiply two extended integers.
 */
@Suppress("unused")
object P_Multiplication : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return try
		{
			interpreter.primitiveSuccess(a.timesCanDestroy(b, true))
		}
		catch (e: ArithmeticException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER.o, NUMBER.o), NUMBER.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_MULTIPLY_ZERO_AND_INFINITY))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		aType.makeImmutable()
		bType.makeImmutable()

		if (aType.isEnumeration && bType.isEnumeration)
		{
			val aValues = aType.instances
			val bValues = bType.instances
			// Compute the Cartesian product as an enumeration if there will
			// be few enough entries.
			if (aValues.setSize * bValues.setSize.toLong() < 100)
			{
				var answers = emptySet
				for (aValue in aValues)
				{
					for (bValue in bValues)
					{
						try
						{
							answers = answers.setWithElementCanDestroy(
								aValue.timesCanDestroy(bValue, false),
								false)
						}
						catch (e: ArithmeticException)
						{
							// Ignore that combination of inputs, as it will
							// fail rather than return a value.
						}

					}
				}
				return enumerationWith(answers)
			}
		}
		return if (aType.isIntegerRangeType && bType.isIntegerRangeType)
		{
			BoundCalculator(aType, bType).process()
		}
		else
		{
			binaryNumericOperationTypeBound(aType, bType)
		}
	}

	/**
	 * A helper class for computing precise bounds.
	 *
	 * @property aType
	 *   An integer range type.
	 * @property bType
	 *   Another integer range type.
	 *
	 * @constructor
	 * Set up a new `BoundCalculator` for computing the bound of the
	 * product of elements from two integer range types.
	 *
	 * @param aType
	 *   An integer range type.
	 * @param bType
	 *   Another integer range type.
	 */
	class BoundCalculator internal constructor(
		private val aType: A_Type, private val bType: A_Type)
	{
		/** Accumulate the range. */
		private var union = bottom

		/** The infinities that should be included in the result. */
		private val includedInfinities = mutableSetOf<A_Number>()

		/**
		 * Given an element from aType and an element from bType, extend the
		 * union to include their product, while also capturing information
		 * about whether an infinity should be included in the result.
		 *
		 * @param a
		 *   An extended integer from [aType], not necessarily inclusive.
		 * @param b
		 *   An extended integer from [bType], not necessarily inclusive.
		 */
		private fun processPair(a: A_Number, b: A_Number)
		{
			if ((!a.equalsInt(0) || b.isFinite)
				&& (!b.equalsInt(0) || a.isFinite))
			{
				// It's not 0 × ±∞, so include this product in the range.
				// Always include infinities for now, and trim them out later.
				val product = a.timesCanDestroy(b, false)
				product.makeImmutable()
				union = union.typeUnion(inclusive(product, product))
				if (!product.isFinite
					&& a.isInstanceOf(aType)
					&& b.isInstanceOf(bType))
				{
					// Both inputs are inclusive, and the product is infinite.
					// Include the product in the output.
					includedInfinities.add(product)
				}
			}
		}

		/**
		 * Compute the bound for the product of the two integer range types that
		 * were supplied to the constructor.
		 *
		 * @return The bound of the product.
		 */
		internal fun process(): A_Type
		{
			val aRanges = split(aType)
			val bRanges = split(bType)
			for (aRange in aRanges)
			{
				val aMin = aRange.lowerBound
				val aMax = aRange.upperBound
				for (bRange in bRanges)
				{
					val bMin = bRange.lowerBound
					val bMax = bRange.upperBound
					processPair(aMin, bMin)
					processPair(aMin, bMax)
					processPair(aMax, bMin)
					processPair(aMax, bMax)
				}
			}
			// Trim off the infinities for now...
			union = union.typeIntersection(integers)
			// ...and add them back if needed.
			for (infinity in includedInfinities)
			{
				union = union.typeUnion(inclusive(infinity, infinity))
			}
			return union
		}

		companion object
		{
			/** Partition the integers by sign. */
			private val interestingRanges = listOf(
				inclusive(negativeInfinity, negativeOne()),
				inclusive(zero, zero),
				inclusive(one, positiveInfinity))

			/**
			 * Partition the integer range into negatives, zero, and positives,
			 * omitting any empty regions.
			 */
			private fun split(type: A_Type): List<A_Type> =
				interestingRanges
					.map{ type.typeIntersection(it) }
					.filter { subrange -> !subrange.isBottom }
		}
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		val aTypeIncludesZero = zero.isInstanceOf(aType)
		val aTypeIncludesInfinity =
			negativeInfinity.isInstanceOf(aType)
				|| positiveInfinity.isInstanceOf(aType)
		val bTypeIncludesZero = zero.isInstanceOf(bType)
		val bTypeIncludesInfinity =
			negativeInfinity.isInstanceOf(bType)
				|| positiveInfinity.isInstanceOf(bType)
		return if (aTypeIncludesZero && bTypeIncludesInfinity
			|| aTypeIncludesInfinity && bTypeIncludesZero)
		{
			CallSiteCanFail
		}
		else
		{
			CallSiteCannotFail
		}
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper
		): Boolean
	{
		val (a, b) = arguments
		val (aType, bType) = argumentTypes

		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		if (aType.typeIntersection(int32).isBottom
			|| bType.typeIntersection(int32).isBottom)
		{
			return false
		}

		// Attempt to unbox the arguments.
		val generator = translator.generator
		val fallback = generator.createBasicBlock(
			"fall back to boxed multiplication")
		val intA = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		val intB = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallback)
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.
			val returnTypeIfInts = returnTypeGuaranteedByVM(
				rawFunction,
				argumentTypes.map { it.typeIntersection(int32) })
			val semanticTemp = primitiveInvocation(
				this, listOf(a.semanticValue(), b.semanticValue()))
			val tempWriter = generator.intWrite(
				setOf(L2SemanticUnboxedInt(semanticTemp)),
				restrictionForType(returnTypeIfInts, UNBOXED_INT_FLAG))
			if (returnTypeIfInts.isSubtypeOf(int32))
			{
				// The result is guaranteed not to overflow, so emit an
				// instruction that won't bother with an overflow check.  Note
				// that both the unboxed and boxed registers end up in the same
				// synonym, so subsequent uses of the result might use either
				// register, depending whether an unboxed value is desired.
				translator.addInstruction(
					L2_MULTIPLY_INT_BY_INT_MOD_32_BITS,
					intA,
					intB,
					tempWriter)
			}
			else
			{
				// The result could exceed an int32.
				val success =
					generator.createBasicBlock("product is in range")
				translator.addInstruction(
					L2_MULTIPLY_INT_BY_INT,
					intA,
					intB,
					tempWriter,
					edgeTo(fallback),
					edgeTo(success))
				generator.startBlock(success)
			}
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
		}
		if (fallback.predecessorEdges().isNotEmpty())
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the product.
			generator.startBlock(fallback)
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}
}
