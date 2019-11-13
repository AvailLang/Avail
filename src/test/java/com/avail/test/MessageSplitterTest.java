/*
 * MessageSplitterTest.java
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

import com.avail.compiler.ParsingOperation;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.A_DefinitionParsingPlan;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ListPhraseTypeDescriptor;
import com.avail.descriptor.LiteralTokenTypeDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.avail.compiler.ParsingOperation.APPEND_ARGUMENT;
import static com.avail.compiler.ParsingOperation.BRANCH_FORWARD;
import static com.avail.compiler.ParsingOperation.CHECK_ARGUMENT;
import static com.avail.compiler.ParsingOperation.CONCATENATE;
import static com.avail.compiler.ParsingOperation.DISCARD_SAVED_PARSE_POSITION;
import static com.avail.compiler.ParsingOperation.EMPTY_LIST;
import static com.avail.compiler.ParsingOperation.ENSURE_PARSE_PROGRESS;
import static com.avail.compiler.ParsingOperation.JUMP_BACKWARD;
import static com.avail.compiler.ParsingOperation.PARSE_ARGUMENT;
import static com.avail.compiler.ParsingOperation.PARSE_PART;
import static com.avail.compiler.ParsingOperation.PARSE_RAW_STRING_LITERAL_TOKEN;
import static com.avail.compiler.ParsingOperation.PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN;
import static com.avail.compiler.ParsingOperation.SAVE_PARSE_POSITION;
import static com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT;
import static com.avail.compiler.ParsingOperation.WRAP_IN_LIST;
import static com.avail.compiler.ParsingOperation.decode;
import static com.avail.compiler.ParsingOperation.distinctInstructions;
import static com.avail.compiler.ParsingOperation.operand;
import static com.avail.compiler.splitter.MessageSplitter.indexForConstant;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ListPhraseTypeDescriptor.createListNodeType;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Test the {@link MessageSplitter}.  It splits method names into a sequence of
 * tokens to expect and underscores, but it also implements the repeated
 * argument mechanism by producing a sequence of mini-instructions to say how
 * to do the parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MessageSplitterTest
{
	/**
	 * Forbid instantiation.
	 */
	private MessageSplitterTest ()
	{
		// No implementation required.
	}

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
		 * The phrase type for which to instantiate a {@link
		 * A_DefinitionParsingPlan}.
		 */
		final A_Type listPhraseType;

		/**
		 * Construct a new {@code Case}.
		 *
		 * @param message
		 *        The method name to split.
		 * @param listPhraseType
		 *        The list phrase type for which the instructions should be
		 *        specialized.
		 * @param tokens
		 *        The expected substrings comprising the method name.
		 * @param instructions
		 *        The expected encoded parsing instructions.
		 */
		Case (
			final String message,
			final A_Type listPhraseType,
			final String[] tokens,
			final Integer[] instructions)
		{
			this.message = message;
			this.listPhraseType = listPhraseType;
			this.tokens = tokens.clone();
			this.instructions = Arrays.asList(instructions);
		}

		@Override
		public String toString ()
		{
			return message
				+ " @ " + listPhraseType
				+ " → " + Arrays.toString(tokens);
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
	 * @param listPhraseType A {@link ListPhraseTypeDescriptor list phrase type}.
	 * @param instructions The parsing operations to parse this message.
	 * @return An array of Strings starting with the message, then all the
	 *         tokens, then a print representation of the numeric instructions
	 *         converted to a {@link List}.
	 */
	static Case C (
		final String message,
		final A_Type listPhraseType,
		final String[] tokens,
		final Integer[] instructions)
	{
		assert listPhraseType.isSubtypeOf(List(0, -1, Phrase(ANY.o())));
		return new Case(message, listPhraseType, tokens, instructions);
	}

	/**
	 * Construct a list phrase type from the lower bound, upper bound (-1 for
	 * infinity), and vararg array of subexpression phrase types.
	 *
	 * @param lowerBound
	 *        The smallest this tuple can be.
	 * @param upperBound
	 *        The largest this tuple can be, or -1 if it's unbounded.
	 * @param expressionPhraseTypes
	 *        The vararg array of subexpression phrase types, the last one being
	 *        implicitly repeated if the upperBound is bigger than the length of
	 *        the array.
	 * @return A {@link ListPhraseTypeDescriptor list phrase type}.
1	 */
	static A_Type List (
		final int lowerBound,
		final int upperBound,
		final A_Type... expressionPhraseTypes)
	{
		assert upperBound >= -1;
		final A_Number lower = fromInt(lowerBound);
		final A_Number upperPlusOne = (upperBound >= 0)
			? fromLong(upperBound + 1L)
			: positiveInfinity();
		final A_Type subexpressionsTupleType =
			tupleTypeForSizesTypesDefaultType(
				integerRangeType(lower, true, upperPlusOne, false),
				tupleFromArray(expressionPhraseTypes),
				expressionPhraseTypes.length > 0
					? expressionPhraseTypes[expressionPhraseTypes.length - 1]
					: bottom());
		return createListNodeType(
			LIST_PHRASE, mostGeneralTupleType(), subexpressionsTupleType);
	}

	/**
	 * Construct a list phrase type from the given type that the phrase type
	 * should yield.
	 *
	 * @param yieldType
	 *        The type that the resulting phrase type would yield.
	 * @return A {@link PhraseTypeDescriptor phrase type}.
	 */
	static A_Type Phrase(
		final A_Type yieldType)
	{
		return PARSE_PHRASE.create(yieldType);
	}

	/**
	 * Construct a literal token type which yields the given type.
	 *
	 * @param valueType
	 *        The type that the resulting literal token would yield.
	 * @return A {@link LiteralTokenTypeDescriptor literal token type}.
	 */
	static A_Type LiteralToken(
		final A_Type valueType)
	{
		return literalTokenType(valueType);
	}

	/**
	 * Create a simple array.  This greatly reduces the syntactic noise of the
	 * test cases.  It's "A(...)" versus "new Integer[] {...}".
	 *
	 * @param x The varargs array passed in.
	 * @return The same array.
	 * @param <X> The type of elements in the array.
	 */
	@SafeVarargs
	static <X> X[] A(final X... x)
	{
		return x;
	}

	/** Test cases. */
	private static final Case[] splitCases;

	static
	{
		splitCases = new Case[] {
			C(
				"Foo",
				List(0, 0),
				A("Foo"),
				A(
					PARSE_PART.encoding(1))),
		/* Backticked underscores */
			C(
				"Moo`_Sauce",
				List(0, 0),
				A("Moo_Sauce"),
				A(
					PARSE_PART.encoding(1))),
			C(
				"`_Moo`_Saucier",
				List(0, 0),
				A("_Moo_Saucier"),
				A(
					PARSE_PART.encoding(1))),
			C(
				"Moo`_`_`_Sauciest",
				List(0, 0),
				A("Moo___Sauciest"),
				A(
					PARSE_PART.encoding(1))),
			C(
				"Most`_Saucy`_",
				List(0, 0),
				A("Most_Saucy_"),
				A(
					PARSE_PART.encoding(1))),
			C(
				"Most `_Saucy",
				List(0, 0),
				A("Most", "_Saucy"),
				A(
					PARSE_PART.encoding(1),
					PARSE_PART.encoding(2))),
		/* Simple keywords and underscores. */
			C(
				"Print_",
				List(1, 1, Phrase(stringType())),
				A("Print", "_"),
				A(
					PARSE_PART.encoding(1),
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					APPEND_ARGUMENT.getEncoding())),
			C(
				"_+_",
				List(2, 2, Phrase(NUMBER.o())),
				A("_", "+", "_"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(2),  // Hoisted before the checks.
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(2),
					typeCheckEncodingForPhrase(NUMBER.o()),
					WRAP_IN_LIST.encoding(2),
					CONCATENATE.getEncoding())),
			C(
				"_+_*_",
				List(3, 3, Phrase(NUMBER.o())),
				A("_", "+", "_", "*", "_"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(2),  // Hoisted before arg 1 checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(4),  // Hoisted before arg 2 checks
					CHECK_ARGUMENT.encoding(2),
					typeCheckEncodingForPhrase(NUMBER.o()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(3),
					typeCheckEncodingForPhrase(NUMBER.o()),
					WRAP_IN_LIST.encoding(3),
					CONCATENATE.getEncoding())),
			C(
				"_;",
				List(1, 1, Phrase(Phrase(TOP.o()))),
				A("_", ";"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(2),  // Hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(Phrase(TOP.o())),
					APPEND_ARGUMENT.getEncoding())),
			C(
				"__",
				List(2, 2, Phrase(stringType())),
				A("_", "_"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(2),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					WRAP_IN_LIST.encoding(2),
					CONCATENATE.getEncoding())),
		/* Literals */
			C(
				"…#",
				List(1, 1, Phrase(LiteralToken(wholeNumbers()))),
				A("…", "#"),
				A(
					PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN.getEncoding(),
					typeCheckEncodingForPhrase(LiteralToken(wholeNumbers())),
					APPEND_ARGUMENT.getEncoding())),
			C(
				"…$",
				List(1, 1, Phrase(LiteralToken(stringType()))),
				A("…", "$"),
				A(
					PARSE_RAW_STRING_LITERAL_TOKEN.getEncoding(),
					typeCheckEncodingForPhrase(LiteralToken(stringType())),
					APPEND_ARGUMENT.getEncoding())),
		/* Backquotes. */
			C(
				"`__",
				List(1, 1, Phrase(stringType())),
				A("`", "_", "_"),
				A(
					PARSE_PART.encoding(2),
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					APPEND_ARGUMENT.getEncoding())),
			C(
				"`$_",
				List(1, 1, Phrase(stringType())),
				A("`", "$", "_"),
				A(
					PARSE_PART.encoding(2),
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					APPEND_ARGUMENT.getEncoding())),
			C(
				"_`«_",
				List(2, 2, Phrase(stringType())),
				A("_", "`", "«", "_"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(3), // Hoisted above checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(),
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(2),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					WRAP_IN_LIST.encoding(2),
					CONCATENATE.getEncoding())),
			C(
				"_``_",
				List(2, 2, Phrase(stringType())),
				A("_", "`", "`", "_"),
				A(
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(3), // Hoisted above checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					PARSE_ARGUMENT.getEncoding(),
					CHECK_ARGUMENT.encoding(2),
					typeCheckEncodingForPhrase(stringType()),
					//APPEND_ARGUMENT.encoding(), // See wrap/concatenate below
					WRAP_IN_LIST.encoding(2),
					CONCATENATE.getEncoding())),
			C(
				"`#`?`~",
				List(0, 0),
				A("`", "#", "`", "?", "`", "~"),
				A(
					PARSE_PART.encoding(2),
					PARSE_PART.encoding(4),
					PARSE_PART.encoding(6))),

			C(
				"`|`|_`|`|",
				List(1, 1, Phrase(NUMBER.o())),
				A("`", "|", "`", "|", "_", "`", "|", "`", "|"),
				A(
					PARSE_PART.encoding(2),
					PARSE_PART.encoding(4),
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(7), // Hoisted before checks
					PARSE_PART.encoding(9), // Also hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					APPEND_ARGUMENT.getEncoding())),
		/* Repeated groups. */
			C(
				"«_;»",
				List(1, 1, Phrase(zeroOrMoreOf(NUMBER.o()))),
				A("«", "_", ";", "»"),
				A(
					EMPTY_LIST.getEncoding(),
					BRANCH_FORWARD.encoding(16),
					// First unrolled loop
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(3), // Hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					APPEND_ARGUMENT.getEncoding(),
					BRANCH_FORWARD.encoding(16), // Maybe that's all
					// 9: Top of loop.
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(3), // Hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					APPEND_ARGUMENT.getEncoding(),
					BRANCH_FORWARD.encoding(16), // Maybe that's all
					JUMP_BACKWARD.encoding(9), // To top of loop
					// 16: After loop
					APPEND_ARGUMENT.getEncoding())),
			C(
				"«x»",
				List(1, 1, List(0, -1, List(0, 0))),
				A("«", "x", "»"),
				A(
					EMPTY_LIST.getEncoding(), // whole expression
					BRANCH_FORWARD.encoding(13), // allow zero occurrences
					PARSE_PART.encoding(2), // unroll first one.
					EMPTY_LIST.getEncoding(), // first occurrence has no arguments.
					BRANCH_FORWARD.encoding(12), // done after one?
					APPEND_ARGUMENT.getEncoding(), // save it and parse more.
					//7: Start of loop after unrolled iteration.
					PARSE_PART.encoding(2),
					EMPTY_LIST.getEncoding(), // other occurrences have no args.
					BRANCH_FORWARD.encoding(12), // exit loop?
					APPEND_ARGUMENT.getEncoding(), // capture it and continue
					JUMP_BACKWARD.encoding(7),
					//12:
					APPEND_ARGUMENT.getEncoding(), // save last occurrence
					//13:
					APPEND_ARGUMENT.getEncoding())),  // save all occurrences
			C(
				"«x y»",
				List(1, 1, List(0, -1, List(0, 0))),
				A("«", "x", "y", "»"),
				A(
					EMPTY_LIST.getEncoding(), // whole expression
					BRANCH_FORWARD.encoding(15), // allow zero occurrences
					PARSE_PART.encoding(2), // unroll first x...
					PARSE_PART.encoding(3), // ... and y.
					EMPTY_LIST.getEncoding(), // first occurrence has no arguments.
					BRANCH_FORWARD.encoding(14), // done after one?
					APPEND_ARGUMENT.getEncoding(), // save it and parse more.
					//8: Start of loop after unrolled iteration.
					PARSE_PART.encoding(2),  // x
					PARSE_PART.encoding(3),  // y
					EMPTY_LIST.getEncoding(), // other occurrences have no args.
					BRANCH_FORWARD.encoding(14), // exit loop?
					APPEND_ARGUMENT.getEncoding(), // capture it and continue
					JUMP_BACKWARD.encoding(8),
					//14:
					APPEND_ARGUMENT.getEncoding(), // save all occurrences
					//15:
					APPEND_ARGUMENT.getEncoding())),
			C(
				"«x_y»",
				List(1, 1, List(0, -1, Phrase(NUMBER.o()))),
				A("«", "x", "_", "y", "»"),
				A(
					// NOTE: The group's left half has one argument and the
					// right half has none (it's elided).  Use single-wrapping
					// to avoid creating a sequence of singleton tuples.
					EMPTY_LIST.getEncoding(), // whole expression
					BRANCH_FORWARD.encoding(18), // allow zero occurrences
					PARSE_PART.encoding(2), // unroll first occurrence
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(4), // Hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					APPEND_ARGUMENT.getEncoding(), // save it and parse more.
					BRANCH_FORWARD.encoding(18), // done after one?
					//10: Start of loop after unrolled iteration.
					PARSE_PART.encoding(2),  // next x
					PARSE_ARGUMENT.getEncoding(),
					PARSE_PART.encoding(4), // Hoisted before checks
					CHECK_ARGUMENT.encoding(1),
					typeCheckEncodingForPhrase(NUMBER.o()),
					APPEND_ARGUMENT.getEncoding(), // save it and parse more.
					BRANCH_FORWARD.encoding(18), // exit loop?
					JUMP_BACKWARD.encoding(10),
					//18:
					APPEND_ARGUMENT.getEncoding())),  // save all occurrences
		C(
			"«_:_»",
			List(1, 1, List(0, -1, List(2, 2, Phrase(NUMBER.o())))),
			A("«", "_", ":", "_", "»"),
			A(
				// NOTE: The group's left half has two argument positions, so we
				// have to double-wrap (i.e., produce a tuple of 2-tuples).
				EMPTY_LIST.getEncoding(), // whole expression
				BRANCH_FORWARD.encoding(25), // allow zero occurrences
				PARSE_ARGUMENT.getEncoding(),
				PARSE_PART.encoding(3), // Hoisted before checks
				CHECK_ARGUMENT.encoding(1),
				typeCheckEncodingForPhrase(NUMBER.o()),
				PARSE_ARGUMENT.getEncoding(),
				CHECK_ARGUMENT.encoding(2),
				typeCheckEncodingForPhrase(NUMBER.o()),
				WRAP_IN_LIST.encoding(2),
				BRANCH_FORWARD.encoding(24), // done after one?
				APPEND_ARGUMENT.getEncoding(), // save it and parse more.
				//13: Start of loop after unrolled iteration.
				PARSE_ARGUMENT.getEncoding(),
				PARSE_PART.encoding(3), // Hoisted before checks
				CHECK_ARGUMENT.encoding(1),
				typeCheckEncodingForPhrase(NUMBER.o()),
				PARSE_ARGUMENT.getEncoding(),
				CHECK_ARGUMENT.encoding(2),
				typeCheckEncodingForPhrase(NUMBER.o()),
				WRAP_IN_LIST.encoding(2),
				BRANCH_FORWARD.encoding(24), // exit loop?
				APPEND_ARGUMENT.getEncoding(), // save it and parse more.
				JUMP_BACKWARD.encoding(13),
				//24:
				APPEND_ARGUMENT.getEncoding(), // save the last pair
				//25:
				APPEND_ARGUMENT.getEncoding())),  // save all occurrences
		C("«»",
			List(1, 1, List(0, -1, List(0, 0))),
			A("«", "»"),
			A(
				// This is a degenerate case, and can't actually pass the
				// progress checks because no tokens can ever be parsed.
				EMPTY_LIST.getEncoding(), // Zero occurrences.
				BRANCH_FORWARD.encoding(16), // Try zero occurrences.
				//3: Unrolled first occurrence
				SAVE_PARSE_POSITION.getEncoding(), //Occurrences must make progress
				EMPTY_LIST.getEncoding(), // Unrolled first occurrence.
				BRANCH_FORWARD.encoding(13), // Try a single occurrence.
				APPEND_ARGUMENT.getEncoding(), // Add the occurrence.
				ENSURE_PARSE_PROGRESS.getEncoding(), // Make sure it was productive
				//8: second and later occurrences.
				EMPTY_LIST.getEncoding(),
				BRANCH_FORWARD.encoding(13), // Try the new occurrence.
				APPEND_ARGUMENT.getEncoding(), // Save it.
				ENSURE_PARSE_PROGRESS.getEncoding(), // Make sure it was productive
				JUMP_BACKWARD.encoding(8), // Try another
				//13: Save latest occurrence and try it.
				APPEND_ARGUMENT.getEncoding(),
				ENSURE_PARSE_PROGRESS.getEncoding(), // Must have made progress
				DISCARD_SAVED_PARSE_POSITION.getEncoding(), // Chuck progress mark
				APPEND_ARGUMENT.getEncoding())), // Save list as sole argument.
//		C("«»«»",
//			A("«", "»", "«", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(9),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(23),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(21),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(16),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«_»",
//			A("«", "_", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(1),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(10),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		/* Repeated groups with double dagger. */
//		C("«_‡,»",
//			A("«", "_", "‡", ",", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(1),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(11),
//				PARSE_PART.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«‡»",
//			A("«", "‡", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(9),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("new_«with«_=_‡,»»",
//			A("new", "_", "«", "with", "«", "_", "=", "_", "‡", ",", "»", "»"),
//			A(
//				PARSE_PART.encoding(1),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(1),
//				APPEND_ARGUMENT.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(33),
//				PARSE_PART.encoding(4),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(27),
//				EMPTY_LIST.encoding(),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(2),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(7),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(3),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(25),
//				PARSE_PART.encoding(10),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(12),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(32),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(8),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		/* Counting groups. */
//		C("«x»#",
//			A("«", "x", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(2),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(10),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«x y»#",
//			A("«", "x", "y", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(2),
//				PARSE_PART.encoding(3),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(11),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«»#",
//			A("«", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(10),
//				EMPTY_LIST.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(9),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«»#«»#",
//			A("«", "»", "#", "«", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(10),
//				EMPTY_LIST.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(9),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(22),
//				EMPTY_LIST.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(21),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(16),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		/* Counting groups with double dagger. */
//		C("«‡»#",
//			A("«", "‡", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(10),
//				EMPTY_LIST.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(9),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«fish‡»#",
//			A("«", "fish", "‡", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(2),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(10),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«‡face»#",
//			A("«", "‡", "face", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(11),
//				EMPTY_LIST.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(10),
//				PARSE_PART.encoding(3),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«fish‡face»#",
//			A("«", "fish", "‡", "face", "»", "#"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(2),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(11),
//				PARSE_PART.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("««fish‡face»#»",
//			A("«", "«", "fish", "‡", "face", "»", "#", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(22),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(15),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(3),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(14),
//				PARSE_PART.encoding(5),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(7),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(21),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		/* Optional groups. */
//		C("«x»?",
//			A("«", "x", "»", "?"),
//			A(
//				BRANCH.encoding(8),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(2),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PUSH_TRUE.encoding(),
//				JUMP.encoding(9),
//				PUSH_FALSE.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«x y»?",
//			A("«", "x", "y", "»", "?"),
//			A(
//				BRANCH.encoding(9),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(2),
//				PARSE_PART.encoding(3),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PUSH_TRUE.encoding(),
//				JUMP.encoding(10),
//				PUSH_FALSE.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«»?",
//			A("«", "»", "?"),
//			A(
//				BRANCH.encoding(7),
//				SAVE_PARSE_POSITION.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PUSH_TRUE.encoding(),
//				JUMP.encoding(8),
//				PUSH_FALSE.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("««bagel»#«friend»?»",
//			A("«", "«", "bagel", "»", "#", "«", "friend", "»", "?", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(33),
//				EMPTY_LIST.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(15),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(3),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(14),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(8),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(25),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(7),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PUSH_TRUE.encoding(),
//				JUMP.encoding(26),
//				PUSH_FALSE.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(31),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		/* Completely optional groups. */
//		C("very⁇good",
//			A("very", "⁇", "good"),
//			A(
//				BRANCH.encoding(6),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(1),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(3))),
//		C("«very extremely»⁇good",
//			A("«", "very", "extremely", "»", "⁇", "good"),
//			A(
//				BRANCH.encoding(7),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(2),
//				PARSE_PART.encoding(3),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PARSE_PART.encoding(6))),
//		/* Case insensitive. */
//		C("fnord~",
//			A("fnord", "~"),
//			A(
//				PARSE_PART_CASE_INSENSITIVELY.encoding(1))),
//		C("the~_",
//			A("the", "~", "_"),
//			A(
//				PARSE_PART_CASE_INSENSITIVELY.encoding(1),
//				PARSE_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(1),
//				APPEND_ARGUMENT.encoding())),
//		C("«x~»",
//			A("«", "x", "~", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				EMPTY_LIST.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				BRANCH.encoding(10),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«x»~",
//			A("«", "x", "»", "~"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				EMPTY_LIST.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				BRANCH.encoding(10),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«x~y»",
//			A("«", "x", "~", "y", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(13),
//				EMPTY_LIST.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				PARSE_PART.encoding(4),
//				BRANCH.encoding(11),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«x y»~",
//			A("«", "x", "y", "»", "~"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(13),
//				EMPTY_LIST.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
//				BRANCH.encoding(11),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		C("«x y»#~",
//			A("«", "x", "y", "»", "#", "~"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(12),
//				EMPTY_LIST.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(11),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(4),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				CONVERT.encoding(LIST_TO_SIZE.number()),
//				APPEND_ARGUMENT.encoding())),
//		C("«x y»?~",
//			A("«", "x", "y", "»", "?", "~"),
//			A(
//				BRANCH.encoding(9),
//				SAVE_PARSE_POSITION.encoding(),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(2),
//				PARSE_PART_CASE_INSENSITIVELY.encoding(3),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				PUSH_TRUE.encoding(),
//				JUMP.encoding(10),
//				PUSH_FALSE.encoding(),
//				APPEND_ARGUMENT.encoding())),
//		/* Alternation. */
//		C("hello|greetings",
//			A("hello", "|", "greetings"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				BRANCH.encoding(5),
//				PARSE_PART.encoding(1),
//				JUMP.encoding(6),
//				PARSE_PART.encoding(3),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding())),
//		C("a|b|c|d|e|f|g",
//			A("a", "|", "b", "|", "c", "|", "d", "|", "e", "|", "f", "|", "g"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				BRANCH.encoding(5),
//				PARSE_PART.encoding(1),
//				JUMP.encoding(21),
//				BRANCH.encoding(8),
//				PARSE_PART.encoding(3),
//				JUMP.encoding(21),
//				BRANCH.encoding(11),
//				PARSE_PART.encoding(5),
//				JUMP.encoding(21),
//				BRANCH.encoding(14),
//				PARSE_PART.encoding(7),
//				JUMP.encoding(21),
//				BRANCH.encoding(17),
//				PARSE_PART.encoding(9),
//				JUMP.encoding(21),
//				BRANCH.encoding(20),
//				PARSE_PART.encoding(11),
//				JUMP.encoding(21),
//				PARSE_PART.encoding(13),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding())),
//		C("«fruit bats»|sloths|carp|«breakfast cereals»",
//			A("«", "fruit", "bats", "»", "|", "sloths", "|", "carp", "|", "«",
// "breakfast", "cereals", "»"),
//			A(
//				SAVE_PARSE_POSITION.encoding(),
//				BRANCH.encoding(17),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(15),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(2),
//				PARSE_PART.encoding(3),
//				BRANCH.encoding(13),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(6),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				JUMP.encoding(36),
//				BRANCH.encoding(20),
//				PARSE_PART.encoding(6),
//				JUMP.encoding(36),
//				BRANCH.encoding(23),
//				PARSE_PART.encoding(8),
//				JUMP.encoding(36),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(35),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(11),
//				PARSE_PART.encoding(12),
//				BRANCH.encoding(33),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(26),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding())),
//		C("«x|y»!",
//			A("«", "x", "|", "y", "»", "!"),
//			A(
//				BRANCH.encoding(5),
//				PARSE_PART.encoding(2),
//				PUSH_INTEGER_LITERAL.encoding(1),
//				JUMP.encoding(7),
//				PARSE_PART.encoding(4),
//				PUSH_INTEGER_LITERAL.encoding(2),
//				APPEND_ARGUMENT.encoding())),
//		C("[««…:_†§‡,»`|»?«Primitive…#«(…:_†)»?§;»?«`$…«:_†»?;
// §»?«_!»«_!»?]«:_†»?«^«_†‡,»»?",
//			A("[",
//					"«", "«", "…", ":", "_", "†", "§", "‡", ",", "»",
//						"`", "|", "»", "?",
//					"«", "Primitive", "…", "#",
//						"«", "(", "…", ":", "_", "†", ")", "»", "?", "§", ";",
// "»", "?",
//					"«", "`", "$", "…", "«", ":", "_", "†", "»", "?", ";",
// "§", "»", "?",
//					"«", "_", "!", "»",
//					"«", "_", "!", "»", "?",
//				"]",
//				"«", ":", "_", "†", "»", "?",
//				"«", "^", "«", "_", "†", "‡", ",", "»", "»", "?"),
//			A(
//			// open square bracket:
//				PARSE_PART.encoding(1),  // = "["
//			// argument declarations:
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(33),
//				CHECK_AT_MOST.encoding(1),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(26),
//				EMPTY_LIST.encoding(),
//				PARSE_RAW_KEYWORD_TOKEN.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(5),  // = ":"
//				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
//				CHECK_ARGUMENT.encoding(2),
//				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
//				APPEND_ARGUMENT.encoding(),
//				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(4),
//				RUN_PREFIX_FUNCTION.encoding(1),
//				BRANCH.encoding(24),
//				PARSE_PART.encoding(10),  // = ","
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(9),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(13),  // = "|"
//				BRANCH.encoding(32),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(5),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// optional primitive declaration:
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(74),
//				CHECK_AT_MOST.encoding(1),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(17),  // = "Primitive"
//				PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(63),
//				CHECK_AT_MOST.encoding(1),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(21),  // = "("
//				PARSE_RAW_KEYWORD_TOKEN.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(23),  // = ":"
//				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
//				CHECK_ARGUMENT.encoding(5),
//				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(26),  // = ")"
//				BRANCH.encoding(61),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(46),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(3),
//				RUN_PREFIX_FUNCTION.encoding(2),
//				PARSE_PART.encoding(30),  // = ";"
//				BRANCH.encoding(72),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(38),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// optional label declaration:
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(108),
//				CHECK_AT_MOST.encoding(1),
//				EMPTY_LIST.encoding(),
//				PARSE_PART.encoding(35),  // = "$"
//				PARSE_RAW_KEYWORD_TOKEN.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(97),
//				CHECK_AT_MOST.encoding(1),
//				PARSE_PART.encoding(38),  // = ":"
//				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
//				CHECK_ARGUMENT.encoding(7),
//				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(96),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(87),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				PARSE_PART.encoding(43),  // = ";"
//				PREPARE_TO_RUN_PREFIX_FUNCTION.encoding(3),
//				RUN_PREFIX_FUNCTION.encoding(3),
//				BRANCH.encoding(106),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(79),
//				APPEND_ARGUMENT.encoding(),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// statements and declarations
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(120),
//				PARSE_TOP_VALUED_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(8),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(119),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(113),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// optional return expression
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(133),
//				CHECK_AT_MOST.encoding(1),
//				PARSE_TOP_VALUED_ARGUMENT.encoding(),
//				CHECK_ARGUMENT.encoding(9),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(132),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(125),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// close square bracket
//				PARSE_PART.encoding(56),  // = "]"
//			// optional result type declaration
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(149),
//				CHECK_AT_MOST.encoding(1),
//				PARSE_PART.encoding(58),  // = ":"
//				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
//				CHECK_ARGUMENT.encoding(10),
//				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(148),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(139),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//			// optional exceptions declaration
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(174),
//				CHECK_AT_MOST.encoding(1),
//				PARSE_PART.encoding(64),  // = "^"
//				SAVE_PARSE_POSITION.encoding(),
//				EMPTY_LIST.encoding(),
//				BRANCH.encoding(168),
//				PARSE_ARGUMENT_IN_MODULE_SCOPE.encoding(),
//				CHECK_ARGUMENT.encoding(11),
//				CONVERT.encoding(EVALUATE_EXPRESSION.number()),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(167),
//				PARSE_PART.encoding(69),  // = ","
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(159),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding(),
//				BRANCH.encoding(173),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				JUMP.encoding(154),
//				ENSURE_PARSE_PROGRESS.encoding(),
//				DISCARD_SAVED_PARSE_POSITION.encoding(),
//				APPEND_ARGUMENT.encoding()
//			))
		};
	}

	/**
	 * A helper for creating an {@code int} encoding a {@link
	 * ParsingOperation#TYPE_CHECK_ARGUMENT} for a phrase that yields the
	 * indicated type.
	 *
	 * @param type The type to check the latest parsed argument against.
	 * @return An {@code int} encoding a type check parsing operation.
	 */
	private static int typeCheckEncodingForPhrase (final A_Type type)
	{
		return TYPE_CHECK_ARGUMENT.encoding(
			indexForConstant(PARSE_PHRASE.create(type)));
	}

	/**
	 * Describe a sequence of instructions, one per line, and answer the
	 * resulting string.
	 *
	 * @param instructions A sequence of integer-encoded parse instructions.
	 * @return The descriptive string.
	 */
	private static String dumpInstructions (final List<Integer> instructions)
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
			final ParsingOperation operation = decode(instructionEncoding);
			builder.append(operation.name());
			if (operation.ordinal() >= distinctInstructions)
			{
				builder.append('(');
				builder.append(operand(instructionEncoding));
				builder.append(')');
			}
			first = false;
		}
		return builder.toString();
	}

	/**
	 * Provide a {@link Stream} of {@link Case}s to test.
	 *
	 * @return The {@link Stream} of {@link Arguments} for parameterizing
	 *         a JUnit test.
	 */
	private static Stream<Arguments> casesProvider() {
		return stream(splitCases).map(Arguments::of);
	}

	/**
	 * Check that the {@link MessageSplitter} processes the information in the
	 * given {@link Case} as expected.
	 *
	 * @param splitCase The {@link Case} to check.
	 * @throws MalformedMessageException
	 *         If the method name is determined to be malformed.
	 * @throws SignatureException
	 *         If the {@link Case}'s type signature is inconsistent with the
	 *         method name.
	 */
	@DisplayName("Message Splitter")
	@ParameterizedTest(name = "{index} => case={0}")
	@MethodSource("casesProvider")
	public void testSplitCase (
		final Case splitCase)
	throws MalformedMessageException, SignatureException
	{
		final String msgString = splitCase.message;
		final A_String message = stringFrom(msgString);
		final MessageSplitter splitter = new MessageSplitter(message);
		final A_Tuple parts = splitter.getMessagePartsTuple();
		assert splitCase.tokens.length == parts.tupleSize();
		for (int i = 1; i <= parts.tupleSize(); i++)
		{
			assertEquals(
				splitCase.tokens[i - 1],
				parts.tupleAt(i).asNativeString(),
				"Split was not as expected");
		}
		final A_Type tupleType = splitCase.listPhraseType.expressionType();
		final A_Type sizeRange = tupleType.sizeRange();
		assert sizeRange.lowerBound().equals(sizeRange.upperBound());
		final A_Tuple typeTuple =
			tupleType.tupleOfTypesFromTo(
				1, sizeRange.lowerBound().extractInt());
		splitter.checkImplementationSignature(
			functionType(typeTuple, TOP.o()));
		final A_Tuple instructionsTuple =
			splitter.instructionsTupleFor(splitCase.listPhraseType);
		final List<Integer> instructionsList = new ArrayList<>();
		for (final A_Number instruction : instructionsTuple)
		{
			instructionsList.add(instruction.extractInt());
		}
		if (!splitCase.instructions.toString().equals(
			instructionsList.toString()))
		{
			System.out.println(splitCase.instructions);
			System.out.println(instructionsList);
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
