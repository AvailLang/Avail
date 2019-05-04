/*
 * Strings.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code Strings} provides various string utilities.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class Strings
{
	/**
	 * Forbid instantiation.
	 */
	private Strings ()
	{
		// No implementation required.
	}

	/**
	 * Produce an escaped variant of the specified {@linkplain String string}.
	 *
	 * @param s
	 *        An arbitrary string.
	 * @return An escaped string that is suitable for use as a literal in Java
	 *         source.
	 */
	public static String escape (final String s)
	{
		final StringBuilder builder = new StringBuilder(s.length() << 1);
		builder.append('"');
		int codePoint;
		for (int i = 0; i < s.length(); i += Character.charCount(codePoint))
		{
			codePoint = s.codePointAt(i);
			switch (codePoint)
			{
				case '\\':
					builder.append("\\\\");
					break;
				case '"':
					builder.append("\\\"");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					builder.appendCodePoint(codePoint);
					break;
			}
		}
		builder.append('"');
		return builder.toString();
	}

	/**
	 * Add line numbers to the given string.  Start the numbering at the
	 * specified value.
	 *
	 * @param source
	 *        The string to add line numbers to.
	 * @param pattern
	 *        The pattern to use on each line.  The first pattern argument is
	 *        the line number (int), and the second is the String containing
	 *        the line, including a terminal '\n'.
	 * @param startingLineNumber
	 *        What to number the first line as.
	 * @return The string containing line numbers.
	 */
	public static String addLineNumbers (
		final String source,
		final String pattern,
		final int startingLineNumber)
	{
		try (final Formatter formatter = new Formatter())
		{
			int line = startingLineNumber;
			int position = 0;
			while (position < source.length())
			{
				int nextStart = source.indexOf('\n', position);
				nextStart = nextStart == -1 ? source.length() : nextStart + 1;
				formatter.format(
					pattern, line, source.substring(position, nextStart));
				position = nextStart;
				line++;
			}
			return formatter.toString();
		}
	}

	/** Strings containing a reasonably small number of tabs. */
	private static final String[] tabs = new String[10];

	static
	{
		final StringBuilder str = new StringBuilder();
		for (int i = 0; i < tabs.length; i++)
		{
			tabs[i] = str.toString();
			str.append('\t');
		}
	}

	/**
	 * Answer a String containing the specified number of tabs.
	 *
	 * @param indent The number of tabs.
	 * @return The string.
	 */
	public static String tabs (final int indent)
	{
		if (indent < tabs.length)
		{
			return tabs[indent];
		}
		final StringBuilder builder = new StringBuilder(indent);
		for (int i = 1; i <= indent; i++)
		{
			builder.append('\t');
		}
		return builder.toString();
	}

	/**
	 * Append the specified number of tab ('\t') characters to the given {@link
	 * StringBuilder}.
	 *
	 * @param builder A {@link StringBuilder}.
	 * @param indent The number of tabs to append.
	 */
	public static void tab (
		final StringBuilder builder,
		final int indent)
	{
		for (int i = 1; i <= indent; i++)
		{
			builder.append('\t');
		}
	}

	/**
	 * Append a newline ('\n' = U+000A) then the specified number of tab ('\t' =
	 * U+0009) characters to the given {@link StringBuilder}.
	 *
	 * @param builder A {@link StringBuilder}.
	 * @param indent The number of tabs to append after the newline.
	 */
	public static void newlineTab (
		final StringBuilder builder,
		final int indent)
	{
		builder.append('\n');
		for (int i = 1; i <= indent; i++)
		{
			builder.append('\t');
		}
	}

	/**
	 * Append each provided string to the {@link StringBuilder}.
	 *
	 * @param builder The {@link StringBuilder}.
	 * @param strings The vararg array of strings to append.
	 */
	public static void appendAll (
		final StringBuilder builder,
		final String... strings)
	{
		for (final String string : strings)
		{
			builder.append(string);
		}
	}

	/** A regex {@link Pattern} containing just a line break. */
	public static final Pattern lineBreakPattern =
		Pattern.compile("\n", Pattern.LITERAL);

	/**
	 * Increase the indentation by the given non-negative amount.  The first
	 * line of the string (prior to the first line break) is not affected.
	 *
	 * @param originalString
	 *        The {@link String} to adjust.
	 * @param increasedIndentation
	 *        The number of additional tabs (&ge; 0) to add after each line break.
	 * @return The newly indented string.
	 */
	public static String increaseIndentation (
		final String originalString,
		final int increasedIndentation)
	{
		assert increasedIndentation >= 0;
		if (increasedIndentation == 0)
		{
			return originalString;
		}
		return lineBreakPattern
			.matcher(originalString)
			.replaceAll(
				Matcher.quoteReplacement("\n" + tabs(increasedIndentation)));
	}

	/**
	 * Answer the stringification of the {@linkplain StackTraceElement stack
	 * trace} for the specified {@linkplain Throwable exception}.
	 *
	 * @param e
	 *        A {@link Throwable}.
	 * @return The stringification of the stack trace.
	 */
	public static String traceFor (final Throwable e)
	{
		try
		{
			final ByteArrayOutputStream traceBytes =
				new ByteArrayOutputStream();
			final PrintStream trace = new PrintStream(
				traceBytes, true, StandardCharsets.UTF_8.name());
			e.printStackTrace(trace);
			return new String(traceBytes.toByteArray(), StandardCharsets.UTF_8);
		}
		catch (final UnsupportedEncodingException x)
		{
			assert false : "This never happens!";
			throw new RuntimeException(x);
		}
	}
}
