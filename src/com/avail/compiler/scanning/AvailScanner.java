/**
 * compiler/scanner/AvailScanner.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.compiler.scanning;

import static com.avail.compiler.scanning.AvailScanner.ScannerAction.*;
import java.util.*;

import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.ExpectedToken;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.utility.LRUCache;
import org.jetbrains.annotations.Nullable;

/**
 * An {@code AvailScanner} converts a stream of characters into a {@link List}
 * of {@linkplain TokenDescriptor tokens}, which are tastier for the {@linkplain
 * AvailCompiler compiler}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailScanner
{
	/**
	 * The string being parsed, usually the entire contents of an Avail source
	 * file.
	 */
	private final String inputString;

	/**
	 * The name of the module being lexically scanned.
	 */
	private final String moduleName;

	/**
	 * Whether to stop scanning after encountering the keyword "Body".
	 */
	@InnerAccess final boolean stopAfterBodyToken;

	/**
	 * The tokens that have been parsed so far.
	 */
	private final List<A_Token> outputTokens;

	/**
	 * The comment tokens that have been parsed so far.
	 */
	private final List<A_Token> commentTokens;

	/**
	 * The zero-based position in the input string.  Must be converted to a
	 * one-based index when creating tokens.
	 */
	private int position;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	private int startOfToken;

	/**
	 * The line number of the start of the token currently being parsed.
	 */
	@InnerAccess int lineNumber;

	/**
	 * Whether the BODY token has already been encountered. Only set if
	 * {@link #stopAfterBodyToken} is set.
	 */
	@InnerAccess boolean encounteredBodyToken;

	/** The previously scanned whitespace. */
	private A_String previousWhitespace = TupleDescriptor.empty();

	/** The previously added {@linkplain A_Token token}. */
	private @Nullable A_Token previousToken;

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
	String moduleName ()
	{
		return moduleName;
	}

	/**
	 * Create an ordinary {@linkplain TokenDescriptor token}, initialize it, and
	 * add it to my sequence of parsed tokens. In particular, create the token
	 * and set its start position
	 * <ul>
	 * <li>{@link A_Token#start() start} position,</li>
	 * <li>{@link A_Token#string() string} content, and</li>
	 * <li>{@link A_Token#tokenType() token type} based on the passed {@link
	 * TokenType}.</li>
	 * </ul>
	 *
	 * @param tokenType
	 *        The {@link TokenType} enumeration value to set in the token.
	 * @return The newly added token.
	 */
	@InnerAccess A_Token addCurrentToken (
		final TokenType tokenType)
	{
		final A_Token token = TokenDescriptor.create(
			StringDescriptor.from(currentTokenString()),
			previousWhitespace,
			TupleDescriptor.empty(),
			startOfToken + 1,
			lineNumber,
			tokenType);
		token.makeShared();
		outputTokens.add(token);
		previousToken = token;
		forgetWhitespace();
		return token;
	}

	/**
	 * Add the provided uninitialized {@linkplain LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param anAvailObject
	 *        A {@linkplain LiteralTokenDescriptor literal token}.
	 */
	@InnerAccess
	void addCurrentLiteralToken (
		final A_BasicObject anAvailObject)
	{
		final A_Token token = LiteralTokenDescriptor.create(
			StringDescriptor.from(currentTokenString()),
			previousWhitespace,
			TupleDescriptor.empty(),
			startOfToken + 1,
			lineNumber,
			TokenType.LITERAL,
			anAvailObject);
		token.makeShared();
		outputTokens.add(token);
		previousToken = token;
		forgetWhitespace();
	}

	/**
	 * Add the provided {@linkplain CommentTokenDescriptor comment
	 * token}.
	 *
	 * @param startLine The line the token started.
	 */
	@InnerAccess
	void addCurrentCommentToken (final int startLine)
	{
		final A_Token token = CommentTokenDescriptor.create(
			StringDescriptor.from(currentTokenString()),
			previousWhitespace,
			TupleDescriptor.empty(),
			startOfToken + 1,
			startLine);
		token.makeShared();
		commentTokens.add(token);
		previousToken = null;
		forgetWhitespace();
	}

	/**
	 * A {@link List} of the positions of {@linkplain BasicCommentPosition
	 * basic comments}.
	 */
	private final List<BasicCommentPosition> basicCommentPositions =
		new ArrayList<>();

	/**
	 * Add a {@link BasicCommentPosition} to the {@linkplain
	 * #basicCommentPositions basic comments list}.
	 */
	void logBasicCommentPosition ()
	{
		basicCommentPositions.add(new BasicCommentPosition(
			startOfToken, currentTokenString().length()));
	}

	/**
	 * Answer whether we have exhausted the input string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess boolean atEnd ()
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
	 * @throws AvailScannerException If scanning fails.
	 */
	@InnerAccess int next () throws AvailScannerException
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
	 * @throws AvailScannerException If scanning fails.
	 */
	@InnerAccess byte nextDigitValue () throws AvailScannerException
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
	@InnerAccess int peek ()
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
	 * @throws AvailScannerException If scanning fails.
	 */
	@InnerAccess boolean peekFor (final int aCharacter)
		throws AvailScannerException
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
	 * @throws AvailScannerException If scanning fails.
	 */
	@InnerAccess boolean peekForLetterOrAlphaNumeric ()
		throws AvailScannerException
	{
		if (!atEnd() && Character.isUnicodeIdentifierPart(peek()))
		{
			next();
			return true;
		}
		return false;
	}

	/**
	 * Answer whether the current character is a digit.
	 *
	 * @return Whether the current character is a digit.
	 */
	@InnerAccess boolean peekIsDigit ()
	{
		if (!atEnd())
		{
			return Character.isDigit(peek());
		}
		return false;
	}

	/**
	 * Move the current {@link #position} back by one character.  The previous
	 * character must not be part of a surrogate pair.
	 */
	@InnerAccess void backUp ()
	{
		position--;
		assert 0 <= position && position <= inputString.length();
		assert !Character.isSurrogate(inputString.charAt(position));
	}

	/**
	 * A global {@linkplain LRUCache cache} of common whitespace strings,
	 * already shared.
	 */
	private static final LRUCache<String, A_String> whitespaceCache =
		new LRUCache<>(
			100,
			0,
			whitespace ->
			{
				assert whitespace != null;
				return StringDescriptor.from(whitespace).makeShared();
			});

	/**
	 * Note that whitespace was discovered.
	 */
	@InnerAccess void noteWhitespace ()
	{
		assert previousWhitespace.tupleSize() == 0;
		previousWhitespace = whitespaceCache.getNotNull(
			inputString.substring(startOfToken, position));
		final A_Token previous = previousToken;
		if (previous != null)
		{
			assert previous.tokenType() != TokenType.COMMENT;
			previous.trailingWhitespace(previousWhitespace);
			previousToken = null;
		}
	}

	/**
	 * Forget any saved whitespace.
	 */
	@InnerAccess void forgetWhitespace ()
	{
		previousWhitespace = TupleDescriptor.empty();
	}

	/**
	 * An enumeration of actions to be performed based on the next character
	 * encountered.
	 */
	enum ScannerAction
	{
		/**
		 * A digit was encountered. Scan a (positive) numeric constant. The
		 * constant must be an integer.
		 */
		DIGIT ()
		{
			@Override
			void scan (final AvailScanner scanner)
				throws AvailScannerException
			{
				scanner.backUp();
				assert scanner.position() == scanner.startOfToken();
				while (scanner.peekIsDigit())
				{
					scanner.next();
				}

				// Now convert the thing to numeric form.
				scanner.position(scanner.startOfToken());
				A_Number result = IntegerDescriptor.zero();
				final A_Number ten = IntegerDescriptor.ten();
				while (scanner.peekIsDigit())
				{
					result = result.noFailTimesCanDestroy(ten, true);
					result = result.noFailPlusCanDestroy(
						IntegerDescriptor.fromUnsignedByte(
							scanner.nextDigitValue()),
						true);
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
				throws AvailScannerException
			{
				final int literalStartingLine = scanner.lineNumber;
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw new AvailScannerException(
						"Unterminated string literal",
						scanner);
				}
				int c = scanner.next();
				final StringBuilder stringBuilder = new StringBuilder(40);
				boolean canErase = true;
				int erasurePosition = 0;
				while (c != '\"')
				{
					switch (c)
					{
						case '\\':
						{
							if (scanner.atEnd())
							{
								throw new AvailScannerException(
									"Encountered end of file after backslash"
										+ " in string literal",
									scanner);
							}
							c = scanner.next();
							switch (c)
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
									scanner.lineNumber = literalStartingLine;
									throw new AvailScannerException(
										"Power strings are not yet supported",
										scanner);
									// TODO parsePowerString(scanner);
									// break;
								default:
									throw new AvailScannerException(
										"Backslash escape should be followed by"
											+ " one of n, r, t, \\, \", (, [, |, or a"
											+ " line break, not Unicode character "
											+ c,
										scanner);
							}
							erasurePosition = stringBuilder.length();
							break;
						}
						case '\r':
						{
							// Transform \r or \r\n in the source into \n.
							if (!scanner.atEnd())
							{
								scanner.peekFor('\n');
							}
							stringBuilder.appendCodePoint('\n');
							canErase = true;
							erasurePosition = stringBuilder.length();
							break;
						}
						case '\n':
						{
							// Just like a regular character, but limit how much
							// can be removed by a subsequent '\|'.
							stringBuilder.appendCodePoint('\n');
							canErase = true;
							erasurePosition = stringBuilder.length();
							break;
						}
						default:
						{
							stringBuilder.appendCodePoint(c);
							if (canErase && !Character.isWhitespace(c))
							{
								canErase = false;
							}
							break;
						}
					}
					if (scanner.atEnd())
					{
						// Indicate where the quoted string started, to make it
						// easier to figure out where the end-quote is missing.
						scanner.lineNumber = literalStartingLine;
						throw new AvailScannerException(
							"Unterminated string literal",
							scanner);
					}
					c = scanner.next();
				}
				final String string = stringBuilder.toString();
				final A_String availValue = StringDescriptor.from(string);
				availValue.makeImmutable();
				final int lineAfterToken = scanner.lineNumber;
				scanner.lineNumber = literalStartingLine;
				scanner.addCurrentLiteralToken(availValue);
				scanner.lineNumber = lineAfterToken;
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
				throws AvailScannerException
			{
				if (scanner.atEnd())
				{
					throw new AvailScannerException(
						"Expected hexadecimal Unicode codepoints separated by"
						+ " commas",
						scanner);
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
							throw new AvailScannerException(
								"Expected a hex digit or comma or closing"
								+ " parenthesis",
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
					// assert digitCount >= 1 && digitCount <= 6;
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw new AvailScannerException(
							"The maximum allowed code point for a Unicode"
							+ " character is U+10FFFF",
							scanner);
					}
					stringBuilder.appendCodePoint(value);
					if (c == ',')
					{
						c = scanner.next();
					}
				}
			}
		},

		/**
		 * An alphabetic was encountered. Scan a keyword.
		 */
		IDENTIFIER_START ()
		{
			@Override
			void scan (final AvailScanner scanner)
				throws AvailScannerException
			{
				//noinspection StatementWithEmptyBody
				while (scanner.peekForLetterOrAlphaNumeric())
				{
					// no body
				}
				final A_Token token =
					scanner.addCurrentToken(TokenType.KEYWORD);
				if (scanner.stopAfterBodyToken
					&& token.string().equals(
						ExpectedToken.BODY.lexeme()))
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
				throws AvailScannerException
			{
				if (!scanner.peekFor('*'))
				{
					scanner.addCurrentToken(TokenType.OPERATOR);
				}
				else
				{
					final boolean capture = scanner.peekFor('*') && (scanner.peek() != '*');
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
							if (capture)
							{
								scanner.addCurrentCommentToken(startLine);
							}
							else
							{
								scanner.logBasicCommentPosition();
								scanner.forgetWhitespace();
							}
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
				throws AvailScannerException
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
				throws AvailScannerException
			{
				while (true)
				{
					if (scanner.atEnd())
					{
						break;
					}
					final int c = scanner.peek();
					if (!Character.isWhitespace(c)
						&& !Character.isSpaceChar(c)
						&& c != '\uFEFF')
					{
						break;
					}
					scanner.next();
				}
				scanner.noteWhitespace();
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
		 * ScannerAction has an implicitly defined (due to being an Enum)
		 * {@code values()} method, but it makes a copy of the array for safety
		 * because immutable arrays aren't possible in Java.  Copy it once here
		 * for speed.
		 */
		static final ScannerAction[] all = values();

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *            The scanner processing this character.
		 * @throws AvailScannerException If scanning fails.
		 */
		abstract void scan (AvailScanner scanner)
			throws AvailScannerException;

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
				return all[dispatchTable[c]];
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
	 * @param moduleName
	 *            The name of the module being lexically scanned.
	 * @param stopAfterBodyTokenFlag
	 *            Stop scanning after encountering the <em>Names</em> token?
	 * @return A {@linkplain AvailScannerResult result}.
	 * @throws AvailScannerException
	 *         If scanning fails.
	 */
	public static AvailScannerResult scanString (
			final String string,
			final String moduleName,
			final boolean stopAfterBodyTokenFlag)
		throws AvailScannerException
	{
		final AvailScanner scanner =
			new AvailScanner(string, moduleName, stopAfterBodyTokenFlag);
		scanner.scan();
		return new AvailScannerResult(
			string,
			scanner.outputTokens,
			scanner.commentTokens,
			scanner.basicCommentPositions);
	}

	/**
	 * Construct a new {@link AvailScanner}.
	 *
	 * @param inputString
	 *            The {@link String} containing Avail source code to lexically scan.
	 * @param moduleName
	 *            The name of the module being scanned.
	 * @param stopAfterBodyToken
	 *            Whether to skip parsing the body.
	 */
	private AvailScanner (
		final String inputString,
		final String moduleName,
		final boolean stopAfterBodyToken)
	{
		this.inputString = inputString;
		this.moduleName = moduleName;
		this.stopAfterBodyToken = stopAfterBodyToken;
		this.outputTokens = new ArrayList<>(inputString.length() / 20);
		this.commentTokens = new ArrayList<>(10);
	}

	/**
	 * Scan the already-specified {@link String} to produce {@linkplain
	 * #outputTokens tokens}.
	 *
	 * @throws AvailScannerException If there is a problem scanning.
	 */
	private void scan ()
		throws AvailScannerException
	{
		position = 0;
		lineNumber = 1;
		if (stopAfterBodyToken)
		{
			while (!encounteredBodyToken && !atEnd())
			{
				startOfToken = position;
				forCodePoint(next()).scan(this);
			}
		}
		else
		{
			while (!atEnd())
			{
				startOfToken = position;
				forCodePoint(next()).scan(this);
			}
		}
		startOfToken = position;
		addCurrentToken(TokenType.END_OF_FILE);
	}

	/**
	 * A table whose indices are Unicode code points (up to 65535) and whose
	 * values are {@link ScannerAction scanner actions}.
	 */
	static final byte[] dispatchTable = new byte[65536];

	/**
	 * Statically initialize the {@link dispatchTable} with suitable
	 * {@link ScannerAction scanner actions}. Note that this
	 * happens as part of class loading.
	 */
	static
	{
		for (int i = 0; i < 65536; i++)
		{
			final char c = (char) i;
			final ScannerAction action;
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
			else if (c < 32 || (c < 160 && c > 126) || !Character.isDefined(c))
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
		// This is also known as the byte-order-mark and generally only appears
		// at the start of a file as a hint about the file's endianness. Treat
		// it as whitespace even though Unicode says it isn't.
		dispatchTable['\uFEFF'] = (byte) WHITESPACE.ordinal();
	}

	/**
	 * A {@code BasicCommentPosition} is a holder for the position and length
	 * of an untokenized comment in the source module.
	 */
	public static class BasicCommentPosition
	{
		/**
		 * The start position of this comment in the source module.
		 */
		final int start;

		/**
		 * Answer the start position of this comment in the source module.
		 *
		 * @return An int.
		 */
		public int start ()
		{
			return start;
		}

		/**
		 * The length of the comment.
		 */
		final int length;

		/**
		 * Answer the length of the comment.
		 *
		 * @return An int.
		 */
		public int length ()
		{
			return length;
		}

		/**
		 * Construct a {@link BasicCommentPosition}.
		 *
		 * @param start
		 *        The start position of this comment in the source module.
		 * @param length
		 *        The length of the comment.
		 */
		BasicCommentPosition (
			final int start,
			final int length)
		{
			this.start = start;
			this.length = length;
		}
	}
}
