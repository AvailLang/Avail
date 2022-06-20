/*
 * P_MapWithoutKey.kt
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
import avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer a new [map][MapDescriptor], but without the given key.
 * Answer the original map if the key does not occur in it.
 */
@Suppress("unused")
object P_MapWithoutKey : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val map = interpreter.argument(0)
		val key = interpreter.argument(1)
		return interpreter.primitiveSuccess(
			map.mapWithoutKeyCanDestroy(key, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralMapType(), ANY.o), mostGeneralMapType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (mapType, keyType) = argumentTypes

		val mapSizes = mapType.sizeRange
		assert(mapSizes.lowerInclusive)
		if (mapType.keyType.typeIntersection(keyType).isBottom)
		{
			// That key will not be found.
			return mapType
		}
		// It's possible that the new map will be smaller by one.
		var minSize = mapSizes.lowerBound
		if (minSize.greaterThan(zero))
		{
			minSize = minSize.minusCanDestroy(one, false)
		}
		val newSizeRange = integerRangeType(
			minSize, true, mapSizes.upperBound, mapSizes.upperInclusive)
		return mapTypeForSizesKeyTypeValueType(
			newSizeRange, mapType.keyType, mapType.valueType)
	}
}
