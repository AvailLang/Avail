/*
 * P_DoubleTimesTwoPower.kt
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
package avail.interpreter.primitive.doubles

import avail.descriptor.numbers.DoubleDescriptor
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDoubleRecycling
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractDouble
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.greaterOrEqual
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive3
import java.lang.Math.scalb
import kotlin.math.max
import kotlin.math.min

/**
 * **Primitive:** Compute the [double][DoubleDescriptor] `a*(2**b)` without
 * intermediate overflow or any precision loss.
 */
@Suppress("unused")
object P_DoubleTimesTwoPower : Primitive3(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		// val literalTwo = arg2
		val b = arg3

		val scale = when {
			b.isInt -> min(max(b.extractInt, -10000), 10000)
			b.greaterOrEqual(zero) -> 10000
			else -> -10000
		}
		val d = scalb(a.extractDouble, scale)
		return fromDoubleRecycling(d, a, true)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				DOUBLE(),
				instanceType(two),
				integers),
			DOUBLE())
}
