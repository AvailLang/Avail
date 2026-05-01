/*
 * P_BootstrapLexerOperatorBody.kt
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

package avail.interpreter.primitive.bootstrap.lexing

import avail.descriptor.fiber.A_Fiber.Companion.currentLexer
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.Bootstrap
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive3

/**
 * The `P_BootstrapLexerOperatorBody` primitive is used for parsing operator
 * tokens.  These currently are a single character long.
 *
 *
 * Note that if a slash is encountered, and it's followed an asterisk, it
 * should reject the lexical scan, allowing the comment lexer to deal with it
 * instead.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapLexerOperatorBody
	: Primitive3(CannotFail, CanFold, CanInline, Bootstrap)
{
	override fun attempt3(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val source = arg1
		val sourcePositionInteger = arg2
		val lineNumberInteger = arg3

		val sourceSize = source.tupleSize
		val startPosition = sourcePositionInteger.extractInt

		val c = source.tupleCodePointAt(startPosition)
		if (c == '/'.code)
		{
			if (startPosition < sourceSize
				&& source.tupleCodePointAt(startPosition + 1) == '*'.code)
			{
				// No solution in this case, but don't complain.
				return emptySet
			}
		}
		val token = newToken(
			source.copyStringFromToCanDestroy(
				startPosition, startPosition, false),
			startPosition,
			lineNumberInteger.extractInt,
			OPERATOR,
			interpreter.fiber().currentLexer)
		return set(tuple(token))
	}

	override fun privateBlockTypeRestriction(): A_Type = lexerBodyFunctionType()
}
