/*
 * P_GetObjectTypeField.kt
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
package avail.interpreter.primitive.objects

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_Atom.Companion.atomName
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Map.Companion.hasKey
import avail.descriptor.representation.A_Map.Companion.mapAtOrNull
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.fieldTypeMap
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.typeUnion
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2
import avail.optimizer.values.L2SemanticPrimitiveInvocation

/**
 * **Primitive:** Extract the specified [field's][AtomDescriptor] type from the
 * [object&#32;type][ObjectTypeDescriptor].
 */
@Suppress("unused")
object P_GetObjectTypeField : Primitive2(CanFold, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val objectType = arg1
		val field = arg2

		return when (val fieldType = objectType.fieldTypeAtOrNull(field))
		{
			null -> fail(E_NO_SUCH_FIELD)
			else -> fieldType
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralObjectMeta, ATOM()), anyMeta)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?, argumentTypes: List<A_Type>): A_Type
	{
		val (objectMeta, fieldType) = argumentTypes
		if (objectMeta.isBottom)
		{
			return bottom
		}
		if (fieldType.isEnumeration)
		{
			val objectType = objectMeta.instance
			val fieldTypeMap = objectType.fieldTypeMap
			var union = bottom
			for (possibleField in fieldType.instances)
			{
				val newType = fieldTypeMap.mapAtOrNull(possibleField) ?:
					// Unknown field, so the field type could be any type.
					return anyMeta
				union = union.typeUnion(newType)
			}
			// Shift it up; a primitive invocation will return the field's type.
			return instanceMeta(union)
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (objectMeta, fieldType) = argumentTypes
		if (fieldType.isEnumeration)
		{
			val objectType = objectMeta.instance
			val fieldTypeMap = objectType.fieldTypeMap
			for (possibleField in fieldType.instances)
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

	override fun printSemanticInvocation(
		invocation: L2SemanticPrimitiveInvocation
	): String
	{
		assert(invocation.primitive == this)
		val (obj, field) = invocation.argumentSemanticValues
		if (!field.isConstant) return super.printSemanticInvocation(invocation)
		val fieldAtom: A_Atom = field.constant!!
		val fieldName = fieldAtom.atomName.asNativeString()
		var objString = obj.toString()
		if (obj.requiresParentheses()) objString = "($objString)"
		return "$objString.$fieldName"
	}
}
