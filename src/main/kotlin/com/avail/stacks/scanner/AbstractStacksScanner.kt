/*
 * AbstractStacksScanner.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.stacks.exceptions.StacksScannerException
import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.stacks.tokens.BracketedStacksToken
import com.avail.stacks.tokens.KeywordStacksToken
import com.avail.stacks.tokens.QuotedStacksToken
import com.avail.stacks.tokens.StacksToken

/**
 * The basics of a Stacks scanner.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property moduleName
 *   The name of the module being lexically scanned.
 * @constructor
 * Create an `AbstractStacksScanner`.
 *
 * @param moduleName
 *   The name of the module being scanned.
 */
abstract class AbstractStacksScanner internal constructor(
	internal var moduleName: String)
{
	/**
	 * The [comment&#32;token][CommentTokenDescriptor] text that has been lexed
	 * as one long token.
	 */
	var tokenString: String = ""
		private set

	/** The tokens that have been parsed so far. */
	var outputTokens: MutableList<AbstractStacksToken> = mutableListOf()

	/** The current position in the input string. */
	var position: Int = 0
		private set

	/** The start position of the comment in the file. */
	var filePosition: Int = 0
		private set

	/** The position of the start of the token currently being parsed. */
	private var startOfToken: Int = 0

	/**
	 * The place on the currently line where the token starts. Always
	 * initialized to zero at creation of [scanner][StacksScanner].
	 */
	private var startOfTokenLinePosition: Int = 0

	/** The line number of the start of the token currently being parsed. */
	var lineNumber: Int = 0

	/** A [StringBuilder] used for tokenization. */
	private var beingTokenized = StringBuilder()

	/**
	 * Alter the scanner's position in the input String.
	 *
	 * @param newLineNumber
	 *   The new lineNumber for this scanner as an index.
	 */
	protected fun lineNumber(newLineNumber: Int)
	{
		this.lineNumber = newLineNumber
	}

	/**
	 * Alter the scanner's startOfTokenLinePosition in the input String.
	 *
	 * @param newStartOfTokenLinePosition
	 *   The new position for this scanner as an index into the input String's
	 *   chars current line.
	 */
	protected fun startOfTokenLinePosition(
		newStartOfTokenLinePosition: Int)
	{
		this.startOfTokenLinePosition = newStartOfTokenLinePosition
	}

	/**
	 * Answer the scanner's current index in the input String.
	 *
	 * @return The current character position on the line in the input String.
	 */
	protected fun startOfTokenLinePosition(): Int = startOfTokenLinePosition

	/**
	 * Alter the scanner's startOfToken in the input String.
	 *
	 * @param newStartOfToken
	 *   The new startOfToken for this scanner as an index into the input
	 *   String.
	 */
	protected fun startOfToken(newStartOfToken: Int)
	{
		this.startOfToken = newStartOfToken
	}

	/**
	 * Alter the scanner's input String.
	 *
	 * @param aTokenString
	 *   The tokenString to be lexed
	 */
	protected fun tokenString(aTokenString: String)
	{
		this.tokenString = aTokenString
	}

	/**
	 * Alter the scanner's position in the input String.
	 *
	 * @param newPosition
	 *    new position for this scanner as an index into the input String's
	 *    chars (which may not correspond with the actual code points in the
	 *    Supplementary Planes).
	 */
	protected fun position(newPosition: Int)
	{
		this.position = newPosition
	}

	fun beingTokenized(): StringBuilder
	{
		return beingTokenized
	}

	fun resetBeingTokenized()
	{
		beingTokenized = StringBuilder()
	}

	/**
	 * Extract a native [String] from the input, from the [startOfToken] to the
	 * current [position].
	 *
	 * @return A substring of the input.
	 */
	private fun currentTokenString(): String
	{
		val alternateToken = beingTokenized.toString()
		return if (alternateToken.isEmpty())
		{
			tokenString.substring(startOfToken, position)
		}
		else alternateToken
	}

	/**
	 * Alter the scanner's ultimate filePosition in the input module.
	 *
	 * @param newFilePosition
	 *   The new position for this scanner as an index into the input String's
	 *   chars (which may not correspond with the actual code points in the
	 *   Supplementary Planes).
	 */
	protected fun filePosition(newFilePosition: Int)
	{
		this.filePosition = newFilePosition
	}

	/**
	 * From the full directory path of a module name, obtain the leaf portion
	 * that represents the file name in the directory.
	 *
	 * @return The module leaf name.
	 */
	fun obtainModuleSimpleName(): String
	{
		val modName = moduleName
		var i = modName.length - 1
		while (i > -1 && modName[i] != '\\' && modName[i] != '/')
		{
			i--
		}
		return modName.substring(i + 1)
	}

	/**
	 * Add the provided uninitialized [quoted][StacksToken].
	 *
	 * @return The newly added token.
	 */
	internal fun addCurrentToken()
	{
		val token = StacksToken.create(
			currentTokenString(),
			position + filePosition,
			lineNumber,
			startOfTokenLinePosition(),
			moduleName)
		outputTokens.add(token)
	}

	/**
	 * Add the provided uninitialized [quoted][QuotedStacksToken].
	 *
	 * @return The newly added token.
	 */
	@Throws(StacksScannerException::class)
	internal fun addBracketedToken()
	{
		val token = BracketedStacksToken.create(
			currentTokenString(),
			lineNumber,
			position + filePosition,
			startOfTokenLinePosition(),
			moduleName)
		outputTokens.add(token)
	}

	/**
	 * Add the provided uninitialized [quoted][QuotedStacksToken].
	 *
	 * @return The newly added token.
	 */
	internal fun addQuotedToken()
	{
		val token = QuotedStacksToken.create(
			currentTokenString(),
			position + filePosition,
			lineNumber,
			startOfTokenLinePosition(),
			moduleName)
		outputTokens.add(token)
	}

	/**
	 * Add the provided uninitialized [keyword][KeywordStacksToken].
	 *
	 * @return The newly added token.
	 */
	internal fun addKeywordToken(): AbstractStacksToken
	{
		val token = KeywordStacksToken.create(
			currentTokenString(),
			position + filePosition,
			lineNumber,
			startOfTokenLinePosition(),
			moduleName)
		outputTokens.add(token)
		return token
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	internal abstract fun atEnd(): Boolean

	/**
	 * Extract the current character and increment the [position].
	 *
	 * @return The consumed character.
	 * @throws StacksScannerException If scanning fails.
	 */
	@Throws(StacksScannerException::class)
	operator fun next(): Char
	{
		if (atEnd())
		{
			throw StacksScannerException(
				String.format(
					"\n<li><strong>%s"
						+ "</strong><em>Line #: %d</em>: Scanner Error: Attempted to "
						+ "read past end of file.</li>",
					this.obtainModuleSimpleName(),
					this.lineNumber),
				this)
		}
		val c = Character.codePointAt(tokenString, position)
		position += Character.charCount(c)
		if (c == '\n'.toInt())
		{
			lineNumber++
			startOfTokenLinePosition = 0
		}
		return c.toChar()
	}

	/**
	 * Answer the character at the current [position] of the input.
	 *
	 * @return The current character.
	 */
	internal fun peek(): Char =
		Character.codePointAt(tokenString, position).toChar()

	/**
	 * Skip aCharacter if it's next in the input stream. Answer whether it was
	 * present.
	 *
	 * @param aCharacter
	 *   The character to look for, as an int to allow code points beyond the
	 *   Basic Multilingual Plane (U+0000 - U+FFFF).
	 * @return Whether the specified character was found and skipped.
	 * @throws StacksScannerException If scanning fails.
	 */
	@Throws(StacksScannerException::class)
	internal fun peekFor(aCharacter: Char): Boolean
	{
		if (atEnd())
		{
			return false
		}
		if (peek() != aCharacter)
		{
			return false
		}
		next()
		return true
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
		 */
		DOUBLE_QUOTE
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: AbstractStacksScanner)
			{
				val literalStartingLine = scanner.lineNumber
				if (scanner.atEnd())
				{
					// Just the open quote, then end of file.
					throw StacksScannerException(
						String.format(
							"\n<li><strong>%s"
								+ "</strong><em>Line #: %d</em>: Scanner Error: "
								+ "Unterminated string literal.</li>",
							scanner.obtainModuleSimpleName(),
							scanner.lineNumber),
						scanner)
				}
				var c = scanner.next()
				scanner.resetBeingTokenized()
				var canErase = true
				var erasurePosition = 0
				while (c != '"')
				{
					when (c)
					{
						'\\' ->
						{
							if (scanner.atEnd())
							{
								throw StacksScannerException(
									String.format(
										"\n<li><strong>%s</strong><em>Line #: %d"
											+ "</em>: Scanner Error: Encountered end "
											+ "of file after backslash in string "
											+ "literal.\n",
										scanner.obtainModuleSimpleName(),
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
										scanner
											.beingTokenized()
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
													+ "contains non-whitespace"
													+ "/not-'*'.</li>",
												scanner.obtainModuleSimpleName(),
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
											+ "escape should be followed by "
											+ "one of n, r, t, \\, \", (, "
											+ "[, |, or a line break.</li>",
										scanner.obtainModuleSimpleName(),
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
									+ "</strong><em>Line #: %d</em>: Scanner Error: "
									+ "Unterminated string literal.</li>",
								scanner.obtainModuleSimpleName(),
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
				scanner: AbstractStacksScanner,
				@Suppress("UNUSED_PARAMETER") stringBuilder: StringBuilder)
			{
				if (scanner.atEnd())
				{
					throw StacksScannerException(
						String.format(
							"\n<li><strong>%s</strong><em> Line #: "
								+ "%d</em>: Scanner Error: Expected hexadecimal "
								+ "Unicode codepoints separated by commas</li>",
							scanner.obtainModuleSimpleName(),
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
										+ "%d</em>: Scanner Error: Expected a "
										+ "hex digit or comma or closing "
										+ "parenthesis</li>",
									scanner.obtainModuleSimpleName(),
									scanner.lineNumber),
								scanner)
						}
						if (digitCount > 6)
						{
							throw StacksScannerException(
								String.format(
									"\n<li><strong>%s</strong><em> Line #: "
										+ "%d</em>: Scanner Error: Expected at "
										+ "most six hex digits per comma-separated "
										+ "Unicode entry</li>",
									scanner.obtainModuleSimpleName(),
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
									+ "each being one to six (upper case) hexadecimal "
									+ "digits</li>",
								scanner.obtainModuleSimpleName(),
								scanner.lineNumber),
							scanner)
					}
					assert(digitCount >= 1)
					if (value > CharacterDescriptor.maxCodePointInt)
					{
						throw StacksScannerException(
							String.format(
								"\n<li><strong>%s</strong><em> Line #: "
									+ "%d</em>: Scanner Error: The maximum "
									+ "allowed code point for a Unicode character "
									+ "is U+10FFFF</li>",
								scanner.obtainModuleSimpleName(),
								scanner.lineNumber),
							scanner)
					}
					scanner.beingTokenized().appendCodePoint(value)
					if (c == ',')
					{
						c = scanner.next()
					}
				}
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a
		 * [BracketedStacksToken].
		 */
		BRACKET
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: AbstractStacksScanner)
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
					scanner.next()
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
		 * Process a Bracket Token to determine if it is a [KeywordStacksToken].
		 */
		KEYWORD_START
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: AbstractStacksScanner)
			{
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek()))
				{
					scanner.next()
				}
				scanner.addKeywordToken()
			}
		},

		/**
		 * Process a Bracket Token to determine if it is a [KeywordStacksToken].
		 */
		NEWLINE
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: AbstractStacksScanner)
			{
				while (Character.isSpaceChar(scanner.peek()) || Character.isWhitespace(
						scanner.peek()))
				{
					scanner.next()
				}
				if (scanner.peekFor('*'))
				{
					scanner.next()
				}
			}
		},

		/**
		 * A slash was encountered. Check if it's the start of a nested comment,
		 * and if so skip it. If not, add the slash as a [Token][StacksToken].
		 *
		 * Nested comments are supported.
		 */
		SLASH
		{
			@Throws(StacksScannerException::class)
			override fun scan(scanner: AbstractStacksScanner)
			{
				if (!scanner.peekFor('*'))
				{
					while (Character.isSpaceChar(scanner.peek())
						|| Character.isWhitespace(scanner.peek()))
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
									scanner.obtainModuleSimpleName(),
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
			override fun scan(scanner: AbstractStacksScanner)
			{
				scanner.resetBeingTokenized()
				while (!Character.isSpaceChar(scanner.peek())
					&& !Character.isWhitespace(scanner.peek())
					&& !scanner.atEnd())
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
			override fun scan(scanner: AbstractStacksScanner) = Unit
		},

		/**
		 * The zero-width non-breaking space character was encountered. This is
		 * also known as the byte-order-mark and generally only appears at the
		 * start of a file as a hint about the file's endianness.
		 *
		 * Treat it as whitespace even though Unicode says it isn't.
		 */
		ZERO_WIDTH_WHITESPACE
		{
			// Do nothing
			override fun scan(scanner: AbstractStacksScanner) = Unit
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner
		 *   The scanner processing this character.
		 * @throws StacksScannerException If scanning fails.
		 */
		@Throws(StacksScannerException::class)
		internal abstract fun scan(scanner: AbstractStacksScanner)

		companion object
		{
			/**
			 * Figure out the `ScannerAction` to invoke for the specified code
			 * point. The argument may be any Unicode code point, including
			 * those in the Supplemental Planes.
			 *
			 * @param cp
			 *   The [Char] point to analyze.
			 * @return The ScannerAction to invoke when that code point is
			 *   encountered.
			 */
			fun forCodePoint(cp: Char): ScannerAction
			{
				val c = cp.toInt()
				return if (c < 65536)
				{
					values()[dispatchTable[c].toInt()]
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

	companion object
	{
		/**
		 * A table whose indices are Unicode code points (up to 65535) and whose
		 * values are [scanner&#32;actions][StacksScanner.ScannerAction].
		 */
		internal val dispatchTable = ByteArray(65536)

		/**
		 * Statically initialize the `dispatchTable` with suitable scanner
		 * actions. Note that this happens as part of class loading.
		 */
		init
		{
			for (i in 0..65535)
			{
				val c = i.toChar()
				val action: ScannerAction
				action =
					if (Character.isSpaceChar(c) || Character.isWhitespace(c))
					{
						ScannerAction.WHITESPACE
					}
					else
					{
						ScannerAction.STANDARD_CHARACTER
					}
				dispatchTable[i] = action.ordinal.toByte()
			}
			dispatchTable['\n'.toInt()] = ScannerAction
				.NEWLINE.ordinal.toByte()
			dispatchTable['{'.toInt()] = ScannerAction
				.BRACKET.ordinal.toByte()
			dispatchTable['"'.toInt()] =
				ScannerAction
					.DOUBLE_QUOTE.ordinal.toByte()
			dispatchTable['/'.toInt()] = ScannerAction.SLASH.ordinal.toByte()
			dispatchTable['\uFEFF'.toInt()] =
				ScannerAction
					.ZERO_WIDTH_WHITESPACE.ordinal.toByte()
			dispatchTable['@'.toInt()] =
				ScannerAction
					.KEYWORD_START.ordinal.toByte()
		}
	}
}
