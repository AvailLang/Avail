/*
 * BracketedStacksToken.kt
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

package com.avail.stacks.tokens

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.exceptions.StacksScannerException
import com.avail.stacks.scanner.StacksBracketScanner
import com.avail.stacks.tags.StacksLinkTag
import com.avail.stacks.tags.StacksSeeTag
import com.avail.utility.json.JSONWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * A stacks token representing a bracketed region in the comment.  This region
 * generally contains some sort of action such as a link.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [BracketedStacksToken].
 *
 * @param string
 *   The string to be tokenized.
 * @param lineNumber
 *   The line number where the token occurs/begins.
 * @param position
 *   The absolute start position of the token.
 * @param startOfTokenLinePosition
 *   The position on the line where the token starts.
 * @param moduleName
 * @throws StacksScannerException
 */
class BracketedStacksToken @Throws(StacksScannerException::class) constructor(
	string: String,
	lineNumber: Int,
	position: Int,
	startOfTokenLinePosition: Int,
	moduleName: String) : RegionStacksToken(
	string,
	lineNumber,
	position,
	startOfTokenLinePosition,
	moduleName,
	'\"',
	'\"')
{
	/**
	 * The tokens that have been parsed so far.
	 */
	internal var subTokens: List<AbstractStacksToken> =
		StacksBracketScanner.scanBracketString(this)

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter): String
	{
		val keyword = StacksKeyword.keywordTable[this.subTokens[0].lexeme]
			?: return ""

		return keyword.toJSON(this, linkingFileMap, hashID, errorLog, jsonWriter)
	}

	/**
	 *
	 * The collection of keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 *
	 * @property lexeme
	 *   String representation of the keyword.
	 *
	 * @constructor
	 * The constructor of th e [StacksKeyword].
	 * @param lexeme
	 *   String representation of the keyword.
	 */
	private enum class StacksKeyword constructor(internal val lexeme: String)
	{
		/**
		 * The code keyword creates a stylized code section.
		 */
		CODE("@code")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				val stringBuilder = StringBuilder()

				stringBuilder
					.append("<code class=")
					.append('"')
					.append("method")
					.append('"')
					.append(">")
				val tokenCount = bracketToken.subTokens.size
				for (i in 1 until tokenCount - 1)
				{
					val tokenToWrite = bracketToken
						.subTokens[i].lexeme.replace("<".toRegex(), "&lt;")

					stringBuilder
						.append(tokenToWrite)
						.append(" ")
				}

				val tokenToWrite = bracketToken
					.subTokens[tokenCount - 1]
					.lexeme.replace("<".toRegex(), "&lt;")
				stringBuilder
					.append(tokenToWrite)
					//.append('<')
					//.append('\\')
					.append("</code>")
				return stringBuilder.toString()
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @field tag section; "
							+ "expected link content immediately "
							+ "following the @field tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken

						return linkBuilderNolink(
							link.lexeme, linkingFileMap, hashID)

					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @field tag section; "
								+ "expected a quoted link immediately "
								+ "following the @field tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}

				return "<code>" + links[0].lexeme + "</code>"
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		GLOBAL("@global")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @global tag section; "
							+ "expected link content immediately "
							+ "following the @global tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken

						if (linkingFileMap.internalLinks!!
								.containsKey(link.lexeme))
						{
							return linkBuilder(link.lexeme, linkingFileMap)
						}
					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @global tag section; "
								+ "expected a quoted link immediately "
								+ "following the @global tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}

				return "<code>" + links[0].lexeme + "</code>"
			}
		},

		/**
		 * The link keyword creates an external web link.
		 */
		LINK("@link")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @link tag section; "
							+ "expected link content immediately "
							+ "following the @link tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken

						return ("<a href="
							+ '"'.toString()
							+ link.toJSON(
							linkingFileMap, hashID, errorLog, jsonWriter)
							+ '"'.toString()
							+ '>'.toString()
							+ link.toJSON(
							linkingFileMap, hashID,
							errorLog, jsonWriter)
							+ "</a>")

					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @link tag section; "
								+ "expected a quoted link immediately "
								+ "following the @link tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}
				try
				{
					val link = links[0] as QuotedStacksToken

					return StacksLinkTag(
						link,
						links.subList(1, links.size))
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter)
				}
				catch (e: ClassCastException)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @link tag section; "
							+ "expected a quoted link immediately "
							+ "following the @link tag.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @method tag section; "
							+ "expected link content immediately "
							+ "following the @method tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken

						if (linkingFileMap.internalLinks!!
								.containsKey(link.lexeme))
						{
							return linkBuilder(link.lexeme, linkingFileMap)
						}
					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @method tag section; "
								+ "expected a quoted link immediately "
								+ "following the @method tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}
				//for links.size() >= 2

				val listSize = links.size
				if (listSize >= 2)
				{
					val linkBuilder = StringBuilder()
					for (i in 1 until listSize - 1)
					{
						linkBuilder
							.append(
								links[i]
									.toJSON(
										linkingFileMap, hashID, errorLog,
										jsonWriter))
							.append(" ")
					}
					linkBuilder
						.append(
							links[listSize - 1]
								.toJSON(
									linkingFileMap, hashID, errorLog,
									jsonWriter))
				}

				val link: QuotedStacksToken

				try
				{
					link = links[0] as QuotedStacksToken
				}
				catch (e: ClassCastException)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @method tag section.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				return if (linkingFileMap.internalLinks!!
						.containsKey(link.lexeme))
				{
					linkBuilder(link.lexeme, linkingFileMap)
				}
				else "<code>" + links[0].lexeme + "</code>"

			}
		},

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @param tag section; "
							+ "expected link content immediately "
							+ "following the @param tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				/*if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);


						return linkBuilderNolink(
							link.lexeme,linkingFileMap, hashID);
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @param tag section; "
							+ "expected a quoted link immediately "
							+ "following the @param tag.</li>\n",
							bracketToken.moduleName,
							bracketToken.lineNumber);

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}*/

				return "<code>" + links[0].lexeme + "</code>"
			}
		},

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @restricts tag section; "
							+ "expected link content immediately "
							+ "following the @restricts tag, but tag was empty."
							+ "</li>\n",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken


						return linkBuilderNolink(
							link.lexeme, linkingFileMap, hashID)
					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @restricts tag section; "
								+ "expected a quoted link immediately "
								+ "following the @restricts tag.</li>\n",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}

				return "<code>" + links[0].lexeme + "</code>"
			}
		},

		/**
		 * The see keyword refers the reader to something else.  Not inherently
		 * linked.
		 */
		SEE("@see")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog,
				jsonWriter: JSONWriter): String
			{

				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @see tag section; "
							+ "expected link content immediately "
							+ "following the @see tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						return StacksLinkTag(
							links[0] as QuotedStacksToken)
							.toJSON(
								linkingFileMap,
								hashID,
								errorLog,
								jsonWriter)
					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @see tag section; "
								+ "expected a quoted link immediately "
								+ "following the @see tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}
				try
				{
					val link = links[0] as RegionStacksToken

					return StacksSeeTag(link)
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter)
				}
				catch (e: ClassCastException)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @link tag section; "
							+ "expected a quoted link immediately "
							+ "following the @link tag.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

			}
		},

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type")
		{
			override fun toJSON(
				bracketToken: BracketedStacksToken,
				linkingFileMap: LinkingFileMap,
				hashID: Int,
				errorLog: StacksErrorLog, jsonWriter: JSONWriter): String
			{
				if (bracketToken.subTokens.size == 1)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @type tag section; "
							+ "expected link content immediately "
							+ "following the @type tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				val links = bracketToken.subTokens
					.subList(1, bracketToken.subTokens.size)

				if (links.size == 1)
				{
					try
					{
						val link = links[0] as QuotedStacksToken

						return if (linkingFileMap.internalLinks!!
								.containsKey(link.lexeme))
						{
							linkBuilder(link.lexeme, linkingFileMap)
						}
						else "<code>" + link.lexeme + "</code>"

					}
					catch (e: ClassCastException)
					{
						val errorMessage = String.format(
							"\n<li>"
								+ "<strong>%s</strong><em> Line #: %d</em>: "
								+ "Malformed @type tag section; "
								+ "expected a quoted link immediately "
								+ "following the @type tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber)

						val errorBuffer = ByteBuffer.wrap(
							errorMessage
								.toByteArray(StandardCharsets.UTF_8))
						errorLog.addLogEntry(errorBuffer, 1)

						return ""
					}

				}

				//for links.size() >= 2

				val listSize = links.size
				if (listSize >= 2)
				{
					val linkBuilder = StringBuilder()
					for (i in 1 until listSize - 1)
					{
						linkBuilder
							.append(
								links[i]
									.toJSON(
										linkingFileMap, hashID, errorLog,
										jsonWriter))
							.append(" ")
					}
					linkBuilder
						.append(
							links[listSize - 1]
								.toJSON(
									linkingFileMap, hashID, errorLog,
									jsonWriter))
				}

				val link: QuotedStacksToken
				try
				{
					link = links[0] as QuotedStacksToken
				}
				catch (e: ClassCastException)
				{
					val errorMessage = String.format(
						"\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @type tag section.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber)

					val errorBuffer = ByteBuffer.wrap(
						errorMessage
							.toByteArray(StandardCharsets.UTF_8))
					errorLog.addLogEntry(errorBuffer, 1)

					return ""
				}

				if (linkingFileMap.internalLinks!!
						.containsKey(link.lexeme))
				{
					return linkBuilder(link.lexeme, linkingFileMap)
				}
				val plainText = StringBuilder()
				val shiftedTokenCount = links.size - 1
				for (i in 1 until shiftedTokenCount)
				{
					plainText.append(links[i].lexeme)
					plainText.append(' ')
				}
				plainText.append(links[shiftedTokenCount].lexeme)
				return plainText.toString()
			}
		};

		/**
		 * Create the appropriate keyword token
		 *
		 * @param bracketToken
		 *   This [BracketedStacksToken]
		 * @param linkingFileMap
		 *   The internal link map
		 * @param hashID
		 *   The hash id for the implementation this belongs to
		 * @param errorLog
		 *   The [StacksErrorLog]
		 * @param jsonWriter TODO
		 * @return
		 */
		internal abstract fun toJSON(
			bracketToken: BracketedStacksToken,
			linkingFileMap: LinkingFileMap,
			hashID: Int,
			errorLog: StacksErrorLog,
			jsonWriter: JSONWriter): String

		companion object
		{
			/** An array of all [StacksKeyword] enumeration values.  */
			private val all = values()

			/**
			 * Answer an array of all [StacksKeyword] enumeration values.
			 *
			 * @return An array of all [StacksKeyword] enum values.  Do not
			 *   modify the array.
			 */
			fun all(): Array<StacksKeyword> =
				all

			/**
			 * A [mapping][Map] from the string lexeme of the keyword to the
			 * [StacksKeywords][Enum]
			 */
			internal val keywordTable: MutableMap<String, StacksKeyword> =
				HashMap()

			// Learn the lexeme's of the keywords.
			init
			{
				for (keyword in all())
				{
					keywordTable[keyword.lexeme] = keyword
				}
			}

			/**
			 * Create html link that can be embedded in JSON
			 * @param aLexeme
			 *   The lexeme to get from the [linkingFileMap][LinkingFileMap].
			 * @param linkingFileMap
			 *   The map containing the links
			 * @return A constructed string of a link.
			 */
			internal fun linkBuilder(
				aLexeme: String, linkingFileMap: LinkingFileMap): String =
				("<a href="
					+ '"'.toString()
					+ linkingFileMap.internalLinks!![aLexeme]
					+ '"'.toString()
					+ '>'.toString()
					+ aLexeme
					+ "</a>")

			/**
			 * Create html link that can be embedded in JSON.
			 *
			 * @param aLexeme
			 *   The lexeme to get from the [linkingFileMap][LinkingFileMap].
			 * @param linkingFileMap
			 *   The map containing the links.
			 * @param hashID
			 * @return A constructed string of a link
			 */
			internal fun linkBuilderNolink(
				aLexeme: String,
				linkingFileMap: LinkingFileMap,
				hashID: Int): String =
					("<a href="
						+ '"'.toString()
						+ "#"
						+ aLexeme + hashID
						+ '"'.toString()
						+ '>'.toString()
						+ aLexeme
						+ "</a>")
		}
	}

	companion object
	{
		/**
		 * Statically create a new [BracketedStacksToken].
		 *
		 * @param source
		 *   The string to be tokenized.
		 * @param lineNumber
		 *   The line number where the token occurs/begins
		 * @param position
		 *   The absolute start position of the token
		 * @param startOfTokenLinePosition
		 *   The position on the line where the token starts.
		 * @param moduleName
		 *   The name of the module the token is in.
		 * @return A new [stacks token][BracketedStacksToken]
		 * @throws StacksScannerException
		 */
		@Throws(StacksScannerException::class)
		fun create(
			source: String,
			lineNumber: Int,
			position: Int,
			startOfTokenLinePosition: Int,
			moduleName: String): BracketedStacksToken =
			BracketedStacksToken(
				source,
				lineNumber,
				position,
				startOfTokenLinePosition,
				moduleName)
	}
}
