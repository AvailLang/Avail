/*
 * P_AtomicFetchAndAdd.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.variables

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.extendedIntegers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.VariableDescriptor
import com.avail.descriptor.VariableTypeDescriptor.variableReadWriteType
import com.avail.exceptions.ArithmeticException
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect

/**
 * **Primitive:** Atomically read and update the specified
 * [variable][VariableDescriptor] by the specified [ ].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_AtomicFetchAndAdd : Primitive(2, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val `var` = interpreter.argument(0)
		val addend = interpreter.argument(1)
		try
		{
			return interpreter.primitiveSuccess(
				`var`.fetchAndAddValue(addend))
		}
		catch (e: VariableGetException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: VariableSetException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: ArithmeticException)
		{
			return interpreter.primitiveFailure(e)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				variableReadWriteType(
					extendedIntegers(),
					bottom()),
				extendedIntegers()),
			extendedIntegers())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		return argumentTypes[0].readType()
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_CANNOT_ADD_UNLIKE_INFINITIES,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
	}

}