/*
 * ScannerTest.java
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

package com.avail.test;

import com.avail.AvailRuntime;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.LiteralTokenDescriptor;
import com.avail.descriptor.TokenDescriptor;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.FloatDescriptor.fromFloat;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.test.ScannerTest.Case.C;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link AvailScanner}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ScannerTest
{
	/**
	 * A {@code Case} consists of a string to be lexically scanned, and a
	 * description of the exact tokens that should be produced.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	static final class Case
	{
		/**
		 * The input string to be lexically scanned.
		 */
		final String inputString;

		/**
		 * An array of {@linkplain Supplier suppliers} of {@linkplain
		 * TokenDescriptor tokens}.  They're suppliers because the array of
		 * {@linkplain Case cases} may need to be created statically, before
		 * initialization of the {@link AvailRuntime}.
		 */
		final Supplier<A_Token>[] tokenSuppliers;

		/**
		 * Construct a new {@code Case}.
		 *
		 * @param inputString
		 *        The string to be lexically scanned into tokens.
		 * @param tokenSuppliers
		 *        The {@linkplain Supplier suppliers} that produce the reference
		 *        tokens with which to check the result of the lexical scanning.
		 */
		@SafeVarargs
		private Case (
			final String inputString,
			final Supplier<A_Token>... tokenSuppliers)
		{
			this.inputString = inputString;
			this.tokenSuppliers = tokenSuppliers;
		}

		/**
		 * This static method allows a concise notation for specifying a case.
		 *
		 * @param inputString
		 *        The string to scan into tokens.
		 * @param tokenSuppliers
		 *        The {@linkplain Supplier suppliers} of the tokens that are
		 *        expected from the lexical scanning.
		 * @return The new {@code Case}.
		 */
		@SafeVarargs
		static Case C (
			final String inputString,
			final Supplier<A_Token>... tokenSuppliers)
		{
			return new Case(inputString, tokenSuppliers);
		}

		@Override
		public String toString ()
		{
			//noinspection DynamicRegexReplaceableByCompiledPattern
			return "Case \""
				+ inputString.replace("\\","\\\\").replace("\"", "\\\"")
				+ "\"";
		}
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenDescriptor token} with the specified string and {@linkplain
	 * TokenType token type}.  Its one-based start position in the entire input
	 * string is set to one.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.
	 * @param tokenType
	 *        The type of token to construct.
	 * @return The new token.
	 */
	static Supplier<A_Token> T (
		final String string,
		final TokenType tokenType)
	{
		return T(string, tokenType, 1);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenDescriptor token} with the specified string, {@linkplain TokenType
	 * token type}, and start offset.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.
	 * @param tokenType
	 *        The type of token to construct.
	 * @param start
	 *        The one-based offset of the first character of this token within
	 *        the entire input string.
	 * @return The new token.
	 */
	static Supplier<A_Token> T (
		final String string,
		final TokenType tokenType,
		final int start)
	{
		return () ->
			newToken(
				stringFrom(string),
				emptyTuple(),
				emptyTuple(),
				start,
				1,
				tokenType);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenType#KEYWORD keyword} {@linkplain TokenDescriptor token} with the
	 * specified string.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.
	 * @return The new keyword token.
	 */
	static Supplier<A_Token> K (final String string)
	{
		return T(string, KEYWORD);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenType#KEYWORD keyword} {@linkplain TokenDescriptor token} with the
	 * specified string and start offset.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.
	 * @param start
	 *        The one-based offset of the first character of this token within
	 *        the entire input string.
	 * @return The new keyword token.
	 */
	static Supplier<A_Token> K (
		final String string,
		final int start)
	{
		return T(string, KEYWORD, start);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenType#OPERATOR operator} {@linkplain TokenDescriptor token} with the
	 * specified string.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.  An operator token is always a single character.
	 * @return The new operator token.
	 */
	static Supplier<A_Token> O (
		final String string)
	{
		assert string.codePointCount(0, string.length()) == 1;
		return T(string, OPERATOR);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * TokenType#OPERATOR operator} {@linkplain TokenDescriptor token} with the
	 * specified string and start offset.
	 *
	 * @param string
	 *        The characters from which the token will ostensibly have been
	 *        constructed.  An operator token is always a single character.
	 * @param start
	 *        The one-based offset of the first character of this token within
	 *        the entire input string.
	 * @return The new operator token.
	 */
	static Supplier<A_Token> O (
		final String string,
		final int start)
	{
		return T(string, OPERATOR, start);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * LiteralTokenDescriptor literal token} with the specified value and
	 * string.  The start position will be one, indicating that the token is at
	 * the beginning of the entire input string.
	 *
	 * @param object
	 *        The value of the literal.
	 * @param string
	 *        The characters from which the literal token will ostensibly have
	 *        been constructed.
	 * @return The new operator token.
	 */
	static Supplier<A_Token> L (
		final Object object,
		final String string)
	{
		return L(object, string, 1);
	}

	/**
	 * A concise static method for building a {@link Supplier} of {@linkplain
	 * LiteralTokenDescriptor literal token} with the specified value, string,
	 * and start offset.
	 *
	 * @param object
	 *        The value of the literal.
	 * @param string
	 *        The characters from which the literal token will ostensibly have
	 *        been constructed.
	 * @param start
	 *        The one-based offset of the first character of this literal token
	 *        within the entire input string.
	 * @return The new operator token.
	 */
	static Supplier<A_Token> L (
		final Object object,
		final String string,
		final int start)
	{
		return () ->
		{
			final @Nullable A_BasicObject literal;
			if (object instanceof Double)
			{
				literal = fromDouble((Double) object);
			}
			else if (object instanceof Float)
			{
				literal = fromFloat((Float) object);
			}
			else if (object instanceof Number)
			{
				final long asLong = ((Number) object).longValue();
				literal = fromLong(asLong);
			}
			else if (object instanceof String)
			{
				literal = stringFrom((String) object);
			}
			else
			{
				fail(
					"Unexpected literal type: "
					+ object.getClass().getCanonicalName());
				// The null propagation and definite-assignment flow analyzers
				// have a blind spot if we don't do this, even if we assign a
				// null explicitly.  A throw solves it.
				throw new RuntimeException("Should not get here");
			}
			return (A_Token) literalToken(
				stringFrom(string),
				emptyTuple(),
				emptyTuple(),
				start,
				1,
				LITERAL,
				literal);
		};
	}

	/**
	 * The {@code null} value, typed as a {@link Supplier} of {@link A_Token}
	 * for convenience.
	 */
	private static final @Nullable Supplier<A_Token> nullSupplier = null;

	/**
	 * The collection of test cases with which to test the {@link AvailScanner}.
	 * The first item of each {@link Case} is the string to be scanned, and the
	 * remaining values represent the tokens that the scanner should produce
	 * from the input string.  The tokens are actually {@link Supplier
	 * suppliers} of {@linkplain TokenDescriptor token} to allow this list to be
	 * constructed statically.  If a case has a single token generator and it
	 * produces null, then the lexical scanner is supposed to fail to parse that
	 * input string.
	 */
	private static final Case[] tests =
		{
			C(""),

			// integers
			C("0", L(0,"0")),
			C("1", L(1,"1")),
			C("123", L(123,"123")),
			C("1 2", L(1,"1"), L(2,"2", 3)),
			C("-12", O("-"), L(12, "12", 2)),

			// Reals and such:
			C(".", O(".")),
			C("..", O("."), O(".",2)),
			C(".f", O("."), K("f",2)),
			C(".e5", O("."), K("e5",2)),
			C("5.", L(5,"5"), O(".",2)),
			C(".5", O("."), L(5,"5",2)),
			C("3.14159", L(3,"3"), O(".", 2), L(14159, "14159", 3)),
			C("123.056", L(123, "123"), O(".", 4), L(56, "056", 5)),
			C("1d", L(1,"1"), K("d",2)),
			C("5e", L(5,"5"), K("e",2)),
			C("5e5", L(5, "5"), K("e5", 2)),
			C("5e+5", L(5, "5"), K("e", 2), O("+", 3), L(5, "5", 4)),
			C("5e-5", L(5, "5"), K("e", 2), O("-", 3), L(5, "5", 4)),
			C("12.34e5f", L(12, "12"), O(".", 3), L(34, "34", 4), K("e5f", 6)),

			C("hello", K("hello")),
			C("hello world", K("hello"), K("world",7)),
			C("hello  world", K("hello"), K("world",8)),
			C("   cat   dog   ", K("cat",4), K("dog",10)),
			C("hello-world", K("hello"), O("-",6), K("world",7)),
			C("𝄞", O("𝄞")),
			C("𝄞𝄞", O("𝄞"), O("𝄞",3)),
			C("𝄞𝄞cat", O("𝄞"), O("𝄞",3), K("cat",5)),
			C("«cat»", O("«"), K("cat",2), O("»",5)),
			C("\\", O("\\")),
			C("/", O("/")),
			C("\\(", O("\\"), O("(",2)),
			C("`", O("`")),
			C(";", O(";")),

			C("\"", nullSupplier),
			C("\"cat", nullSupplier),
			C("\"\\\"", nullSupplier),
			C("\"\\\\\"", L("\\","\"\\\\\"")),
			C("\"cat\"", L("cat","\"cat\"")),

			C("\"ab\\", nullSupplier),
			C("\"ab\\(", nullSupplier),
			C("\"ab\\(6", nullSupplier),
			C("\"ab\\(,", nullSupplier),
			C("\"ab\\()", nullSupplier),
			C("\"ab\\(,)", nullSupplier),
			C("\"ab\\(6,)", nullSupplier),
			C("\"ab\\(,6)", nullSupplier),
			C("\"ab\\(63,64)\"", L("abcd","\"ab\\(63,64)\"")),
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
			try
			{
				final AvailScannerResult result = AvailScanner.scanString(
					input,
					"A module synthesized in ScannerTest.testScanner()",
					false);
				List<A_Token> scannedTokens = result.outputTokens();
				if (c.tokenSuppliers.length == 1
					&& c.tokenSuppliers[0] == null)
				{
					fail(
						c + ": Expected scanner to fail, not produce "
						+ scannedTokens.size()
						+ " tokens");
				}
				assertEquals(
					scannedTokens.get(scannedTokens.size() - 1),
					newToken(
						emptyTuple(),
						emptyTuple(),
						emptyTuple(),
						input.length() + 1,
						1,
						END_OF_FILE),
					c + ": Expected scanner to have produced the"
						+ " end-of-file token.");
				scannedTokens = scannedTokens.subList(
					0,
					scannedTokens.size() - 1);
				assertEquals(
					c.tokenSuppliers.length,
					scannedTokens.size(),
					c + ": Scanner produced the wrong number of tokens.");
				for (int i = 0; i < c.tokenSuppliers.length; i++)
				{
					final A_BasicObject expected = c.tokenSuppliers[i].get();
					final A_BasicObject actual = scannedTokens.get(i);
					assertEquals(
						expected,
						actual,
						c + ": Scanner produced a wrong token.");
				}
			}
			catch (final AvailScannerException e)
			{
				if (c.tokenSuppliers.length != 1
					|| c.tokenSuppliers[0] != null)
				{
					fail(
						c + ": Expected scanner to produce "
						+ c.tokenSuppliers.length
						+ " tokens, not fail with: "
						+ e);
				}
			}
		}
	}

	/**
	 * Test that literal tokens compare content, not just their strings.
	 */
	@Test
	public void testLiteralComparison ()
	{
		final A_String string = stringFrom("xxx");
		final List<AvailObject> literals = new ArrayList<>(4);
		literals.add(literalToken(
			string,
			emptyTuple(),
			emptyTuple(),
			0,
			0,
			LITERAL,
			fromFloat(1.5f)));
		literals.add(literalToken(
			string,
			emptyTuple(),
			emptyTuple(),
			0,
			0,
			LITERAL,
			fromFloat(1.5f)));
		literals.add(literalToken(
			string,
			emptyTuple(),
			emptyTuple(),
			0,
			0,
			LITERAL,
			fromFloat(2.5f)));
		literals.add(literalToken(
			string,
			emptyTuple(),
			emptyTuple(),
			0,
			0,
			LITERAL,
			fromDouble(2.5)));
		for (final A_Token lit_i : literals)
		{
			for (final A_Token lit_j : literals)
			{
				if (lit_i.literal().equals(lit_j.literal()))
				{
					assertEquals(
						lit_i,
						lit_j,
						"Literals on same value should be equal");
				}
				else
				{
					assertFalse(
						lit_i.equals(lit_j),
						"Literals on different values should be unequal");
				}
			}
		}
	}
}
