/*
 * P_TupleToObjectType.kt
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

package avail.interpreter.primitive.objects

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromMap
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromTuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Convert a [tuple][TupleDescriptor] of field definitions into
 * an [object&#32;type][ObjectTypeDescriptor]. A field definition is a 2-tuple
 * whose first element is an [atom][AtomDescriptor] that represents the field
 * and whose second element is the value [type][TypeDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_TupleToObjectType : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val tuple = interpreter.argument(0)
		return interpreter.primitiveSuccess(objectTypeFromTuple(tuple))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(zeroOrMoreOf(tupleTypeForTypes(ATOM.o, anyMeta()))),
			mostGeneralObjectMeta)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val tupleSizes = tupleType.sizeRange
		val tupleSizeLowerBound = tupleSizes.lowerBound
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound)
			|| !tupleSizeLowerBound.isInt)
		{
			// Variable number of <key,value> pairs.  Give up.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val tupleSize = tupleSizeLowerBound.extractInt
		var fieldTypeMap = emptyMap
		for (i in 1 .. tupleSize)
		{
			val pairType = tupleType.typeAtIndex(i)
			assert(pairType.sizeRange.lowerBound.extractInt == 2)
			assert(pairType.sizeRange.upperBound.extractInt == 2)
			val keyType = pairType.typeAtIndex(1)
			if (!keyType.isEnumeration || !keyType.instanceCount.equalsInt(1))
			{
				// Can only strengthen if all key atoms are statically known.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
			}
			val keyValue = keyType.instance
			assert(keyValue.isAtom)
			if (fieldTypeMap.hasKey(keyValue))
			{
				// In case the semantics of this situation change.  Give up.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
			}
			val valueMeta = pairType.typeAtIndex(2)
			assert(valueMeta.isInstanceMeta)
			val valueType = valueMeta.instance
			fieldTypeMap =
				fieldTypeMap.mapAtPuttingCanDestroy(keyValue, valueType, true)
		}
		return instanceMeta(objectTypeFromMap(fieldTypeMap))
	}
}
