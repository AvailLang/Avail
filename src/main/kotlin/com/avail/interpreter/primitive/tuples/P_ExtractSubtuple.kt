/*
 * P_ExtractSubtuple.kt
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
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter
import kotlin.math.max
import kotlin.math.min

/**
 * **Primitive:** Extract a [subtuple][TupleDescriptor] with the given range of
 * elements.
 */
@Suppress("unused")
object P_ExtractSubtuple : Primitive(3, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val tuple = interpreter.argument(0)
		val start = interpreter.argument(1)
		val end = interpreter.argument(2)
		if (!start.isInt || !end.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val startInt = start.extractInt()
		val endInt = end.extractInt()
		return when
		{
			startInt < 1 ->
				interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
			startInt > endInt + 1 ->
				interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
			endInt > tuple.tupleSize() ->
				interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
			else ->
				interpreter.primitiveSuccess(
					tuple.copyTupleFromToCanDestroy(startInt, endInt, true))
		}
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility
	{
		val tupleType = argumentTypes[0]
		val startIndexType = argumentTypes[1]
		val endIndexType = argumentTypes[2]

		return checkFallibility(
			tupleType.sizeRange(), startIndexType, endIndexType)
	}

	/**
	 * Check whether the given tuple size range can have a slice extracted with
	 * start and end indices with the given range.  Answer a
	 * [Primitive.Fallibility] that indicates whether the extraction would
	 * always, sometimes, or never fail.
	 *
	 * @param sizeRange
	 *   The integer range type describing the possible tuple sizes.
	 * @param startIndexType
	 *   An integer range type bounding the possible slice starting index.
	 * @param endIndexType
	 *   An integer range type bounding the possible slice ending index.
	 * @return
	 *   The fallibility guarantee of the tuple extraction attempt.
	 */
	fun checkFallibility(
		sizeRange: A_Type,
		startIndexType: A_Type,
		endIndexType: A_Type
	): Fallibility
	{
		val sizeLower = sizeRange.lowerBound().run {
			if (!isInt) return CallSiteMustFail  // Call can't actually happen.
			extractInt()
		}
		val sizeUpper = sizeRange.upperBound().run {
			if (isInt) extractInt()
			else Int.MAX_VALUE  // The *actual* tuple is constrained.
		}

		val startLower = startIndexType.lowerBound().run {
			if (!isInt) return CallSiteMustFail
			extractInt()
		}
		val endLower = endIndexType.lowerBound().run {
			if (!isInt) return CallSiteMustFail
			extractInt()
		}
		val startUpper = startIndexType.upperBound().run {
			if (!isInt) return CallSiteCanFail
			extractInt()
		}
		val endUpper = endIndexType.upperBound().run {
			if (!isInt) return CallSiteCanFail
			extractInt()
		}
		return when
		{
			// Definitely a degenerate slice.
			endUpper < startLower - 1 -> CallSiteMustFail
			// Definitely out-of-range.
			startLower - 1 > sizeUpper -> CallSiteMustFail
			// At this point, the slice is at least possible.  Check for
			// possible degeneracy.
			endLower < startUpper - 1 -> CallSiteCanFail
			// And check for a possible out-of-range.
			endUpper > sizeLower -> CallSiteCanFail
			else -> CallSiteCannotFail
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val startIndexType = argumentTypes[1]
		val endIndexType = argumentTypes[2]

		return computeSliceType(tupleType, startIndexType, endIndexType)
	}

	/**
	 * Compute the types that can be produced by extracting a subrange of a
	 * tuple having the given [tupleType], starting at an index that falls in
	 * the integer range type [startIndexType], and ending at an index that
	 * falls in the integer range type [endIndexType].
	 *
	 * @param tupleType
	 *   The type of the tuple being sliced.
	 * @param startIndexType
	 *   An integer range type bounding the possible slice starting index.
	 * @param endIndexType
	 *   An integer range type bounding the possible slice ending index.
	 * @return
	 *   The guaranteed type of the resulting tuple, in the event that it's
	 *   successful.
	 */
	fun computeSliceType(
		tupleType: A_Type,
		startIndexType: A_Type,
		endIndexType: A_Type
	): A_Type
	{
		// Precompute a fallback return type for easy exits.
		val fallback = mostGeneralTupleType()

		val sizeRange = tupleType.sizeRange()
		val sizeUpper = sizeRange.upperBound().run {
			if (isInt) extractInt()
			else Int.MAX_VALUE  // If it's very large or even infinity
		}
		val startLower = startIndexType.lowerBound().run {
			if (!isInt) return fallback
			extractInt()
		}
		val startUpper = startIndexType.upperBound().run {
			if (!isInt) Int.MAX_VALUE
			else extractInt()
		}
		val endLower = endIndexType.lowerBound().run {
			if (!isInt) return fallback
			extractInt()
		}
		val endUpper = endIndexType.upperBound().run {
			if (!isInt) Int.MAX_VALUE
			else extractInt()
		}

		if (startLower > endUpper)
		{
			// It's always either empty or degenerate.
			return instanceType(emptyTuple)
		}

		// No need to compute element types beyond this position, since
		// they'll all have the same type.
		val variationLimit =
			min(tupleType.typeTuple().tupleSize() + 1, sizeUpper)

		// The difference between extrema for starting positions.
		val smearDelta = startUpper - startLower

		val leadingTypes =
			(startLower .. max(startLower, min(endUpper, variationLimit))).map {
				tupleType.unionOfTypesAtThrough(
					it,
					// Don't exceed an int.
					min(
						it.toLong() + smearDelta,
						Int.MAX_VALUE.toLong()
					).toInt())
			}
		val minSize = max(endLower - startUpper + 1, 0)
		val maxSize = min(endUpper, sizeUpper) - startLower + 1
		val maxSizeObject = when
		{
			endUpper == Int.MAX_VALUE && sizeUpper == Int.MAX_VALUE ->
				positiveInfinity()
			else -> fromInt(maxSize)
		}
		return tupleTypeForSizesTypesDefaultType(
			integerRangeType(
				fromInt(minSize),
				true,
				maxSizeObject,
				maxSizeObject.isFinite),
			tupleFromList(leadingTypes),
			leadingTypes.last()).makeImmutable()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers,
				wholeNumbers
			),
			mostGeneralTupleType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))
}
