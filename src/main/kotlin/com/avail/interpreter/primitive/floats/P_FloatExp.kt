/*
 * P_FloatExp.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.floats

import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import com.avail.descriptor.numbers.FloatDescriptor
import com.avail.descriptor.numbers.FloatDescriptor.Companion.fromFloatRecycling
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.TypeDescriptor.Types.FLOAT
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter
import kotlin.math.E
import kotlin.math.exp

/**
 * **Primitive:** Compute `e^a`, the natural exponential of the
 * [float][FloatDescriptor] `a`.
 */
@Suppress("unused")
object P_FloatExp : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val a = interpreter.argument(0)
		return interpreter.primitiveSuccess(
			fromFloatRecycling(
				exp(a.extractFloat().toDouble()).toFloat(), a, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				instanceType(fromDouble(E)),
				FLOAT.o
			),
			FLOAT.o
		)
}
