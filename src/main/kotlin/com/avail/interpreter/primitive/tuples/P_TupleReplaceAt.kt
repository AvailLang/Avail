/*
 * P_TupleReplaceAt.kt
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
package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.greaterThan
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.defaultType
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter
import kotlin.math.max
import kotlin.math.min

/**
 * **Primitive:** Answer a [tuple][TupleDescriptor] like the given one, but with
 * an element changed as indicated.
 */
@Suppress("unused")
object P_TupleReplaceAt : Primitive(3, CanFold, CanInline)
{
	/**
	 * A measure of complexity beyond which we don't bother computing a precise
	 * guarantee about the resulting type, since the cost of computing it might
	 * be higher than the potential savings.
	 */
	private val maximumComplexity = fromInt(1000)

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val tuple = interpreter.argument(0)
		val indexObject = interpreter.argument(1)
		val value = interpreter.argument(2)
		if (!indexObject.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val index = indexObject.extractInt
		return if (index > tuple.tupleSize)
		{
			interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		else interpreter.primitiveSuccess(
			tuple.tupleAtPuttingCanDestroy(index, value, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				naturalNumbers,
				ANY.o),
			mostGeneralTupleType)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (originalTupleType, subscripts, newElementType) = argumentTypes
		val lowerBound = subscripts.lowerBound
		val upperBound = subscripts.upperBound
		val singleSubscript = lowerBound.equals(upperBound)
		if (lowerBound.greaterThan(maximumComplexity)
			|| (upperBound.isFinite
				&& upperBound.greaterThan(maximumComplexity)))
		{
			// Too expensive.  Fall back on the primitive's basic type.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val originalTypeTuple = originalTupleType.typeTuple
		val originalTypeTupleSize = originalTypeTuple.tupleSize
		val minSubscript =
			if (lowerBound.isInt) max(lowerBound.extractInt, 1)
			else 1
		val maxSubscript =
			if (upperBound.isFinite)
				min(upperBound.extractInt, originalTypeTupleSize)
			else
				Integer.MAX_VALUE
		val typeList = mutableListOf<A_Type>()
		typeList.addAll(originalTypeTuple)
		for (i in 1 until minSubscript)
		{
			typeList.add(originalTupleType.typeAtIndex(i))
		}
		val limit = min(maxSubscript, originalTypeTupleSize)
		for (i in minSubscript .. limit)
		{
			if (singleSubscript)
			{
				// A single subscript is possible, so that element *must* be
				// replaced by the new value.
				assert(minSubscript == limit)
				typeList.add(newElementType)
			}
			else
			{
				// A non-singular range of subscripts is eligible to be
				// overwritten, so any element in that range can have either the
				// old type or the new type.
				typeList.add(
					originalTupleType.typeAtIndex(i).typeUnion(newElementType))
			}
		}
		for (i in limit + 1 .. originalTypeTupleSize)
		{
			typeList.add(originalTupleType.typeAtIndex(i))
		}
		val newDefaultType = if (upperBound.isFinite)
			originalTupleType.defaultType
		else
			originalTupleType.defaultType.typeUnion(newElementType)
		return tupleTypeForSizesTypesDefaultType(
			originalTupleType.sizeRange,
			tupleFromList(typeList),
			newDefaultType)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))
}
