/*
 * P_BootstrapLexerStringBody.kt
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

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import com.avail.descriptor.character.CharacterDescriptor
import com.avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapLexerStringBody` primitive is used for parsing quoted
 * string literal tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapLexerStringBody
	: Primitive(3, CannotFail, CanFold, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val source = interpreter.argument(0)
		val sourcePositionInteger = interpreter.argument(1)
		val lineNumberInteger = interpreter.argument(2)

		val startPosition = sourcePositionInteger.extractInt()
		val startLineNumber = lineNumberInteger.extractInt()

		val scanner = Scanner(
			source, startPosition + 1, startLineNumber)
		val builder = StringBuilder(32)
		var canErase = true
		var erasurePosition = 0
		while (scanner.hasNext())
		{
			var c = scanner.next().toChar()
			when (c)
			{
				'\"' ->
				{
					val token = literalToken(
						source.copyStringFromToCanDestroy(
							startPosition, scanner.position - 1, false),
						startPosition,
						startLineNumber,
						stringFrom(builder.toString()))
					return interpreter.primitiveSuccess(set(tuple(token)))
				}
				'\\' ->
				{
					if (!scanner.hasNext())
					{
						throw AvailRejectedParseException(
							STRONG,
							"more characters after backslash in string " + "literal")
					}
					c = scanner.next().toChar()
					when (c)
					{
						'n' -> builder.append('\n')
						'r' -> builder.append('\r')
						't' -> builder.append('\t')
						'\\' -> builder.append('\\')
						'\"' -> builder.append('\"')
						'\r' ->
						{
							// Treat \r or \r\n in the source just like \n.
							if (scanner.hasNext() && scanner.peek() == '\n'.toInt())
							{
								scanner.next()
							}
							canErase = true
						}
						'\u000C', '\n', // '\u000C' == '\f'
							// NEL (Next Line)
						'\u0085',
							// LS (Line Separator)
						'\u2028',
							// PS (Paragraph Separator)
						'\u2029' ->
							// A backslash ending a line.  Emit nothing.
							// Allow '\|' to back up to here as long as only
							// whitespace follows.
							canErase = true
						'|' ->
							// Remove all immediately preceding white space
							// from this line.
							if (canErase)
							{
								builder.setLength(erasurePosition)
								canErase = false
							}
							else
							{
								throw AvailRejectedParseException(
									STRONG,
									"only whitespace characters on line "
										+ "before \"\\|\" ")
							}
						'(' -> parseUnicodeEscapes(scanner, builder)
						'[' -> throw AvailRejectedParseException(
							WEAK,
							"something other than \"\\[\", because power "
								+ "strings are not yet supported")
						else -> throw AvailRejectedParseException(
							STRONG,
							"Backslash escape should be followed by "
							+ "one of n, r, t, \\, \", (, [, |, or a "
							+ "line break, not Unicode character "
							+ c)
					}
					erasurePosition = builder.length
				}
				'\r' ->
				{
					// Transform \r or \r\n in the source into \n.
					if (scanner.hasNext() && scanner.peek() == '\n'.toInt())
					{
						scanner.next()
					}
					builder.appendCodePoint('\n'.toInt())
					canErase = true
					erasurePosition = builder.length
				}
				'\n' ->
				{
					// Just like a regular character, but limit how much
					// can be removed by a subsequent '\|'.
					builder.appendCodePoint(c.toInt())
					canErase = true
					erasurePosition = builder.length
				}
				else ->
				{
					builder.appendCodePoint(c.toInt())
					if (canErase && !Character.isWhitespace(c))
					{
						canErase = false
					}
				}
			}
		}
		// TODO MvG - Indicate where the quoted string started, to make it
		// easier to figure out where the end-quote is missing.
		throw AvailRejectedParseException(
			STRONG, "close-quote (\") for string literal")
	}

	/**
	 * A class to help consume codepoints during parsing.
	 *
	 * @property source
	 *   The module source string.
	 * @property position
	 *   The starting position in the source.
	 * @property lineNumber
	 *   The starting line number in the source.
	 *
	 * @constructor
	 * Create a `Scanner` to help parse the string literal.
	 *
	 * @param source
	 *   The module source string.
	 * @param position
	 *   The starting position in the source.
	 * @param lineNumber
	 *   The starting line number in the source.
	 */
	internal class Scanner constructor (
		private val source: A_String, var position: Int, var lineNumber: Int)
	{
		/** The number of characters in the module source.  */
		private val sourceSize: Int = source.tupleSize()

		/**
		 * Whether there are any more codepoints available.
		 *
		 * @return `true` if there are more codepoints available,
		 * otherwise `false`
		 */
		operator fun hasNext(): Boolean = position <= sourceSize

		/**
		 * Answer the next codepoint from the source, without consuming it.
		 * Should only be called if [hasNext] would produce true.
		 *
		 * @return The next codepoint.
		 */
		fun peek(): Int = source.tupleCodePointAt(position)

		/**
		 * Answer the next codepoint from the source, and consume it.  Should
		 * only be called if [hasNext] would produce true before this call.
		 *
		 * @return The consumed codepoint.
		 */
		operator fun next(): Int
		{
			val c = source.tupleCodePointAt(position++)
			if (c == '\n'.toInt())
			{
				lineNumber++
			}
			return c
		}
	}

	/**
	 * Parse Unicode hexadecimal encoded characters.  The characters "\" and "("
	 * were just encountered, so expect a comma-separated sequence of hex
	 * sequences, each with one to six digits, and having a value between 0 and
	 * 0x10FFFF, followed by a ")".
	 *
	 * @param scanner
	 *   The source of characters.
	 * @param stringBuilder
	 *   The [StringBuilder] on which to append the corresponding Unicode
	 *   characters.
	 */
	private fun parseUnicodeEscapes(
		scanner: Scanner,
		stringBuilder: StringBuilder)
	{
		if (!scanner.hasNext())
		{
			throw AvailRejectedParseException(
				STRONG,
				"Unicode escape sequence in string literal")
		}
		var c = scanner.next()
		while (c != ')'.toInt())
		{
			var value = 0
			var digitCount = 0
			while (c != ','.toInt() && c != ')'.toInt())
			{
				if (c >= '0'.toInt() && c <= '9'.toInt())
				{
					value = (value shl 4) + c - '0'.toInt()
					digitCount++
				}
				else if (c >= 'A'.toInt() && c <= 'F'.toInt())
				{
					value = (value shl 4) + c - 'A'.toInt() + 10
					digitCount++
				}
				else if (c >= 'a'.toInt() && c <= 'f'.toInt())
				{
					value = (value shl 4) + c - 'a'.toInt() + 10
					digitCount++
				}
				else
				{
					throw AvailRejectedParseException(
						STRONG,
						"a hex digit or comma or closing parenthesis")
				}
				if (digitCount > 6)
				{
					throw AvailRejectedParseException(
						STRONG,
						"at most six hex digits per comma-separated Unicode "
							+ "entry")
				}
				c = scanner.next()
			}
			if (digitCount == 0)
			{
				throw AvailRejectedParseException(
					STRONG,
					"a comma-separated list of Unicode code"
					+ " points, each being one to six (upper case)"
					+ " hexadecimal digits")
			}
			if (value > CharacterDescriptor.maxCodePointInt)
			{
				throw AvailRejectedParseException(
					STRONG,
					"A valid Unicode code point, which must be <= U+10FFFF")
			}
			stringBuilder.appendCodePoint(value)
			if (c == ','.toInt())
			{
				c = scanner.next()
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type = lexerBodyFunctionType()
}
