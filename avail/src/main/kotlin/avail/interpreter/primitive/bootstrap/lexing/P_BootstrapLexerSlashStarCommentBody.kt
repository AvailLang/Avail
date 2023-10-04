/*
 * P_BootstrapLexerSlashStarCommentBody.kt
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

import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.descriptor.character.A_Character
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.A_Fiber.Companion.currentLexer
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.CommentTokenDescriptor.Companion.newCommentToken
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.firstIndexOfOr
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.style.P_BootstrapLexerSlashStarCommentBodyStyler

/**
 * The `P_BootstrapLexerSlashStarCommentBody` primitive is used for parsing
 * slash-star star-slash delimited comment tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapLexerSlashStarCommentBody
	: Primitive(3, CannotFail, CanFold, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val source = interpreter.argument(0)
		val sourcePositionInteger = interpreter.argument(1)
		val startingLineNumber = interpreter.argument(2)

		val sourceSize = source.tupleSize
		val startPosition = sourcePositionInteger.extractInt
		var position = startPosition + 1

		if (position > sourceSize
			|| source.tupleCodePointAt(position) != '*'.code)
		{
			// It didn't start with "/*", so it's not a comment.
			return interpreter.primitiveSuccess(emptySet)
		}
		position++

		var depth = 1
		while (true)
		{
			// At least two characters are available to examine.  Do a fast
			// scan to locate the next star or slash.
			position = source.firstIndexOfOr(
				slashCharacter,
				starCharacter,
				position,
				// Don't include the final character in the scan.
				sourceSize - 1)
			if (position == 0)
			{
				// There are no more slashes or stars prior to the last
				// character, so a "*/" or "/*" is impossible.  Reject the
				// lexing with a suitable warning.
				throw AvailRejectedParseException(
					STRONG,
					"Subsequent '*/' to close this (nestable) block comment")
			}
			val c = source.tupleCodePointAt(position)
			if (c == '*'.code
				&& source.tupleCodePointAt(position + 1) == '/'.code)
			{
				// Close a nesting level.
				position += 2
				depth--
				if (depth == 0)
				{
					break
				}
			}
			else if (c == '/'.code
				&& source.tupleCodePointAt(position + 1) == '*'.code)
			{
				// Open a new nesting level.
				position += 2
				depth++
			}
			else
			{
				position++
			}
		}

		// A comment was successfully parsed.
		val token = newCommentToken(
			source.copyStringFromToCanDestroy(
				startPosition, position - 1, false),
			startPosition,
			startingLineNumber.extractInt,
			interpreter.fiber().currentLexer)
		return interpreter.primitiveSuccess(set(tuple(token)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		lexerBodyFunctionType()

	override fun bootstrapStyler() = P_BootstrapLexerSlashStarCommentBodyStyler

	/** The [A_Character] for the '/' code point. */
	private val slashCharacter = fromCodePoint('/'.code)

	/** The [A_Character] for the '*' code point. */
	private val starCharacter = fromCodePoint('*'.code)
}
