/*
 * P_MapWithoutKey.kt
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
package com.avail.interpreter.primitive.maps

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerDescriptor.one
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.MapDescriptor
import com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.MapTypeDescriptor.mostGeneralMapType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

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
		functionType(tuple(mostGeneralMapType(), ANY.o()), mostGeneralMapType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val mapType = argumentTypes[0]
		val keyType = argumentTypes[1]

		val mapSizes = mapType.sizeRange()
		assert(mapSizes.lowerInclusive())
		var minSize = mapSizes.lowerBound()
		if (mapType.keyType().typeIntersection(keyType).isBottom)
		{
			// That key will not be found.
			return mapType
		}
		// It's possible that the new map will be smaller by one.
		if (minSize.greaterThan(zero()))
		{
			minSize = minSize.minusCanDestroy(one(), false)
		}
		val newSizeRange = integerRangeType(
			minSize, true, mapSizes.upperBound(), mapSizes.upperInclusive())
		return mapTypeForSizesKeyTypeValueType(
			newSizeRange, mapType.keyType(), mapType.valueType())
	}
}