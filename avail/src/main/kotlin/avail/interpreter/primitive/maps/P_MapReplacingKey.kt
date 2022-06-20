/*
 * P_MapReplacingKey.kt
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
package avail.interpreter.primitive.maps

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer a new [map][MapDescriptor] like the given map, but also
 * including the binding between `key` and `value`. Overwrite any existing value
 * if the key is already present.
 */
@Suppress("unused")
object P_MapReplacingKey : Primitive(3, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val (map, key, value) = interpreter.argsBuffer
		return interpreter.primitiveSuccess(
			map.mapAtPuttingCanDestroy(key, value, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralMapType(), ANY.o, ANY.o),
			mapTypeForSizesKeyTypeValueType(naturalNumbers, ANY.o, ANY.o))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val (mapType, addedKeyType, addedValueType) = argumentTypes

		val oldMapKeyType = mapType.keyType
		val newKeyType = oldMapKeyType.typeUnion(addedKeyType)
		val newValueType = mapType.valueType.typeUnion(addedValueType)
		val oldSizes = mapType.sizeRange
		// Now there's at least one element.
		var newMin = oldSizes.lowerBound
		if (oldMapKeyType.typeIntersection(newKeyType).isBottom
			|| newMin.equalsInt(0))
		{
			newMin = newMin.plusCanDestroy(one, false)
		}
		// ...and at most one more element.  We add two and make the bound
		// exclusive to accommodate positive infinity.
		val newMaxPlusOne = oldSizes.upperBound.plusCanDestroy(two, false)
		val newSizes = integerRangeType(
			newMin, true, newMaxPlusOne, false)

		return mapTypeForSizesKeyTypeValueType(
			newSizes, newKeyType, newValueType)
	}
}
