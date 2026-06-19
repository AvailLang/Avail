/*
 * P_AtomicFetchAndAdd.kt
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

import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.readType
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.A_Variable.Companion.fetchAndAddValue
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_CANNOT_ADD_UNLIKE_INFINITIES
import avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Atomically read and update the specified
 * [variable][A_Variable] by the specified [amount][IntegerDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicFetchAndAdd : Primitive2(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val variable = arg1
		val addend = arg2
		try
		{
			return variable.fetchAndAddValue(addend)
		}
		catch (e: VariableGetException)
		{
			return fail(e.errorCode)
		}
		catch (e: VariableSetException)
		{
			return fail(e.errorCode)
		}
		catch (e: ArithmeticException)
		{
			return fail(e.errorCode)
		}
	}

	/**
	 * If the variable had a reactor, adjusting it can activate that reactor,
	 * which might cause a variable captured in it to become shared.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				variableReadWriteType(
					extendedIntegers,
					bottom),
				extendedIntegers),
			extendedIntegers)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type =
		argumentTypes[0].readType

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_CANNOT_ADD_UNLIKE_INFINITIES,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
}
