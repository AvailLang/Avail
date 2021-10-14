/*
 * P_FloatTruncatedAsInteger.kt
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
package avail.interpreter.primitive.floats

import avail.descriptor.numbers.A_Number.Companion.extractFloat
import avail.descriptor.numbers.DoubleDescriptor.Companion.doubleTruncatedToExtendedInteger
import avail.descriptor.numbers.FloatDescriptor
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.FLOAT
import avail.exceptions.AvailErrorCode.E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Convert a [float][FloatDescriptor] to an
 * [integer][IntegerDescriptor], rounding towards zero.
 */
@Suppress("unused")
object P_FloatTruncatedAsInteger : Primitive(1, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val a = interpreter.argument(0)
		// Extract the top two 32-bit sections.  That guarantees 33 bits
		// of mantissa, which is more than a float actually captures.
		val f = a.extractFloat
		return if (java.lang.Float.isNaN(f))
		{
			interpreter.primitiveFailure(
				E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER)
		}
		else interpreter.primitiveSuccess(
			doubleTruncatedToExtendedInteger(f.toDouble()))
		// Do the conversion as a Double.
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(FLOAT.o), extendedIntegers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER))
}
