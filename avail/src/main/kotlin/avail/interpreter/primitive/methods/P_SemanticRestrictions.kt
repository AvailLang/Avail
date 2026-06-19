/*
 * P_SemanticRestrictions.kt
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
package avail.interpreter.primitive.methods

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Method
import avail.descriptor.representation.A_Method.Companion.numArgs
import avail.descriptor.representation.A_Method.Companion.semanticRestrictions
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.acceptsTupleOfArguments
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Answer a [tuple][A_Tuple] of restriction
 * [functions][A_Function] that would run for a call site for the specified
 * [method][A_Method] and tuple of argument types.
 */
@Suppress("unused")
object P_SemanticRestrictions : Primitive2(CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val method = arg1
		val argTypes = arg2

		if (method.numArgs != argTypes.tupleSize)
		{
			return fail(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val restrictions = method.semanticRestrictions
		val applicable = mutableListOf<A_Function>()
		for (restriction in restrictions)
		{
			val function = restriction.function()
			if (function.kind().acceptsTupleOfArguments(argTypes))
			{
				applicable.add(function)
			}
		}
		return tupleFromList(applicable)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(METHOD(), zeroOrMoreOf(anyMeta)),
			zeroOrMoreOf(functionTypeReturning(topMeta)))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INCORRECT_NUMBER_OF_ARGUMENTS))
}
