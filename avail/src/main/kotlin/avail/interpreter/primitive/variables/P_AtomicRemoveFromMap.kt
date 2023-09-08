/*
 * P_AtomicRemoveFromMap.kt
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

import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.variables.A_Variable
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Atomically read and update the map in the specified
 * [variable][A_Variable] by removing the given key, if present.  If not
 * present, this has no effect.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicRemoveFromMap : Primitive(2, CanInline, HasSideEffect) {
	override fun attempt(interpreter: Interpreter): Result {
		interpreter.checkArgumentCount(2)
		val variable = interpreter.argument(0)
		val key = interpreter.argument(1)
		try
		{
			variable.atomicRemoveFromMap(key)
		}
		catch (e: VariableGetException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: VariableSetException)
		{
			return interpreter.primitiveFailure(e)
		}

		interpreter.availLoaderOrNull()?.recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.REMOVE_FROM_MAP_VARIABLE, variable, key))
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				variableReadWriteType(
					mostGeneralMapType(),
					bottom),
				ANY.o),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE))
}
