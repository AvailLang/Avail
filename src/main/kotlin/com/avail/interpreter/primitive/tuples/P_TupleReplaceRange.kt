/*
 * P_TupleReplaceRange.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.noFailPlusCanDestroy
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.staticTupleAtPutting
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.lowerInclusive
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.A_Type.Companion.upperInclusive
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.ConcatenatedTupleTypeDescriptor.Companion.concatenatingAnd
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.exceptions.AvailErrorCode.E_NEGATIVE_SIZE
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create a tuple from the original tuple, with the designated
 * range of indices replaced with another tuple, not necessarily of the same
 * size.
 *
 * The start index may be as high as the tuple's size + 1 to indicate that the
 * replacement should be appended.  The end index may be as low as the start
 * index minus one to indicate a pure insertion.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_TupleReplaceRange : Primitive(4, CanInline, CanFold)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val originalTuple = interpreter.argument(0)
		val firstIndex = interpreter.argument(1)
		val lastIndex = interpreter.argument(2)
		val replacementSubtuple = interpreter.argument(3)
		if (!firstIndex.isInt || !lastIndex.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val startInt = firstIndex.extractInt()
		val endInt = lastIndex.extractInt()
		if (startInt < 1 || endInt < 0 || startInt > endInt + 1)
		{
			return interpreter.primitiveFailure(E_NEGATIVE_SIZE)
		}
		val originalSize = originalTuple.tupleSize()
		if (endInt > originalSize)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}

		var result: A_Tuple
		val sizeToReplace = endInt - startInt + 1
		if (sizeToReplace < 10
			&& replacementSubtuple.tupleSize() == sizeToReplace)
		{
			// Replacement is small and tuple won't change size, so do a series
			// of element replacements.
			var sourceIndex = 1
			result = originalTuple
			for (destIndex in startInt..endInt)
			{
				result = staticTupleAtPutting(
					result,
					destIndex,
					replacementSubtuple.tupleAt(sourceIndex++))
			}
		}
		else
		{
			val leftPart = originalTuple.copyTupleFromToCanDestroy(
				1, startInt - 1, false)
			val rightPart = originalTuple.copyTupleFromToCanDestroy(
				endInt + 1, originalSize, true)
			result = leftPart
				.concatenateWith(replacementSubtuple, true)
				.concatenateWith(rightPart, true)
			assert(result.tupleSize() ==
				originalSize - sizeToReplace + replacementSubtuple.tupleSize())
		}
		return interpreter.primitiveSuccess(result)
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility
	{
		val tupleType = argumentTypes[0]
		val startType = argumentTypes[1]
		val endType = argumentTypes[2]
		// val replacementType = argumentTypes[3]

		val tupleSizeRange = tupleType.sizeRange()
		val leftFallibility = P_ExtractSubtuple.checkFallibility(
			tupleSizeRange,
			singleInt(1),
			integerRangeType(
				startType.lowerBound().noFailMinusCanDestroy(one(), false),
				startType.lowerInclusive(),
				startType.upperBound().noFailMinusCanDestroy(one(), false),
				startType.upperInclusive()))
		val rightFallibility = P_ExtractSubtuple.checkFallibility(
			tupleSizeRange,
			integerRangeType(
				endType.lowerBound().noFailPlusCanDestroy(one(), false),
				endType.lowerInclusive(),
				endType.upperBound().noFailPlusCanDestroy(one(), false),
				endType.upperInclusive()),
			tupleSizeRange)
		// It's only the extraction of the left and right parts that can fail,
		// not the concatenation.  If P_ConcatenateTuples introduces a failure
		// case, this should be updated.
		return when {
			leftFallibility == CallSiteMustFail -> CallSiteMustFail
			rightFallibility == CallSiteMustFail -> CallSiteMustFail
			leftFallibility == CallSiteCanFail -> CallSiteCanFail
			rightFallibility == CallSiteCanFail -> CallSiteCanFail
			else -> CallSiteCannotFail
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val startType = argumentTypes[1]
		val endType = argumentTypes[2]
		val replacementType = argumentTypes[3]

		val tupleSizeRange = tupleType.sizeRange()
		val leftTupleType = P_ExtractSubtuple.computeSliceType(
			tupleType,
			singleInt(1),
			integerRangeType(
				startType.lowerBound().noFailMinusCanDestroy(one(), false),
				startType.lowerInclusive(),
				startType.upperBound().noFailMinusCanDestroy(one(), false),
				startType.upperInclusive()))
		val rightTupleType = P_ExtractSubtuple.computeSliceType(
			tupleType,
			integerRangeType(
				endType.lowerBound().noFailPlusCanDestroy(one(), false),
				endType.lowerInclusive(),
				endType.upperBound().noFailPlusCanDestroy(one(), false),
				endType.upperInclusive()),
			tupleSizeRange)
		return concatenatingAnd(
			leftTupleType,
			concatenatingAnd(replacementType, rightTupleType))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers,
				wholeNumbers,
				mostGeneralTupleType()),
			mostGeneralTupleType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_NEGATIVE_SIZE))
}
