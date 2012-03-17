/**
 * ScannerTest.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.test.ScannerTest.Case.C;
import java.util.List;
import org.junit.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.scanning.*;
import com.avail.descriptor.*;
import com.avail.utility.Generator;

/**
 * Unit tests for the {@link AvailScanner}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class ScannerTest
{
	/**
	 * A {@code Case} consists of a string to be lexically scanned, and a
	 * description of the exact tokens that should be produced.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	static class Case
	{
		/**
		 * The input string to be lexically scanned.
		 */
		final String inputString;

		/**
		 * An array of {@linkplain Generator generators} of {@linkplain
		 * TokenDescriptor tokens}.  They're generators because the array of
		 * {@linkplain Case cases} may need to be created statically, before
		 * initialization of the {@link AvailRuntime}.
		 */
		final Generator<AvailObject>[] tokenGenerators;

		/**
		 * Construct a new {@link Case}.
		 *
		 * @param inputString
		 *            The string to be lexically scanned into tokens.
		 * @param tokenGenerators
		 *            The {@linkplain Generator generators} that produce the
		 *            reference tokens with which to check the result of the
		 *            lexical scanning.
		 */
		private Case (
			final @NotNull String inputString,
			final Generator<AvailObject>... tokenGenerators)
		{
			this.inputString = inputString;
			this.tokenGenerators = tokenGenerators;
		}

		/**
		 * This static method allows a concise notation for specifying a case.
		 *
		 * @param inputString
		 *            The string to scan into tokens.
		 * @param tokenGenerators
		 *            The {@linkplain Generator generators} of the tokens
		 *            that are expected from the lexical scanning.
		 * @return The new {@link Case}.
		 */
		static Case C (
			final @NotNull String inputString,
			final Generator<AvailObject>... tokenGenerators)
		{
			return new Case(inputString, tokenGenerators);
		}

		@Override
		public String toString ()
		{
			return "Case \""
				+ inputString.replace("\\","\\\\").replace("\"", "\\\"")
				+ "\"";
		}
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor token} with the specified string and {@linkplain
	 * TokenDescriptor.TokenType token type}.  Its zero-based start position
	 * in the entire input string is set to zero.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.
	 * @param tokenType
	 *            The type of token to construct.
	 * @return The new token.
	 */
	static Generator<AvailObject> T (
		final String string,
		final TokenDescriptor.TokenType tokenType)
	{
		return T(string, tokenType, 0);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor token} with the specified string, {@linkplain
	 * TokenDescriptor.TokenType token type}, and start offset.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.
	 * @param tokenType
	 *            The type of token to construct.
	 * @param start
	 *            The zero-based offset of the first character of this token
	 *            within the entire input string.
	 * @return The new token.
	 */
	static Generator<AvailObject> T (
		final String string,
		final TokenDescriptor.TokenType tokenType,
		final int start)
	{
		return new Generator<AvailObject>()
		{
			@Override public AvailObject value ()
			{
				final AvailObject token = TokenDescriptor.create(
					StringDescriptor.from(string),
					start,
					1,
					tokenType);
				return token;
			}
		};
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor.TokenType#KEYWORD keyword} {@linkplain TokenDescriptor
	 * token} with the specified string.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.
	 * @return The new keyword token.
	 */
	static Generator<AvailObject> K (
		final String string)
	{
		return T(string, KEYWORD);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor.TokenType#KEYWORD keyword} {@linkplain TokenDescriptor
	 * token} with the specified string and start offset.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.
	 * @param start
	 *            The zero-based offset of the first character of this token
	 *            within the entire input string.
	 * @return The new keyword token.
	 */
	static Generator<AvailObject> K (
		final String string,
		final int start)
	{
		return T(string, KEYWORD, start);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor.TokenType#OPERATOR operator} {@linkplain TokenDescriptor
	 * token} with the specified string.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.  An operator token is always a single character.
	 * @return The new operator token.
	 */
	static Generator<AvailObject> O (
		final String string)
	{
		assert string.codePointCount(0, string.length()) == 1;
		return T(string, OPERATOR);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * TokenDescriptor.TokenType#OPERATOR operator} {@linkplain TokenDescriptor
	 * token} with the specified string and start offset.
	 *
	 * @param string
	 *            The characters from which the token will ostensibly have been
	 *            constructed.  An operator token is always a single character.
	 * @param start
	 *            The zero-based offset of the first character of this token
	 *            within the entire input string.
	 * @return The new operator token.
	 */
	static Generator<AvailObject> O (
		final String string,
		final int start)
	{
		return T(string, OPERATOR, start);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * LiteralTokenDescriptor literal token} with the specified value and
	 * string.  The start position will be zero, indicating that the token is at
	 * the beginning of the entire input string.
	 *
	 * @param object
	 *            The value of the literal.
	 * @param string
	 *            The characters from which the literal token will ostensibly
	 *            have been constructed.
	 * @return The new operator token.
	 */
	static Generator<AvailObject> L (
		final @NotNull Object object,
		final @NotNull String string)
	{
		return L (object, string, 0);
	}

	/**
	 * A concise static method for building a {@link Generator} of {@linkplain
	 * LiteralTokenDescriptor literal token} with the specified value, string,
	 * and start offset.
	 *
	 * @param object
	 *            The value of the literal.
	 * @param string
	 *            The characters from which the literal token will ostensibly
	 *            have been constructed.
	 * @param start
	 *            The zero-based offset of the first character of this literal
	 *            token within the entire input string.
	 * @return The new operator token.
	 */
	static Generator<AvailObject> L (
		final @NotNull Object object,
		final @NotNull String string,
		final int start)
	{
		return new Generator<AvailObject>()
		{
			@Override
			public AvailObject value ()
			{
				final AvailObject literal;
				if (object instanceof Double)
				{
					literal = DoubleDescriptor.fromDouble((Double)object);
				}
				else if (object instanceof Float)
				{
					literal = FloatDescriptor.fromFloat((Float)object);
				}
				else if (object instanceof Number)
				{
					final long asLong = ((Number)object).longValue();
					literal = IntegerDescriptor.fromLong(asLong);
				}
				else if (object instanceof String)
				{
					literal = StringDescriptor.from((String)object);
				}
				else
				{
					fail(
						"Unexpected literal type: "
						+ object.getClass().getCanonicalName());
					literal = null;
				}
				final AvailObject token =
					LiteralTokenDescriptor.create(
						StringDescriptor.from(string),
						start,
						1,
						LITERAL,
						literal);
				return token;
			}
		};
	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	/**
	 * Test fixture: clear all special objects.
	 */
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
	}

	/**
	 * The collection of test cases with which to test the {@link AvailScanner}.
	 * The first item of each {@link Case} is the string to be scanned, and the
	 * remaining values represent the tokens that the scanner should produce
	 * from the input string.  The tokens are actually {@link Generator
	 * generators} of {@linkplain TokenDescriptor token} to allow this list to
	 * be constructed statically.  If a case has a single token generator and it
	 * produces null, then the lexical scanner is supposed to fail to parse that
	 * input string.
	 */
	@SuppressWarnings("unchecked")
	private static final Case[] tests =
	{
		C(""),

		C("0", L(0,"0")),
		C("1", L(1,"1")),
		C("123", L(123,"123")),
		C("1 2", L(1,"1"), L(2,"2", 2)),
		C("1.1", L(1.1f,"1.1")),
		C("1.02", L(1.02f,"1.02")),
		C("1.02e5", L(1.02e5f,"1.02e5")),

		C("7d", L(7.0d,"7d")),
		C("0.1d", L(0.1d,"0.1d")),
		C("0.05d", L(0.05d,"0.05d")),
		C("1.02e-8", L(1.02e-8,"1.02e-8")),
		C("9.99d307", L(9.99e307,"9.99d307")),
		C("1.", L(1,"1"), O(".",1)),
		C(".1", O("."), L(1,"1",1)),
		C("12.34.56", L(12.34f,"12.34"), O(".",5), L(56,"56",6)),
		C("12.34e56", L(12.34e56,"12.34e56")),
		C("12.34d56", L(12.34e56d,"12.34d56")),

		C("hello", K("hello")),
		C("hello world", K("hello"), K("world",6)),
		C("hello  world", K("hello"), K("world",7)),
		C("   cat   dog   ", K("cat",3), K("dog",9)),
		C("hello-world", K("hello"), O("-",5), K("world",6)),
		C("𝄞", O("𝄞")),
		C("𝄞𝄞", O("𝄞"), O("𝄞",2)),
		C("𝄞𝄞cat", O("𝄞"), O("𝄞",2), K("cat",4)),
		C("«cat»", O("«"), K("cat",1), O("»",4)),
		C("\\", O("\\")),
		C("/", O("/")),
		C("\\(", O("\\"), O("(",1)),
		C("`", O("`")),
		C(";", T(";", END_OF_STATEMENT)),

		C("\"cat", (Generator<AvailObject>)null),
		C("\"cat\"", L("cat","\"cat\"")),

		C("\"ab\\(63)\"", L("abc","\"ab\\(63)\"")),
		C("\"ab\\(063)\"", L("abc","\"ab\\(063)\"")),
		C("\"ab\\(0063)\"", L("abc","\"ab\\(0063)\"")),
		C("\"ab\\(00063)\"", L("abc","\"ab\\(00063)\"")),
		C("\"ab\\(000063)\"", L("abc","\"ab\\(000063)\"")),

		C("  \t  ")
	};

	/**
	 * Test: Test basic functionality of the {@link AvailScanner}.
	 */
	@Test
	public void testScanner ()
	{
		for (final Case c : tests)
		{
			final String input = c.inputString;
			final AvailScanner scanner = new AvailScanner();
			List<AvailObject> scannedTokens = null;
			try
			{
				scannedTokens = scanner.scanString(input, false);
				if (c.tokenGenerators.length == 1
					&& c.tokenGenerators[0] == null)
				{
					fail(
						c + ": Expected scanner to fail, not produce "
						+ scannedTokens.size()
						+ " tokens");
				}
				assertEquals(
					c + ": Expected scanner to have produced the"
					+ " end-of-file token.",
					TokenDescriptor.create(
						TupleDescriptor.empty(),
						input.length(),
						1,
						END_OF_FILE),
					scannedTokens.get(scannedTokens.size() - 1));
				scannedTokens = scannedTokens.subList(
					0,
					scannedTokens.size() - 1);
				assertEquals(
					c + ": Scanner produced the wrong number of tokens.",
					c.tokenGenerators.length,
					scannedTokens.size());
				for (int i = 0; i < c.tokenGenerators.length; i++)
				{
					final AvailObject expected = c.tokenGenerators[i].value();
					final AvailObject actual = scannedTokens.get(i);
					assertEquals(
						c + ": Scanner produced a wrong token.",
						expected,
						actual);
				}
			}
			catch (final AvailScannerException e)
			{
				if (c.tokenGenerators.length != 1
					|| c.tokenGenerators[0] != null)
				{
					fail(
						c + ": Expected scanner to produce "
						+ c.tokenGenerators.length
						+ " tokens, not fail with: "
						+ e);
				}
			}
		}
	}
}
