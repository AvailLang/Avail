/*
 * P_CatchException.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.exceptionType
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Always fail. The Avail failure code invokes the [body
 * block][FunctionDescriptor]. A handler block is only invoked when an exception
 * is raised.
 */
object P_CatchException : Primitive(
	3,
	CatchException,
	PreserveFailureVariable,
	PreserveArguments,
	CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		//		final A_BasicObject bodyBlock = interpreter.argument(0);
		val handlerBlocks = interpreter.argument(1)
		//		final A_BasicObject ensureBlock = interpreter.argument(2);

		val innerVariable = newVariableWithOuterType(
			failureVariableType)

		for (block in handlerBlocks)
		{
			if (!block.kind().argsTupleType().typeAtIndex(1).isSubtypeOf(
					exceptionType()))
			{
				innerVariable.setValueNoCheck(
					E_INCORRECT_ARGUMENT_TYPE.numericCode())
				return interpreter.primitiveFailure(innerVariable)
			}
		}
		innerVariable.setValueNoCheck(E_REQUIRED_FAILURE.numericCode())
		return interpreter.primitiveFailure(innerVariable)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				functionType(emptyTuple(), TOP.o()),
				zeroOrMoreOf(functionType(tuple(bottom()), TOP.o())),
				functionType(emptyTuple(), TOP.o())),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		// Note: The failure value is itself a new variable stuffed into the
		// outer (primitive-failure) variable.
		variableTypeFor(
			enumerationWith(
				set(
					E_REQUIRED_FAILURE,
					E_INCORRECT_ARGUMENT_TYPE,
					E_HANDLER_SENTINEL,
					E_UNWIND_SENTINEL)))
}
