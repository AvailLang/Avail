/**
 * StacksScanner.java
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
import java.util.List;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;

/**
 * A scanner for Stacks comments.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksScanner extends AbstractStacksScanner
{
	/**
	 * Does the comment end with the standard asterisk-forward slash?
	 */
	private final boolean commentEndsStandardly;

	/**
	 * The number of newlines.
	 */
	private int newlineCount;

	/**
	 * Increment new line
	 */
	public void incrementNewlineCount()
	{
		newlineCount++;
	}

	/**
	 * decrement new line
	 */
	public void decrementNewLineCount()
	{
		newlineCount--;
	}

	/**
	 * @return the newlineCount
	 */
	public int newlineCount()
	{
		return newlineCount;
	}

	/**
	 * Set the newlineCount to 0;
	 */
	public void resetNewlineCount()
	{
		newlineCount = 0;
	}

	/**
	 * Does the current content start with an HTML tag?
	 */
	private boolean hasHTMLTag;

	/**
	 * @return the hasHTMLTag
	 */
	public boolean hasHTMLTag ()
	{
		return hasHTMLTag;
	}

	/**
	 * set the hasHTMLTag to true
	 */
	public void hasHTMLTagTrue ()
	{
		hasHTMLTag = true;
	}

	/**
	 * set the hasHTMLTag to false
	 */
	public void hasHTMLTagFalse()
	{
		hasHTMLTag = false;
	}

	/**
	 * Was a paragraph HTML tag added to the output tokens??
	 */
	private boolean addedParagraphHTMLTag;

	/**
	 * @return the addedParagraphHTMLTag
	 */
	public boolean addedParagraphHTMLTag ()
	{
		return addedParagraphHTMLTag;
	}

	/**
	 * set the hasHTMLTag to true
	 */
	public void addedParagraphHTMLTagTrue ()
	{
		addedParagraphHTMLTag = true;
	}

	/**
	 * set the hasHTMLTag to false
	 */
	public void addedParagraphHTMLTagFalse()
	{
		addedParagraphHTMLTag = false;
	}

	/**
	 * Does the comment end with the standard asterisk-forward slash?
	 */
	private final boolean commentStartsStandardly;

	/**
	 * The start line of the overall comment
	 */
	private final int commentStartLine;

	/**
	 * @return the commentStartLine
	 */
	public int commentStartLine ()
	{
		return commentStartLine;
	}

	/**
	 * The module file name without the path.
	 */
	private final String moduleLeafName;

	/**
	 * The index locations where a new {@link SectionKeywordStacksToken section}
	 * begins in in the
	 */
	final ArrayList<Integer> sectionStartLocations;

	/**
	 * Construct a new {@link StacksScanner}.
	 *
	 * @param commentToken
	 * 		the {@link CommentTokenDescriptor comment token} to be scanned and
	 * 		tokenized.
	 * @param moduleName
	 *		The name of the module the comment is in.
	 */
	public StacksScanner (final A_Token commentToken, final String moduleName)
	{
		resetNewlineCount();
		hasHTMLTagFalse();
		addedParagraphHTMLTagFalse();
		this.moduleLeafName =
			moduleName.substring(moduleName.lastIndexOf('/') + 1);

		final String commentString =
			commentToken.string().asNativeString();
		tokenString(commentString);
		this.commentStartLine = commentToken.lineNumber();
		this.moduleName = moduleName;
		this.outputTokens = new ArrayList<>(
			tokenString().length() / 20);
		this.commentEndsStandardly = tokenString().substring(
			tokenString().length()-2,
			tokenString().length()).equals("*/");
		this.commentStartsStandardly =
			tokenString().substring(0,3).equals("/**");
		this.lineNumber(commentToken.lineNumber());
		this.filePosition(commentToken.start());
		this.startOfTokenLinePostion(0);
		this.sectionStartLocations = new ArrayList<>(9);
		this.beingTokenized = new StringBuilder();
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess @Override
	boolean atEnd ()
	{
		if (commentEndsStandardly)
		{
			return position() == tokenString().length() - 2;
		}
		return position() == tokenString().length();
	}

	/**
	 * Scan the already-specified {@link String} to produce {@linkplain
	 * #outputTokens tokens}.
	 *
	 * @throws StacksScannerException
	 */
	public void scan () throws StacksScannerException
	{
		if (commentStartsStandardly)
		{
			position(3);
		}
		else
		{
			position(0);
		}
		while (!atEnd())
		{
			startOfToken(position());
			ScannerAction.forCodePoint(next()).scan(this);
		}
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
				final int literalStartingLine = scanner.lineNumber();
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw new StacksScannerException(String.format(
						"\n<li><strong>%s</strong><em> Line #: %d</em>: "
						+ "Scanner Error: Unterminated string literal.</li>",
						scanner.moduleLeafName(),
						scanner.lineNumber()),
						scanner);
				}
				int c = scanner.next();
				scanner.resetBeingTokenized();
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
								throw new StacksScannerException(
									String.format(
										"\n<li><strong>%s</strong><em> Line #: %d"
											+ "</em>: Scanner Error: Encountered end "
											+ "of file after backslash in string "
											+ "literal.\n",
										scanner.moduleLeafName(),
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
										scanner.beingTokenized()
											.setLength(erasurePosition);
										canErase = false;
									}
									else
									{
										throw new StacksScannerException(
											String.format(
												"\n<li><strong>%s</strong><em> "
													+ "Line #: %d</em>: Scanner Error: "
													+ "The input before  \"\\|\" "
													+ "contains non-whitespace.</li>",
												scanner.moduleLeafName(),
												scanner.lineNumber()),
											scanner);
									}
									break;
								case '(':
									parseUnicodeEscapes(
										scanner,
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
												+ "escape should be followed by one of "
												+ "n , r, t, \\, \", (, [, |, or "
												+ "a line break.</li>",
											scanner.moduleLeafName(),
											scanner.lineNumber()),
										scanner);
							}
							erasurePosition = scanner.beingTokenized().length();
							break;
						}
						case '\r':
						{
							// Transform \r or \r\n in the source into \n.
							if (!scanner.atEnd())
							{
								scanner.peekFor('\n');
							}
							scanner.beingTokenized().appendCodePoint('\n');
							canErase = true;
							erasurePosition = scanner.beingTokenized().length();
							break;
						}
						case '\n':
						{
							// Just like a regular character, but limit how much
							// can be removed by a subsequent '\|'.
							scanner.beingTokenized().appendCodePoint('\n');
							canErase = true;
							erasurePosition = scanner.beingTokenized().length();
							break;
						}
						case '*':
						{
							// Just like a regular character, but limit how much
							// can be removed by a subsequent '\|'.
							if (!canErase)
							{
								scanner.beingTokenized().appendCodePoint('*');
								erasurePosition = scanner
									.beingTokenized()
									.length();
							}
							break;
						}
						default:
						{
							scanner.beingTokenized().appendCodePoint(c);
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
						scanner.lineNumber(literalStartingLine);
						throw new StacksScannerException(String.format(
							"\n<li><strong>%s"
							+ "</strong><em> Line #: %d</em>: Scanner Error: "
							+ "Unterminated string literal.</li>",
								scanner.moduleLeafName(),
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
					final StacksScanner scanner,
					final StringBuilder stringBuilder)
				throws StacksScannerException
			{
				if (scanner.atEnd())
				{
					throw new StacksScannerException(String.format(
						"\n<li><strong>%s</strong><em> Line #: "
						+ "%d</em>: Scanner Error: Expected hexadecimal "
						+ "Unicode codepoints separated by commas</li>",
						scanner.moduleLeafName(),
						scanner.lineNumber()),
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
							throw new StacksScannerException(String.format(
								"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Expected a hex "
									+ "digit or comma or closing "
									+ "parenthesis</li>",
									scanner.moduleLeafName(),
									scanner.lineNumber()),
								scanner);
						}
						if (digitCount > 6)
						{
							throw new StacksScannerException(String.format(
									"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Expected at "
									+ "most six hex digits per "
									+ "comma-separated Unicode entry</li>",
									scanner.moduleLeafName(),
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
							+ "each being one to six (upper case) "
							+ "hexadecimal digits</li>",
							scanner.moduleLeafName(),
							scanner.lineNumber()),
						scanner);
					}
					assert digitCount >= 1 && digitCount <= 6;
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw new StacksScannerException(
							String.format(
								"\n<li><strong>%s</strong><em> Line #: "
								+ "%d</em>: Scanner Error: The maximum allowed "
								+ "code point for a Unicode character is "
								+ "U+10FFFF</li>",
								scanner.moduleLeafName(),
								scanner.lineNumber()),
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
		 * Process a Bracket Token to determine if it is a {@link
		 * BracketedStacksToken}
		 */
		BRACKET ()
		{
			@Override
			void scan (final StacksScanner scanner)
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
					int c = scanner.next();
					if (c == '\"')
					{
						c = scanner.next();
						final int literalStartingLine = scanner.lineNumber();
						while (c != '\"')
						{
							if (c == '\\')
							{
								if (scanner.atEnd())
								{
									throw new StacksScannerException(
										String.format(
											"\n<li><strong>%s</strong><em> "
											+ "Line #: %d </em>: Scanner "
											+ "Error: Encountered end of "
											+ "file after backslash in string "
											+ "literal.\n",
											scanner.moduleLeafName(),
											scanner.lineNumber()),
										scanner);
								}
								c = scanner.next();
							}
							if (scanner.atEnd())
							{
								// Indicate where the quoted string started, to
								// make it easier to figure out where the
								// end-quote is missing.
								scanner.lineNumber(literalStartingLine);
								throw new StacksScannerException(
									String.format("\n<li><strong>%s"
									+ "</strong><em> Line #: %d</em>: Scanner "
									+ "Error: Unterminated string "
									+ "literal.</li>",
										scanner.moduleLeafName(),
										scanner.lineNumber()),
									scanner);
							}
							c = scanner.next();
						}
					}
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
		 * Process a token to determine if it starts a section
		 */
		KEYWORD_START ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next();
				}
				final AbstractStacksToken specialToken =
					scanner.addKeywordToken();

				if (specialToken.isSectionToken())
				{
					scanner.sectionStartLocations
						.add(scanner.outputTokens.size()-1);
				}
			}
		},

		/**
		 * Process a newline
		 */
		NEWLINE ()
		{
			@Override
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				scanner.incrementNewlineCount();
				while (Character.isSpaceChar(scanner.peek())
					|| Character.isWhitespace(scanner.peek()))
				{
					int cp = scanner.next();
					if (forCodePoint(cp) == NEWLINE)
					{
							scanner.incrementNewlineCount();
					}
					if (scanner.peekFor('*'))
					{
						cp = scanner.next();
						if (forCodePoint(cp) == NEWLINE)
						{
								scanner.incrementNewlineCount();
						}
					}
				}
				if (scanner.peek() == '<')
				{
					scanner.hasHTMLTagTrue();
				}
				if (!scanner.hasHTMLTag() && scanner.newlineCount() > 1
					&& !(forCodePoint(scanner.peek()) == SLASH)
					&& !(forCodePoint(scanner.peek()) == KEYWORD_START))
				{
					if (scanner.addedParagraphHTMLTag())
					{
						scanner.addHTMLTokens("</p>\n<p>");
					}
					else
					{
						scanner.addHTMLTokens("<p>");
					}
					scanner.addedParagraphHTMLTagTrue();
				}
				if (scanner.hasHTMLTag() && scanner.newlineCount() > 1
					&& scanner.addedParagraphHTMLTag())
				{
					if (!(forCodePoint(scanner.peek()) == SLASH)
						&& !(forCodePoint(scanner.peek()) == KEYWORD_START))
					{
						scanner.addHTMLTokens("</p>");
						scanner.hasHTMLTagFalse();
					}
				}
				if ((forCodePoint(scanner.peek()) == KEYWORD_START ||
							scanner.atEnd())
						&& scanner.addedParagraphHTMLTag())
				{
					scanner.addHTMLTokens("</p>");
					scanner.hasHTMLTagFalse();
					scanner.addedParagraphHTMLTagFalse();
				}

				scanner.resetNewlineCount();
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
			void scan (final StacksScanner scanner)
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
								scanner.moduleLeafName(),
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
			void scan (final StacksScanner scanner)
				throws StacksScannerException
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next();
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
	 * Answer the {@linkplain AbstractCommentImplementation}
	 * that comprise a {@linkplain CommentTokenDescriptor Avail comment}.
	 *
	 * @param commentToken
	 *		An {@linkplain CommentTokenDescriptor Avail comment} to be
	 *		tokenized.
	 * @param moduleName
	 * 		The name of the module this comment appears in.
	 * @param linkingFileMap
	 * 		A map for all the files for Stacks
	 * @return a {@link List list} of all tokenized words in the {@link
	 * 		CommentTokenDescriptor Avail comment}.
	 * @throws StacksScannerException If scanning fails.
	 * @throws StacksCommentBuilderException
	 */
	public static AbstractCommentImplementation processCommentString (
			final A_Token commentToken,
			final String moduleName,
			final LinkingFileMap linkingFileMap)
		throws StacksScannerException, StacksCommentBuilderException
	{
		final StacksScanner scanner =
			new StacksScanner(commentToken,moduleName);
		scanner.scan();

		if (scanner.sectionStartLocations.isEmpty())
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Scanner Error: Malformed "
				+ "comment; has no distinguishing main tags.</li>",
				scanner.moduleLeafName(),
				scanner.commentStartLine);
			throw new StacksScannerException(errorMessage,scanner);
		}

		return StacksParser.parseCommentString(
			scanner.outputTokens,
			scanner.sectionStartLocations,
			scanner.moduleName(),
			scanner.commentStartLine(),
			linkingFileMap);
	}

	/**
	 * @return the module file name without the path
	 */
	public String moduleLeafName ()
	{
		return moduleLeafName;
	}

	/**
	 * Insert HTML tags in tokenized comments.
	 * @param htmltags the html text to add.
	 */
	public void addHTMLTokens (final String htmltags)
	{
		final StacksToken token = StacksToken.create(
			htmltags,
			position () + filePosition(),
			lineNumber(),
			startOfTokenLinePostion(),
			moduleName);
		outputTokens.add(token);
	}
}
