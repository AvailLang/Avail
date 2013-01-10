/**
 * MessageSplitterTest.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static junit.framework.Assert.*;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.ParsingConversionRule.*;
import java.util.*;
import org.junit.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.exceptions.SignatureException;

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
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@Before
	public void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@After
	public void clearAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
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
	static String[] C(
		final String message,
		final String[] tokens,
		final Integer[] instructions)
	{
		final String[] strings = new String[tokens.length + 2];
		strings[0] = message;
		System.arraycopy(tokens, 0, strings, 1, tokens.length);
		strings[strings.length - 1] = Arrays.asList(instructions).toString();
		return strings;
	}

	/**
	 * Create a simple array.  This greatly reduces the syntactic noise of the
	 * test cases.  It's "A(...)" versus "new Integer[] {...}".
	 *
	 * @param x The varargs array passed in.
	 * @return The same array.
	 */
	static <X> X[] A(final X... x)
	{
		return x;
	}

	/** Test cases. */
	private static String[][] splitCases =
	{
		C("Foo",
			A("Foo"),
			A(
				parsePart.encoding(1))),
		C("Print_",
			A("Print", "_"),
			A(
				parsePart.encoding(1),
				parseArgument.encoding(),
				checkArgument.encoding(1))),
		C("_+_",
			A("_", "+", "_"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(2),
				parseArgument.encoding(),
				checkArgument.encoding(2))),
		C("_+_*_",
			A("_", "+", "_", "*", "_"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(2),
				parseArgument.encoding(),
				checkArgument.encoding(2),
				parsePart.encoding(4),
				parseArgument.encoding(),
				checkArgument.encoding(3))),
		C("_;",
			A("_", ";"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(2))),
		C("__",
			A("_", "_"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parseArgument.encoding(),
				checkArgument.encoding(2))),
		/* Backquotes. */
		C("`__",
			A("`", "_", "_"),
			A(
				parsePart.encoding(2),
				parseArgument.encoding(),
				checkArgument.encoding(1))),
		C("_`«_",
			A("_", "`", "«", "_"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(3),
				parseArgument.encoding(),
				checkArgument.encoding(2))),
		C("_``_",
			A("_", "`", "`", "_"),
			A(
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(3),
				parseArgument.encoding(),
				checkArgument.encoding(2))),
		C("`#`?`~",
			A("`", "#", "`", "?", "`", "~"),
			A(
				parsePart.encoding(2),
				parsePart.encoding(4),
				parsePart.encoding(6))),

		C("`|`|_`|`|",
			A("`", "|", "`", "|", "_", "`", "|", "`", "|"),
			A(
				parsePart.encoding(2),
				parsePart.encoding(4),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(7),
				parsePart.encoding(9))),
		/* Repeated groups. */
		C("«_;»",
			A("«", "_", ";", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(3),
				appendArgument.encoding(),
				branch.encoding(11),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x»",
			A("«", "x", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePart.encoding(2),
				branch.encoding(10),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x y»",
			A("«", "x", "y", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(13),
				newList.encoding(),
				parsePart.encoding(2),
				parsePart.encoding(3),
				branch.encoding(11),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x_y»",
			A("«", "x", "_", "y", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(13),
				parsePart.encoding(2),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				parsePart.encoding(4),
				appendArgument.encoding(),
				branch.encoding(12),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«_:_»",
			A("«", "_", ":", "_", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(18),
				newList.encoding(),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				appendArgument.encoding(),
				parsePart.encoding(3),
				parseArgument.encoding(),
				checkArgument.encoding(2),
				appendArgument.encoding(),
				branch.encoding(16),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«»",
			A("«", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				branch.encoding(9),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«»«»",
			A("«", "»", "«", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				branch.encoding(9),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(22),
				newList.encoding(),
				branch.encoding(20),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(15),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«_»",
			A("«", "_", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				appendArgument.encoding(),
				branch.encoding(10),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		/* Repeated groups with double dagger. */
		C("«_‡,»",
			A("«", "_", "‡", ",", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				appendArgument.encoding(),
				branch.encoding(11),
				parsePart.encoding(4),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«‡»",
			A("«", "‡", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				branch.encoding(9),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("new_«with«_=_‡,»»",
			A("new", "_", "«", "with", "«", "_", "=", "_", "‡", ",", "»", "»"),
			A(
				parsePart.encoding(1),
				parseArgument.encoding(),
				checkArgument.encoding(1),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(32),
				parsePart.encoding(4),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(26),
				newList.encoding(),
				parseArgument.encoding(),
				checkArgument.encoding(2),
				appendArgument.encoding(),
				parsePart.encoding(7),
				parseArgument.encoding(),
				checkArgument.encoding(3),
				appendArgument.encoding(),
				branch.encoding(24),
				parsePart.encoding(10),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(11),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				appendArgument.encoding(),
				branch.encoding(31),
				ensureParseProgress.encoding(),
				jump.encoding(7),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		/* Counting groups. */
		C("«x»#",
			A("«", "x", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				parsePart.encoding(2),
				appendArgument.encoding(),
				branch.encoding(10),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«x y»#",
			A("«", "x", "y", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePart.encoding(2),
				parsePart.encoding(3),
				appendArgument.encoding(),
				branch.encoding(11),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«»#",
			A("«", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(10),
				newList.encoding(),
				appendArgument.encoding(),
				branch.encoding(9),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«»#«»#",
			A("«", "»", "#", "«", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(10),
				newList.encoding(),
				appendArgument.encoding(),
				branch.encoding(9),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(21),
				newList.encoding(),
				appendArgument.encoding(),
				branch.encoding(20),
				ensureParseProgress.encoding(),
				jump.encoding(15),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		/* Counting groups with double dagger. */
		C("«‡»#",
			A("«", "‡", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(10),
				newList.encoding(),
				appendArgument.encoding(),
				branch.encoding(9),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«fish‡»#",
			A("«", "fish", "‡", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				parsePart.encoding(2),
				appendArgument.encoding(),
				branch.encoding(10),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«‡face»#",
			A("«", "‡", "face", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(11),
				newList.encoding(),
				appendArgument.encoding(),
				branch.encoding(10),
				parsePart.encoding(3),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«fish‡face»#",
			A("«", "fish", "‡", "face", "»", "#"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePart.encoding(2),
				appendArgument.encoding(),
				branch.encoding(11),
				parsePart.encoding(4),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("««fish‡face»#»",
			A("«", "«", "fish", "‡", "face", "»", "#", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(22),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(15),
				newList.encoding(),
				parsePart.encoding(3),
				appendArgument.encoding(),
				branch.encoding(14),
				parsePart.encoding(5),
				ensureParseProgress.encoding(),
				jump.encoding(7),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()),
				appendArgument.encoding(),
				branch.encoding(21),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		/* Optional groups. */
		C("«x»?",
			A("«", "x", "»", "?"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(8),
				newList.encoding(),
				parsePart.encoding(2),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToNonemptiness.number()))),
		C("«x y»?",
			A("«", "x", "y", "»", "?"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(9),
				newList.encoding(),
				parsePart.encoding(2),
				parsePart.encoding(3),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToNonemptiness.number()))),
		C("«»?",
			A("«", "»", "?"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(7),
				newList.encoding(),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToNonemptiness.number()))),
		C("««bagel»#«friend»?»",
			A("«", "«", "bagel", "»", "#", "«", "friend", "»", "?", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(34),
				newList.encoding(),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(15),
				newList.encoding(),
				parsePart.encoding(3),
				appendArgument.encoding(),
				branch.encoding(14),
				ensureParseProgress.encoding(),
				jump.encoding(8),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()),
				appendArgument.encoding(),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(25),
				newList.encoding(),
				parsePart.encoding(7),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToNonemptiness.number()),
				appendArgument.encoding(),
				branch.encoding(32),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		/* Completely optional groups. */
		C("very⁇good",
			A("very", "⁇", "good"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(6),
				parsePart.encoding(1),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				pop.encoding(),
				parsePart.encoding(3))),
		C("«very extremely»⁇good",
			A("«", "very", "extremely", "»", "⁇", "good"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(7),
				parsePart.encoding(2),
				parsePart.encoding(3),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				pop.encoding(),
				parsePart.encoding(6))),
		/* Case insensitive. */
		C("fnord~",
			A("fnord", "~"),
			A(
				parsePartCaseInsensitive.encoding(1))),
		C("the~_",
			A("the", "~", "_"),
			A(
				parsePartCaseInsensitive.encoding(1),
				parseArgument.encoding(),
				checkArgument.encoding(1))),
		C("«x~»",
			A("«", "x", "~", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				branch.encoding(10),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x»~",
			A("«", "x", "»", "~"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				branch.encoding(10),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x~y»",
			A("«", "x", "~", "y", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(13),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				parsePart.encoding(4),
				branch.encoding(11),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x y»~",
			A("«", "x", "y", "»", "~"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(13),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				parsePartCaseInsensitive.encoding(3),
				branch.encoding(11),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding())),
		C("«x y»#~",
			A("«", "x", "y", "»", "#", "~"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(12),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				parsePartCaseInsensitive.encoding(3),
				appendArgument.encoding(),
				branch.encoding(11),
				ensureParseProgress.encoding(),
				jump.encoding(4),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToSize.number()))),
		C("«x y»?~",
			A("«", "x", "y", "»", "?", "~"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(9),
				newList.encoding(),
				parsePartCaseInsensitive.encoding(2),
				parsePartCaseInsensitive.encoding(3),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				convert.encoding(listToNonemptiness.number()))),
		/* Alternation. */
		C("hello|greetings",
			A("hello", "|", "greetings"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(6),
				parsePart.encoding(1),
				jump.encoding(7),
				parsePart.encoding(3),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				pop.encoding())),
		C("a|b|c|d|e|f|g",
			A("a", "|", "b", "|", "c", "|", "d", "|", "e", "|", "f", "|", "g"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(6),
				parsePart.encoding(1),
				jump.encoding(22),
				branch.encoding(9),
				parsePart.encoding(3),
				jump.encoding(22),
				branch.encoding(12),
				parsePart.encoding(5),
				jump.encoding(22),
				branch.encoding(15),
				parsePart.encoding(7),
				jump.encoding(22),
				branch.encoding(18),
				parsePart.encoding(9),
				jump.encoding(22),
				branch.encoding(21),
				parsePart.encoding(11),
				jump.encoding(22),
				parsePart.encoding(13),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				pop.encoding())),
		C("«fruit bats»|sloths|carp|«breakfast cereals»",
			A("«", "fruit", "bats", "»", "|", "sloths", "|", "carp", "|", "«", "breakfast", "cereals", "»"),
			A(
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(18),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(16),
				newList.encoding(),
				parsePart.encoding(2),
				parsePart.encoding(3),
				branch.encoding(14),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(7),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				jump.encoding(37),
				branch.encoding(21),
				parsePart.encoding(6),
				jump.encoding(37),
				branch.encoding(24),
				parsePart.encoding(8),
				jump.encoding(37),
				saveParsePosition.encoding(),
				newList.encoding(),
				branch.encoding(36),
				newList.encoding(),
				parsePart.encoding(11),
				parsePart.encoding(12),
				branch.encoding(34),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				jump.encoding(27),
				appendArgument.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				ensureParseProgress.encoding(),
				discardSavedParsePosition.encoding(),
				pop.encoding())),
		C("«x|y»!",
			A("«", "x", "|", "y", "»", "!"),
			A(
				branch.encoding(5),
				parsePart.encoding(2),
				pushIntegerLiteral.encoding(1),
				jump.encoding(7),
				parsePart.encoding(4),
				pushIntegerLiteral.encoding(2)))
	};

	/**
	 * Test: Split the test cases.
	 *
	 * @throws SignatureException If the message name is malformed.
	 */
	@Test
	public void testSplitting () throws SignatureException
	{
		for (final String[] splitCase : Arrays.asList(splitCases))
		{
			final String msgString = splitCase[0];
			final AvailObject message = StringDescriptor.from(msgString);
			final MessageSplitter splitter = new MessageSplitter(message);
			final AvailObject parts = splitter.messageParts();
			assert splitCase.length == parts.tupleSize() + 2;
			for (int i = 1; i <= parts.tupleSize(); i++)
			{
				assertEquals(
					"Split was not as expected: " + msgString,
					splitCase[i],
					parts.tupleAt(i).asNativeString());
			}
			final AvailObject instructionsTuple = splitter.instructionsTuple();
			final List<Integer> instructionsList = new ArrayList<Integer>();
			for (final AvailObject instruction : instructionsTuple)
			{
				instructionsList.add(instruction.extractInt());
			}
			assertEquals(
				"Generated parse code was not as expected: " + msgString,
				splitCase[splitCase.length - 1],
				instructionsList.toString());
		}
	}
}
