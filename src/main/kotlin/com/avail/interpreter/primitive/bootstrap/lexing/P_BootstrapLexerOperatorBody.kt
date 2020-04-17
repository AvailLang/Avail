/*
 * P_BootstrapLexerOperatorBody.kt
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

package com.avail.interpreter.primitive.bootstrap.lexing

import com.avail.descriptor.parsing.LexerDescriptor.lexerBodyFunctionType
import com.avail.descriptor.sets.SetDescriptor.emptySet
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import com.avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

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
	: Primitive(3, CannotFail, CanFold, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val source = interpreter.argument(0)
		val sourcePositionInteger = interpreter.argument(1)
		val lineNumberInteger = interpreter.argument(2)

		val sourceSize = source.tupleSize()
		val startPosition = sourcePositionInteger.extractInt()

		val c = source.tupleCodePointAt(startPosition)
		if (c == '/'.toInt())
		{
			if (startPosition < sourceSize && source.tupleCodePointAt(startPosition + 1) == '*'.toInt())
			{
				// No solution in this case, but don't complain.
				return interpreter.primitiveSuccess(emptySet())
			}
		}
		val token = newToken(
			source.copyStringFromToCanDestroy(
				startPosition, startPosition, false),
			startPosition,
			lineNumberInteger.extractInt(),
			OPERATOR)
		return interpreter.primitiveSuccess(set(tuple(token)))
	}

	override fun privateBlockTypeRestriction(): A_Type = lexerBodyFunctionType()
}
