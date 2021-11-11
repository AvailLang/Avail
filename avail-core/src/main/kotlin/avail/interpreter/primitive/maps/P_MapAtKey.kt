/*
 * P_MapAtKey.kt
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
package avail.interpreter.primitive.maps

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Look up the key in the [map][A_Map], answering the
 * corresponding value.
 */
@Suppress("unused")
object P_MapAtKey : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val map = interpreter.argument(0)
		val key = interpreter.argument(1)
		map.mapAtOrNull(key)?.let {
			return interpreter.primitiveSuccess(it)
		}
		return interpreter.primitiveFailure(E_KEY_NOT_FOUND)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralMapType(), ANY.o), ANY.o)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val (mapType, keyType) = argumentTypes
		if (mapType.isEnumeration && keyType.isEnumeration)
		{
			var values = emptySet
			val keyTypeInstances = keyType.instances
			for (mapInstance in mapType.instances)
			{
				for (keyInstance in keyTypeInstances)
				{
					mapInstance.mapAtOrNull(keyInstance)?.let { value ->
						values = values.setWithElementCanDestroy(value, true)
					}
				}
			}
			return enumerationWith(values)
		}
		// Fall back on the map type's value type.
		return mapType.valueType
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_KEY_NOT_FOUND))
}
