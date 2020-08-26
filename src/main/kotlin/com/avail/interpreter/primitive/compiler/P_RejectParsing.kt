/*
 * P_RejectParsing.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.primitive.compiler

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel
import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag.CAN_REJECT_PARSE
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.exceptions.AvailErrorCode.E_UNTIMELY_PARSE_REJECTION
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Reject current macro substitution with the specified one-based
 * error level [1..4] and error string.  The levels correspond to the instances
 * of [ParseNotificationLevel], although the integer has to be adjusted to
 * zero-based to use [ParseNotificationLevel.levelFromInt].
 */
@Suppress("unused")
object P_RejectParsing : Primitive(2, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		if (!interpreter.fiber().generalFlag(CAN_REJECT_PARSE))
		{
			return interpreter.primitiveFailure(E_UNTIMELY_PARSE_REJECTION)
		}
		val oneBasedRejectionLevel = interpreter.argument(0)
		val rejectionString = interpreter.argument(1)
		throw AvailRejectedParseException(
			ParseNotificationLevel.levelFromInt(
				oneBasedRejectionLevel.extractInt() - 1),
			rejectionString)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				inclusive(1L, 4L),
				stringType()),
			bottom
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_UNTIMELY_PARSE_REJECTION))
}
