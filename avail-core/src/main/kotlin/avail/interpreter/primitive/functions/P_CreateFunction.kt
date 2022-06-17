/*
 * P_CreateFunction.kt
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
package avail.interpreter.primitive.functions

import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.exceptions.AvailErrorCode.E_WRONG_OUTERS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer a [function][FunctionDescriptor] built from the
 * [function][CompiledCodeDescriptor] and the outer variables.
 */
@Suppress("unused")
object P_CreateFunction : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val rawFunction = interpreter.argument(0)
		val outers = interpreter.argument(1)
		val numOuters = rawFunction.numOuters
		if (outers.tupleSize != numOuters)
		{
			return interpreter.primitiveFailure(E_WRONG_OUTERS)
		}
		for (i in 1 .. numOuters)
		{
			val outer = outers.tupleAt(i)
			val requiredType = rawFunction.outerTypeAt(i)
			if (!outer.isInstanceOf(requiredType))
			{
				return interpreter.primitiveFailure(E_WRONG_OUTERS)
			}
		}
		val function = createFunction(rawFunction, outers)
		return interpreter.primitiveSuccess(function)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralCompiledCodeType(), mostGeneralTupleType),
			mostGeneralFunctionType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_WRONG_OUTERS))
}
