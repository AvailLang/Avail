/*
 * CommentTools.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.anvil.text

import java.lang.Integer.min

/**
 * Count the number of tabs that prefix each line (after a `\n` character) in
 * each line in the provided text and answer the lowest tab count amongst the
 * lines.
 *
 * @param text
 *   The text to count tabs from.
 * @return
 *   The lowest number of tabs for any line.
 */
fun minTabCount (text: String): Int
{
	var currentMin = Int.MAX_VALUE
	val split = text.split("\n")
	split.forEach line@{ it ->
		var tabCount = 0
		it.forEach { c ->
			if (c == '\t') tabCount ++
			else return@forEach
			if (tabCount == currentMin) return@forEach
		}
		currentMin = min(currentMin, tabCount)
	}
	return currentMin
}

/**
 * Therepresentation of comment syntax that can be used to comment text.
 *
 * @author Richard Arriaga
 */
interface CommentSyntax
{
	/**
	 * Toggle a comment for the provided text.
	 *
	 * @param targetText
	 *   The text to toggle comment for.
	 * @param commentFullLine
	 *   Must comment the full lines of text.
	 * @param atMinTab
	 *   `true` comments out the provided text placing the initial comment
	 *   syntax on each line at the character position just after the
	 *   [minTabCount] position for the target text.
	 */
	fun toggleComment (
		targetText: String,
		commentFullLine: Boolean,
		atMinTab: Boolean
	): String
}

/**
 * The String line prefix, first non-whitespace characters of the first line of
 * the file or the first non-whitespace that represents the start of a line
 * comment.
 *
 * @author Richard Arriaga
 *
 * @property prefix
 *   The string that represents the prefix characters to a line comment.
 */
open class LineComment constructor(private val prefix: String): CommentSyntax
{
	/**
	 * Comments out the provided text placing the [prefix] on each line at the
	 * character position just after the [minTabCount] position for the target
	 * text.
	 *
	 * @param targetText
	 *   The text to transform commented text.
	 * @return
	 *   The commented text.
	 */
	private fun commentAtMinTab (targetText: String): String
	{
		val minTabCount = minTabCount(targetText)
		return targetText.split("\n")
			.map {
				it.substring(0, minTabCount) +
					prefix + it.substring(minTabCount)
			}
			.joinToString("\n") { it }
	}

	/**
	 * Comments out the provided text placing the [prefix] at the start of each
	 * line.
	 *
	 * @param targetText
	 *   The text to transform commented text.
	 * @return
	 *   The commented text.
	 */
	private fun commentAtLineStart (targetText: String): String =
		targetText.split("\n").joinToString("\n$prefix", prefix) { it }

	/**
	 * Uncomment the provided text.
	 *
	 * @param targetText
	 *   The text to uncomment.
	 */
	private fun uncomment (targetText: String): String =
		targetText.split("\n")
			.map { it.replaceFirst(prefix, "") }
			.joinToString("\n") { it }

	override fun toggleComment (
		targetText: String,
		commentFullLine: Boolean,
		atMinTab: Boolean): String
	{
		targetText.split("\n")
			.forEach {
				if (!targetText.trim().startsWith(prefix))
					return if (atMinTab) commentAtMinTab(targetText)
						else commentAtLineStart(targetText)
			}
		return uncomment(targetText)
	}
}

/**
 * The [LineComment] that uses `//` to indicate a line comment.
 *
 * @author Richard Arriaga
 */
object DoubleForwardSlashLineComment: LineComment("//")

/**
 * The [LineComment] that uses `#` to indicate a line comment.
 *
 * @author Richard Arriaga
 */
object HashTagLineComment: LineComment("#")

/**
 * The syntax that represents a multi-line comment (block comment).
 *
 * @author Richard Arriaga
 *
 * @property prefix
 *   The string that represents the prefix characters to the entire block
 *   comment. These are inserted right before the first character regardless of
 *   whitespace.
 * @property suffix
 *   The string that represents suffix characters add after the last characters
 *   in the text.
 */
