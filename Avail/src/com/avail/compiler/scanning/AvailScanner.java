/**
 * compiler/scanner/AvailScanner.java Copyright (c) 2010, Mark van Gulik. All
 * rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.descriptor.*;

/**
 * An {@code AvailScanner} converts a stream of characters into a {@link List}
 * of {@link TokenDescriptor tokens}, which are tastier for the compiler.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
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
	 * The position of the start of the token currently being parsed.
	 */
	int lineNumber;

	/**
	 * The tokens that have been parsed so far.
	 */
	List<AvailObject> outputTokens;

	/**
	 * Whether to stop scanning after encountering the keyword "Names".
	 */
	boolean stopAfterNamesToken;

	/**
	 * Whether the "Names" token has already been encountered. Only set if
	 * {@link #stopAfterNamesToken} is set.
	 */
	boolean encounteredNamesToken;

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
	 * Set the index to the character that is the start of the current token.
	 *
	 * @param newStartOfToken
	 *            The index of the character that starts the current token.
	 */
	void startOfToken (final int newStartOfToken)
	{
		this.startOfToken = newStartOfToken;
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
	 * Add the (uninitialized) token to my sequence of parsed tokens. Also set
	 * its:
	 * <ul>
	 * <li>{@link TokenDescriptor#o_Start(AvailObject, int) start},
	 * <li>{@link TokenDescriptor#o_String(AvailObject, AvailObject) string},
	 * and
	 * <li>{@link TokenDescriptor#o_TokenType(AvailObject, TokenType) token
	 * type} based on the passed {@link TokenDescriptor.TokenType}.
	 * </ul>
	 *
	 * @param anAvailToken
	 *            The token to initialize and add.
	 * @param tokenType
	 *            The {@link TokenDescriptor.TokenType enumeration value} to set
	 *            in the token.
	 */
	@InnerAccess
	void addToken (
		final AvailObject anAvailToken,
		final TokenDescriptor.TokenType tokenType)
	{
		anAvailToken.tokenType(tokenType);
		anAvailToken.start(startOfToken);
		anAvailToken.lineNumber(lineNumber);
		anAvailToken.string(ByteStringDescriptor.from(currentTokenString()));
		anAvailToken.makeImmutable();
		outputTokens.add(anAvailToken);
	}

	/**
	 * Create an ordinary {@link TokenDescriptor token}, initialize it, and add
	 * it to my sequence of parsed tokens. In particular, create the token and
	 * set its:
	 * <ul>
	 * <li>{@link TokenDescriptor#o_Start(AvailObject, int) start},
	 * <li>{@link TokenDescriptor#o_String(AvailObject, AvailObject) string},
	 * and
	 * <li>{@link TokenDescriptor#o_TokenType(AvailObject, TokenType) token
	 * type} based on the passed {@link TokenDescriptor.TokenType}.
	 * </ul>
	 *
	 * @param tokenType
	 *            The {@link TokenDescriptor.TokenType enumeration value} to set
	 *            in the token.
	 */
	@InnerAccess
	void addToken (final TokenDescriptor.TokenType tokenType)
	{
		final AvailObject token = TokenDescriptor.mutable().create();
		addToken(token, tokenType);
	}

	/**
	 * Add the provided uninitialized {@link LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param anAvailObject
	 *            A {@link LiteralTokenDescriptor literal token}.
	 */
	@InnerAccess
	void addTokenForLiteral (final AvailObject anAvailObject)
	{
		final AvailObject token = LiteralTokenDescriptor.mutable().create();
		token.literal(anAvailObject);
		addToken(token, TokenType.LITERAL);
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
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	enum ScannerAction
	{
		/**
		 * A digit was encountered. Scan a (positive) numeric constant. The
		 * constant may be an integer, float, or double.
		 */
		DIGIT()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.backUp();
				assert scanner.position() == scanner.startOfToken();
				while (scanner.peekIsDigit())
				{
					scanner.next();
				}
				long mantissa;
				boolean isDoublePrecision;
				int decimalExponentPart1;
				int decimalExponent;
				int decimalExponentPart2;
				AvailObject result;
				long twoTo59;
				scanner.position(scanner.startOfToken());
				if (scanner.peekFor('d') || scanner.peekFor('e')
					|| (scanner.peekFor('.') && scanner.peekIsDigit()))
				{
					mantissa = 0;
					decimalExponent = 0;
					twoTo59 = 0x800000000000000L;
					isDoublePrecision = false;
					while (scanner.peekIsDigit())
					{
						if (mantissa > twoTo59)
						{
							decimalExponent++;
						}
						else
						{
							mantissa = mantissa * 10 + scanner.nextDigitValue();
						}
					}
					if (scanner.peekFor('.'))
					{
						scanner.next(); // TODO Is this right?
						while (scanner.peekIsDigit())
						{
							if (mantissa <= twoTo59)
							{
								mantissa = mantissa * 10
									+ scanner.nextDigitValue();
								decimalExponent--;
							}
							else
							{
								scanner.next();
							}
						}
					}
					isDoublePrecision = scanner.peekFor('d');
					if (isDoublePrecision || scanner.peek() == 'e')
					{
						int explicitExponent = 0;
						final boolean negateExponent = scanner.peekFor('-');
						while (scanner.peekIsDigit())
						{
							explicitExponent = explicitExponent * 10
								+ scanner.nextDigitValue();
							if (explicitExponent > 0x2710)
							{
								throw new AvailScannerException(
									"Literal exponent is (way) out of bounds",
									scanner);
							}
						}
						decimalExponent = negateExponent ? decimalExponent
							- explicitExponent : decimalExponent
							+ explicitExponent;
					}
					// The mantissa is in the range of a long, so even if 10**e
					// would overflow we know that 10**(e/2) will not (unless
					// the double we're parsing is way out of bounds.
					decimalExponentPart1 = decimalExponent / 2;
					decimalExponentPart2 = decimalExponent
						- decimalExponentPart1;
					double d = mantissa;
					d *= Math.pow(10, decimalExponentPart1);
					d *= Math.pow(10, decimalExponentPart2);
					result = isDoublePrecision ? DoubleDescriptor
						.objectFromDouble(d) : FloatDescriptor
						.objectFromFloat((float) d);
				}
				else
				{
					result = IntegerDescriptor.zero();
					final AvailObject ten = IntegerDescriptor.ten();
					while (scanner.peekIsDigit())
					{
						result = result.noFailTimesCanDestroy(ten, true);
						result = result.noFailPlusCanDestroy(IntegerDescriptor
							.fromUnsignedByte(scanner.nextDigitValue()), true);
					}
				}
				scanner.addTokenForLiteral(result);
			}
		},

		/**
		 * Parse double-quoted string literal. This is an open-quote, followed
		 * by zero or more items, and a close-quote. An item is either a single
		 * character that is neither backslash (\) nor quote ("), or a backslash
		 * followed by: 1) n, r, or t indicating newline (U+000A), return
		 * (U+000D) or tab {U+0009), respectively. 2) a backslash (\) indicating
		 * a single literal backslash character. 3) a quote (") indicating a
		 * single literal quote character. 4) open parenthesis "(", one or more
		 * upper case hexadecimal sequences, one to six digits, separated by
		 * commas, and a close parenthesis. 5) open bracket "[", an expression
		 * yielding a string, and "]".
		 */
		DOUBLE_QUOTE()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				int c = scanner.next();
				final StringBuilder stringBuilder = new StringBuilder(40);
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
							case '(':
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
										else
										{
											throw new AvailScannerException(
												"Expected an upper case hex"
												+ " digit or comma or closing"
												+ " parenthesis",
												scanner);
										}
										if (digitCount > 6)
										{
											throw new AvailScannerException(
												"Expected at most six hex"
												+ " digits per comma-separated"
												+ " Unicode entry",
												scanner);
										}
										c = scanner.next();
									}
									if (digitCount == 0)
									{
										throw new AvailScannerException(
											"Expected a comma-separated list"
											+ " of Unicode code points, each"
											+ " being one to six (upper case)"
											+ " hexadecimal digits",
											scanner);
									}
									assert digitCount >= 1 && digitCount <= 6;
									if (value
										> CharacterDescriptor.maxCodePointInt)
									{
										throw new AvailScannerException(
											"The maximum allowed code point for"
											+ "a Unicode character is U+10FFFF",
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
								break;
							case '[':
								// TODO: Support power strings
								throw new AvailScannerException(
									"Power strings are not yet supported",
									scanner);
							default:
								throw new AvailScannerException(
									"Invalid escape sequence – must be one of:"
									+ "\\n \\r \\t \\\\ \\\" \\( \\[",
									scanner);
						}
					}
					else
					{
						stringBuilder.appendCodePoint(c);
					}
					c = scanner.next();
				}
				final String string = stringBuilder.toString();
				final AvailObject availValue = ByteStringDescriptor
					.from(string);
				availValue.makeImmutable();
				scanner.addTokenForLiteral(availValue);
			}
		},

		/**
		 * An alphabetic was encountered. Scan a keyword.
		 */
		IDENTIFIER_START()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				while (scanner.peekForLetterOrAlphaNumeric())
				{
					// no body
				}
				final AvailObject token = TokenDescriptor.mutable().create();
				scanner.addToken(token, TokenType.KEYWORD);
				if (scanner.stopAfterNamesToken
					&& token.string().asNativeString().equals("Names"))
				{
					scanner.encounteredNamesToken = true;
				}
			}
		},

		/**
		 * The semicolon is not considered an operator character and cannot be
		 * used within operators. Parse it by itself as a
		 * {@link TokenDescriptor token} whose type is
		 * {@link TokenType#END_OF_STATEMENT}.
		 */
		SEMICOLON()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.addToken(TokenType.END_OF_STATEMENT);
			}
		},

		/**
		 * A slash was encountered. Check if it's the start of a comment, and if
		 * so skip it. If not, add the slash as a {@link TokenDescriptor token}
		 * of type {@link TokenType#OPERATOR}.
		 *
		 * <p>
		 * Support nested comments.
		 * </p>
		 */
		SLASH()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				if (!scanner.peekFor('*'))
				{
					scanner.addToken(TokenType.OPERATOR);
				}
				else
				{
					int depth = 1;
					while (true)
					{
						if (scanner.atEnd())
						{
							throw new AvailScannerException(
								"Expected a close comment (*/) to correspond"
									+ " with the open comment (/*)",
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
		UNKNOWN()
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
		WHITESPACE()
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
		ZEROWIDTHWHITESPACE()
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
		OPERATOR()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.addToken(TokenType.OPERATOR);
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
		 * @return The ScannerAction to invoke when that code point is
		 *         encountered.
		 */
		public static ScannerAction forCodePoint (final int c)
		{
			if (c <= 65536)
			{
				return values()[DispatchTable[c]];
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
	 * @param stopAfterNamesTokenFlag
	 *            Stop scanning after encountering the <em>Names</em> token?
	 * @return A {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 *         terminated by a token of type {@link TokenType#END_OF_FILE}.
	 */
	public @NotNull
	List<AvailObject> scanString (
		final @NotNull String string,
		final boolean stopAfterNamesTokenFlag)
	{
		inputString = string;
		position = 0;
		lineNumber = 1;
		outputTokens = new ArrayList<AvailObject>(100);
		stopAfterNamesToken = stopAfterNamesTokenFlag;
		while (!(stopAfterNamesToken ? encounteredNamesToken : atEnd()))
		{
			startOfToken = position;
			final int c = next();
			ScannerAction.forCodePoint(c).scan(this);
		}
		addToken(TokenType.END_OF_FILE);
		return outputTokens;
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
		return DispatchTable[c] == (byte) OPERATOR.ordinal();
	}

	/**
	 * A table whose indices are Unicode characters (up to 65535) and whose
	 * values are {@link AvailScanner.ScannerAction scanner actions}.
	 */
	static byte[] DispatchTable = new byte[65536];

	/**
	 * Statically initialize the {@link DispatchTable} with suitable
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
			DispatchTable[i] = (byte) action.ordinal();
		}
		DispatchTable['"'] = (byte) DOUBLE_QUOTE.ordinal();
		DispatchTable[';'] = (byte) SEMICOLON.ordinal();
		DispatchTable['/'] = (byte) SLASH.ordinal();
		DispatchTable['\uFEFF'] = (byte) ZEROWIDTHWHITESPACE.ordinal();
	}
}
