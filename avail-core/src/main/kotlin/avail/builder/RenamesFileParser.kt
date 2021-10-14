/*
 * RenamesFileParser.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.builder

import avail.annotations.ThreadSafe
import avail.builder.RenamesFileParser.Token
import avail.descriptor.module.ModuleDescriptor
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.Reader
import java.nio.charset.MalformedInputException

/**
 * A `RenamesFileParser` parses a [file][File] of Avail
 * [module][ModuleDescriptor] renaming rules and answers a [map][Map].
 *
 * The format of the renames file is specified by the following simple grammar:
 *
 * ```
 * renamesFile ::= renameRule* ;
 * renameRule ::= quotedModulePath "→" quotedModulePath ;
 * quotedModulePath ::= '"' modulePath '"' ;
 * modulePath ::= moduleId ++ "/" ;
 * moduleId ::= [^/"]+ ;
 * ```
 *
 * Conceptually the renames file establishes a set of module reference renaming
 * rules. On the left-hand side of a rule is a fully-qualified module reference
 * (*quotedModulePath*) of the form **"/R/X/.../Y/Z"**, where **R** is a root
 * name referring to a vendor, **X** is a public package provided by the vendor,
 * **Y** is a package recursively within module group **X**, and **Z** is a
 * local module name. On the right-hand side of a rule is another module
 * reference of the form **"/Q/A/.../B/C"**, where **Q** is a root name
 * referring to a vendor, **A** is a public package provided by the vendor,
 * **B** is a package recursively within module group **A**, and **C** is a
 * local module name.
 *
 * Note that some operating systems may have difficulty resolving certain
 * *moduleId*s if they contain arbitrary Unicode characters.
 *
 * @property reader
 *   The [reader][Reader] responsible for fetching [tokens][Token].
 * @property roots
 *   The Avail [module&#32;roots][ModuleRoots].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `RenamesFileParser`.
 *
 * @param reader
 *   The [reader][Reader] responsible for fetching [tokens][Token]. The reader
 *   must [support&#32;marking][Reader.markSupported].
 * @param roots
 *   The Avail [module&#32;roots][ModuleRoots].
 */
