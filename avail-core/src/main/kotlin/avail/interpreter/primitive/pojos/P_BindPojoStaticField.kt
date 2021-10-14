/*
 * P_BindPojoStaticField.kt
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
package avail.interpreter.primitive.pojos

import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.pojos.PojoFieldDescriptor
import avail.descriptor.pojos.PojoFieldDescriptor.Companion.pojoFieldVariableForInnerType
import avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import avail.descriptor.pojos.RawPojoDescriptor.Companion.rawNullPojo
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PojoTypeDescriptor.Companion.resolvePojoType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_JAVA_FIELD_NOT_AVAILABLE
import avail.exceptions.AvailErrorCode.E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.pojos.PrimitiveHelper.lookupField
import avail.utility.Mutable
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * **Primitive:** Given a [type][A_Type] that can be successfully marshaled to a
 * Java type and a [string][A_String] that names a `static` [field][Field] of
 * that type, bind the `static` field to a [variable][PojoFieldDescriptor] such
 * that reads and writes of this variable pass through to the bound field.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BindPojoStaticField : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val pojoType = interpreter.argument(0)
		val fieldName = interpreter.argument(1)

		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)

		val errorOut = Mutable<AvailErrorCode?>(null)
		val field = lookupField(pojoType, fieldName, errorOut)
			?: return interpreter.primitiveFailure(errorOut.value!!)
		if (!Modifier.isStatic(field.modifiers))
		{
			// This is not the right primitive to bind instance fields.
			return interpreter.primitiveFailure(E_JAVA_FIELD_NOT_AVAILABLE)
		}
		// A static field cannot have a type parametric on type variables
		// of the declaring class, so pass an empty map where the type
		// variables are expected.
		val fieldType = resolvePojoType(field.genericType, emptyMap)
		val variable = pojoFieldVariableForInnerType(
			equalityPojo(field), rawNullPojo(), fieldType)
		return interpreter.primitiveSuccess(variable)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				anyMeta(),
				stringType),
			mostGeneralVariableType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_JAVA_FIELD_NOT_AVAILABLE,
			E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS))
}
