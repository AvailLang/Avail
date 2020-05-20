/*
 * StacksScanner.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.stacks.scanner

import com.avail.descriptor.character.CharacterDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksParser
import com.avail.stacks.comment.AvailComment
import com.avail.stacks.exceptions.StacksCommentBuilderException
import com.avail.stacks.exceptions.StacksScannerException
import com.avail.stacks.tokens.BracketedStacksToken
import com.avail.stacks.tokens.SectionKeywordStacksToken
import com.avail.stacks.tokens.StacksToken
import java.util.*

/**
 * A scanner for Stacks comments.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [StacksScanner].
 *
 * @param commentToken
 *   The [comment token][CommentTokenDescriptor] to be scanned and tokenized.
 * @param moduleName
 *   The name of the module the comment is in.
 */
class StacksScanner constructor (commentToken: A_Token, moduleName: String)
	: AbstractStacksScanner(moduleName)
{
	/**
	 * Does the comment end with the standard asterisk-forward slash?
	 */
	private val commentEndsStandardly: Boolean

	/**
	 * The number of newlines.
	 */
	private var newlineCount: Int = 0

	/**
	 * Does the current content start with an HTML tag?
	 */
	private var hasHTMLTag: Boolean = false

	/**
	 * Was a paragraph HTML tag added to the output tokens??
	 */
	private var addedParagraphHTMLTag: Boolean = false

	/**
	 * Does the comment end with the standard asterisk-forward slash?
	 */
	private val commentStartsStandardly: Boolean

	/**
	 * The start line of the overall comment
	 */
	private val commentStartLine: Int

	/** The module file name without the path.  */
	val moduleLeafName: String

	/**
	 * The index locations where a new [section][SectionKeywordStacksToken]
	 * begins in in the
	 */
	internal val sectionStartLocations: ArrayList<Int>

	/**
	 * Increment new line
	 */
	fun incrementNewlineCount()
	{
		newlineCount++
	}

	/**
	 * decrement new line
	 */
	fun decrementNewLineCount()
	{
		newlineCount--
	}

	/**
	 * @return the newlineCount
	 */
	fun newlineCount(): Int
	{
		return newlineCount
	}

	/**
	 * Set the newlineCount to 0;
	 */
	fun resetNewlineCount()
	{
		newlineCount = 0
	}

	/**
	 * @return the hasHTMLTag
	 */
	fun hasHTMLTag(): Boolean
	{
		return hasHTMLTag
	}

	/**
	 * set the hasHTMLTag to true
	 */
	fun hasHTMLTagTrue()
	{
		hasHTMLTag = true
	}

	/**
	 * set the hasHTMLTag to false
	 */
	fun hasHTMLTagFalse()
	{
		hasHTMLTag = false
	}

	/**
	 * @return the addedParagraphHTMLTag
	 */
	fun addedParagraphHTMLTag(): Boolean
	{
		return addedParagraphHTMLTag
	}

	/**
	 * set the hasHTMLTag to true
	 */
	fun addedParagraphHTMLTagTrue()
	{
		addedParagraphHTMLTag = true
	}

	/**
	 * set the hasHTMLTag to false
	 */
	fun addedParagraphHTMLTagFalse()
	{
		addedParagraphHTMLTag = false
	}

	init
	{
		resetNewlineCount()
		hasHTMLTagFalse()
		addedParagraphHTMLTagFalse()
		this.moduleLeafName =
			moduleName.substring(moduleName.lastIndexOf('/') + 1)

		val commentString = commentToken.string().asNativeString()
		tokenString(commentString)
		this.commentStartLine = commentToken.lineNumber()
		this.commentEndsStandardly = tokenString.substring(
			tokenString.length - 2
		) == "*/"
		this.commentStartsStandardly = tokenString.substring(0, 3) == "/**"
		this.lineNumber(commentToken.lineNumber())
		this.filePosition(commentToken.start())
		this.startOfTokenLinePosition(0)
		this.sectionStartLocations = ArrayList(9)
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	override fun atEnd(): Boolean
	{
		return if (commentEndsStandardly)
		{
			position == tokenString.length - 2
		}
		else position == tokenString.length
	}

	/**
	 * Scan the already-specified [String] to produce [ ][outputTokens].
	 *
	 * @throws StacksScannerException
	 */
	@Throws(StacksScannerException::class)
	fun scan()
	{
		if (commentStartsStandardly)
		{
			position(3)
		}
		else
		{
			position(0)
		}
		while (!atEnd())
		{
			startOfToken(position)
			ScannerAction
				.forCodePoint(next()).scan(this)
		}
	}

	/**
	 * An enumeration of actions to be performed based on the next character
	 * encountered.
	 */
	internal enum class ScannerAction
	{
		/**
		 * Parse double-quoted string literal. This is an open-quote, followed
		 * by zero or more items, and a close-quote. An item is either a single
		 * character that is neither backslash (\) nor quote ("), or a backslash
		 * followed by:
		 *
		 *  1. n, r, or t indicating newline (U+000A), return (U+000D) or tab
		 * (U+0009) respectively,
		 *  1. a backslash (\) indicating a single literal backslash character,
		 *
		 *  1. a quote (") indicating a single literal quote character,
		 *  1. open parenthesis "(", one or more upper case hexadecimal
		 * sequences of one to six digits, separated by commas, and a close
		 * parenthesis,
		 *  1. open bracket "[", an expression yielding a string, and "]", or
		 *
		 *  1. the end of the line, indicating the next line is to be considered
		 * a continuation of the current line.
		 *
		 */
		DOUBLE_QUOTE
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				val literalStartingLine = scanner.lineNumber
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw StacksScannerException(
						String.format(
							"\n<li><strong>%s</strong><em> Line #: %d</em>: "
								+ "Scanner Error: Unterminated string literal.</li>",
							scanner.moduleLeafName,
							scanner.lineNumber),
						scanner)
				}
				var c = scanner.next()
				scanner.resetBeingTokenized()
				var canErase = true
				var erasurePosition = 0
				while (c != '\"')
				{
					when (c)
					{
						'\\' ->
						{
							if (scanner.atEnd())
							{
								throw StacksScannerException(
									String.format(
										"\n<li><strong>%s</strong><em> Line #: %d"
											+ "</em>: Scanner Error: Encountered end "
											+ "of file after backslash in string "
											+ "literal.\n",
										scanner.moduleLeafName,
										scanner.lineNumber),
									scanner)
							}
							when (scanner.next())
							{
								'n' -> scanner.beingTokenized().append('\n')
								'r' -> scanner.beingTokenized().append('\r')
								't' -> scanner.beingTokenized().append('\t')
								'\\' -> scanner.beingTokenized().append('\\')
								'\"' -> scanner.beingTokenized().append('\"')
								'\r' ->
								{
									// Treat \r or \r\n in the source just like \n.
									if (!scanner.atEnd())
									{
										scanner.peekFor('\n')
									}
									canErase = true
								}
								'\n' ->
									// A backslash ending a line.  Emit nothing.
									// Allow '\|' to back up to here as long as only
									// whitespace follows.
									canErase = true
								'|' ->
									// Remove all immediately preceding white space
									// from this line.
									if (canErase)
									{
										scanner.beingTokenized()
											.setLength(erasurePosition)
										canErase = false
									}
									else
									{
										throw StacksScannerException(
											String.format(
												"\n<li><strong>%s</strong><em> "
													+ "Line #: %d</em>: Scanner Error: "
													+ "The input before  \"\\|\" "
													+ "contains non-whitespace.</li>",
												scanner.moduleLeafName,
												scanner.lineNumber),
											scanner)
									}
								'(' -> parseUnicodeEscapes(
									scanner,
									scanner.beingTokenized())
								'[' ->
								{
									scanner.lineNumber(literalStartingLine)
									throw StacksScannerException(
										"Power strings are not yet supported",
										scanner)
								}
								else -> throw StacksScannerException(
									String.format(
										"\n<li><strong>%s</strong><em> Line #: "
											+ "%d</em>: Scanner Error: Backslash "
											+ "escape should be followed by one of "
											+ "n , r, t, \\, \", (, [, |, or "
											+ "a line break.</li>",
										scanner.moduleLeafName,
										scanner.lineNumber),
									scanner)
							}
							erasurePosition = scanner.beingTokenized().length
						}
						'\r' ->
						{
							// Transform \r or \r\n in the source into \n.
							if (!scanner.atEnd())
							{
								scanner.peekFor('\n')
							}
							scanner.beingTokenized().append('\n')
							canErase = true
							erasurePosition = scanner.beingTokenized().length
						}
						'\n' ->
						{
							// Just like a regular character, but limit how much
							// can be removed by a subsequent '\|'.
							scanner.beingTokenized().append('\n')
							canErase = true
							erasurePosition = scanner.beingTokenized().length
						}
						'*' ->
						{
							// Just like a regular character, but limit how much
							// can be removed by a subsequent '\|'.
							if (!canErase)
							{
								scanner.beingTokenized().append('*')
								erasurePosition = scanner
									.beingTokenized()
									.length
							}
						}
						else ->
						{
							scanner.beingTokenized().append(c)
							if (canErase && !Character.isWhitespace(c))
							{
								canErase = false
							}
						}
					}
					if (scanner.atEnd())
					{
						// Indicate where the quoted string started, to make it
						// easier to figure out where the end-quote is missing.
						scanner.lineNumber(literalStartingLine)
						throw StacksScannerException(
							String.format(
								"\n<li><strong>%s"
									+ "</strong><em> Line #: %d</em>: Scanner Error: "
									+ "Unterminated string literal.</li>",
								scanner.moduleLeafName,
								scanner.lineNumber),
							scanner)
					}
					c = scanner.next()
				}
				scanner.addQuotedToken()
				scanner.resetBeingTokenized()
			}

			/**
			 * Parse Unicode hexadecimal encoded characters.  The characters
			 * "\" and "(" were just encountered, so expect a comma-separated
			 * sequence of hex sequences, each of no more than six digits, and
			 * having a value between 0 and 0x10FFFF, followed by a ")".
			 *
			 * @param scanner
			 *   The source of characters.
			 * @param stringBuilder
			 *   The [StringBuilder] on which to append the corresponding
			 *   Unicode characters.
			 */
			@Throws(StacksScannerException::class)
			private fun parseUnicodeEscapes(
				scanner: StacksScanner, stringBuilder: StringBuilder)
			{
				if (scanner.atEnd())
				{
					throw StacksScannerException(
						String.format(
							"\n<li><strong>%s</strong><em> Line #: "
								+ "%d</em>: Scanner Error: Expected hexadecimal "
								+ "Unicode codepoints separated by commas</li>",
							scanner.moduleLeafName,
							scanner.lineNumber),
						scanner)
				}
				var c = scanner.next()
				while (c != ')')
				{
					var value = 0
					var digitCount = 0
					while (c != ',' && c != ')')
					{
						when (c)
						{
							in '0'..'9' ->
							{
								value = (value shl 4) + c.toInt() - '0'.toInt()
								digitCount++
							}
							in 'A'..'F' ->
							{
								value = (value shl 4) + c.toInt() - 'A'.toInt() + 10
								digitCount++
							}
							in 'a'..'f' ->
							{
								value = (value shl 4) + c.toInt() - 'a'.toInt() + 10
								digitCount++
							}
							else -> throw StacksScannerException(
								String.format(
									"\n<li><strong>%s</strong><em> Line #: "
										+ "%d</em>: Scanner Error: Expected a hex "
										+ "digit or comma or closing "
										+ "parenthesis</li>",
									scanner.moduleLeafName,
									scanner.lineNumber),
								scanner)
						}
						if (digitCount > 6)
						{
							throw StacksScannerException(
								String.format(
									"\n<li><strong>%s</strong><em> Line #: "
										+ "%d</em>: Scanner Error: Expected at "
										+ "most six hex digits per "
										+ "comma-separated Unicode entry</li>",
									scanner.moduleLeafName,
									scanner.lineNumber),
								scanner)
						}
						c = scanner.next()
					}
					if (digitCount == 0)
					{
						throw StacksScannerException(
							String.format(
								"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: Expected a "
									+ "comma-separated list of Unicode code points, "
									+ "each being one to six (upper case) "
									+ "hexadecimal digits</li>",
								scanner.moduleLeafName,
								scanner.lineNumber),
							scanner)
					}
					assert(digitCount in 1..6)
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw StacksScannerException(
							String.format(
								"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: The maximum allowed "
									+ "code point for a Unicode character is "
									+ "U+10FFFF</li>",
								scanner.moduleLeafName,
								scanner.lineNumber),
							scanner)
					}
					stringBuilder.appendCodePoint(value)
					assert(c == ',' || c == ')')
					if (c == ',')
					{
						c = scanner.next()
					}
				}
				assert(c == ')')
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a
		 * [BracketedStacksToken].
		 */
		BRACKET
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				val startOfBracket = scanner.position
				val startOfBracketLineNumber = scanner.lineNumber
				val startOfBracketTokenLinePosition =
					scanner.startOfTokenLinePosition()

				while (Character.isSpaceChar(scanner.peek())
					|| Character.isWhitespace(scanner.peek()))
				{
					scanner.next()
					if (scanner.atEnd())
					{
						scanner.position(startOfBracket)
						scanner.lineNumber(startOfBracketLineNumber)
						scanner.startOfTokenLinePosition(
							startOfBracketTokenLinePosition)
						scanner.addCurrentToken()
						return
					}
				}

				if (!scanner.peekFor('@'))
				{
					if (scanner.position != startOfBracket + 1)
					{
						scanner.position(startOfBracket)
						scanner.addCurrentToken()
						return
					}
					return
				}
				while (!scanner.peekFor('}'))
				{
					var c = scanner.next()
					if (c == '\"')
					{
						c = scanner.next()
						val literalStartingLine = scanner.lineNumber
						while (c != '\"')
						{
							if (c == '\\')
							{
								if (scanner.atEnd())
								{
									throw StacksScannerException(
										String.format(
											"\n<li><strong>%s</strong><em> "
												+ "Line #: %d </em>: Scanner "
												+ "Error: Encountered end of "
												+ "file after backslash in string "
												+ "literal.\n",
											scanner.moduleLeafName,
											scanner.lineNumber),
										scanner)
								}
								scanner.next()
							}
							if (scanner.atEnd())
							{
								// Indicate where the quoted string started, to
								// make it easier to figure out where the
								// end-quote is missing.
								scanner.lineNumber(literalStartingLine)
								throw StacksScannerException(
									String.format(
										"\n<li><strong>%s"
											+ "</strong><em> Line #: %d</em>: Scanner "
											+ "Error: Unterminated string "
											+ "literal.</li>",
										scanner.moduleLeafName,
										scanner.lineNumber),
									scanner)
							}
							c = scanner.next()
						}
					}
					if (scanner.atEnd())
					{
						scanner.position(startOfBracket)
						scanner.lineNumber(startOfBracketLineNumber)
						scanner.startOfTokenLinePosition(
							startOfBracketTokenLinePosition)
						return
					}
				}
				scanner.addBracketedToken()
			}
		},

		/**
		 * Process a token to determine if it starts a section
		 */
		KEYWORD_START
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next()
				}
				val specialToken = scanner.addKeywordToken()

				if (specialToken.isSectionToken)
				{
					scanner.sectionStartLocations
						.add(scanner.outputTokens.size - 1)
				}
			}
		},

		/**
		 * Process a newline
		 */
		NEWLINE
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				scanner.incrementNewlineCount()
				while (Character.isSpaceChar(scanner.peek())
					|| Character.isWhitespace(scanner.peek()))
				{
					var cp = scanner.next()
					if (forCodePoint(
							cp) === NEWLINE)
					{
						scanner.incrementNewlineCount()
					}
					if (scanner.peekFor('*'))
					{
						cp = scanner.next()
						if (forCodePoint(
								cp) === NEWLINE)
						{
							scanner.incrementNewlineCount()
						}
					}
				}
				if (scanner.peek() == '<')
				{
					scanner.hasHTMLTagTrue()
				}
				if (!scanner.hasHTMLTag() && scanner.newlineCount() > 1
					&& !(forCodePoint(
						scanner.peek()) === SLASH)
					&& !(forCodePoint(
						scanner.peek()) === KEYWORD_START))
				{
					if (scanner.addedParagraphHTMLTag())
					{
						scanner.addHTMLTokens("</p>\n<p>")
					}
					else
					{
						scanner.addHTMLTokens("<p>")
					}
					scanner.addedParagraphHTMLTagTrue()
				}
				if (scanner.hasHTMLTag() && scanner.newlineCount() > 1
					&& scanner.addedParagraphHTMLTag())
				{
					if (!(forCodePoint(
							scanner.peek()) === SLASH)
						&& !(forCodePoint(
							scanner.peek()) === KEYWORD_START))
					{
						scanner.addHTMLTokens("</p>")
						scanner.hasHTMLTagFalse()
					}
				}
				if ((forCodePoint(
						scanner.peek()) === KEYWORD_START
						|| scanner.atEnd()) && scanner.addedParagraphHTMLTag())
				{
					scanner.addHTMLTokens("</p>")
					scanner.hasHTMLTagFalse()
					scanner.addedParagraphHTMLTagFalse()
				}

				scanner.resetNewlineCount()
			}
		},

		/**
		 * A slash was encountered. Check if it's the start of a nested comment,
		 * and if so skip it. If not, add the slash as a [token][StacksToken].
		 *
		 * Nested comments are supported.
		 */
		SLASH
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				if (!scanner.peekFor('*'))
				{
					while (Character.isSpaceChar(scanner.peek()) || Character.isWhitespace(
							scanner.peek()))
					{
						scanner.next()
					}
					scanner.addCurrentToken()
				}
				else
				{
					val startLine = scanner.lineNumber
					var depth = 1
					do
					{
						if (scanner.atEnd())
						{
							throw StacksScannerException(
								String.format(
									"\n<li><strong>%s</strong><em> Line #: "
										+ "%d</em>: Scanner Error: Expected a close "
										+ "comment (*/) to correspond with the open "
										+ "comment (/*) on line #%d</li>",
									scanner.moduleLeafName,
									scanner.lineNumber,
									startLine),
								scanner)
						}
						if (scanner.peekFor('/')
							&& scanner.peekFor('*'))
						{
							depth++
						}
						else if (scanner.peekFor('*'))
						{
							if (scanner.peekFor('/'))
							{
								depth--
							}
						}
						else
						{
							scanner.next()
						}
					}
					while (depth != 0)
				}
			}
		},

		/**
		 * Scan an unrecognized character.
		 */
		STANDARD_CHARACTER
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: StacksScanner)
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next()
				}
				scanner.addCurrentToken()
			}
		},

		/**
		 * A whitespace character was encountered. Just skip it.
		 */
		WHITESPACE
		{
			// Do nothing
			override fun scan(scanner: StacksScanner) = Unit
		},

		/**
		 * The zero-width non-breaking space character was encountered. This is
		 * also known as the byte-order-mark and generally only appears at the
		 * start of a file as a hint about the file's endianness.
		 *
		 * Treat it as whitespace even though Unicode says it isn't.
		 */
		ZEROWIDTHWHITESPACE
		{
			// Do nothing
			override fun scan(scanner: StacksScanner) = Unit
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *   The scanner processing this character.
		 * @throws StacksScannerException If scanning fails.
		 */
		@Throws(StacksScannerException::class)
		internal abstract fun scan(scanner: StacksScanner)

		companion object
		{

			/**
			 * Figure out the [ScannerAction] to invoke for the specified code
			 * point. The argument may be any Unicode code point, including
			 * those in the Supplemental Planes.
			 *
			 * @param c
			 *   The code point to analyze.
			 * @return The ScannerAction to invoke when that code point is
			 *   encountered.
			 */
			fun forCodePoint(c: Char): ScannerAction
			{
				val cp = c.toInt()
				return if (cp < 65536)
				{
					values()[dispatchTable[cp].toInt()]
				}
				else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
				{
					WHITESPACE
				}
				else
				{
					STANDARD_CHARACTER
				}
			}
		}
	}

	/**
	 * Insert HTML tags in tokenized comments.
	 *
	 * @param htmltags the html text to add.
	 */
	fun addHTMLTokens(htmltags: String)
	{
		val token = StacksToken.create(
			htmltags,
			position + filePosition,
			lineNumber,
			startOfTokenLinePosition(),
			moduleName)
		outputTokens.add(token)
	}

	companion object
	{

		/**
		 * Answer the [AvailComment]
		 * that comprise a [Avail comment][CommentTokenDescriptor].
		 *
		 * @param commentToken
		 *   An [Avail comment][CommentTokenDescriptor] to be tokenized.
		 * @param moduleName
		 *   The name of the module this comment appears in.
		 * @param linkingFileMap
		 *   A map for all the files for Stacks
		 * @return A [List] of all tokenized words in the
		 *   [Avail comment][CommentTokenDescriptor].
		 * @throws StacksScannerException If scanning fails.
		 * @throws StacksCommentBuilderException
		 */
		@Throws(
			StacksScannerException::class,
			StacksCommentBuilderException::class)
		fun processCommentString(
			commentToken: A_Token,
			moduleName: String,
			linkingFileMap: LinkingFileMap): AvailComment?
		{
			val scanner =
				StacksScanner(commentToken, moduleName)
			scanner.scan()

			if (scanner.sectionStartLocations.isEmpty())
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Scanner Error: Malformed "
						+ "comment; has no distinguishing main tags.</li>",
					scanner.moduleLeafName,
					scanner.commentStartLine)
				throw StacksScannerException(
					errorMessage,
					scanner)
			}

			return StacksParser.parseCommentString(
				scanner.outputTokens,
				scanner.sectionStartLocations,
				scanner.moduleName,
				scanner.commentStartLine,
				linkingFileMap)
		}
	}
}
