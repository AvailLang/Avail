/*
 * P_BitShiftWithTruncation.kt
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

package avail.interpreter.primitive.integers

import avail.descriptor.numbers.A_Number.Companion.bitShiftLeftTruncatingToBits
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_TOO_LARGE_TO_REPRESENT
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Given a positive integer B, a shift factor S, and a truncation
 * bit count T, shift B to the left by S bits (treating a negative factor as a
 * right shift), then truncate the result to the bottom T bits by zeroing the
 * rest.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BitShiftWithTruncation : Primitive(3, CanInline, CanFold)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val baseInteger = interpreter.argument(0)
		val shiftFactor = interpreter.argument(1)
		val truncationBits = interpreter.argument(2)
		return try
		{
			return interpreter.primitiveSuccess(
				baseInteger.bitShiftLeftTruncatingToBits(
					shiftFactor, truncationBits, true))
		}
		catch (e: ArithmeticException)
		{
			// Note: The primitive's type signature ensures both baseInteger and
			// truncationBits are non-negative.
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(wholeNumbers, integers, wholeNumbers),
			wholeNumbers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_TOO_LARGE_TO_REPRESENT))
}