open class BlockComment constructor(
	private val prefix: String,
	private val suffix: String
): CommentSyntax
{
	/**
	 * Comments out the provided text.
	 *
	 * @param targetText
	 *   The text to transform commented text.
	 * @return
	 *   The commented text.
	 */
	fun comment (targetText: String): String =
		"$prefix$targetText$suffix"

	/**
	 * Uncomment the provided text.
	 *
	 * @param targetText
	 *   The text to uncomment.
	 */
	private fun uncomment (targetText: String): String =
		targetText.replaceFirst(prefix, "").replaceFirst(suffix, "")

	override fun toggleComment (
		targetText: String,
		commentFullLine: Boolean,
		atMinTab: Boolean
	): String
	{
		val prefixStart = targetText.indexOf(prefix)
		if (prefixStart < 0 || targetText.indexOf(suffix) <= prefixStart)
			return comment(targetText)
		return uncomment(targetText)
	}
}

/**
 * The [BlockComment] with a prefix of forward slash (`/`) followed by an
 * asterisk (`*`) ("\u002F\u002A") and a suffix of an asterisk (`*`) followed by
 * a forward slash ("\u002A\u002F").
 *
 * @author Richard Arriaga
 */
object ForwardSlashAsteriskBlockComment: BlockComment("/*", "*/")

/**
 * The HTML-style [BlockComment] with prefix, `<!--`, and suffix, `-->`.
 *
 * @author Richard Arriaga
 */
object HTMLBlockComment: BlockComment("<!--", "-->")

/**
 * The enumeration of file extensions and their associated comment syntax.
 *
 * @author Richard Arriaga
 *
 * @property extension
 *   The file extension that represents the file type that this
 *   [FileExtensionCommentSyntax] is associated with.
 * @property lineComment
 *   The [LineComment] for the associated [extension] or `null` if
 *   [LineComment]s are not supported.
 * @property blockComment
 *   The [BlockComment] for the associated [extension] or `null` if
 *   [BlockComment]s are not supported.
 */
enum class FileExtensionCommentSyntax constructor(
	val extension: String,
	val lineComment: LineComment?,
	val blockComment: BlockComment?)
{
	/** The Avail module (`.avail`) default comment syntax. */
	AVAIL(
		".avail",
		DoubleForwardSlashLineComment,
		ForwardSlashAsteriskBlockComment),

	/** Comment support for bash files. */
	BASH(".sh", HashTagLineComment, null),

	/** Comment support for Windows batch files. */
	BATCH(".bat", HashTagLineComment, null),

	/** Comment support for HTML files. */
	HTML(".html", null, HTMLBlockComment),

	/** Comment support for HTML files alternate file extension, `htm`. */
	HTM(".htm", null, HTMLBlockComment),

	/** JSON does not support comments. */
	JSON(".json", null, null),

	/** Markdown does not support comments. */
	MARKDOWN(".md", null, null),

	/** Comment support for XML files. */
	XML(".xml", null, HTMLBlockComment),

	/** The system default for commenting unrecognized file [extension]s. */
	DEFAULT(
		"",
		DoubleForwardSlashLineComment,
		ForwardSlashAsteriskBlockComment);

	companion object
	{
		/**
		 * Answer the [FileExtensionCommentSyntax] for the provided file name.
		 *
		 * @param filename
		 *   The name of the file to determine the comment strategy for.
		 * @return
		 *   The associated [FileExtensionCommentSyntax] if defined or
		 *   [FileExtensionCommentSyntax.DEFAULT] if no express definition
		 *   located.
		 */
		operator fun get (filename: String): FileExtensionCommentSyntax =
			values().firstOrNull {
				filename.lowercase().endsWith(it.extension)
			} ?: DEFAULT
	}
}
