/**
 * StacksScanner.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.util.ArrayList;
import java.util.List;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;

/**
 * TODO: Document StacksScanner!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksScanner
{
	/**
	 * The {@link CommentTokenDescriptor comment token} text that has been
	 * lexed as one long token.
	 */
	final String commentTokenString;

	/**
	 * The name of the module being lexically scanned.
	 */
	final A_String moduleName;

	/**
	 * The tokens that have been parsed so far.
	 */
	final List<AbstractStacksToken> outputTokens;

	/**
	 * The current position in the input string.
	 */
	private int position;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	private int startOfToken;

	/**
	 * The place on the currently line where the token starts.
	 */
	private int startOfTokenLinePostion;

	/**
	 * The line number of the start of the token currently being parsed.
	 */
	int lineNumber;

	/**
	 * Alter the scanner's position in the input String.
	 *
	 * @param newPosition
	 *            The new position for this scanner as an index into the input
	 *            String's chars (which may not correspond with the actual code
	 *            points in the Supplementary Planes).
	 */
	void position (final int newPosition)
	{
		this.position = newPosition;
	}

	/**
	 * Answer the scanner's current index in the input String.
	 *
	 * @return The current position in the input String.
	 */
	int position ()
	{
		return position;
	}

	/**
	 * Answer the index into the input String at which the current token starts.
	 *
	 * @return The position of the current token in the input String.
	 */
	int startOfToken ()
	{
		return startOfToken;
	}

	/**
	 * Answer the name of the module being lexically scanned.
	 *
	 * @return The resolved module name.
	 */
	A_String moduleName ()
	{
		return moduleName;
	}

	/**
	 * Extract a native {@link String} from the input, from the
	 * {@link #startOfToken} to the current {@link #position}.
	 *
	 * @return A substring of the input.
	 */
	private String currentTokenString ()
	{
		return commentTokenString.substring(startOfToken, position);
	}

	/**
	 * Add the provided uninitialized {@linkplain StacksToken quoted
	 * token}.
	 *
	 * @return The newly added token.
	 */
	@InnerAccess
	StacksToken addCurrentToken ()
	{
		final StacksToken token = StacksToken.create(
			currentTokenString(),
			lineNumber,
			startOfToken,
			startOfTokenLinePostion);
		outputTokens.add(token);
		return token;
	}

	/**
	 * Add the provided uninitialized {@linkplain QuotedStacksToken quoted
	 * token}.
	 *
	 * @return The newly added token.
	 */
	@InnerAccess
	QuotedStacksToken addQuotedToken ()
	{
		final QuotedStacksToken token = QuotedStacksToken.create(
			currentTokenString(),
			startOfToken,
			lineNumber,
			startOfTokenLinePostion);
		outputTokens.add(token);
		return token;
	}

	/**
	 * Construct a new {@link StacksScanner}.
	 * @param commentToken
	 * 		the {@link CommentTokenDescriptor comment token} to be scanned and
	 * 		tokenized.
	 *
	 */
	private StacksScanner (final A_Token commentToken)
	{
		final String commentString =
			commentToken.string().asNativeString();
		this.commentTokenString = commentString;
		this.moduleName = commentToken.moduleName();
		this.outputTokens = new ArrayList<AbstractStacksToken>(
			commentTokenString.length() / 20);
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess
	boolean atEnd ()
	{
		return position == commentTokenString.length();
	}

	/**
	 * Extract the current character and increment the {@link #position}.
	 *
	 * @return The consumed character.
	 * @throws StacksScannerException If scanning fails.
	 */
	@InnerAccess
	int next ()
		throws StacksScannerException
	{
		if (atEnd())
		{
			throw new StacksScannerException(
				"Attempted to read past end of file",
				this);
		}
		final int c = Character.codePointAt(commentTokenString, position);
		position += Character.charCount(c);
		if (c == '\n')
		{
			lineNumber++;
		}
		return c;
	}

	/**
	 * Scan the already-specified {@link String} to produce {@linkplain
	 * #outputTokens tokens}.
	 *
	 * @throws StacksScannerException
	 */
	private void scan ()
		throws StacksScannerException
	{
		position = 0;
		lineNumber = 1;
		while (!atEnd())
		{
			startOfToken = position;
			ScannerAction.forCodePoint(next()).scan(this);
		}

		startOfToken = position;
		addCurrentToken(TokenType.END_OF_FILE);
	}

	/**
	 * Answer the character at the current {@link #position} of the input.
	 *
	 * @return The current character.
	 */
	@InnerAccess
	int peek ()
	{
		return Character.codePointAt(commentTokenString, position);
	}

	/**
	 * Skip aCharacter if it's next in the input stream. Answer whether it was
	 * present.
	 *
	 * @param aCharacter
	 *            The character to look for, as an int to allow code points
	 *            beyond the Basic Multilingual Plane (U+0000 - U+FFFF).
	 * @return Whether the specified character was found and skipped.
	 * @throws StacksScannerException If scanning fails.
	 */
	@InnerAccess
	boolean peekFor (final int aCharacter)
		throws StacksScannerException
	{
		if (atEnd())
		{
			return false;
		}
		if (peek() != aCharacter)
		{
			return false;
		}
		next();
		return true;
	}

	/**
	 * An enumeration of actions to be performed based on the next character
	 * encountered.
	 */
	enum ScannerAction
	{
		/**
		 * Parse double-quoted string literal. This is an open-quote, followed
		 * by zero or more items, and a close-quote. An item is either a single
		 * character that is neither backslash (\) nor quote ("), or a backslash
		 * followed by:
		 * <ol>
		 * <li>n, r, or t indicating newline (U+000A), return (U+000D) or tab
		 * (U+0009) respectively,</li>
		 * <li>a backslash (\) indicating a single literal backslash character,
		 * </li>
		 * <li>a quote (") indicating a single literal quote character,</li>
		 * <li>open parenthesis "(", one or more upper case hexadecimal
		 * sequences of one to six digits, separated by commas, and a close
		 * parenthesis,</li>
		 * <li>open bracket "[", an expression yielding a string, and "]", or
		 * </li>
		 * <li>the end of the line, indicating the next line is to be considered
		 * a continuation of the current line.</li>
		 * </ol>
		 */
		DOUBLE_QUOTE ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				final int literalStartingLine = scanner.lineNumber;
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw new StacksScannerException(
						"Unterminated string literal",
						scanner);
				}
				int c = scanner.next();
				final StringBuilder stringBuilder = new StringBuilder(40);
				boolean canErase = true;
				int erasurePosition = 0;
				while (c != '\"')
				{
					if (c == '\\')
					{
						if (scanner.atEnd())
						{
							throw new StacksScannerException(
								"Encountered end of file after backslash"
								+ " in string literal",
								scanner);
						}
						switch (c = scanner.next())
						{
							case 'n':
								stringBuilder.append('\n');
								break;
							case 'r':
								stringBuilder.append('\r');
								break;
							case 't':
								stringBuilder.append('\t');
								break;
							case '\\':
								stringBuilder.append('\\');
								break;
							case '\"':
								stringBuilder.append('\"');
								break;
							case '\r':
								// Treat \r or \r\n in the source just like \n.
								if (!scanner.atEnd())
								{
									scanner.peekFor('\n');
								}
								canErase = true;
								break;
							case '\n':
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
									stringBuilder.setLength(erasurePosition);
									canErase = false;
								}
								else
								{
									throw new StacksScannerException(
										"The input line before \"\\|\" "
										+ "contains non-whitespace",
										scanner);
								}
								break;
							case '(':
								parseUnicodeEscapes(scanner, stringBuilder);
								break;
							case '[':
								scanner.lineNumber = literalStartingLine;
								throw new StacksScannerException(
									"Power strings are not yet supported",
									scanner);
								// TODO parsePowerString(scanner);
								// break;
							default:
								throw new StacksScannerException(
									"Backslash escape should be followed by"
									+ " one of n, r, t, \\, \", (, [, |, or a"
									+ " line break",
									scanner);
						}
						erasurePosition = stringBuilder.length();
					}
					else if (c == '\r')
					{
						// Transform \r or \r\n in the source into \n.
						if (!scanner.atEnd())
						{
							scanner.peekFor('\n');
						}
						stringBuilder.appendCodePoint('\n');
						canErase = true;
						erasurePosition = stringBuilder.length();
					}
					else if (c == '\n')
					{
						// Just like a regular character, but limit how much
						// can be removed by a subsequent '\|'.
						stringBuilder.appendCodePoint(c);
						canErase = true;
						erasurePosition = stringBuilder.length();
					}
					else
					{
						stringBuilder.appendCodePoint(c);
						if (canErase && !Character.isWhitespace(c))
						{
							canErase = false;
						}
					}
					if (scanner.atEnd())
					{
						// Indicate where the quoted string started, to make it
						// easier to figure out where the end-quote is missing.
						scanner.lineNumber = literalStartingLine;
						throw new StacksScannerException(
							"Unterminated string literal",
							scanner);
					}
					c = scanner.next();
				}
				final String string = stringBuilder.toString();
				final A_String availValue = StringDescriptor.from(string);
				availValue.makeImmutable();
				scanner.addCurrentLiteralToken(availValue);
			}

			/**
			 * Parse Unicode hexadecimal encoded characters.  The characters
			 * "\" and "(" were just encountered, so expect a comma-separated
			 * sequence of hex sequences, each of no more than six digits, and
			 * having a value between 0 and 0x10FFFF, followed by a ")".
			 *
			 * @param scanner
			 *            The source of characters.
			 * @param stringBuilder
			 *            The {@link StringBuilder} on which to append the
			 *            corresponding Unicode characters.
			 */
			private void parseUnicodeEscapes (
					final StacksScanner scanner,
					final StringBuilder stringBuilder)
				throws StacksScannerException
			{
				int c;
				if (scanner.atEnd())
				{
					throw new StacksScannerException(
						"Expected hexadecimal Unicode codepoints separated by"
						+ " commas",
						scanner);
				}
				c = scanner.next();
				while (c != ')')
				{
					int value = 0;
					int digitCount = 0;
					while (c != ',' && c != ')')
					{
						if (c >= '0' && c <= '9')
						{
							value = (value << 4) + c - '0';
							digitCount++;
						}
						else if (c >= 'A' && c <= 'F')
						{
							value = (value << 4) + c - 'A' + 10;
							digitCount++;
						}
						else if (c >= 'a' && c <= 'f')
						{
							value = (value << 4) + c - 'a' + 10;
							digitCount++;
						}
						else
						{
							throw new StacksScannerException(
								"Expected a hex digit or comma or closing"
								+ " parenthesis",
								scanner);
						}
						if (digitCount > 6)
						{
							throw new StacksScannerException(
								"Expected at most six hex digits per"
								+ " comma-separated Unicode entry",
								scanner);
						}
						c = scanner.next();
					}
					if (digitCount == 0)
					{
						throw new StacksScannerException(
							"Expected a comma-separated list of Unicode code"
							+ " points, each being one to six (upper case)"
							+ " hexadecimal digits",
							scanner);
					}
					assert digitCount >= 1 && digitCount <= 6;
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw new StacksScannerException(
							"The maximum allowed code point for a Unicode"
							+ " character is U+10FFFF",
							scanner);
					}
					stringBuilder.appendCodePoint(value);
					assert c == ',' || c == ')';
					if (c == ',')
					{
						c = scanner.next();
					}
				}
				assert c == ')';
			}
		},

		/**
		 * A slash was encountered. Check if it's the start of a comment, and if
		 * so skip it. If not, add the slash as a {@linkplain TokenDescriptor
		 * token} of type {@link TokenType#OPERATOR}.
		 *
		 * <p>
		 * Nested comments are supported.
		 * </p>
		 */
		SLASH ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				if (!scanner.peekFor('*'))
				{
					scanner.addCurrentToken(TokenType.OPERATOR);
				}
				else
				{
					final int startLine = scanner.lineNumber;
					int depth = 1;
					while (true)
					{
						if (scanner.atEnd())
						{
							throw new StacksScannerException(
								"Expected a close comment (*/) to correspond"
									+ " with the open comment (/*) on line #"
									+ startLine,
								scanner);
						}
						if (scanner.peekFor('/') && scanner.peekFor('*'))
						{
							depth++;
						}
						else if (scanner.peekFor('*'))
						{
							if (scanner.peekFor('/'))
							{
								depth--;
							}
						}
						else
						{
							scanner.next();
						}
						if (depth == 0)
						{
							break;
						}
					}

				}

			}
		},

		/**
		 * Scan an unrecognized character.
		 */
		UNKNOWN ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				throw new StacksScannerException("Unknown character", scanner);
			}
		},

		/**
		 * A whitespace character was encountered. Just skip it.
		 */
		WHITESPACE ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				// do nothing.
			}
		},

		/**
		 * The zero-width non-breaking space character was encountered. This is
		 * also known as the byte-order-mark and generally only appears at the
		 * start of a file as a hint about the file's endianness.
		 *
		 * <p>
		 * Treat it as whitespace even though Unicode says it isn't.
		 * </p>
		 */
		ZEROWIDTHWHITESPACE ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				// do nothing
			}
		},

		/**
		 * Operator characters are never grouped. Instead, method names treat a
		 * string of operator characters as separate pseudo-keywords.
		 */
		OPERATOR ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				scanner.addCurrentToken(TokenType.OPERATOR);
			}
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *            The scanner processing this character.
		 * @throws StacksScannerException If scanning fails.
		 */
		abstract void scan (StacksScanner scanner)
			throws StacksScannerException;

		/**
		 * Figure out the {@link ScannerAction} to invoke for the specified code
		 * point. The argument may be any Unicode code point, including those in
		 * the Supplemental Planes.
		 *
		 * @param c
		 *            The code point to analyze.
		 * @return
		 *            The ScannerAction to invoke when that code point is
		 *            encountered.
		 */
		public static ScannerAction forCodePoint (final int c)
		{
			if (c < 65536)
			{
				return values()[dispatchTable[c]];
			}
			else if (Character.isDigit(c))
			{
				return DIGIT;
			}
			else if (Character.isUnicodeIdentifierStart(c))
			{
				return IDENTIFIER_START;
			}
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				return WHITESPACE;
			}
			else if (Character.isDefined(c))
			{
				return OPERATOR;
			}
			else
			{
				return UNKNOWN;
			}
		}
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 * that comprise a {@linkplain CommentTokenDescriptor Avail comment}.
	 *
	 * @param commentToken
	 *		An {@linkplain CommentTokenDescriptor Avail comment} to be
	 *		tokenized.
	 * @return a {@link List list} of all tokenized words in the {@link
	 * 		CommentTokenDescriptor Avail comment}.
	 * @throws StacksScannerException If scanning fails.
	 */
	public static List<A_Token> scanString (final A_Token commentToken)
		throws StacksScannerException
	{
		final StacksScanner scanner =
			new StacksScanner(commentToken);
		scanner.scan();
		return scanner.outputTokens;
	}

}
