/*
 * P_GetObjectTypeField.kt
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
package com.avail.interpreter.primitive.objects

import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Extract the specified [field's][AtomDescriptor] type from the
 * [object&#32;type][ObjectTypeDescriptor].
 */
@Suppress("unused")
object P_GetObjectTypeField : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val objectType = interpreter.argument(0)
		val field = interpreter.argument(1)

		return interpreter.primitiveSuccess(objectType.fieldTypeAt(field))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralObjectMeta(), ATOM.o()), anyMeta())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val objectMeta = argumentTypes[0]
		val fieldType = argumentTypes[1]

		if (objectMeta.isBottom)
		{
			return bottom()
		}
		if (fieldType.isEnumeration)
		{
			val objectType = objectMeta.instance()
			val fieldTypeMap = objectType.fieldTypeMap()
			var union = bottom()
			for (possibleField in fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field, so the field type could be any type.
					return anyMeta()
				}
				union = union.typeUnion(fieldTypeMap.mapAt(possibleField))
			}
			// Shift it up; a primitive invocation will return the field's type.
			return instanceMeta(union)
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val objectMeta = argumentTypes[0]
		val fieldType = argumentTypes[1]

		if (fieldType.isEnumeration)
		{
			val objectType = objectMeta.instance()
			val fieldTypeMap = objectType.fieldTypeMap()
			for (possibleField in fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field.
					return CallSiteCanFail
				}
			}
			return CallSiteCannotFail
		}
		return CallSiteCanFail
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NO_SUCH_FIELD))
}
