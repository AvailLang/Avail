/*
 * P_ExtractSubtuple.kt
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

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerDescriptor.one
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.*
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
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
		return if (startInt < 1
			|| startInt > endInt + 1
			|| endInt > tuple.tupleSize())
		{
			interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		else interpreter.primitiveSuccess(
			tuple.copyTupleFromToCanDestroy(startInt, endInt, true))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val startType = argumentTypes[1]
		val endType = argumentTypes[2]

		// If the start type is a fixed index (and it's an int) then strengthen
		// the result easily.  Otherwise it's too tricky for now.
		val startInteger = startType.lowerBound()
		if (startInteger.equals(startType.upperBound()) && startInteger.isInt)
		{
			val startInt = startInteger.extractInt()
			val adjustment =
				startInteger.minusCanDestroy(one(), false).makeImmutable()
			val oldSizes = tupleType.sizeRange()
			val oldEnd1 = oldSizes.upperBound()
			val oldEnd2 = endType.upperBound()
			val oldEnd = if (oldEnd1.greaterOrEqual(oldEnd2))
				oldEnd2
			else
				oldEnd1
			val newLower =
				endType.lowerBound().minusCanDestroy(adjustment, false)
			val realLower = if (newLower.lessThan(zero())) zero() else newLower
			val newEnd = oldEnd.minusCanDestroy(adjustment, false)
			val newSizes = integerRangeType(
				realLower, true, newEnd.plusCanDestroy(one(), true), false)
			val originalLeading = tupleType.typeTuple()
			val newLeading = originalLeading.copyTupleFromToCanDestroy(
				min(startInt, originalLeading.tupleSize() + 1),
				originalLeading.tupleSize(),
				false)
			return tupleTypeForSizesTypesDefaultType(
				newSizes, newLeading, tupleType.defaultType())
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers(),
				wholeNumbers()),
			mostGeneralTupleType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))
}