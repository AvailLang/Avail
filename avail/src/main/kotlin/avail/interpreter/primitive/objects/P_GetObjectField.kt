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

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromTuple
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String.Companion.asNativeString
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
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_GET_OBJECT_FIELD
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue

/**
 * **Primitive:** Extract the specified [field][AtomDescriptor] from the
 * [object][ObjectDescriptor].
 */
@Suppress("unused")
object P_GetObjectField : Primitive2(CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val obj = arg1
		val field = arg2

		return when (val fieldValue = obj.fieldAtOrNull(field))
		{
			null -> interpreter.fail(E_NO_SUCH_FIELD)
			else -> fieldValue
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?, argumentTypes: List<A_Type>): A_Type
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
					return ANY()
				union = union.typeUnion(newType)
			}
			return union
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralObjectType, ATOM()), ANY())

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

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val (objectRead, fieldTypeRead) = arguments.elements
		val fieldAtom = fieldTypeRead.constantOrNull
		if (fieldAtom === null)
		{
			// It can't be an arbitrary atom that may or may not be a field, but
			// it could be a choice between multiple atoms that are known to be
			// fields of the object.  Fall back.
			emitBasicInfalliblePrimitive(rawFunction, arguments, result)
			return
		}
		objectRead.constantOrNull?.let { exactObject ->
			val fieldValue = exactObject.fieldAt(fieldAtom)
			moveBoxedRegister(
				boxedConstant(fieldValue).semanticValue(),
				result.semanticValues())
			return
		}
		val objectType = objectRead.type()
		assert(objectType.fieldTypeAtOrNull(fieldAtom) !== null)
		+L2_GET_OBJECT_FIELD(
			objectRead,
			L2ConstantOperand(fieldAtom),
			result)
	}

	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue<BOXED_KIND>>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// We've narrowed a field of some object.  Narrow the type of that
		// object accordingly.
		val (containingObject, field) = arguments
		// Only works if we know which field statically.
		val fieldAtom = field.constant ?: return
		assert(fieldAtom.isAtom)
		// See if the object that we represent the field of is listed under an
		// equivalent semantic value.
		val equivalentObject =
			manifest.equivalentSemanticValue(containingObject) ?: return
		manifest.intersectType(
			equivalentObject,
			objectTypeFromTuple(tuple(tuple(fieldAtom, restriction.type))))
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
