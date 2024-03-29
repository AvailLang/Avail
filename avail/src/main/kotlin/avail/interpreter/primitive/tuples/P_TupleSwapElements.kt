/*
 * P_TupleSwapElements.kt
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
package avail.interpreter.primitive.tuples

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import kotlin.math.max
import kotlin.math.min

/**
 * **Primitive:** Answer a [tuple][TupleDescriptor] like the given one, but with
 * the elements at the specified indices swapped.
 */
@Suppress("unused")
object P_TupleSwapElements : Primitive(3, CanFold, CanInline)
{
	/**
	 * A measure of complexity beyond which we don't bother computing a precise
	 * guarantee about the resulting type, since the cost of computing it might
	 * be higher than the potential savings.
	 */
	private const val maximumComplexity = 100

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val tuple = interpreter.argument(0)
		val indexObject1 = interpreter.argument(1)
		val indexObject2 = interpreter.argument(2)

		if (!indexObject1.isInt || !indexObject2.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val index1 = indexObject1.extractInt
		val index2 = indexObject2.extractInt
		if (index1 == index2)
		{
			return interpreter.primitiveSuccess(tuple)
		}
		val temp1 = tuple.tupleAt(index1)
		val temp2 = tuple.tupleAt(index2)
		var newTuple = tuple.tupleAtPuttingCanDestroy(index1, temp2, true)
		newTuple = newTuple.tupleAtPuttingCanDestroy(index2, temp1, true)
		return interpreter.primitiveSuccess(newTuple)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				naturalNumbers,
				naturalNumbers),
			mostGeneralTupleType)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (originalTupleType, indexRange1, indexRange2) = argumentTypes
		val tupleSizeRange = originalTupleType.sizeRange
		val lowerBound1 = indexRange1.lowerBound
		val lowerBound2 = indexRange2.lowerBound
		if (!lowerBound1.isInt || !lowerBound2.isInt)
		{
			// At least one subscript is always out of range of the primitive.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val maxLowerBound =
			max(lowerBound1.extractInt, lowerBound2.extractInt)
		val maxTupleSize = tupleSizeRange.upperBound
		if (maxTupleSize.isInt && maxLowerBound > maxTupleSize.extractInt)
		{
			// One of the indices is always out of range of the tuple.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val minLowerBound =
			min(lowerBound1.extractInt, lowerBound2.extractInt)
		val startOfSmear = min(minLowerBound, maximumComplexity)
		// Keep the leading N types the same, but smear the rest.
		val newLeadingTypes = generateObjectTupleFrom(startOfSmear - 1) {
			originalTupleType.typeAtIndex(it)
		}
		return tupleTypeForSizesTypesDefaultType(
			tupleSizeRange,
			newLeadingTypes,
			originalTupleType.unionOfTypesAtThrough(
				startOfSmear, Integer.MAX_VALUE))
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))
}
