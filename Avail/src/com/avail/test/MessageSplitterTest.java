/**
 * MessageSplitterTest.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.test;

import static org.junit.Assert.*;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.ParsingConversionRule.*;
import java.util.*;
import org.junit.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.exceptions.MalformedMessageException;

/**
 * Test the {@link MessageSplitter}.  It splits method names into a sequence of
 * tokens to expect and underscores, but it also implements the repeated
 * argument mechanism by producing a sequence of mini-instructions to say how
 * to do the parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class MessageSplitterTest
{
	/**
	 * This is a helper class for building test cases.
	 */
	static class Case
	{
		/** The {@link String} which is the method name being tested. */
		final String message;

		/**
		 * The sequence of {@link String}s into which the method name should
		 * decompose.
		 */
		final String[] tokens;

		/**
		 * The sequence of int-encoded {@link ParsingOperation}s that can parse
		 * sends of a method with this name.
		 */
		final List<Integer> instructions;

		/**
		 * Construct a new {@link Case}.
		 *
		 * @param message The method name to split.
		 * @param tokens The expected substrings comprising the method name.
		 * @param instructions The expected encoded parsing instructions.
		 */
		public Case (
			final String message,
			final String[] tokens,
			final Integer[] instructions)
		{
			this.message = message;
			this.tokens = tokens;
			this.instructions = Arrays.asList(instructions);
		}
	}

	/**
	 * Construct a simple test case.  The given message (a {@link String}) is
	 * expected to be split into the given array of tokens (also strings) and
	 * produce the given sequence of instructions (numerical encodings of {@link
	 * ParsingOperation}s).
	 *
	 * @param message The message to split.
	 * @param tokens The separated tokens from the message.
	 * @param instructions The parsing operations to parse this message.
	 * @return An array of Strings starting with the message, then all the
	 *         tokens, then a print representation of the numeric instructions
	 *         converted to a {@link List}.
	 */
	static Case C(
		final String message,
		final String[] tokens,
		final Integer[] instructions)
	{
		return new Case(message, tokens, instructions);
	}

	/**
	 * Create a simple array.  This greatly reduces the syntactic noise of the
	 * test cases.  It's "A(...)" versus "new Integer[] {...}".
	 *
	 * @param x The varargs array passed in.
	 * @return The same array.
	 */
	@SafeVarargs
	static <X> X[] A(final X... x)
	{
		return x;
	}

	/** Test cases. */
	private static Case[] splitCases =
	{
		C("Foo",
			A("Foo"),
			A(
				PARSE_PART.encoding(1))),
		C("Print_",
			A("Print", "_"),
			A(
				PARSE_PART.encoding(1),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding())),
		C("_+_",
			A("_", "+", "_"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(2),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding())),
		C("_+_*_",
			A("_", "+", "_", "*", "_"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(2),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(4),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(3),
				APPEND_ARGUMENT.encoding())),
		C("_;",
			A("_", ";"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(2))),
		C("__",
			A("_", "_"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding())),
		/* Backquotes. */
		C("`__",
			A("`", "_", "_"),
			A(
				PARSE_PART.encoding(2),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding())),
		C("_`«_",
			A("_", "`", "«", "_"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(3),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding())),
		C("_``_",
			A("_", "`", "`", "_"),
			A(
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(3),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding())),
		C("`#`?`~",
			A("`", "#", "`", "?", "`", "~"),
			A(
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(4),
				PARSE_PART.encoding(6))),

		C("`|`|_`|`|",
			A("`", "|", "`", "|", "_", "`", "|", "`", "|"),
			A(
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(4),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(7),
				PARSE_PART.encoding(9))),
		/* Repeated groups. */
		C("«_;»",
			A("«", "_", ";", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(3),
				BRANCH.encoding(11),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x»",
			A("«", "x", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				BRANCH.encoding(10),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x y»",
			A("«", "x", "y", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(13),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(3),
				BRANCH.encoding(11),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x_y»",
			A("«", "x", "_", "y", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(13),
				PARSE_PART.encoding(2),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(4),
				BRANCH.encoding(12),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«_:_»",
			A("«", "_", ":", "_", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(18),
				NEW_LIST.encoding(),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(3),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(16),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«»",
			A("«", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				BRANCH.encoding(9),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«»«»",
			A("«", "»", "«", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				BRANCH.encoding(9),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(23),
				NEW_LIST.encoding(),
				BRANCH.encoding(21),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(16),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«_»",
			A("«", "_", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(10),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		/* Repeated groups with double dagger. */
		C("«_‡,»",
			A("«", "_", "‡", ",", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(11),
				PARSE_PART.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«‡»",
			A("«", "‡", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				BRANCH.encoding(9),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("new_«with«_=_‡,»»",
			A("new", "_", "«", "with", "«", "_", "=", "_", "‡", ",", "»", "»"),
			A(
				PARSE_PART.encoding(1),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding(),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(33),
				PARSE_PART.encoding(4),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(27),
				NEW_LIST.encoding(),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(2),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(7),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(3),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(25),
				PARSE_PART.encoding(10),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(12),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(32),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(8),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		/* Counting groups. */
		C("«x»#",
			A("«", "x", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(10),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«x y»#",
			A("«", "x", "y", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(3),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(11),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«»#",
			A("«", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(10),
				NEW_LIST.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(9),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«»#«»#",
			A("«", "»", "#", "«", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(10),
				NEW_LIST.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(9),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding(),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(22),
				NEW_LIST.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(21),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(16),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		/* Counting groups with double dagger. */
		C("«‡»#",
			A("«", "‡", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(10),
				NEW_LIST.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(9),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«fish‡»#",
			A("«", "fish", "‡", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(10),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«‡face»#",
			A("«", "‡", "face", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(11),
				NEW_LIST.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(10),
				PARSE_PART.encoding(3),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«fish‡face»#",
			A("«", "fish", "‡", "face", "»", "#"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(11),
				PARSE_PART.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("««fish‡face»#»",
			A("«", "«", "fish", "‡", "face", "»", "#", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(22),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(15),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(3),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(14),
				PARSE_PART.encoding(5),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(7),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(21),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		/* Optional groups. */
		C("«x»?",
			A("«", "x", "»", "?"),
			A(
				BRANCH.encoding(8),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(2),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PUSH_TRUE.encoding(),
				JUMP.encoding(9),
				PUSH_FALSE.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x y»?",
			A("«", "x", "y", "»", "?"),
			A(
				BRANCH.encoding(9),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(3),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PUSH_TRUE.encoding(),
				JUMP.encoding(10),
				PUSH_FALSE.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«»?",
			A("«", "»", "?"),
			A(
				BRANCH.encoding(7),
				SAVE_PARSE_POSITION.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PUSH_TRUE.encoding(),
				JUMP.encoding(8),
				PUSH_FALSE.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("««bagel»#«friend»?»",
			A("«", "«", "bagel", "»", "#", "«", "friend", "»", "?", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(33),
				NEW_LIST.encoding(),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(15),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(3),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(14),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(8),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(25),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(7),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PUSH_TRUE.encoding(),
				JUMP.encoding(26),
				PUSH_FALSE.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(31),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		/* Completely optional groups. */
		C("very⁇good",
			A("very", "⁇", "good"),
			A(
				BRANCH.encoding(6),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(1),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(3))),
		C("«very extremely»⁇good",
			A("«", "very", "extremely", "»", "⁇", "good"),
			A(
				BRANCH.encoding(7),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(3),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PARSE_PART.encoding(6))),
		/* Case insensitive. */
		C("fnord~",
			A("fnord", "~"),
			A(
				PARSE_PART_CASE_INSENSITIVELY.encoding(1))),
		C("the~_",
			A("the", "~", "_"),
			A(
				PARSE_PART_CASE_INSENSITIVELY.encoding(1),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(1),
				APPEND_ARGUMENT.encoding())),
		C("«x~»",
			A("«", "x", "~", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				BRANCH.encoding(10),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x»~",
			A("«", "x", "»", "~"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				BRANCH.encoding(10),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x~y»",
			A("«", "x", "~", "y", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(13),
				NEW_LIST.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				PARSE_PART.encoding(4),
				BRANCH.encoding(11),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x y»~",
			A("«", "x", "y", "»", "~"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(13),
				NEW_LIST.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
				BRANCH.encoding(11),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding())),
		C("«x y»#~",
			A("«", "x", "y", "»", "#", "~"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(12),
				NEW_LIST.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(11),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(4),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				CONVERT.encoding(LIST_TO_SIZE.number()),
				APPEND_ARGUMENT.encoding())),
		C("«x y»?~",
			A("«", "x", "y", "»", "?", "~"),
			A(
				BRANCH.encoding(9),
				SAVE_PARSE_POSITION.encoding(),
				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				PUSH_TRUE.encoding(),
				JUMP.encoding(10),
				PUSH_FALSE.encoding(),
				APPEND_ARGUMENT.encoding())),
		/* Alternation. */
		C("hello|greetings",
			A("hello", "|", "greetings"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				BRANCH.encoding(5),
				PARSE_PART.encoding(1),
				JUMP.encoding(6),
				PARSE_PART.encoding(3),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding())),
		C("a|b|c|d|e|f|g",
			A("a", "|", "b", "|", "c", "|", "d", "|", "e", "|", "f", "|", "g"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				BRANCH.encoding(5),
				PARSE_PART.encoding(1),
				JUMP.encoding(21),
				BRANCH.encoding(8),
				PARSE_PART.encoding(3),
				JUMP.encoding(21),
				BRANCH.encoding(11),
				PARSE_PART.encoding(5),
				JUMP.encoding(21),
				BRANCH.encoding(14),
				PARSE_PART.encoding(7),
				JUMP.encoding(21),
				BRANCH.encoding(17),
				PARSE_PART.encoding(9),
				JUMP.encoding(21),
				BRANCH.encoding(20),
				PARSE_PART.encoding(11),
				JUMP.encoding(21),
				PARSE_PART.encoding(13),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding())),
		C("«fruit bats»|sloths|carp|«breakfast cereals»",
			A("«", "fruit", "bats", "»", "|", "sloths", "|", "carp", "|", "«", "breakfast", "cereals", "»"),
			A(
				SAVE_PARSE_POSITION.encoding(),
				BRANCH.encoding(17),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(15),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(2),
				PARSE_PART.encoding(3),
				BRANCH.encoding(13),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(6),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				JUMP.encoding(36),
				BRANCH.encoding(20),
				PARSE_PART.encoding(6),
				JUMP.encoding(36),
				BRANCH.encoding(23),
				PARSE_PART.encoding(8),
				JUMP.encoding(36),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(35),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(11),
				PARSE_PART.encoding(12),
				BRANCH.encoding(33),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(26),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding())),
		C("«x|y»!",
			A("«", "x", "|", "y", "»", "!"),
			A(
				BRANCH.encoding(5),
				PARSE_PART.encoding(2),
				PUSH_INTEGER_LITERAL.encoding(1),
				JUMP.encoding(7),
				PARSE_PART.encoding(4),
				PUSH_INTEGER_LITERAL.encoding(2),
				APPEND_ARGUMENT.encoding())),
		C("[««…:_†§‡,»`|»«Primitive_«(…:_†)§»;»«$…:_†§»«_§»«_»]«:_†»«^«_†‡,»»",
			A("[",
				"«", "«", "…", ":", "_", "†", "§", "‡", ",", "»", "`", "|", "»",
				"«", "Primitive", "_", "«",
					"(", "…", ":", "_", "†", ")", "§", "»", ";", "»",
				"«", "$", "…", ":", "_", "†", "§", "»",
				"«", "_", "§", "»",
				"«", "_", "»",
				"]",
				"«", ":", "_", "†", "»",
				"«", "^", "«", "_", "†", "‡", ",", "»", "»"),
			A(
			// open square bracket:
				PARSE_PART.encoding(1), 	// "[" to start block
			// argument declarations:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(32),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(25),
				NEW_LIST.encoding(),
				PARSE_RAW_KEYWORD_TOKEN.encoding(),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(5), 	// ":" in arg declaration
				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
				CHECK_ARGUMENT.encoding(2),
				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
				APPEND_ARGUMENT.encoding(),
				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(3),
				RUN_PREFIX_FUNCTION.encoding(1),
				BRANCH.encoding(23),
				PARSE_PART.encoding(10),	// "," between arg declarations
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(8),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(13),	// "|" after non-empty args list
				BRANCH.encoding(31),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(5),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Optional primitive declaration:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(72),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(16),	// "Primitive"
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(3),
				APPEND_ARGUMENT.encoding(),
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(63),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(19),	// "(" before prim failure var
				PARSE_RAW_KEYWORD_TOKEN.encoding(),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(21),	// ":" in prim failure var decl
				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
				CHECK_ARGUMENT.encoding(5),
				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(24),	// ")" after prim failure var
				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(4),
				RUN_PREFIX_FUNCTION.encoding(2),
				BRANCH.encoding(61),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(45),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(27),	// ";" after primitive declaration
				BRANCH.encoding(70),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(37),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Optional label declaration:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(94),
				NEW_LIST.encoding(),
				PARSE_PART.encoding(30),	// "$" in label declaration
				PARSE_RAW_KEYWORD_TOKEN.encoding(),
				APPEND_ARGUMENT.encoding(),
				PARSE_PART.encoding(32),	// ":" in label declaration
				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
				CHECK_ARGUMENT.encoding(7),
				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
				APPEND_ARGUMENT.encoding(),
				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(2),
				RUN_PREFIX_FUNCTION.encoding(3),
				BRANCH.encoding(92),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(77),
				APPEND_ARGUMENT.encoding(),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Statements and declarations:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(108),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(8),
				APPEND_ARGUMENT.encoding(),
				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(1),
				RUN_PREFIX_FUNCTION.encoding(4),
				BRANCH.encoding(107),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(99),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Final expression:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(120),
				PARSE_ARGUMENT.encoding(),
				CHECK_ARGUMENT.encoding(9),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(119),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(113),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Close square bracket
				PARSE_PART.encoding(44),	// "]" after all statements
			// Optional return type declaration
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(135),
				PARSE_PART.encoding(46),	// ":" for return type declaration
				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
				CHECK_ARGUMENT.encoding(10),
				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(134),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(126),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
			// Optional checked exceptions declaration:
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(159),
				PARSE_PART.encoding(51),	// "^" for exceptions declaration
				SAVE_PARSE_POSITION.encoding(),
				NEW_LIST.encoding(),
				BRANCH.encoding(153),
				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
				CHECK_ARGUMENT.encoding(11),
				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(152),
				PARSE_PART.encoding(56),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(144),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding(),
				BRANCH.encoding(158),
				ENSURE_PARSE_PROGRESS.encoding(),
				JUMP.encoding(140),
				ENSURE_PARSE_PROGRESS.encoding(),
				DISCARD_SAVED_PARSE_POSITION.encoding(),
				APPEND_ARGUMENT.encoding()))
	};

	/**
	 * Describe a sequence of instructions, one per line, and answer the
	 * resulting string.
	 *
	 * @param instructions A sequence of integer-encoded parse instructions.
	 * @return The descriptive string.
	 */
	private String dumpInstructions (final List<Integer> instructions)
	{
		final StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (final int instructionEncoding : instructions)
		{
			if (!first)
			{
				builder.append(",\n");
			}
			builder.append('\t');
			final ParsingOperation operation =
				ParsingOperation.decode(instructionEncoding);
			builder.append(operation.name());
			if (operation.ordinal() >= ParsingOperation.distinctInstructions)
			{
				builder.append('(');
				builder.append(operation.operand(instructionEncoding));
				builder.append(')');
			}
			first = false;
		}
		return builder.toString();
	}

	/**
	 * Test: Split the test cases.
	 *
	 * @throws MalformedMessageException If the message name is malformed.
	 */
	@Test
	public void testSplitting () throws MalformedMessageException
	{
		for (final Case splitCase : splitCases)
		{
			final String msgString = splitCase.message;
			final A_String message = StringDescriptor.from(msgString);
			final MessageSplitter splitter = new MessageSplitter(message);
			final A_Tuple parts = splitter.messageParts();
			assert splitCase.tokens.length == parts.tupleSize();
			for (int i = 1; i <= parts.tupleSize(); i++)
			{
				assertEquals(
					"Split was not as expected: " + msgString,
					splitCase.tokens[i - 1],
					parts.tupleAt(i).asNativeString());
			}
			final A_Tuple instructionsTuple = splitter.instructionsTuple();
			final List<Integer> instructionsList = new ArrayList<>();
			for (final A_Number instruction : instructionsTuple)
			{
				instructionsList.add(instruction.extractInt());
			}
			if (!splitCase.instructions.toString().equals(
				instructionsList.toString()))
			{
				System.out.println(dumpInstructions(instructionsList));
				fail(
					String.format(
						"Generated parse code for \"%s\" was not as expected:%n"
							+ "%s%ninstead it was:%n%s",
						msgString,
						dumpInstructions(splitCase.instructions),
						dumpInstructions(instructionsList)));
			}
		}
	}
}
