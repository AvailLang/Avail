/*
 * P_MapAtKey.java
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
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.MapTypeDescriptor.mostGeneralMapType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.emptySet
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Look up the key in the [ ], answering the corresponding value.
 */
object P_MapAtKey : Primitive(2, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val map = interpreter.argument(0)
		val key = interpreter.argument(1)
		return if (!map.hasKey(key))
		{
			interpreter.primitiveFailure(E_KEY_NOT_FOUND)
		}
		else interpreter.primitiveSuccess(map.mapAt(key))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(mostGeneralMapType(), ANY.o()), ANY.o())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val mapType = argumentTypes[0]
		val keyType = argumentTypes[1]
		if (mapType.isEnumeration && keyType.isEnumeration)
		{
			var values = emptySet()
			val keyTypeInstances = keyType.instances()
			for (mapInstance in mapType.instances())
			{
				for (keyInstance in keyTypeInstances)
				{
					if (mapInstance.hasKey(keyInstance))
					{
						values = values.setWithElementCanDestroy(
							mapInstance.mapAt(keyInstance),
							true)
					}
				}
			}
			return enumerationWith(values)
		}
		// Fall back on the map type's value type.
		return mapType.valueType()
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_KEY_NOT_FOUND))
	}

}