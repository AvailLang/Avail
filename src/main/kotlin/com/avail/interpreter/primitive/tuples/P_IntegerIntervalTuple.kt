/*
 * P_IntegerIntervalTuple.kt
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

package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.createInterval
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.integers
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.*
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create an
 * [integer&#32;interval&#32;tuple][IntegerIntervalTupleDescriptor].
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
@Suppress("unused")
object P_IntegerIntervalTuple : Primitive(3, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val start = interpreter.argument(0)
		val end = interpreter.argument(1)
		val delta = interpreter.argument(2)

		return if (delta.equalsInt(0))
		{
			interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
		}
		else interpreter.primitiveSuccess(createInterval(start, end, delta))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				integers(),
				integers(),
				integers()),
			zeroOrMoreOf(integers()))

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility {
		// val start = argumentTypes[0]
		// val end = argumentTypes[1]
		val delta = argumentTypes[2]
		val lowerDelta = delta.lowerBound()
		val upperDelta = delta.upperBound()
		return when
		{
			lowerDelta.greaterThan(zero()) || upperDelta.lessThan(zero()) ->
				CallSiteCannotFail
			lowerDelta.equalsInt(0) && upperDelta.equalsInt(0) ->
				CallSiteMustFail
			else -> CallSiteCanFail
		}
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INCORRECT_ARGUMENT_TYPE))
}
