/**
 * compiler/scanner/AvailScanner.java Copyright © 1993-2013, Mark van Gulik and Todd L Smith. All
 * rights reserved.
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

package com.avail.compiler.scanning;

import static com.avail.compiler.scanning.AvailScanner.ScannerAction.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AbstractAvailCompiler;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;

/**
 * An {@code AvailScanner} converts a stream of characters into a {@link List}
 * of {@linkplain TokenDescriptor tokens}, which are tastier for the {@linkplain
 * AbstractAvailCompiler compiler}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailScanner
{
	/**
	 * The string being parsed, usually the entire contents of an Avail source
	 * file.
	 */
	String inputString;

	/**
	 * The current position in the input string.
	 */
	private int position;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	private int startOfToken;

	/**
	 * The line number of the start of the token currently being parsed.
	 */
	int lineNumber;

	/**
	 * The tokens that have been parsed so far.
	 */
	List<A_Token> outputTokens;

	/**
	 * Whether to stop scanning after encountering the keyword "Body".
	 */
	boolean stopAfterBodyToken;

	/**
	 * Whether the BODY token has already been encountered. Only set if
	 * {@link #stopAfterBodyToken} is set.
	 */
	boolean encounteredBodyToken;

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
	 * Create an ordinary {@linkplain TokenDescriptor token}, initialize it, and
	 * add it to my sequence of parsed tokens. In particular, create the token
	 * and set its:
	 * <ul>
	 * <li>{@link com.avail.descriptor.TokenDescriptor.IntegerSlots#START
	 * start},</li>
	 * <li>{@link com.avail.descriptor.TokenDescriptor.ObjectSlots#STRING
	 * string}, and</li>
	 * <li>{@link
	 * com.avail.descriptor.TokenDescriptor.IntegerSlots#TOKEN_TYPE_CODE token
	 * type} based on the passed {@link TokenType}.</li>
	 * </ul>
	 *
	 * @param tokenType
	 *        The {@link TokenType enumeration value} to set in the token.
	 * @return The newly added token.
	 */
	@InnerAccess
	A_BasicObject addCurrentToken (
		final TokenDescriptor.TokenType tokenType)
	{
		final A_Token token = TokenDescriptor.create(
			StringDescriptor.from(currentTokenString()),
			startOfToken,
			lineNumber,
			tokenType);
		token.makeShared();
		outputTokens.add(token);
		return token;
	}

	/**
	 * Add the provided uninitialized {@linkplain LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param anAvailObject
	 *            A {@linkplain LiteralTokenDescriptor literal token}.
	 * @return The newly added token.
	 */
	@InnerAccess
	A_Token addCurrentLiteralToken (
		final A_BasicObject anAvailObject)
	{
		final A_Token token = LiteralTokenDescriptor.create(
			StringDescriptor.from(currentTokenString()),
			startOfToken,
			lineNumber,
			TokenType.LITERAL,
			anAvailObject);
		token.makeShared();
		outputTokens.add(token);
		return token;
	}

	/**
	 * Answer whether we have exhausted the input string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess
	boolean atEnd ()
	{
		return position == inputString.length();
	}

	/**
	 * Extract a native {@link String} from the input, from the
	 * {@link #startOfToken} to the current {@link #position}.
	 *
	 * @return A substring of the input.
	 */
	private String currentTokenString ()
	{
		return inputString.substring(startOfToken, position);
	}

	/**
	 * Extract the current character and increment the {@link #position}.
	 *
	 * @return The consumed character.
	 */
	@InnerAccess
	int next ()
	{
		if (atEnd())
		{
			throw new AvailScannerException(
				"Attempted to read past end of file",
				this);
		}
		final int c = Character.codePointAt(inputString, position);
		position += Character.charCount(c);
		if (c == '\n')
		{
			lineNumber++;
		}
		return c;
	}

	/**
	 * Extract the value of the next character, which must be a digit.
	 *
	 * @return The digit's value.
	 */
	@InnerAccess
	byte nextDigitValue ()
	{
		assert peekIsDigit();
		final int value = Character.digit(next(), 10);
		assert value >= 0 && value <= 9;
		return (byte) value;
	}

	/**
	 * Answer the character at the current {@link #position} of the input.
	 *
	 * @return The current character.
	 */
	@InnerAccess
	int peek ()
	{
		return Character.codePointAt(inputString, position);
	}

	/**
	 * Skip aCharacter if it's next in the input stream. Answer whether it was
	 * present.
	 *
	 * @param aCharacter
	 *            The character to look for, as an int to allow code points
	 *            beyond the Basic Multilingual Plane (U+0000 - U+FFFF).
	 * @return Whether the specified character was found and skipped.
	 */
	@InnerAccess
	boolean peekFor (final int aCharacter)
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
	 * Skip the next character if it's alphanumeric. Answer whether such a
	 * character was encountered.
	 *
	 * @return Whether an alphanumeric character was encountered and skipped.
	 */
	@InnerAccess
	boolean peekForLetterOrAlphaNumeric ()
	{
		if (!atEnd())
		{
			if (Character.isUnicodeIdentifierPart(peek()))
			{
				next();
				return true;
			}
		}
		return false;
	}

	/**
	 * Answer whether the current character is a digit.
	 *
	 * @return Whether the current character is a digit.
	 */
	@InnerAccess
	boolean peekIsDigit ()
	{
		if (!atEnd())
		{
			return Character.isDigit(peek());
		}
		return false;
	}

	/**
	 * Move the current {@link #position} back by one character.
	 */
	@InnerAccess
	void backUp ()
	{
		position--;
		assert 0 <= position && position <= inputString.length();
	}

	/**
	 * An enumeration of actions to be performed based on the next character
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	enum ScannerAction
	{
		/**
		 * A digit was encountered. Scan a (positive) numeric constant. The
		 * constant may be an integer, float, or double.  Note that if a decimal
		 * point is present there must be digits on each side of it.
		 */
		DIGIT ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.backUp();
				assert scanner.position() == scanner.startOfToken();
				boolean isReal = false;  // Might be an integer.
				while (scanner.peekIsDigit())
				{
					scanner.next();
				}
				if (scanner.peekFor('.'))
				{
					// Decimal point appeared, so fractional digits are
					// mandatory.  Otherwise expressions like [10..20] would be
					// horribly, horribly ugly.
					if (scanner.peekIsDigit())
					{
						isReal = true;
						while (scanner.peekIsDigit())
						{
							scanner.next();
						}
					}
					else
					{
						// Put that dot back down.  It's not a decimal point.
						scanner.backUp();
					}
				}
				final int beforeE = scanner.position();
				if (scanner.peekFor('e') || scanner.peekFor('E'))
				{
					if (scanner.peekFor('-') || scanner.peekFor('+'))
					{
						// Optional exponent sign.
					}
					if (scanner.peekIsDigit())
					{
						isReal = true;
						while (scanner.peekIsDigit())
						{
							scanner.next();
						}
					}
					else
					{
						scanner.position(beforeE);
					}
				}

				// Now convert the thing to numeric form.
				A_Number result;
				if (!isReal)
				{
					// It's a positive integer.
					scanner.position(scanner.startOfToken());
					result = IntegerDescriptor.zero();
					final A_Number ten = IntegerDescriptor.ten();
					while (scanner.peekIsDigit())
					{
						result = result.noFailTimesCanDestroy(ten, true);
						result = result.noFailPlusCanDestroy(
							IntegerDescriptor.fromUnsignedByte(
								scanner.nextDigitValue()),
							true);
					}
				}
				else
				{
					// It's a double.
					final StringBuilder builder = new StringBuilder();
					final int end = scanner.position();
					scanner.position(scanner.startOfToken());
					while (scanner.position() < end)
					{
						builder.appendCodePoint(scanner.next());
					}
					try
					{
						result = DoubleDescriptor.fromDouble(
							Double.valueOf(builder.toString()));
					}
					catch (final NumberFormatException e)
					{
						throw new AvailScannerException(
							"Malformed floating point constant ("
								+ builder.toString()
								+ "): "
								+ e.toString(),
							scanner);
					}
				}
				scanner.addCurrentLiteralToken(result);
			}
		},

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
			void scan (final AvailScanner scanner)
			{
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
							throw new AvailScannerException(
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
								// Treat \r or \r\n in the source as \n.
								if (!scanner.atEnd())
								{
									scanner.peekFor('\n');
								}
								//$FALL-THROUGH$
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
									throw new AvailScannerException(
										"The input line before \"\\|\" "
										+ "contains non-whitespace",
										scanner);
								}
								break;
							case '(':
								parseUnicodeEscapes(scanner, stringBuilder);
								break;
							case '[':
								throw new AvailScannerException(
									"Power strings are not yet supported",
									scanner);
								// TODO parsePowerString(scanner);
								// break;
							default:
								throw new AvailScannerException(
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
						if (!Character.isWhitespace(c))
						{
							canErase = false;
						}
					}
					if (scanner.atEnd())
					{
						throw new AvailScannerException(
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
				final AvailScanner scanner,
				final StringBuilder stringBuilder)
			{
				int c;
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
							throw new AvailScannerException(
								"Expected an upper case hex  digit or comma or"
								+ " closing parenthesis",
								scanner);
						}
						if (digitCount > 6)
						{
							throw new AvailScannerException(
								"Expected at most six hex digits per"
								+ " comma-separated Unicode entry",
								scanner);
						}
						c = scanner.next();
					}
					if (digitCount == 0)
					{
						throw new AvailScannerException(
							"Expected a comma-separated list of Unicode code"
							+ " points, each being one to six (upper case)"
							+ " hexadecimal digits",
							scanner);
					}
					assert digitCount >= 1 && digitCount <= 6;
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw new AvailScannerException(
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
		 * An alphabetic was encountered. Scan a keyword.
		 */
		IDENTIFIER_START ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				while (scanner.peekForLetterOrAlphaNumeric())
				{
					// no body
				}
				final A_BasicObject token =
					scanner.addCurrentToken(TokenType.KEYWORD);
				if (scanner.stopAfterBodyToken
					&& token.string().equals(
						AbstractAvailCompiler.ExpectedToken.BODY.lexeme()))
				{
					scanner.encounteredBodyToken = true;
				}
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
			void scan (final AvailScanner scanner)
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
							throw new AvailScannerException(
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
			void scan (final AvailScanner scanner)
			{
				throw new AvailScannerException("Unknown character", scanner);
			}
		},

		/**
		 * A whitespace character was encountered. Just skip it.
		 */
		WHITESPACE ()
		{
			@Override
			void scan (final AvailScanner scanner)
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
			void scan (final AvailScanner scanner)
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
			void scan (final AvailScanner scanner)
			{
				scanner.addCurrentToken(TokenType.OPERATOR);
			}
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *            The scanner processing this character.
		 */
		abstract void scan (AvailScanner scanner);

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
	 * that comprise a {@linkplain ModuleDescriptor module}.
	 *
	 * @param string
	 *            The text of an Avail {@linkplain ModuleDescriptor module} (or
	 *            at least the prefix up to the <em>Names</em> token).
	 * @param stopAfterBodyTokenFlag
	 *            Stop scanning after encountering the <em>Names</em> token?
	 * @return A {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 *         terminated by a token of type {@link TokenType#END_OF_FILE}.
	 */
	public static List<A_Token> scanString (
		final String string,
		final boolean stopAfterBodyTokenFlag)
	{
		final AvailScanner scanner =
			new AvailScanner(string, stopAfterBodyTokenFlag);
		return scanner.outputTokens;
	}

	/**
	 * Construct a new {@link AvailScanner}.
	 *
	 * @param string The {@link String} to parse.
	 * @param stopAfterBodyTokenFlag Whether to skip parsing the body.
	 */
	private AvailScanner (
		final String string,
		final boolean stopAfterBodyTokenFlag)
	{
		inputString = string;
		position = 0;
		lineNumber = 1;
		outputTokens = new ArrayList<A_Token>(100);
		stopAfterBodyToken = stopAfterBodyTokenFlag;
		while (!(stopAfterBodyToken ? encounteredBodyToken : atEnd()))
		{
			startOfToken = position;
			final int c = next();
			ScannerAction.forCodePoint(c).scan(this);
		}
		startOfToken = position;
		addCurrentToken(TokenType.END_OF_FILE);
	}

	/**
	 * Answer whether the passed character can appear as an Avail operator.
	 *
	 * @param c
	 *            The character to test.
	 * @return Whether it's a valid operator character.
	 */
	public static boolean isOperatorCharacter (final char c)
	{
		return dispatchTable[c] == (byte) OPERATOR.ordinal();
	}

	/**
	 * A table whose indices are Unicode code points (up to 65535) and whose
	 * values are {@link AvailScanner.ScannerAction scanner actions}.
	 */
	static byte[] dispatchTable = new byte[65536];

	/**
	 * Statically initialize the {@link dispatchTable} with suitable
	 * {@link AvailScanner.ScannerAction scanner actions}. Note that this
	 * happens as part of class loading.
	 */
	static
	{
		for (int i = 0; i < 65536; i++)
		{
			final char c = (char) i;
			AvailScanner.ScannerAction action;
			if (Character.isDigit(c))
			{
				action = DIGIT;
			}
			else if (Character.isUnicodeIdentifierStart(c))
			{
				action = IDENTIFIER_START;
			}
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				action = WHITESPACE;
			}
			else if (c < 32 || (c > 126 && c < 160) || !Character.isDefined(c))
			{
				action = UNKNOWN;
			}
			else
			{
				action = OPERATOR;
			}
			dispatchTable[i] = (byte) action.ordinal();
		}
		dispatchTable['_'] = (byte) IDENTIFIER_START.ordinal();
		dispatchTable['"'] = (byte) DOUBLE_QUOTE.ordinal();
		dispatchTable['/'] = (byte) SLASH.ordinal();
		dispatchTable['\uFEFF'] = (byte) ZEROWIDTHWHITESPACE.ordinal();
	}
}
