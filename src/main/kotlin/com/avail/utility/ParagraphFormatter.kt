/*
 * ParagraphFormatter.kt
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
package com.avail.utility

/**
 * A ParagraphFormatter object holds a particular paragraph style in the form of
 * window width, margin, and indentation settings. It is then used to apply
 * those whitespace and word-wrap settings to a String.
 *
 * This is intended to be the final format procedure applied to a String before
 * it is output, and all adjacent, non-newline character whitespace is condensed
 * to a single space.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @property windowWidth
 *   The width of the output window.
 * @property leftMargin
 *   The number of spaces in the left margin.
 * @property rightMargin
 *   The number of spaces in the right margin
 * @property firstIndent
 *   The number of spaces between the left margin and the initial line in a
 *   paragraph.
 * @property restIndent
 *   The number of spaces between the left margin and non-initial lines in a
 *   paragraph.
 *
 * @constructor
 * Construct a new [ParagraphFormatter] that formats strings to wrap within the
 * supplied window width, if possible. All text is padded within the specified
 * margins.
 *
 * @param windowWidth
 *   The number of characters that can fit horizontally in the window.
 * @param leftMargin
 *   The number of spaces that all text should be, minimum,  from the left side
 *   of the screen.
 * @param rightMargin
 *   The number of spaces that all text should be, minimum, from the right side
 *   of the screen.
 */
class ParagraphFormatter @JvmOverloads constructor(
	val windowWidth: Int = 80,
	val leftMargin: Int = 0,
	val rightMargin: Int = 0,
	val firstIndent: Int = 0,
	val restIndent: Int = 0)
{
	/** The left margin, as a string of spaces.  */
	private val margin: String

	/** The paragraph-initial indent, as a string of spaces.  */
	private val fIndent: String

	/** The non-initial indent of a paragraph, as a string of spaces  */
	private val rIndent: String

	/**
	 * Answer the supplied string reformatted with margins, indentation, and
	 * words wrapped within those constraints, if possible. Non-newline
	 * whitespace is collapsed into a single space.
	 *
	 * @param str
	 *   The supplied string.
	 * @return
	 *   The formatted string.
	 */
	fun format(str: String): String
	{
		var input = str

		// Replace all line separators with the system standard.
		val nwln = System.getProperty("line.separator")
		input = replaceSeparators(input, nwln)

		// Replace any remaining whitespace characters or sequences with a
		// single space.
		input = input.replace("[^\\S\n\r]".toRegex(), " ")
		val lineWidth = windowWidth - leftMargin - rightMargin
		val output = StringBuilder()
		while (input.isNotEmpty())
		{
			// Get the next paragraph.
			var newlineIndex = input.indexOf(nwln)

			// If the next "paragraph" is itself a newline, output it skip the
			// rest of the string manipulation.
			if (newlineIndex == 0)
			{
				output.append(nwln)
				input = input.substring(1)
			}
			else
			{
				// If there are no more newlines in the input, the next
				// paragraph is the remainder of the input.
				if (newlineIndex < 0)
				{
					newlineIndex = input.length
				}
				var paragraph = input.substring(0, newlineIndex)
				input = input.substring(paragraph.length + 1)

				// Calculate the space left for the first line of the paragraph.
				var indent = fIndent
				var adjustedLineWidth = lineWidth - firstIndent

				// If there is data to process in the paragraph,
				while (paragraph.isNotEmpty())
				{
					// Inspect the remaining portion of the paragraph.
					var currentLine = paragraph

					// If it is longer than one line,
					val lastIndex = adjustedLineWidth + 1
					if (lastIndex < currentLine.length)
					{
						// determine where the cutoff should be.
						var cutoff =
							paragraph.substring(0, lastIndex).lastIndexOf(' ')

						// If there was no space, this line is a truncated,
						// single long word. Adjust the cutoff position to the
						// end of that word, even though it is too long.
						if (cutoff < 0)
						{
							cutoff = paragraph.indexOf(' ')
						}
						currentLine = paragraph.substring(0, cutoff)
						paragraph = paragraph.substring(cutoff + 1)
					}
					else
					{
						paragraph = ""
					}
					output.append(margin)
					output.append(indent)
					output.append(currentLine)
					output.append(nwln)

					// If that was the first line in the paragraph, switch to
					// subsequent-line indentation.
					if (indent == fIndent)
					{
						indent = rIndent
						adjustedLineWidth = lineWidth - restIndent
					}
				}
			}
		}
		return output.toString()
	}

	companion object
	{
		/**
		 * Answer the string with all separators replaced by the supplied
		 * separator.
		 *
		 * @param input
		 *   The string to be changed.
		 * @param separator
		 *   The separator to be used.
		 * @return
		 *   The string with all separators replaced.
		 */
		private fun replaceSeparators(input: String, separator: String): String
		{
			var result = input
			result = result.replace("\r\n", separator)
			result = result.replace("\r", separator)
			result = result.replace("\n", separator)
			return result
		}

		/**
		 * Answer a space-filled string with the specified number of spaces.
		 *
		 * @param len
		 *   The number of spaces.
		 * @return
		 *   A string of spaces.
		 */
		private fun padding(len: Int): String
		{
			val padding = StringBuilder(len)
			for (i in 0 until len)
			{
				padding.append(' ')
			}
			return padding.toString()
		}
	}

	init
	{
		margin = padding(leftMargin)
		fIndent = padding(firstIndent)
		rIndent = padding(restIndent)
	}
}
