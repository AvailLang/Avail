/*
 * P_BitShiftWithTruncation.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.integers

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.bitShift
import avail.descriptor.representation.A_Number.Companion.bitShiftLeftTruncatingToBits
import avail.descriptor.representation.A_Number.Companion.greaterThan
import avail.descriptor.representation.A_Number.Companion.lessOrEqual
import avail.descriptor.representation.A_Number.Companion.lessThan
import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Set.Companion.setSize
import avail.descriptor.representation.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.A_Type.Companion.upperInclusive
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_TOO_LARGE_TO_REPRESENT
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive3
import java.lang.Math.multiplyExact

/**
 * **Primitive:** Given a positive integer B, a shift factor S, and a truncation
 * bit count T, shift B to the left by S bits (treating a negative factor as a
 * right shift), then truncate the result to the bottom T bits by zeroing the
 * rest.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BitShiftWithTruncation : Primitive3(CanInline, CanFold)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val baseInteger = arg1
		val shiftFactor = arg2
		val truncationBits = arg3
		return try
		{
			baseInteger.bitShiftLeftTruncatingToBits(
				shiftFactor, truncationBits, true)
		}
		catch (e: ArithmeticException)
		{
			// Note: The primitive's type signature ensures both baseInteger and
			// truncationBits are non-negative.
			fail(e.errorCode)
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val baseIntegers: A_Type = argumentTypes[0]
		val shiftFactors: A_Type = argumentTypes[1]
		val truncationBitsRange: A_Type = argumentTypes[2]
		if (baseIntegers.isEnumeration
			&& shiftFactors.isEnumeration
			&& truncationBitsRange.isEnumeration)
		{
			val bases = baseIntegers.instances
			val leftShifts = shiftFactors.instances
			val truncationBits = truncationBitsRange.instances
			val combinations = try
			{
				multiplyExact(
					multiplyExact(bases.setSize, leftShifts.setSize),
					truncationBits.setSize)
			}
			catch (e: ArithmeticException)
			{
				Int.MAX_VALUE
			}
			// If there are sufficiently few combinations, compute them all.
			if (combinations <= 256L)
			{
				var results = SetDescriptor.emptySet
				truncationBits.forEach { truncationBitCount ->
					leftShifts.forEach { leftShift ->
						bases.forEach { base ->
							results = results.setWithElementCanDestroy(
								base.bitShiftLeftTruncatingToBits(
									leftShift, truncationBitCount, false),
								true)
						}
					}
				}
				return enumerationWith(results)
			}
		}
		val beforeTruncation = P_BitShiftLeft.returnTypeGuaranteedByVM(
			null,
			listOf(baseIntegers, shiftFactors))
		// We can cop out and use 0 as the lower bound, and say the result can
		// grow as large as the minimm of the shifted value or the largest
		// truncation mask.
		val biggestTruncationBitCount = truncationBitsRange.upperBound
		if (biggestTruncationBitCount.greaterThan(fromInt(1000)))
		{
			// Too expensive to create giant integers.  Ignore the truncation.
			return integerRangeType(
				zero,
				true,
				beforeTruncation.upperBound,
				beforeTruncation.upperInclusive)
		}
		val maxMask = one.bitShift(biggestTruncationBitCount, false)
			.minusCanDestroy(one, true)
		val minMask = one.bitShift(truncationBitsRange.lowerBound, false)
			.minusCanDestroy(one, true)
		val beforeUpper = beforeTruncation.upperBound
		if (beforeUpper.lessOrEqual(minMask))
		{
			// Even the smallest mask won't alter the output.
			return beforeTruncation
		}
		return inclusive(
			zero,
			if (beforeUpper.lessThan(maxMask)) beforeUpper
			else maxMask)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(wholeNumbers, integers, wholeNumbers),
			wholeNumbers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_TOO_LARGE_TO_REPRESENT))
}
