/*
 * P_BootstrapLexerStringBody.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.lexing;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG;
import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_BootstrapLexerStringBody} primitive is used for parsing quoted
 * string literal tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapLexerStringBody extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapLexerStringBody().init(
			3, CannotFail, CanFold, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_String source = interpreter.argument(0);
		final A_Number sourcePositionInteger = interpreter.argument(1);
		final A_Number lineNumberInteger = interpreter.argument(2);

		final int startPosition = sourcePositionInteger.extractInt();
		final int startLineNumber = lineNumberInteger.extractInt();

		final Scanner scanner = new Scanner(
			source, startPosition + 1, startLineNumber);
		final StringBuilder builder = new StringBuilder(32);
		boolean canErase = true;
		int erasurePosition = 0;
		while (scanner.hasNext())
		{
			int c = scanner.next();
			switch (c)
			{
				case '\"':
					final A_Token token = literalToken(
						source.copyStringFromToCanDestroy(
							startPosition, scanner.position - 1, false),
						startPosition,
						startLineNumber,
						stringFrom(builder.toString()));
					return interpreter.primitiveSuccess(set(tuple(token)));
				case '\\':
					if (!scanner.hasNext())
					{
						throw new AvailRejectedParseException(
							STRONG,
							"more characters after backslash in string "
								+ "literal");
					}
					c = scanner.next();
					switch (c)
					{
						case 'n':
							builder.append('\n');
							break;
						case 'r':
							builder.append('\r');
							break;
						case 't':
							builder.append('\t');
							break;
						case '\\':
							builder.append('\\');
							break;
						case '\"':
							builder.append('\"');
							break;
						case '\r':
							// Treat \r or \r\n in the source just like \n.
							if (scanner.hasNext() && scanner.peek() == '\n')
							{
								scanner.next();
							}
							canErase = true;
							break;
						case '\f':
						case '\n':
						// NEL (Next Line)
						case '\u0085':
						// LS (Line Separator)
						case '\u2028':
						// PS (Paragraph Separator)
						case '\u2029':
							// A backslash ending a line.  Emit nothing.
							// Allow '\|' to back up to here as long as only
							// whitespace follows.
							canErase = true;
							break;
						case '|':
							// Remove all immediately preceding white space
							// from this line.
							if (canErase)
							{
								builder.setLength(erasurePosition);
								canErase = false;
							}
							else
							{
								throw new AvailRejectedParseException(
									STRONG,
									"only whitespace characters on line "
										+ "before \"\\|\" ");
							}
							break;
						case '(':
							parseUnicodeEscapes(scanner, builder);
							break;
						case '[':
							throw new AvailRejectedParseException(
								WEAK,
								"something other than \"\\[\", because power "
									+ "strings are not yet supported");
						default:
							throw new AvailRejectedParseException(
								STRONG,
								"Backslash escape should be followed by "
									+ "one of n, r, t, \\, \", (, [, |, or a "
									+ "line break, not Unicode character "
									+ c);
					}
					erasurePosition = builder.length();
					break;
				case '\r':
					// Transform \r or \r\n in the source into \n.
					if (scanner.hasNext() && scanner.peek() == '\n')
					{
						scanner.next();
					}
					builder.appendCodePoint('\n');
					canErase = true;
					erasurePosition = builder.length();
					break;
				case '\n':
					// Just like a regular character, but limit how much
					// can be removed by a subsequent '\|'.
					builder.appendCodePoint(c);
					canErase = true;
					erasurePosition = builder.length();
					break;
				default:
					builder.appendCodePoint(c);
					if (canErase && !Character.isWhitespace(c))
					{
						canErase = false;
					}
					break;
			}
		}
		// TODO MvG - Indicate where the quoted string started, to make it
		// easier to figure out where the end-quote is missing.
		throw new AvailRejectedParseException(
			STRONG, "close-quote (\") for string literal");
	}

	/**
	 * A class to help consume codepoints during parsing.
	 */
	final class Scanner
	{
		/** The module source. */
		private final A_String source;

		/** The number of characters in the module source. */
		private final int sourceSize;

		/** The current position in the source. */
		int position;

		/** The current line number in the source. */
		int lineNumber;

		/**
		 * Create a {@code Scanner} to help parse the string literal.
		 *
		 * @param source The module source string.
		 * @param position The starting position in the source.
		 * @param lineNumber The starting line number in the source.
		 */
		Scanner (
			final A_String source,
			final int position,
			final int lineNumber)
		{
			this.source = source;
			this.sourceSize = source.tupleSize();
			this.position = position;
			this.lineNumber = lineNumber;
		}

		/**
		 * Whether there are any more codepoints available.
		 *
		 * @return {@code true} if there are more codepoints available,
		 *         otherwise {@code false}
		 */
		boolean hasNext ()
		{
			return position <= sourceSize;
		}

		/**
		 * Answer the next codepoint from the source, without consuming it.
		 * Should only be called if {@link #hasNext()} would produce true.
		 *
		 * @return The next codepoint.
		 */
		int peek ()
		{
			return source.tupleCodePointAt(position);
		}

		/**
		 * Answer the next codepoint from the source, and consume it.  Should
		 * only be called if {@link #hasNext()} would produce true before
		 * this call.
		 *
		 * @return The consumed codepoint.
		 */
		int next ()
		{
			final int c = source.tupleCodePointAt(position++);
			if (c == '\n')
			{
				lineNumber++;
			}
			return c;
		}
	}

	/**
	 * Parse Unicode hexadecimal encoded characters.  The characters "\" and "("
	 * were just encountered, so expect a comma-separated sequence of hex
	 * sequences, each with one to six digits, and having a value between 0 and
	 * 0x10FFFF, followed by a ")".
	 *
	 * @param scanner
	 *        The source of characters.
	 * @param stringBuilder
	 *        The {@link StringBuilder} on which to append the corresponding
	 *        Unicode characters.
	 */
	private void parseUnicodeEscapes (
		final Scanner scanner,
		final StringBuilder stringBuilder)
	{
		if (!scanner.hasNext())
		{
			throw new AvailRejectedParseException(
				STRONG,
				"Unicode escape sequence in string literal");
		}
		int c = scanner.next();
		while (c != ')')
		{
			int value = 0;
			int digitCount = 0;
			while (c != ',' && c != ')')
			{
				if (c >= '0' && c <= '9')
				{
					value = (value << 4) + c - (int)'0';
					digitCount++;
				}
				else if (c >= 'A' && c <= 'F')
				{
					value = (value << 4) + c - (int)'A' + 10;
					digitCount++;
				}
				else if (c >= 'a' && c <= 'f')
				{
					value = (value << 4) + c - (int)'a' + 10;
					digitCount++;
				}
				else
				{
					throw new AvailRejectedParseException(
						STRONG,
						"a hex digit or comma or closing parenthesis");
				}
				if (digitCount > 6)
				{
					throw new AvailRejectedParseException (
						STRONG,
						"at most six hex digits per comma-separated Unicode "
							+ "entry");
				}
				c = scanner.next();
			}
			if (digitCount == 0)
			{
				throw new AvailRejectedParseException(
					STRONG,
					"a comma-separated list of Unicode code"
						+ " points, each being one to six (upper case)"
						+ " hexadecimal digits");
			}
			if (value > CharacterDescriptor.maxCodePointInt)
			{
				throw new AvailRejectedParseException(
					STRONG,
					"A valid Unicode code point, which must be <= U+10FFFF");
			}
			stringBuilder.appendCodePoint(value);
			if (c == ',')
			{
				c = scanner.next();
			}
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return lexerBodyFunctionType();
	}
}
