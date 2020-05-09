/*
 * P_MapReplacingKey.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.maps

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.two
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.types.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.MapTypeDescriptor.mostGeneralMapType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

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
		val map = interpreter.argument(0)
		val key = interpreter.argument(1)
		val value = interpreter.argument(2)
		return interpreter.primitiveSuccess(
			map.mapAtPuttingCanDestroy(key, value, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralMapType(), ANY.o(), ANY.o()),
			mapTypeForSizesKeyTypeValueType(naturalNumbers(), ANY.o(), ANY.o()))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val mapType = argumentTypes[0]
		val addedKeyType = argumentTypes[1]
		val addedValueType = argumentTypes[2]

		val oldMapKeyType = mapType.keyType()
		val newKeyType = oldMapKeyType.typeUnion(addedKeyType)
		val newValueType = mapType.valueType().typeUnion(addedValueType)
		val oldSizes = mapType.sizeRange()
		// Now there's at least one element.
		var newMin = oldSizes.lowerBound()
		if (oldMapKeyType.typeIntersection(newKeyType).isBottom
		    || newMin.equalsInt(0))
		{
			newMin = newMin.plusCanDestroy(one(), false)
		}
		// ...and at most one more element.  We add two and make the bound
		// exclusive to accommodate positive infinity.
		val newMaxPlusOne =
			oldSizes.upperBound().plusCanDestroy(two(), false)
		val newSizes = integerRangeType(
			newMin, true, newMaxPlusOne, false)

		return mapTypeForSizesKeyTypeValueType(
			newSizes, newKeyType, newValueType)
	}
}
