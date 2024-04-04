/*
 * P_GetObjectField.kt
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
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_GET_OBJECT_FIELD
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * **Primitive:** Extract the specified [field][AtomDescriptor] from the
 * [object][ObjectDescriptor].
 */
@Suppress("unused")
object P_GetObjectField : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (obj, field) = interpreter.argsBuffer

		return when (val fieldValue = obj.fieldAtOrNull(field))
		{
			null -> interpreter.primitiveFailure(E_NO_SUCH_FIELD)
			else -> interpreter.primitiveSuccess(fieldValue)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralObjectType, ATOM.o), ANY.o)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val (objectType, fieldType) = argumentTypes

		if (objectType.isBottom)
		{
			return bottom
		}
		val fieldTypeMap = objectType.fieldTypeMap
		if (fieldType.isEnumeration)
		{
			var union = bottom
			for (possibleField in fieldType.instances)
			{
				val newType = fieldTypeMap.mapAtOrNull(possibleField) ?:
					// Unknown field, so the type could be anything.
					return ANY.o
				union = union.typeUnion(newType)
			}
			return union
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (objectType, fieldType) = argumentTypes
		val fieldTypeMap = objectType.fieldTypeMap
		if (fieldType.isEnumeration)
		{
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

	override fun emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand,
		regenerator: L2Regenerator)
	{
		val (objectRead, fieldTypeRead) = arguments.elements
		val fieldAtom = fieldTypeRead.restriction().constantOrNull
		if (fieldAtom === null)
		{
			// It can't be an arbitrary atom that may or may not be a field, but
			// it could be a choice between multiple atoms that are known to be
			// fields of the object.  Fall back.
			super.emitTransformedInfalliblePrimitive(
				rawFunction, arguments, result, regenerator)
			return
		}
		objectRead.constantOrNull()?.let { exactObject ->
			val fieldValue = exactObject.fieldAt(fieldAtom)
			regenerator.moveRegister(
				BOXED_KIND,
				regenerator.boxedConstant(fieldValue).semanticValue(),
				result.semanticValues())
			return
		}
		val objectType = objectRead.type()
		assert(objectType.fieldTypeAtOrNull(fieldAtom) !== null)
		regenerator.addInstruction(
			L2_GET_OBJECT_FIELD,
			objectRead,
			L2ConstantOperand(fieldAtom),
			result)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NO_SUCH_FIELD))
}
