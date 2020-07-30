/*
 * Strings.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.util.Formatter
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * `Strings` provides various string utilities.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object Strings {
	/**
	 * Produce an escaped variant of the specified [string][String].
	 *
	 * @param s
	 *   An arbitrary string.
	 * @return
	 *   An escaped string that is suitable for use as a literal in Java source.
	 */
	fun escape(s: String): String = buildString {
		append('"')
		var i = 0
		while (i < s.length)
		{
			val codePoint = s.codePointAt(i)
			when (codePoint)
			{
				'\\'.toInt() -> append("\\\\")
				'"'.toInt() -> append("\\\"")
				'\b'.toInt() -> append("\\b")
				'\n'.toInt() -> append("\\n")
				'\r'.toInt() -> append("\\r")
				'\t'.toInt() -> append("\\t")
				else -> appendCodePoint(codePoint)
			}
			i += Character.charCount(codePoint)
		}
		append('"')
	}

	/**
	 * Add line numbers to the given string.  Start the numbering at the
	 * specified value.
	 *
	 * @param source
	 *   The string to add line numbers to.
	 * @param pattern
	 *   The pattern to use on each line.  The first pattern argument is the
	 *   line number (int), and the second is the String containing the line,
	 *   including a terminal '\n'.
	 * @param startingLineNumber
	 *   What to number the first line as.
	 * @return The string containing line numbers.
	 */
	fun addLineNumbers(
		source: String,
		pattern: String,
		startingLineNumber: Int
	): String {
		Formatter().use { formatter ->
			var line = startingLineNumber
			var position = 0
			while (position < source.length) {
				var nextStart = source.indexOf('\n', position)
				nextStart = if (nextStart == -1) source.length
					else nextStart + 1
				formatter.format(
					pattern, line, source.substring(position, nextStart))
				position = nextStart
				line++
			}
			return formatter.toString()
		}
	}

	/** Strings containing a reasonably small number of tabs. */
	private val tabs = Array(10) {
		i -> buildString {
			repeat(i) { append('\t') }
		}
	}

	/**
	 * Answer a String containing the specified number of tabs.
	 *
	 * @param indent The number of tabs.
	 * @return The string.
	 */
	fun tabs(indent: Int): String {
		if (indent < tabs.size) return tabs[indent]
		val builder = StringBuilder(indent)
		for (i in 1..indent) {
			builder.append('\t')
		}
		return builder.toString()
	}

	/**
	 * Append the specified number of tab ('\t') characters to the receiver, a
	 * [StringBuilder].
	 *
	 * @receiver A [StringBuilder].
	 * @param indent The number of tabs to append.
	 */
	@JvmStatic
	fun StringBuilder.tab(
		indent: Int
	) {
		for (i in 1..indent) {
			append('\t')
		}
	}

	/**
	 * Append a newline ('\n' = U+000A) then the specified number of tab ('\t' =
	 * U+0009) characters to the given [StringBuilder].
	 *
	 * @param builder A [StringBuilder].
	 * @param indent The number of tabs to append after the newline.
	 */
	@JvmStatic
	fun newlineTab(
		builder: StringBuilder,
		indent: Int
	) {
		builder.append('\n')
		for (i in 1..indent) {
			builder.append('\t')
		}
	}

	/**
	 * Answer a [String] consisting of [count] repetitions of [string],
	 * concatenated.
	 */
	@JvmStatic
	fun repeated (string: String, count: Int): String
	{
		assert(count >= 0)
		return buildString(string.length * count) {
			repeat(count) {
				append(string)
			}
		}
	}

	/** A regex [Pattern] containing just a line break.  */
	val lineBreakPattern: Pattern = Pattern.compile("\n", Pattern.LITERAL)

	/**
	 * Increase the indentation by the given non-negative amount.  The first
	 * line of the string (prior to the first line break) is not affected.
	 *
	 * @param originalString
	 * The [String] to adjust.
	 * @param increasedIndentation
	 * The number of additional tabs ( 0) to add after each line break.
	 * @return The newly indented string.
	 */
	@JvmStatic
	fun increaseIndentation(
		originalString: String,
		increasedIndentation: Int
	): String {
		assert(increasedIndentation >= 0)
		return if (increasedIndentation == 0) {
			originalString
		} else lineBreakPattern
			.matcher(originalString)
			.replaceAll(
				Matcher.quoteReplacement("\n" + tabs(increasedIndentation)))
	}

	/**
	 * Answer the stringification of the [stack][StackTraceElement] for the
	 * specified [exception][Throwable].
	 *
	 * @param e A [Throwable].
	 * @return The stringification of the stack trace.
	 */
	@JvmStatic
	fun traceFor(e: Throwable): String {
		return try {
			val traceBytes = ByteArrayOutputStream()
			val trace = PrintStream(
				traceBytes, true, StandardCharsets.UTF_8.name())
			e.printStackTrace(trace)
			String(traceBytes.toByteArray(), StandardCharsets.UTF_8)
		} catch (x: UnsupportedEncodingException) {
			assert(false) { "This never happens!" }
			throw RuntimeException(x)
		}
	}
}
