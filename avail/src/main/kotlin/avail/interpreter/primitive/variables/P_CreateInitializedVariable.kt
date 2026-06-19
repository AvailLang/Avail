/*
 * P_CreateInitializedVariable.kt
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
package avail.interpreter.primitive.variables

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.A_Variable.Companion.setValue
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Create a [variable][A_Variable] with the given inner
 * [type][A_Type].  Also initialize it with the given value.  Answer the
 * variable.
 */
@Suppress("unused")
object P_CreateInitializedVariable : Primitive2(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val innerType = arg1
		val initialValue = arg2

		val variable = newVariableWithContentType(innerType)
		try {
			variable.setValue(initialValue)
			return variable
		}
		catch (e: VariableSetException)
		{
			// The variable is new, so this is the only possible way to fail.
			assert(e.errorCode == E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
			return fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(anyMeta, ANY()),
			mostGeneralVariableType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE))
}
