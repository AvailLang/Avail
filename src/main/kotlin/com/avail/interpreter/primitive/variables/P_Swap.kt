/*
 * P_Swap.kt
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
package com.avail.interpreter.primitive.variables

import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.variables.A_Variable
import com.avail.exceptions.AvailErrorCode.E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES
import com.avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Swap the contents of two [variables][A_Variable].
 */
@Suppress("unused")
object P_Swap : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val var1 = interpreter.argument(0)
		val var2 = interpreter.argument(1)
		if (!var1.kind().equals(var2.kind()))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES)
		}
		// This should work even on unassigned variables.
		val value1 = var1.value()
		val value2 = var2.value()
		// Record access specially, since we are using the "fast" variable
		// content accessor.
		if (interpreter.traceVariableReadsBeforeWrites())
		{
			val fiber = interpreter.fiber()
			fiber.recordVariableAccess(var1, true)
			fiber.recordVariableAccess(var2, true)
		}
		return try {
			var1.setValue(value2)
			var2.setValue(value1)
			interpreter.primitiveSuccess(nil)
		} catch (e: VariableSetException) {
			interpreter.primitiveFailure(e)
		}
}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType(),
				mostGeneralVariableType()),
			TOP.o
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
}
