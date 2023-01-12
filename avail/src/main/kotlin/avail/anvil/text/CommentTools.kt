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
 * The String line prefix, first non-whitespace characters of the first line of
 * the file or the first non-whitespace that represents the start of a line
 * comment.
 *
 * @author Richard Arriaga
 *
 * @property prefix
 *   The string that represents the prefix characters to a line comment.
 */
open class LineComment constructor(private val prefix: String)
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
	 * Uncomment the provided text.
	 *
	 * @param targetText
	 *   The text to uncomment.
	 */
	private fun uncomment (targetText: String): String =
		targetText.split("\n")
			.map { it.replaceFirst(prefix, "") }
			.joinToString("\n") { it }

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
		targetText.split("\n").joinToString("\n", "prefix") { it }

	/**
	 * Toggle a line comment for the provided text. If each line starts with a
	 * line comment prefix, [uncomment], else comment.
	 *
	 * @param targetText
	 *   The text to toggle comment for.
	 */
	fun toggleComment (targetText: String, atMinTab: Boolean = false): String
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
open class BlockComment (private val prefix: String, private val suffix: String)
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

	/**
	 * Toggle a line comment for the provided text. If each line starts with a
	 * line comment prefix, [uncomment], else comment.
	 *
	 * @param targetText
	 *   The text to toggle comment for.
	 */
	fun toggleComment (targetText: String): String
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
