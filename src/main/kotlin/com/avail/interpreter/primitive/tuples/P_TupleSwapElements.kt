/*
 * P_TupleSwapElements.java
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
package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import java.lang.Math.max
import java.lang.Math.min
import java.util.function.IntFunction

/**
 * **Primitive:** Answer a [ tuple][TupleDescriptor] like the given one, but with the elements at the specified indices
 * swapped.
 */
object P_TupleSwapElements : Primitive(3, CanFold, CanInline)
{
	/**
	 * A measure of complexity beyond which we don't bother computing a precise
	 * guarantee about the resulting type, since the cost of computing it might
	 * be higher than the potential savings.
	 */
	private val maximumComplexity = 100

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(3)
		val tuple = interpreter.argument(0)
		val indexObject1 = interpreter.argument(1)
		val indexObject2 = interpreter.argument(2)

		if (!indexObject1.isInt || !indexObject2.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val index1 = indexObject1.extractInt()
		val index2 = indexObject2.extractInt()
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

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers(),
				naturalNumbers()),
			mostGeneralTupleType())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val originalTupleType = argumentTypes[0]
		val indexRange1 = argumentTypes[1]
		val indexRange2 = argumentTypes[2]

		val tupleSizeRange = originalTupleType.sizeRange()
		val lowerBound1 = indexRange1.lowerBound()
		val lowerBound2 = indexRange2.lowerBound()
		if (!lowerBound1.isInt || !lowerBound2.isInt)
		{
			// At least one subscript is always out of range of the primitive.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val maxLowerBound = max(lowerBound1.extractInt(), lowerBound2.extractInt())
		val maxTupleSize = tupleSizeRange.upperBound()
		if (maxTupleSize.isInt && maxLowerBound > maxTupleSize.extractInt())
		{
			// One of the indices is always out of range of the tuple.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val minLowerBound = min(lowerBound1.extractInt(), lowerBound2.extractInt())

		val startOfSmear = min(minLowerBound, maximumComplexity)
		// Keep the leading N types the same, but smear the rest.
		val newLeadingTypes = generateObjectTupleFrom(
			startOfSmear - 1, IntFunction<A_BasicObject> { originalTupleType.typeAtIndex(it) })
		return tupleTypeForSizesTypesDefaultType(
			tupleSizeRange,
			newLeadingTypes,
			originalTupleType.unionOfTypesAtThrough(
				startOfSmear, Integer.MAX_VALUE))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))
	}

}