class RenamesFileParser @ThreadSafe constructor(
	private val reader: Reader,
	private val roots: ModuleRoots)
{
	/**
	 * A [module&#32;name&#32;resolver][ModuleNameResolver]. The goal of the
	 * [parser][RenamesFileParser] is to populate the resolver with renaming
	 * rules.
	 */
	@Volatile
	private var resolver: ModuleNameResolver? = null

	init
	{
		assert(reader.markSupported())
	}

	/**
	 * The types of the [tokens][Token].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal enum class TokenType
	{
		/** A [module][ModuleDescriptor] or [file][File] path. */
		PATH,

		/** An arrow (→). */
		ARROW,

		/** An insignificant token. */
		UNKNOWN,

		/** End of file. */
		EOF
	}

	/**
	 * A `Token` associates a [TokenType] with a [lexeme][String] from the
	 * source text of the renames file.
	 *
	 * @property tokenType
	 *   The [TokenType].
	 * @property lexeme
	 *   The [lexeme][String].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `Token`.
	 *
	 * @param tokenType
	 *   The [TokenType].
	 * @param lexeme
	 *   The [lexeme][String].
	 */
	internal class Token constructor(
		val tokenType: TokenType,
		val lexeme: String)

	/**
	 * Has the scanner read the entire source text?
	 *
	 * @return
	 *   `true` if the scanner has read the entire [stream][Reader] of source
	 *   text, `false` otherwise.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	private val atEnd: Boolean
		@Throws(IOException::class)
		get()
		{
			reader.mark(1)
			val next = reader.read()
			reader.reset()
			return next == -1
		}

	/**
	 * Answer and consume the next code point from the [reader].
	 *
	 * @return
	 *   The next character from the reader.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun nextCodePoint(): Int
	{
		var next = reader.read()
		if (next == -1)
		{
			return -1
		}
		assert((next and 0xFFFF) == next)
		val high = next.toChar()
		if (Character.isSurrogate(high))
		{
			next = reader.read()
			if (next == -1)
			{
				throw MalformedInputException(1)
			}
			assert((next and 0xFFFF) == next)
			val low = next.toChar()
			if (!Character.isSurrogate(low))
			{
				throw MalformedInputException(1)
			}
			return Character.toCodePoint(high, low)
		}
		return high.code
	}

	/**
	 * Peek for the specified character. If the next character from the [reader]
	 * matches, then consume it and answer `true`.
	 *
	 * @param c
	 *   A character.
	 * @return
	 *   `true` if the next character from the [reader] matches the specified
	 *   character, `false` otherwise.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun peekFor(c: Int): Boolean
	{
		reader.mark(2)
		val next = nextCodePoint()
		if (next == c)
		{
			return true
		}
		reader.reset()
		return false
	}

	/**
	 * Answer a [token][Token] whose lexeme began with a double-quote (").
	 *
	 * @return
	 *   A [token][Token].
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] or unexpected end-of-file
	 *   occurs.
	 */
	@Throws(IOException::class)
	internal fun scanDoubleQuote(): Token
	{
		if (atEnd)
		{
			return Token(TokenType.UNKNOWN, "\"")
		}

		val builder = StringBuilder(50)
		while (true)
		{
			if (atEnd)
			{
				throw EOFException(
					"Expected close quote to correspond with open quote "
					+ "but found end-of-file")
			}
			if (peekFor('"'.code))
			{
				return Token(TokenType.PATH, builder.toString())
			}
			builder.appendCodePoint(nextCodePoint())
		}
	}

	/**
	 * Answer a [token][Token] whose lexeme began with a slash (/).
	 *
	 * @return
	 *   A [token][Token] or `null`.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] or unexpected end-of-file
	 *   occurs.
	 */
	@Throws(IOException::class)
	internal fun scanSlash(): Token?
	{
		if (!peekFor('*'.code))
		{
			return Token(TokenType.UNKNOWN, "/")
		}

		var depth = 1
		while (true)
		{
			if (atEnd)
			{
				throw EOFException(
					"Expected close comment to correspond with open comment "
					+ "but found end-of-file")
			}

			if (peekFor('/'.code) && peekFor('*'.code))
			{
				depth++
			}
			else if (peekFor('*'.code) && peekFor('/'.code))
			{
				depth--
			}
			else
			{
				nextCodePoint()
			}

			if (depth == 0)
			{
				return null
			}
		}
	}

	/**
	 * A `ScannerAction` attempts to read a [token][Token] from the [reader].
	 */
	private enum class ScannerAction
	{
		/** A double quote (") was just seen. */
		DOUBLE_QUOTE
		{
			@Throws(IOException::class)
			override fun scan(parser: RenamesFileParser, firstChar: Int) =
				parser.scanDoubleQuote()
		},

		/** A right array (→) was just seen. */
		RIGHT_ARROW
		{
			override fun scan(parser: RenamesFileParser, firstChar: Int) =
				scanRightArrow()
		},

		/** A forward slash (/) was just seen. */
		SLASH
		{
			@Throws(IOException::class)
			override fun scan(parser: RenamesFileParser, firstChar: Int) =
				parser.scanSlash()
		},

		/** A whitespace character was just seen. */
		WHITESPACE
		{
			override fun scan(parser: RenamesFileParser, firstChar: Int) =
				scanWhitespace()
		},

		/** A character of unknown significance was just seen. */
		UNKNOWN
		{
			override fun scan(parser: RenamesFileParser, firstChar: Int) =
				scanUnknown(firstChar)
		};

		/**
		 * Answer the next [token][Token] from the [stream][reader].
		 *
		 * @param parser
		 *   A [RenamesFileParser].
		 * @param firstChar
		 *   The character used to select the [ScannerAction].
		 * @return
		 *   A [token][Token].
		 * @throws IOException
		 *   If the scanner encounters an error while trying to scan for the
		 *   next [token][Token].
		 */
		@Throws(IOException::class)
		abstract fun scan(
			parser: RenamesFileParser,
			firstChar: Int): Token?

		companion object
		{
			/** An array of all [ScannerAction] enumeration values. */
			internal val all = values()
		}
	}

	/**
	 * Answer the next [token][Token].
	 *
	 * @return
	 *   A [token][Token].
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 * @throws RenamesFileParserException
	 *   If an [unknown][TokenType.UNKNOWN] [token][Token] is scanned.
	 */
	@Throws(IOException::class, RenamesFileParserException::class)
	private fun scan(): Token
	{
		while (!atEnd)
		{
			val c = nextCodePoint()
			val token = actionFor(c).scan(this, c)
			if (token !== null)
			{
				if (token.tokenType == TokenType.UNKNOWN)
				{
					throw RenamesFileParserException(
						"Unknown token (" + token.lexeme + ")")
				}

				return token
			}
		}

		return Token(TokenType.EOF, "<EOF>")
	}

	/**
	 * Parse a rename rule (*renameRule*) and install an appropriate
	 * transformation rule into the [module][ModuleNameResolver].
	 *
	 * @param modulePath
	 *   A [module][ModuleDescriptor] path.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException].
	 * @throws RenamesFileParserException
	 *   If a semantic rule is violated.
	 */
	@Throws(IOException::class, RenamesFileParserException::class)
	private fun parseRenameRule(modulePath: String)
	{
		val token = scan()
		if (token.tokenType != TokenType.ARROW)
		{
			throw RenamesFileParserException(
				"expected → but found (${token.lexeme})")
		}

		val filePath = scan()
		if (filePath.tokenType != TokenType.PATH)
		{
			throw RenamesFileParserException(
				"expected a file path but found (${filePath.lexeme})")
		}
		if (filePath.lexeme.isEmpty())
		{
			throw RenamesFileParserException(
				"module path ($modulePath) must not bind an empty file path")
		}

		val theResolver = resolver!!
		if (theResolver.hasRenameRuleFor(modulePath))
		{
			throw RenamesFileParserException(
				"duplicate rename rule for \"$modulePath\" is not allowed")
		}
		theResolver.addRenameRule(modulePath, filePath.lexeme)
	}

	/**
	 * Parse a renames file (*renamesFile*).
	 *
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException].
	 * @throws RenamesFileParserException
	 *   If a semantic rule is violated.
	 */
	@Throws(IOException::class, RenamesFileParserException::class)
	private fun parseRenamesFile()
	{
		var token = scan()
		while (token.tokenType == TokenType.PATH)
		{
			if (token.lexeme.isEmpty())
			{
				throw RenamesFileParserException(
					"module path must not be empty")
			}
			parseRenameRule(token.lexeme)
			token = scan()
		}
		if (token.tokenType != TokenType.EOF)
		{
			throw RenamesFileParserException(
				"expected end of file but found (${token.lexeme})")
		}
	}

	/**
	 * Parse the source text and answer a [module][ModuleNameResolver] with the
	 * appropriate renaming rules.
	 *
	 * @return
	 *   A [module&#32;name&#32;resolver][ModuleNameResolver].
	 * @throws RenamesFileParserException
	 *   If the parse fails for any reason.
	 */
	@Throws(RenamesFileParserException::class)
	fun parse(): ModuleNameResolver
	{
		var theResolver = resolver
		if (theResolver === null)
		{
			theResolver = ModuleNameResolver(roots)
			resolver = theResolver
			try
			{
				parseRenamesFile()
			}
			catch (e: IOException)
			{
				throw RenamesFileParserException(e)
			}

		}
		return theResolver
	}

	companion object
	{
		/**
		 * Build the source text of a renames file from the specified rules.
		 *
		 * @param rules
		 *   An array of rules, where each rule is a 2-element [string][String]
		 *   array whose first element is an Avail [module][ModuleDescriptor]
		 *   path and whose second element is a logical file path.
		 * @return
		 *   The source text of a renames file that declares the specified
		 *   rules.
		 */
		@Suppress("unused")
		@ThreadSafe
		fun renamesFileFromRules(rules: Array<Array<String>>): String
		{
			val builder = StringBuilder(100)
			builder.append("/* Generated by ")
			builder.append(RenamesFileParser::class.java.canonicalName)
			builder.append(".renamesFileFromRules() */\n")
			for (rule in rules)
			{
				builder.append('"')
				builder.append(rule[0])
				builder.append("\" → \"")
				builder.append(rule[1])
				builder.append("\"\n")
			}
			return builder.toString()
		}

		/**
		 * Answer a [token][Token] whose lexeme began with a right arrow (→).
		 *
		 * @return
		 *   A [token][Token].
		 */
		internal fun scanRightArrow() = Token(TokenType.ARROW, "→")

		/**
		 * Consume whitespace.
		 *
		 * @return
		 *   `null`.
		 */
		internal fun scanWhitespace(): Token? = null

		/**
		 * Answer a [token][Token] whose lexeme is the specified character of
		 * unknown significance.
		 *
		 * @param unknownChar
		 *   A character.
		 * @return
		 *   A [token][Token].
		 */
		internal fun scanUnknown(unknownChar: Int) =
			Token(
				TokenType.UNKNOWN,
				String(Character.toChars(unknownChar)))

		/**
		 * A map from Unicode code points to the ordinals of the
		 * [ScannerAction]s responsible for scanning constructs that begin with
		 * them.
		 */
		private val scannerTable = ByteArray(128)

		/*
		 * Initialize the scanner table.
		 */
		init
		{
			for (i in scannerTable.indices)
			{
				val c = i.toChar()
				val action = when
				{
					c == '"' -> ScannerAction.DOUBLE_QUOTE
					c == '/' -> ScannerAction.SLASH
					Character.isSpaceChar(c) || Character.isWhitespace(c) ->
						ScannerAction.WHITESPACE
					else -> ScannerAction.UNKNOWN
				}
				scannerTable[i] = action.ordinal.toByte()
			}
		}

		/**
		 * Lookup the [ScannerAction] for the specified code point.
		 *
		 * @param c
		 *   A code point.
		 * @return
		 *   The appropriate `ScannerAction`.
		 */
		private fun actionFor(c: Int) =
			when
			{
				c == '→'.code -> ScannerAction.RIGHT_ARROW
				c >= scannerTable.size -> ScannerAction.UNKNOWN
				else -> ScannerAction.all[scannerTable[c].toInt()]
			}
	}
}
