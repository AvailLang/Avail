/**
 * AbstractStacksScanner.java
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

package com.avail.stacks;

import java.util.ArrayList;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;

/**
 * The basics of a Stacks scanner.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class AbstractStacksScanner
{
	/**
	 * The {@link CommentTokenDescriptor comment token} text that has been
	 * lexed as one long token.
	 */
	private String tokenString;

	/**
	 * The name of the module being lexically scanned.
	 */
	String moduleName;

	/**
	 * The tokens that have been parsed so far.
	 */
	public ArrayList<AbstractStacksToken> outputTokens;

	/**
	 * The current position in the input string.
	 */
	private int position;

	/**
	 * The start position of the comment in the file.
	 */
	private int filePosition;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	private int startOfToken;

	/**
	 * The place on the currently line where the token starts.
	 * Always initialized to zero at creation of {@link StacksScanner scanner}.
	 */
	private int startOfTokenLinePostion;

	/**
	 * The line number of the start of the token currently being parsed.
	 */
	private int lineNumber;

	/**
	 * Alter the scanner's position in the input String.
	 *
	 * @param newLineNumber
	 *            The new lineNumber for this scanner as an index.
	 */
	protected void lineNumber (final int newLineNumber)
	{
		this.lineNumber = newLineNumber;
	}

	/**
	 * Answer the scanner's current lineNumber in the input String.
	 *
	 * @return The current line number in the input String.
	 */
	protected int lineNumber ()
	{
		return lineNumber;
	}

	/**
	 * Alter the scanner's startOfTokenLinePostion in the input String.
	 *
	 * @param newStartOfTokenLinePostion
	 *            The new position for this scanner as an index into the input
	 *            String's chars current line.
	 */
	protected void startOfTokenLinePostion (
		final int newStartOfTokenLinePostion)
	{
		this.startOfTokenLinePostion = newStartOfTokenLinePostion;
	}

	/**
	 * Answer the scanner's current index in the input String.
	 *
	 * @return The current character position on the line in the input String.
	 */
	protected int startOfTokenLinePostion ()
	{
		return startOfTokenLinePostion;
	}

	/**
	 * Alter the scanner's startOfToken in the input String.
	 *
	 * @param newStartOfToken
	 *            The new startOftoken for this scanner as an index into the
	 *            input String.
	 */
	protected void startOfToken (final int newStartOfToken)
	{
		this.startOfToken = newStartOfToken;
	}

	/**
	 * Alter the scanner's input String.
	 *
	 * @param aTokenString
	 * 		The tokenString to be lexed
	 */
	protected void tokenString (final String aTokenString)
	{
		this.tokenString = aTokenString;
	}

	/**
	 * Answer the scanner's token string.
	 *
	 * @return The string of the token being lexed.
	 */
	protected String tokenString ()
	{
		return tokenString;
	}

	/**
	 * Alter the scanner's position in the input String.
	 *
	 * @param newPosition
	 *            The new position for this scanner as an index into the input
	 *            String's chars (which may not correspond with the actual code
	 *            points in the Supplementary Planes).
	 */
	protected void position (final int newPosition)
	{
		this.position = newPosition;
	}

	/**
	 * Answer the scanner's current index in the input String.
	 *
	 * @return The current position in the input String.
	 */
	protected int position ()
	{
		return position;
	}

	/**
	 * Answer the index into the input String at which the current token starts.
	 *
	 * @return The location of the start of the current token in the input
	 * 		String.
	 */
	int startOfToken ()
	{
		return startOfToken;
	}

	/**
	 *
	 */
	StringBuilder beingTokenized;

	/**
	 * @return
	 */
	public StringBuilder beingTokenized ()
	{
		return beingTokenized;
	}

	/**
	 *
	 */
	public void resetBeingTokenized ()
	{
		beingTokenized = new StringBuilder();
	}

	/**
	 * Extract a native {@link String} from the input, from the
	 * {@link #startOfToken} to the current {@link #position}.
	 *
	 * @return A substring of the input.
	 */
	private String currentTokenString ()
	{
		final String alternateToken =  beingTokenized.toString();
		if (alternateToken.isEmpty())
		{
			return tokenString.substring(startOfToken, position);
		}
		return alternateToken;
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
	 * Alter the scanner's ultimate filePosition in the input module.
	 *
	 * @param newFilePosition
	 *            The new position for this scanner as an index into the input
	 *            String's chars (which may not correspond with the actual code
	 *            points in the Supplementary Planes).
	 */
	protected void filePosition (final int newFilePosition)
	{
		this.filePosition = newFilePosition;
	}

	/**
	 * Answer the scanner's filePosition in the module.
	 *
	 * @return The current filePosition in the module.
	 */
	protected int filePosition ()
	{
		return filePosition;
	}

	/**
	 * From the full directory path of a module name, obtain the leaf portion
	 * that represents the file name in the directory.
	 * @return the module leaf name
	 */
	public String obtainModuleSimpleName ()
	{
		final String modName = moduleName;
		final int modNameLength = modName.length();
		int i = modName.length() - 1;
		while (modName.charAt(i) != '\\' && modName.charAt(i) != '/' &&
			i > -1)
		{
			i--;
		}

		return modName.substring(i+1, modNameLength);
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
			position () + filePosition(),
			lineNumber(),
			startOfTokenLinePostion(),
			moduleName.toString());
		outputTokens.add(token);
		return token;
	}

	/**
	 * Add the provided uninitialized {@linkplain QuotedStacksToken quoted
	 * token}.
	 *
	 * @return The newly added token.
	 * @throws StacksScannerException
	 */
	@InnerAccess
	BracketedStacksToken addBracketedToken () throws StacksScannerException
	{
		final BracketedStacksToken token = BracketedStacksToken.create(
			currentTokenString(),
			lineNumber(),
			position () + filePosition(),
			startOfTokenLinePostion(),
			moduleName.toString());
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
			position () + filePosition(),
			lineNumber(),
			startOfTokenLinePostion(),
			moduleName.toString());
		outputTokens.add(token);
		return token;
	}

	/**
	 * Add the provided uninitialized {@linkplain KeywordStacksToken keyword
	 * token}.
	 *
	 * @return The newly added token.
	 */
	@InnerAccess
	AbstractStacksToken addKeywordToken ()
	{
		final AbstractStacksToken token = KeywordStacksToken.create(
			currentTokenString(),
			position () + filePosition(),
			lineNumber(),
			startOfTokenLinePostion(),
			moduleName.toString());
		outputTokens.add(token);
		return token;
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess
	abstract boolean atEnd ();


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

			throw new StacksScannerException(String.format("\n<li><strong>%s"
				+ "</strong><em>Line #: %d</em>: Scanner Error: Attempted to "
				+ "read past end of file.</li>",
				this.obtainModuleSimpleName(),
				this.lineNumber()),
				this);
		}
		final int c = Character.codePointAt(tokenString, position);
		position += Character.charCount(c);
		if (c == '\n')
		{
			lineNumber++;
			startOfTokenLinePostion = 0;
		}
		return c;
	}

	/**
	 * Answer the character at the current {@link #position} of the input.
	 *
	 * @return The current character.
	 */
	@InnerAccess
	int peek ()
	{
		return Character.codePointAt(tokenString, position);
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
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				final int literalStartingLine = scanner.lineNumber();
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw new StacksScannerException(String.format(
						"\n<li><strong>%s"
						+ "</strong><em>Line #: %d</em>: Scanner Error: "
						+ "Unterminated string literal.</li>",
						scanner.obtainModuleSimpleName(),
						scanner.lineNumber()),
						scanner);
				}
				int c = scanner.next();
				scanner.resetBeingTokenized();
				boolean canErase = true;
				int erasurePosition = 0;
				while (c != '\"')
				{
					if (c == '\\')
					{
						if (scanner.atEnd())
						{
							throw new StacksScannerException(
								String.format(
									"\n<li><strong>%s</strong><em>Line #: %d"
									+ "</em>: Scanner Error: Encountered end "
									+ "of file after backslash in string "
									+ "literal.\n",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber()),
								scanner);
						}
						switch (c = scanner.next())
						{
							case 'n':
								scanner.beingTokenized().append('\n');
								break;
							case 'r':
								scanner.beingTokenized().append('\r');
								break;
							case 't':
								scanner.beingTokenized().append('\t');
								break;
							case '\\':
								scanner.beingTokenized().append('\\');
								break;
							case '\"':
								scanner.beingTokenized().append('\"');
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
									scanner.beingTokenized().setLength(erasurePosition);
									canErase = false;
								}
								else
								{
									throw new StacksScannerException(
										String.format(
											"\n<li><strong>%s</strong><em> "
											+ "Line #: %d</em>: Scanner Error: "
											+ "The input before  \"\\|\" "
											+ "contains non-whitespace"
											+ "/not-'*'.</li>",
											scanner.obtainModuleSimpleName(),
											scanner.lineNumber()),
										scanner);
								}
								break;
							case '(':
								parseUnicodeEscapes(scanner,
									scanner.beingTokenized());
								break;
							case '[':
								scanner.lineNumber(literalStartingLine);
								throw new StacksScannerException(
									"Power strings are not yet supported",
									scanner);
							default:
								throw new StacksScannerException(
									String.format(
									"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Backslash "
									+ "escape should be followed by "
									+ "one of n, r, t, \\, \", (, "
									+ "[, |, or a line break.</li>",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber()),
									scanner);
						}
						erasurePosition = scanner.beingTokenized().length();
					}
					else if (c == '\r')
					{
						// Transform \r or \r\n in the source into \n.
						if (!scanner.atEnd())
						{
							scanner.peekFor('\n');
						}
						scanner.beingTokenized().appendCodePoint('\n');
						canErase = true;
						erasurePosition = scanner.beingTokenized().length();
					}
					else if (c == '\n')
					{
						// Just like a regular character, but limit how much
						// can be removed by a subsequent '\|'.
						scanner.beingTokenized().appendCodePoint(c);
						canErase = true;
						erasurePosition = scanner.beingTokenized().length();
					}
					else if (c == '*')
					{
						// Just like a regular character, but limit how much
						// can be removed by a subsequent '\|'.
						if (!canErase)
						{
							scanner.beingTokenized().appendCodePoint(c);
							erasurePosition = scanner.beingTokenized().length();
						}
					}
					else
					{
						scanner.beingTokenized().appendCodePoint(c);
						if (canErase && !Character.isWhitespace(c))
						{
							canErase = false;
						}
					}
					if (scanner.atEnd())
					{
						// Indicate where the quoted string started, to make it
						// easier to figure out where the end-quote is missing.
						scanner.lineNumber(literalStartingLine);
						throw new StacksScannerException(String.format(
							"\n<li><strong>%s"
							+ "</strong><em>Line #: %d</em>: Scanner Error: "
							+ "Unterminated string literal.</li>",
								scanner.obtainModuleSimpleName(),
								scanner.lineNumber()),
							scanner);
					}
					c = scanner.next();
				}
				scanner.addQuotedToken();
				scanner.resetBeingTokenized();
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
					final AbstractStacksScanner scanner,
					final StringBuilder stringBuilder)
				throws StacksScannerException
			{
				int c;
				if (scanner.atEnd())
				{
					throw new StacksScannerException(String.format(
						"\n<li><strong>%s</strong><em> Line #: "
						+ "%d</em>: Scanner Error: Expected hexadecimal "
						+ "Unicode codepoints separated by commas</li>",
						scanner.obtainModuleSimpleName(),
						scanner.lineNumber()),
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
							throw new StacksScannerException(String.format(
								"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Expected a "
									+ "hex digit or comma or closing "
									+ "parenthesis</li>",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber()),
								scanner);
						}
						if (digitCount > 6)
						{
							throw new StacksScannerException(String.format(
									"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Expected at "
									+ "most six hex digits per comma-separated "
									+ "Unicode entry</li>",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber()),
								scanner);
						}
						c = scanner.next();
					}
					if (digitCount == 0)
					{
						throw new StacksScannerException(String.format(
							"\n<li><strong>%s</strong><em> Line #: "
							+ "%d</em>: Scanner Error: Expected a "
							+ "comma-separated list of Unicode code points, "
							+ "each being one to six (upper case) hexadecimal "
							+ "digits</li>",
							scanner.obtainModuleSimpleName(),
							scanner.lineNumber()),
						scanner);
					}
					assert digitCount >= 1 && digitCount <= 6;
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw new StacksScannerException(
							String.format(
								"\n<li><strong>%s</strong><em> Line #: "
								+ "%d</em>: Scanner Error: The maximum "
								+ "allowed code point for a Unicode character "
								+ "is U+10FFFF</li>",
								scanner.obtainModuleSimpleName(),
								scanner.lineNumber()),
							scanner);
					}
					scanner.beingTokenized().appendCodePoint(value);
					if (c == ',')
					{
						c = scanner.next();
					}
				}
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a {@link
		 * BracketedStacksToken}
		 */
		BRACKET ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				final int startOfBracket = scanner.position();
				final int startOfBracketLineNumber = scanner.lineNumber();
				final int startOfBracketTokenLinePostion =
					scanner.startOfTokenLinePostion();

				while (Character.isSpaceChar(scanner.peek())
					|| Character.isWhitespace(scanner.peek()))
				{
					scanner.next();
					if (scanner.atEnd())
					{
						scanner.position(startOfBracket);
						scanner.lineNumber(startOfBracketLineNumber);
						scanner.startOfTokenLinePostion(
							startOfBracketTokenLinePostion);
						scanner.addCurrentToken();
						return;
					}
				}

				if (!scanner.peekFor('@'))
				{
					if (scanner.position() != (startOfBracket + 1))
					{
						scanner.position(startOfBracket);
						scanner.addCurrentToken();
						return;
					}
					return;
				}
				while (!scanner.peekFor('}'))
				{
					scanner.next();
					if (scanner.atEnd())
					{
						scanner.position(startOfBracket);
						scanner.lineNumber(startOfBracketLineNumber);
						scanner.startOfTokenLinePostion(
							startOfBracketTokenLinePostion);
						return;
					}
				}

				scanner.addBracketedToken();
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a {@link
		 * KeywordStacksToken}
		 */
		KEYWORD_START ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next();
				}
				scanner.addKeywordToken();
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a {@link
		 * KeywordStacksToken}
		 */
		NEWLINE ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				while (Character.isSpaceChar(scanner.peek())
					|| Character.isWhitespace(scanner.peek()))
				{
					scanner.next();
				}
				if (scanner.peekFor('*'))
				{
					scanner.next();
				}
			}
		},

		/**
		 * A slash was encountered. Check if it's the start of a nested comment,
		 * and if so skip it. If not, add the slash as a {@linkplain
		 * StacksToken token}.
		 * <p>
		 * Nested comments are supported.
		 * </p>
		 */
		SLASH ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				if (!scanner.peekFor('*'))
				{
					while (Character.isSpaceChar(scanner.peek())
						|| Character.isWhitespace(scanner.peek()))
					{
						scanner.next();
					}
					scanner.addCurrentToken();
				}
				else
				{
					final int startLine = scanner.lineNumber();
					int depth = 1;
					while (true)
					{
						if (scanner.atEnd())
						{
							throw new StacksScannerException(String.format(
								"\n<li><strong>%s</strong><em> Line #: "
								+ "%d</em>: Scanner Error: Expected a close "
								+ "comment (*/) to correspond with the open "
								+ "comment (/*) on line #%d</li>",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber(),
									startLine),
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
		STANDARD_CHARACTER ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				scanner.resetBeingTokenized();
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek())
					&& !scanner.atEnd())
				{
					@SuppressWarnings("unused")
					final int c = scanner.next();
				}
				scanner.addCurrentToken();
			}
		},

		/**
		 * A whitespace character was encountered. Just skip it.
		 */
		WHITESPACE ()
		{
			@Override
			void scan (final AbstractStacksScanner scanner)
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
			void scan (final AbstractStacksScanner scanner)
				throws StacksScannerException
			{
				// do nothing
			}
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *            The scanner processing this character.
		 * @throws StacksScannerException If scanning fails.
		 */
		abstract void scan (AbstractStacksScanner scanner)
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
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				return WHITESPACE;
			}
			else
			{
				return STANDARD_CHARACTER;
			}
		}
	}

	/**
	 * A table whose indices are Unicode code points (up to 65535) and whose
	 * values are {@link StacksScanner.ScannerAction scanner actions}.
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
			AbstractStacksScanner.ScannerAction action;
			if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				action = ScannerAction.WHITESPACE;
			}
			else
			{
				action = ScannerAction.STANDARD_CHARACTER;
			}
			dispatchTable[i] = (byte) action.ordinal();
		}
		dispatchTable['\n'] = (byte) ScannerAction.NEWLINE.ordinal();
		dispatchTable['{'] = (byte) ScannerAction.BRACKET.ordinal();
		dispatchTable['"'] = (byte) ScannerAction.DOUBLE_QUOTE.ordinal();
		dispatchTable['/'] = (byte) ScannerAction.SLASH.ordinal();
		dispatchTable['\uFEFF'] =
			(byte) ScannerAction.ZEROWIDTHWHITESPACE.ordinal();
		dispatchTable['@'] = (byte) ScannerAction.KEYWORD_START.ordinal();
		}
}
