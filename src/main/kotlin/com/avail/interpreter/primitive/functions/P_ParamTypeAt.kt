/*
 * P_ParamTypeAt.kt
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
package com.avail.interpreter.primitive.functions

import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer the type of the parameter at the given index within the
 * given functionType.
 */
@Suppress("unused")
object P_ParamTypeAt : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val functionType = interpreter.argument(0)
		val indexObject = interpreter.argument(1)

		val parametersType = functionType.argsTupleType()
		val sizeRange = parametersType.sizeRange()
		if (sizeRange.upperBound().lessThan(indexObject)) {
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		if (!indexObject.isInt) {
			// The function type must accept a very large number of arguments.
			return interpreter.primitiveSuccess(parametersType.defaultType())
		}
		val index = indexObject.extractInt()
		return interpreter.primitiveSuccess(
			functionType.argsTupleType().typeAtIndex(index))
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				functionMeta(),
				naturalNumbers()),
			anyMeta())

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		val functionMeta : A_Type = argumentTypes[0]
		val indexType = argumentTypes[1]

		val functionType = functionMeta.instance()
		val sizeRange = functionType.argsTupleType().sizeRange()
		if (sizeRange.isBottom)
		{
			// We know nothing about the actual function's parameter count.
			return CallSiteCanFail
		}
		if (indexType.upperBound().lessOrEqual(sizeRange.lowerBound()))
		{
			return CallSiteCannotFail
		}
		if (indexType.lowerBound().greaterThan(sizeRange.upperBound()))
		{
			return CallSiteMustFail
		}
		return CallSiteCannotFail
	}
}
