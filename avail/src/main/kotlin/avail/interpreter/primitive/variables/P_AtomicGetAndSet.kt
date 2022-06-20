/*
 * P_AtomicGetAndSet.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable
import avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Atomically read and overwrite the specified
 * [variable][A_Variable].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicGetAndSet : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variable = interpreter.argument(0)
		val newValue = interpreter.argument(1)
		return try {
			interpreter.primitiveSuccess(variable.getAndSetValue(newValue))
		}
		catch (e: VariableGetException)
		{
			interpreter.primitiveFailure(e)
		}
		catch (e: VariableSetException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType,
				ANY.o),
			ANY.o)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val varType = argumentTypes[0]
		val readType = varType.readType
		return if (readType.isTop) ANY.o else readType
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
}
