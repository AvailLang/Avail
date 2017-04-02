/**
 * ParagraphFormatter.java
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

package com.avail.utility;

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
 */
public class ParagraphFormatter
{
	/** The width of the output window. */
	private int windowWidth = 80;

	/**
	 * @return The width of the output window.
	 */
	public int windowWidth()
	{
		return windowWidth;
	}

	/** The number of spaces in the left margin. */
	private int leftMargin = 0;

	/**
	 * @return The number of spaces in the left margin.
	 */
	public int leftMargin()
	{
		return leftMargin;
	}

	/** The left margin, as a string of spaces. */
	private final String margin;

	/** The number of spaces in the right margin */
	private int rightMargin = 0;

	/**
	 * @return The number of spaces in the right margin.
	 */
	public int rightMargin()
	{
		return rightMargin;
	}

	/**
	 * The number of spaces between the left margin and the initial line in a
	 * paragraph.
	 */
	private int firstIndent = 0;

	/**
	 * @return The number of spaces between the left margin and the initial line
	 *         in a paragraph.
	 */
	public int firstIndent()
	{
		return firstIndent;
	}

	/** The paragraph-initial indent, as a string of spaces. */
	private final String fIndent;

	/**
	 * The number of spaces between the left margin and non-initial lines in a
	 * paragraph.
	 */
	private int restIndent = 0;

	/**
	 * @return The number of spaces between the left margin and non-initial
	 *         lines in a paragraph.
	 */
	public int restIndent()
	{
		return restIndent;
	}

	/** The non-initial indent of a paragraph, as a string of spaces */
	private final String rIndent;

	/**
	 * Answer the supplied string reformatted with margins, indentation, and
	 * words wrapped within those constraints, if possible. Non-newline
	 * whitespace is collapsed into a single space.
	 *
	 * @param str The supplied string.
	 * @return The formatted string.
	 */
	public String format (final String str)
	{
		String input = str;
		final StringBuilder output = new StringBuilder();

		// Replace all line separators with the system standard.
		final String nwln = System.getProperty("line.separator");
		input = replaceSeparators(input, nwln);

		// Replace any remaining whitespace characters or sequences with a
		// single space.
		input = input.replaceAll("[^\\S\n\r]", " ");

		final int lineWidth = windowWidth - leftMargin - rightMargin;
		int adjustedLineWidth;

		while (!input.isEmpty())
		{
			// Get the next paragraph.
			int newlineIndex = input.indexOf(nwln);

			// If the next "paragraph" is itself a newline, output it skip the
			// rest of the string manipulation.
			if (newlineIndex == 0)
			{
				output.append(nwln);
				input = input.substring(1);
			}
			else
			{
				// If there are no more newlines in the input, the next
				// paragraph is the remainder of the input.
				if (newlineIndex < 0)
				{
					newlineIndex = input.length();
				}

				String paragraph = input.substring(0, newlineIndex);

				input = input.substring(paragraph.length() + 1);

				// Calculate the space left for the first line of the paragraph.
				String indent = fIndent;
				adjustedLineWidth = lineWidth - firstIndent;

				// If there is data to process in the paragraph,
				while (!paragraph.isEmpty())
				{
					// Inspect the remaining portion of the paragraph.
					String currentLine = paragraph;

					// If it is longer than one line,
					final int lastIndex = adjustedLineWidth + 1;
					if (lastIndex < currentLine.length())
					{
						// determine where the cutoff should be.
						int cutoff =
							paragraph.substring(0, lastIndex).lastIndexOf(" ");

						// If there was no space, this line is a truncated,
						// single long word. Adjust the cutoff position to the
						// end of that word, even though it is too long.
						if (cutoff < 0)
						{
							cutoff = paragraph.indexOf(" ");
						}

						currentLine = paragraph.substring(0, cutoff);
						paragraph = paragraph.substring(cutoff + 1);
					}
					// Otherwise, you are done with this paragraph.
					else
					{
						paragraph = "";
					}

					output.append(margin);
					output.append(indent);
					output.append(currentLine);
					output.append(nwln);

					// If that was the first line in the paragraph, switch to
					// subsequent-line indentation.
					if (indent.equals(fIndent))
					{
						indent = rIndent;
						adjustedLineWidth = lineWidth - restIndent;
					}
				}
			}
		}
		return output.toString();
	}

	/**
	 * Answer the string with all separators replaced by the supplied separator.
	 *
	 * @param input The string to be changed.
	 * @param separator The separator to be used.
	 * @return The string with all separators replaced.
	 */
	private String replaceSeparators(final String input, final String separator)
	{
		String result = input;
		result = result.replace("\r\n", separator);
		result = result.replace("\r", separator);
		result = result.replace("\n", separator);
		return result;
	}

	/**
	 * Answer a space-filled string with the specified number of spaces.
	 *
	 * @param len The number of spaces.
	 * @return A string of spaces.
	 */
	private String padding (final int len)
	{
		final StringBuilder padding = new StringBuilder(len);
		for (int i = 0; i < len; i++)
		{
			padding.append(' ');
		}
		return padding.toString();
	}

	/**
	 * Construct a new {@link ParagraphFormatter} that formats strings to wrap
	 * within the supplied window width, if possible. This formatter will use no
	 * margin and no indentation.
	 *
	 * @param windowWidth The width of the output window
	 */
	public ParagraphFormatter (final int windowWidth)
	{
		this(windowWidth, 0, 0, 0, 0);
	}

	/**
	 * Construct a new {@link ParagraphFormatter} that formats strings to wrap
	 * within the supplied window width, if possible. All text is padded within
	 * the specified margins.
	 *
	 * @param windowWidth The number of characters that can fit horizontally in
	 *                    the window.
	 * @param leftMargin The number of spaces that all text should be, minimum,
	 *                   from the left side of the screen.
	 * @param rightMargin The number of spaces that all text should be, minimum,
	 *                    from the right side of the screen.
	 */
	public ParagraphFormatter (
		final int windowWidth,
		final int leftMargin,
		final int rightMargin)
	{
		this(windowWidth, leftMargin, rightMargin, 0, 0);
	}

	/**
	 * Construct a new {@link ParagraphFormatter} that formats strings to wrap
	 * within the supplied window width, if possible. All text is padded within
	 * the specified margins. The first line of each paragraph is indented
	 * independently from subsequent lines within the same paragraph.
	 *
	 * @param windowWidth The number of characters that can fit horizontally in
	 *                    the window.
	 * @param leftMargin The number of spaces that all text should be, minimum,
	 *                   from the left side of the screen.
	 * @param rightMargin The number of spaces that all text should be, minimum,
	 *                    from the right side of the screen.
	 * @param firstIndent The number of spaces that the first line of each
	 *                    paragraph should be from the left margin.
	 * @param restIndent The number of spaces that each subsequent line of a
	 *                   paragraph should be from the left margin.
	 */
	public ParagraphFormatter (
		final int windowWidth,
		final int leftMargin,
		final int rightMargin,
		final int firstIndent,
		final int restIndent)
	{
		this.windowWidth = windowWidth;
		this.leftMargin = leftMargin;
		this.rightMargin = rightMargin;
		this.firstIndent = firstIndent;
		this.restIndent = restIndent;

		this.margin = padding(leftMargin);
		this.fIndent = padding(firstIndent);
		this.rIndent = padding(restIndent);
	}
}
