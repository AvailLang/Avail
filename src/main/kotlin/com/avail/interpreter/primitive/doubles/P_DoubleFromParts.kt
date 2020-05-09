/*
 * P_DoubleFromParts.kt
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

package com.avail.interpreter.primitive.doubles

import com.avail.descriptor.character.CharacterDescriptor.Companion.nonemptyStringOfDigitsType
import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.DOUBLE
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Construct a non-negative [double][A_Number] from parts
 * supplied primarily as [strings][A_String] of digits.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DoubleFromParts : Primitive(4, CannotFail, CanInline, CanFold)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val (wholePart, fractionPart, exponentSign, exponentPart) =
			interpreter.argsBuffer

		// Since we expect that this primitive will only be used for building
		// floating-point literals, it doesn't need to be particularly
		// efficient. We therefore convert the different parts to strings,
		// compose a floating-point numeral, and then ask Java to parse and
		// convert. This is less efficient than doing the work ourselves, but
		// gives us the opportunity to leverage well-tested and tuned Java
		// library code.
		val numeral =
			wholePart.asNativeString() +
				"." +
				fractionPart.asNativeString() +
				"e" +
				when {
					exponentSign.extractBoolean() -> ""
					else -> "-"} +
			    exponentPart.asNativeString()
		val result: A_Number
		try
		{
			result = fromDouble(java.lang.Double.valueOf(numeral))
		}
		catch (e: NumberFormatException)
		{
			assert(false) {
				"This shouldn't happen, since we control the numeral!"
			}
			throw e
		}
		return interpreter.primitiveSuccess(result)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				nonemptyStringOfDigitsType,
				nonemptyStringOfDigitsType,
				booleanType(),
				nonemptyStringOfDigitsType),
			DOUBLE.o())
}
