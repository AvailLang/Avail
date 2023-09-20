/*
 * MessageSplitterTest.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
@file:Suppress("TestFunctionName")

package avail.test

import avail.compiler.APPEND_ARGUMENT
import avail.compiler.ArityOneParsingOperation
import avail.compiler.BRANCH_FORWARD
import avail.compiler.CHECK_ARGUMENT
import avail.compiler.CONCATENATE
import avail.compiler.CONVERT
import avail.compiler.DISCARD_SAVED_PARSE_POSITION
import avail.compiler.EMPTY_LIST
import avail.compiler.ENSURE_PARSE_PROGRESS
import avail.compiler.JUMP_BACKWARD
import avail.compiler.JUMP_FORWARD
import avail.compiler.PARSE_ARGUMENT
import avail.compiler.PARSE_PART
import avail.compiler.PARSE_PART_CASE_INSENSITIVELY
import avail.compiler.PARSE_RAW_LITERAL_TOKEN
import avail.compiler.PUSH_LITERAL
import avail.compiler.ParsingConversionRule.LIST_TO_SIZE
import avail.compiler.ParsingOperation
import avail.compiler.SAVE_PARSE_POSITION
import avail.compiler.TYPE_CHECK_ARGUMENT
import avail.compiler.WRAP_IN_LIST
import avail.compiler.splitter.MessageSplitter
import avail.compiler.splitter.MessageSplitter.Companion.indexForConstant
import avail.compiler.splitter.MessageSplitter.Companion.indexForFalse
import avail.compiler.splitter.MessageSplitter.Companion.indexForTrue
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.ListPhraseTypeDescriptor
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListPhraseType
import avail.descriptor.types.LiteralTokenTypeDescriptor
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import avail.descriptor.types.PhraseTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import avail.utility.cast
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Arrays
import java.util.stream.Stream

/**
 * Test the [MessageSplitter]. It splits method names into a sequence of tokens
 * to expect and underscores, but it also implements the repeated argument
 * mechanism by producing a sequence of mini-instructions to say how to do the
 * parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MessageSplitterTest private constructor ()
{
	/**
	 * This is a helper class for building test cases.
	 *
	 * @property message
	 *   The [String] which is the method name being tested.
	 * @property listPhraseType
	 *   The phrase type for which to instantiate a [A_DefinitionParsingPlan].
	 *
	 * @constructor
	 *
	 * Construct a new `Case`.
	 *
	 * @param message
	 *   The method name to split.
	 * @param listPhraseType
	 *   The list phrase type for which the instructions should be specialized.
	 * @param tokens
	 *   The expected substrings comprising the method name.
	 * @param instructions
	 *   The expected [ParsingOperation]s.
	 */
	class Case (
		val message: String,
		val listPhraseType: A_Type,
		tokens: Array<String>,
		instructions: Array<ParsingOperation>)
	{
		/**
		 * The sequence of [String]s into which the method name should
		 * decompose.
		 */
		val tokens = tokens.clone()

		/**
		 * The sequence of int-encoded [ParsingOperation]s that can parse
		 * sends of a method with this name.
		 */
		val instructions = instructions.toList()

		override fun toString(): String =
			"$message @ $listPhraseType → ${tokens.contentToString()}"
	}

	companion object
	{
		/**
		 * Construct a simple test case. The given message (a [String]) is
		 * expected to be split into the given array of tokens (also strings)
		 * and produce the given sequence of instructions (numerical encodings
		 * of [ParsingOperation]s).
		 *
		 * @param message
		 *   The message to split.
		 * @param tokens
		 *   The separated tokens from the message.
		 * @param listPhraseType
		 *   A [list phrase type][ListPhraseTypeDescriptor].
		 * @param instructions
		 *   The [ParsingOperation]s to parse this message.
		 * @return
		 *   An array of Strings starting with the message, then all the tokens,
		 *   then a print representation of the numeric instructions converted
		 *   to a [List].
		 */
		private fun C (
			message: String,
			listPhraseType: A_Type,
			tokens: Array<String>,
			instructions: Array<ParsingOperation>
		): Case
		{
			assert(
				listPhraseType.isSubtypeOf(
					List(
						0,
						-1,
						Phrase(Types.ANY.o))))
			return Case(message, listPhraseType, tokens, instructions)
		}

		/**
		 * Construct a list phrase type from the lower bound, upper bound (-1
		 * for infinity), and vararg array of subexpression phrase types.
		 *
		 * @param lowerBound
		 *   The smallest this tuple can be.
		 * @param upperBound
		 *   The largest this tuple can be, or -1 if it's unbounded.
		 * @param expressionPhraseTypes
		 *   The `vararg` array of subexpression phrase types, the last one
		 *   being implicitly repeated if the upperBound is bigger than the
		 *   length of the array.
		 * @return A [list phrase type][ListPhraseTypeDescriptor].
		 * 1
		 */
		private fun List (
			lowerBound: Int,
			upperBound: Int,
			vararg expressionPhraseTypes: A_Type
		): A_Type
		{
			assert(upperBound >= -1)
			val lower: A_Number = fromInt(lowerBound)
			val upperPlusOne =
				if (upperBound >= 0) fromLong(upperBound + 1L)
				else positiveInfinity
			val subexpressionsTupleType = tupleTypeForSizesTypesDefaultType(
				integerRangeType(
					lower,
					true,
					upperPlusOne,
					false),
				tupleFromArray(*expressionPhraseTypes.cast()),
				if (expressionPhraseTypes.isNotEmpty())
					expressionPhraseTypes[expressionPhraseTypes.size - 1]
				else bottom)
			return createListPhraseType(
				PhraseKind.LIST_PHRASE,
				mostGeneralTupleType,
				subexpressionsTupleType)
		}

		/**
		 * Construct a list phrase type from the given type that the phrase type
		 * should yield.
		 *
		 * @param yieldType
		 *   The type that the resulting phrase type would yield.
		 * @return
		 *   A [phrase type][PhraseTypeDescriptor].
		 */
		private fun Phrase (yieldType: A_Type?) =
			PhraseKind.PARSE_PHRASE.create(yieldType!!)

		/**
		 * Construct a literal token type which yields the given type.
		 *
		 * @param valueType
		 *   The type that the resulting literal token would yield.
		 * @return
		 *   A [literal token type][LiteralTokenTypeDescriptor].
		 */
		private fun LiteralToken (valueType: A_Type?) = literalTokenType(valueType!!)

		/**
		 * Create a simple array.  This greatly reduces the syntactic noise of
		 * the test cases.  It's "A(...)" versus "new Integer[] {...}".
		 *
		 * @param X
		 *   The type of elements in the array.
		 * @param x
		 *   The varargs array passed in.
		 * @return
		 *   The same array.
		 */
		@SafeVarargs fun <X> A (vararg x: X): Array<X> = x.cast()

		/** Test cases. */
		private val splitCases: Array<Case>

		/**
		 * A helper for creating a [TYPE_CHECK_ARGUMENT] for a phrase that
		 * yields the indicated type.
		 *
		 * @param type
		 *   The type to check the latest parsed argument against.
		 * @return
		 *   An `int` encoding a type check parsing operation.
		 */
		private fun typeCheckEncodingForPhrase (type: A_Type) =
			TYPE_CHECK_ARGUMENT(
				indexForConstant(PhraseKind.PARSE_PHRASE.create(type))
			)

		/**
		 * Describe a sequence of instructions, one per line, and answer the
		 * resulting string.
		 *
		 * @param instructions
		 *   A sequence of integer-encoded parse instructions.
		 * @return
		 *   The descriptive string.
		 */
		private fun dumpInstructions (
			instructions: List<ParsingOperation>
		): String
		{
			val builder = StringBuilder()
			var first = true
			for (instruction in instructions)
			{
				if (!first)
				{
					builder.append(",\n")
				}
				builder.append('\t')
				builder.append(instruction.name)
				if (instruction is ArityOneParsingOperation)
				{
					builder.append('(')
					builder.append(instruction.operand)
					builder.append(')')
				}
				first = false
			}
			return builder.toString()
		}

		/**
		 * Provide a [Stream] of [Case]s to test.
		 *
		 * @return
		 *   The [Stream] of [Arguments] for parameterizing a JUnit test.
		 */
		@Suppress("unused")
		@JvmStatic
		fun casesProvider(): Stream<Arguments> =
			Arrays.stream(splitCases).map { arguments: Case? ->
				Arguments.of(arguments)
			}

		init
		{
			splitCases = arrayOf(
				C(
					"Foo",
					List(0, 0),
					A("Foo"),
					A(PARSE_PART(1))),
				/* Backticked underscores */
				C(
					"Moo`_Sauce",
					List(0, 0),
					A("Moo_Sauce"),
					A(PARSE_PART(1))),
				C(
					"`_Moo`_Saucier",
					List(0, 0),
					A("_Moo_Saucier"),
					A(PARSE_PART(1))),
				C(
					"Moo`_`_`_Sauciest",
					List(0, 0),
					A("Moo___Sauciest"),
					A(PARSE_PART(1))),
				C(
					"Most`_Saucy`_",
					List(0, 0),
					A("Most_Saucy_"),
					A(PARSE_PART(1))),
				C(
					"Most `_Saucy",
					List(0, 0),
					A(
						"Most",
						"_Saucy"),
					A(
						PARSE_PART(1),
						PARSE_PART(2))),
				/* Simple keywords and underscores. */
				C(
					"Print_",
					List(
						1,
						1,
						Phrase(stringType)),
					A(
						"Print",
						"_"),
					A(
						PARSE_PART(1),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						APPEND_ARGUMENT
					)),
				C(
					"_+_",
					List(
						2,
						2,
						Phrase(NUMBER.o)),
					A(
						"_",
						"+",
						"_"),
					A(
						PARSE_ARGUMENT,
						// Hoisted before the checks.
						PARSE_PART(2),
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						// See wrap/concatenate below
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						WRAP_IN_LIST(2),
						CONCATENATE
					)),
				C(
					"_+_*_",
					List(
						3,
						3,
						Phrase(NUMBER.o)),
					A(
						"_",
						"+",
						"_",
						"*",
						"_"),
					A(
						PARSE_ARGUMENT,
						// Hoisted before arg 1 checks
						PARSE_PART(2),
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						// See wrap/concatenate below
						PARSE_ARGUMENT,
						// Hoisted before arg 2 checks
						PARSE_PART(4),
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						// See wrap/concatenate below
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(3),
						typeCheckEncodingForPhrase(NUMBER.o),
						WRAP_IN_LIST(3),
						CONCATENATE)),
				C(
					"_;",
					List(
						1,
						1,
						Phrase(Phrase(TOP.o))),
					A("_", ";"),
					A(
						PARSE_ARGUMENT,
						// Hoisted before checks
						PARSE_PART(2),
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(Phrase(TOP.o)),
						APPEND_ARGUMENT)),
				C(
					"__",
					List(
						2,
						2,
						Phrase(stringType)),
					A("_", "_"),
					A(
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						// See wrap/concatenate below
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(stringType),
						// See wrap/concatenate below
						WRAP_IN_LIST(2),
						CONCATENATE)),
				/* Literals */
				C(
					"…#",
					List(
						1,
						1,
						Phrase(
							LiteralToken(
								wholeNumbers))),
					A("…", "#"),
					A(
						PARSE_RAW_LITERAL_TOKEN,
						typeCheckEncodingForPhrase(LiteralToken(wholeNumbers)),
						APPEND_ARGUMENT)),
				/* Backquotes. */
				C(
					"`__",
					List(
						1,
						1,
						Phrase(stringType)),
					A(
						"`",
						"_",
						"_"),
					A(
						PARSE_PART(2),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						APPEND_ARGUMENT)),
				C(
					"`#_",
					List(
						1,
						1,
						Phrase(stringType)),
					A(
						"`",
						"#",
						"_"),
					A(
						PARSE_PART(2),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						APPEND_ARGUMENT)),
				C(
					"_`«_",
					List(
						2,
						2,
						Phrase(stringType)),
					A(
						"_",
						"`",
						"«",
						"_"),
					A(
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted above checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(stringType),
						// See wrap/concatenate below
						WRAP_IN_LIST(2),
						CONCATENATE)),
				C(
					"_``_",
					List(
						2,
						2,
						Phrase(stringType)),
					A(
						"_",
						"`",
						"`",
						"_"),
					A(
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted above checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						// See wrap/concatenate below
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(stringType),
						WRAP_IN_LIST(2),
						CONCATENATE)),
				C(
					"`#`?`~",
					List(0, 0),
					A(
						"`",
						"#",
						"`",
						"?",
						"`",
						"~"),
					A(
						PARSE_PART(2),
						PARSE_PART(4),
						PARSE_PART(6))),
				C(
					"`|`|_`|`|",
					List(
						1,
						1,
						Phrase(NUMBER.o)),
					A(
						"`",
						"|",
						"`",
						"|",
						"_",
						"`",
						"|",
						"`",
						"|"),
					A(
						PARSE_PART(2),
						PARSE_PART(4),
						PARSE_ARGUMENT,
						PARSE_PART(7), // Hoisted before checks
						PARSE_PART(9), // Also hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT)),
				/* Repeated groups. */
				C(
					"«_;»",
					List(
						1,
						1,
						Phrase(
							zeroOrMoreOf(NUMBER.o))),
					A(
						"«",
						"_",
						";",
						"»"),
					A(
						EMPTY_LIST,
						BRANCH_FORWARD(16), // First unrolled loop
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT,
						BRANCH_FORWARD(16), // Maybe that's all
						// 9: Top of loop.
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT,
						BRANCH_FORWARD(16), // Maybe that's all
						JUMP_BACKWARD(9), // To top of loop
						// 16: After loop
						APPEND_ARGUMENT)),
				C(
					"«x»",
					List(
						1,
						1,
						List(
							0,
							-1,
							List(0, 0))),
					A(
						"«",
						"x",
						"»"),
					A(
						EMPTY_LIST, // whole expression
						BRANCH_FORWARD(13), // allow zero occurrences
						PARSE_PART(2), // unroll first one.
						EMPTY_LIST, // first occurrence has no arguments.
						BRANCH_FORWARD(12), // done after one?
						APPEND_ARGUMENT, // save it and parse more.
						//7: Start of loop after unrolled iteration.
						PARSE_PART(2),
						EMPTY_LIST, // other occurrences have no args.
						BRANCH_FORWARD(12), // exit loop?
						APPEND_ARGUMENT, // capture it and continue
						JUMP_BACKWARD(7),
						//12:
						APPEND_ARGUMENT, // save last occurrence
						//13:
						APPEND_ARGUMENT)), // save all occurrences
				C(
					"«x y»",
					List(
						1,
						1,
						List(
							0,
							-1,
							List(0, 0))),
					A(
						"«",
						"x",
						"y",
						"»"),
					A(
						EMPTY_LIST, // whole expression
						BRANCH_FORWARD(15), // allow zero occurrences
						PARSE_PART(2), // unroll first x...
						PARSE_PART(3), // ... and y.
						EMPTY_LIST, // first occurrence has no arguments.
						BRANCH_FORWARD(14), // done after one?
						APPEND_ARGUMENT, // save it and parse more.
						//8: Start of loop after unrolled iteration.
						PARSE_PART(2), // x
						PARSE_PART(3), // y
						EMPTY_LIST, // other occurrences have no args.
						BRANCH_FORWARD(14), // exit loop?
						APPEND_ARGUMENT, // capture it and continue
						JUMP_BACKWARD(8),
						//14:
						APPEND_ARGUMENT, // save all occurrences
						//15:
						APPEND_ARGUMENT)),
				C(
					"«x_y»",
					List(
						1,
						1,
						List(
							0,
							-1,
							Phrase(NUMBER.o))),
					A(
						"«",
						"x",
						"_",
						"y",
						"»"),
					A(
						// NOTE: The group's left half has one argument and the
						// right half has none (it's elided).  Use
						// single-wrapping to avoid creating a sequence of
						// singleton tuples.
						EMPTY_LIST, // whole expression
						BRANCH_FORWARD(18), // allow zero occurrences
						PARSE_PART(2), // unroll first occurrence
						PARSE_ARGUMENT,
						PARSE_PART(4), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT, // save it and parse more.
						BRANCH_FORWARD(18), // done after one?
						//10: Start of loop after unrolled iteration.
						PARSE_PART(2), // next x
						PARSE_ARGUMENT,
						PARSE_PART(4), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT, // save it and parse more.
						BRANCH_FORWARD(18), // exit loop?
						JUMP_BACKWARD(10),
						//18:
						APPEND_ARGUMENT)), // save all occurrences
				C(
					"«_:_»",
					List(
						1,
						1,
						List(
							0,
							-1,
							List(
								2,
								2,
								Phrase(NUMBER.o)))),
					A(
						"«",
						"_",
						":",
						"_",
						"»"),
					A(
						// NOTE: The group's left half has two argument
						// positions, so we have to double-wrap (i.e., produce a
						// tuple of 2-tuples).
						EMPTY_LIST, // whole expression
						BRANCH_FORWARD(25), // allow zero occurrences
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						WRAP_IN_LIST(2),
						BRANCH_FORWARD(24), // done after one?
						APPEND_ARGUMENT, // save it and parse more.
						//13: Start of loop after unrolled iteration.
						PARSE_ARGUMENT,
						PARSE_PART(3), // Hoisted before checks
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						WRAP_IN_LIST(2),
						BRANCH_FORWARD(24), // exit loop?
						APPEND_ARGUMENT, // save it and parse more.
						JUMP_BACKWARD(13),
						//24:
						APPEND_ARGUMENT, // save the last pair
						//25:
						APPEND_ARGUMENT)),  // save all occurrences
				C(
					"«»",
					List(
						1,
						1,
						List(
							0,
							-1,
							List(0, 0))),
					A("«", "»"),
					A(
						// This is a degenerate case, and can't actually pass
						// the progress checks because no tokens can ever be
						// parsed.
						EMPTY_LIST, // Zero occurrences.
						BRANCH_FORWARD(16), // Try zero occurrences.
						//3: Unrolled first occurrence
						SAVE_PARSE_POSITION, //Occurrences must make progress
						EMPTY_LIST, // Unrolled first occurrence.
						BRANCH_FORWARD(13), // Try a single occurrence.
						APPEND_ARGUMENT, // Add the occurrence.
						ENSURE_PARSE_PROGRESS, // Make sure it was productive
						//8: second and later occurrences.
						EMPTY_LIST,
						BRANCH_FORWARD(13), // Try the new occurrence.
						APPEND_ARGUMENT, // Save it.
						ENSURE_PARSE_PROGRESS, // Make sure it was productive
						JUMP_BACKWARD(8), // Try another
						//13: Save latest occurrence and try it.
						APPEND_ARGUMENT,
						ENSURE_PARSE_PROGRESS, // Must have made progress
						DISCARD_SAVED_PARSE_POSITION, // Chuck progress mark
						//16:
						APPEND_ARGUMENT)),  // Save list as sole argument.
				/* Repeated groups with double dagger. */
				C(
					"«_‡,»",
					List(
						1,
						1,
						List(
							0,
							-1,
							Phrase(NUMBER.o))),
					A(
						"«",
						"_",
						"‡",
						",",
						"»"),
					A(
						EMPTY_LIST, // Zero occurrences.
						BRANCH_FORWARD(16), // Try zero occurrences
						//3: Unrolled first occurrence
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT,
						BRANCH_FORWARD(16), // Try single occurrence
						//8: after double dagger.
						PARSE_PART(4),
						//9: second and later occurrences.
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT,
						BRANCH_FORWARD(16), // Try solution
						//14: after double dagger
						PARSE_PART(4),
						JUMP_BACKWARD(9),
						//16:
						APPEND_ARGUMENT)),  // Save list as sole argument.
				C(
					"new_«with_=_‡,»",
					List(
						2,
						2,
						Phrase(stringType),
						List(
							1,  // require at least one 'with_=_' entry.
							-1,
							List(
								2,
								2,
								Phrase(NUMBER.o),
								Phrase(
									stringType)))),
					A(
						"new",
						"_",
						"«",
						"with",
						"_",
						"=",
						"_",
						"‡",
						",",
						"»"),
					A(
						PARSE_PART(1),  // new
						PARSE_ARGUMENT,
						PARSE_PART(4),  // read ahead for required first "with"
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(stringType),
						EMPTY_LIST,
						PARSE_ARGUMENT,
						PARSE_PART(6),  // "=" read-ahead
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(3),
						typeCheckEncodingForPhrase(stringType),
						WRAP_IN_LIST(2),
						BRANCH_FORWARD(31),  // Try one repetition
						PARSE_PART(9),  // ","
						APPEND_ARGUMENT,  //18: Second and subsequent iterations
						PARSE_PART(4),  // "with"
						PARSE_ARGUMENT,
						PARSE_PART(6),  // "=" read-ahead
						CHECK_ARGUMENT(2),
						typeCheckEncodingForPhrase(NUMBER.o),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(3),
						typeCheckEncodingForPhrase(stringType),
						WRAP_IN_LIST(2),
						BRANCH_FORWARD(31),  // Try with this repetition
						PARSE_PART(9),  // ','
						APPEND_ARGUMENT,
						JUMP_BACKWARD(18),  // 31: Add the latest pair and try it. [],1,[...][2,3]
						APPEND_ARGUMENT,  // [],1, [...[2,3]]
						WRAP_IN_LIST(2),  // [], [1, [...[2,3]]]
						CONCATENATE)),  // [1, [...[2,3]]]
				/* Counting groups. */
				C(
					"«x»#",
					List(
						1,
						1,
						Phrase(
							naturalNumbers)),
					A(
						"«",
						"x",
						"»",
						"#"),
					A(
						PARSE_PART(2),  // Hoisted mandatory first unrolled x
						EMPTY_LIST,  // The list of occurrences
						EMPTY_LIST,  // One empty occurrence
						BRANCH_FORWARD(11),  // Try with one occurrence
						APPEND_ARGUMENT,  // [], [], [] -> [], [[]]
						//6: Second iteration onward
						PARSE_PART(2),  // "x"
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(11),  // Try with latest occurrence
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(6),  //11: Try solution.  [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						CONVERT(LIST_TO_SIZE.number),  // [], N
						APPEND_ARGUMENT)),  // [N]
				C(
					"«x y»#",
					List(
						1,
						1,
						Phrase(
							naturalNumbers)),
					A(
						"«",
						"x",
						"y",
						"»",
						"#"),
					A(
						PARSE_PART(2),  // Hoisted mandatory first x
						PARSE_PART(3),  // Hoisted mandatory first y
						EMPTY_LIST,  // The list of occurrences
						EMPTY_LIST,  // One empty occurrence
						BRANCH_FORWARD(13),  // Try with one occurrence
						APPEND_ARGUMENT,  // [], [], [] -> [], [[]]
						//7: Second iteration onward
						PARSE_PART(2),  // "x"
						PARSE_PART(3),  // "x"
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(13),  // Try with latest occurrence
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(7),  //13: Try solution.  [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						CONVERT(LIST_TO_SIZE.number),  // [], N
						APPEND_ARGUMENT)),  // [N]
				C(
					"«fish‡face»#",
					List(
						1,
						1,
						Phrase(
							integerRangeType(
								fromInt(3),
								true,
								positiveInfinity,
								false))),
					A(
						"«",
						"fish",
						"‡",
						"face",
						"»",
						"#"),
					A(
						PARSE_PART(2),  // Hoisted mandatory 1st fish
						PARSE_PART(4),  // Hoisted mandatory 1st face
						PARSE_PART(2),  // Hoisted mandatory 2nd fish
						PARSE_PART(4),  // Hoisted mandatory 2nd face
						EMPTY_LIST,  // 1st occurrence [], []
						EMPTY_LIST,  // 2nd occurrence [], [], []
						WRAP_IN_LIST(2),  // [], [[],[]]
						//8: Loop
						PARSE_PART(2),  // Next fish
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(14),  // Try with new occurrence
						PARSE_PART(4),  // Next face, hoisted
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(8),  //14: Try solution.  [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						CONVERT(LIST_TO_SIZE.number),  // [], N
						APPEND_ARGUMENT)),  // [N]
				/* Optional groups. */
				C(
					"«x»?",
					List(
						1,
						1,
						Phrase(
							booleanType)),
					A(
						"«",
						"x",
						"»",
						"?"),
					A(
						BRANCH_FORWARD(5),
						PARSE_PART(2),
						PUSH_LITERAL(indexForTrue),  // [], T
						JUMP_FORWARD(6),
						//5:
						PUSH_LITERAL(indexForFalse),  // [], F
						//6:
						APPEND_ARGUMENT)),  // [T/F]
				C(
					"«x y»?",
					List(
						1,
						1,
						Phrase(
							booleanType)),
					A(
						"«",
						"x",
						"y",
						"»",
						"?"),
					A(
						BRANCH_FORWARD(6),
						PARSE_PART(2),
						PARSE_PART(3),
						PUSH_LITERAL(indexForTrue),  // [], T
						JUMP_FORWARD(7),
						//6:
						PUSH_LITERAL(indexForFalse),  // [], F
						//7:
						APPEND_ARGUMENT)),  // [T/F]
				/* Completely optional groups. */
				C(
					"very⁇good",
					List(0, 0),
					A(
						"very",
						"⁇",
						"good"),
					A(
						BRANCH_FORWARD(3),
						PARSE_PART(1),  // very
						//3:
						PARSE_PART(3))),  // good
				C(
					"«very extremely»⁇good",
					List(0, 0),
					A(
						"«",
						"very",
						"extremely",
						"»",
						"⁇",
						"good"),
					A(
						BRANCH_FORWARD(4),
						PARSE_PART(2),  // very
						PARSE_PART(3),  // extremely
						//4:
						PARSE_PART(6))),  // good
				/* Case insensitive. */
				C(
					"fnord~",
					List(0, 0),
					A(
						"fnord",
						"~"),
					A(
						PARSE_PART_CASE_INSENSITIVELY(1)
					)),
				C(
					"the~_",
					List(
						1,
						1,
						Phrase(NUMBER.o)),
					A(
						"the",
						"~",
						"_"),
					A(
						PARSE_PART_CASE_INSENSITIVELY(1),
						PARSE_ARGUMENT,
						CHECK_ARGUMENT(1),
						typeCheckEncodingForPhrase(NUMBER.o),
						APPEND_ARGUMENT)),
				C(
					"«x~»",
					List(
						1,
						1,
						List(
							1,
							-1,
							List(0, 0))),
					A(
						"«",
						"x",
						"~",
						"»"),
					A(
						PARSE_PART_CASE_INSENSITIVELY(2),  // Hoisted 1st
						EMPTY_LIST,  // [], []
						EMPTY_LIST,  // [], [], []
						BRANCH_FORWARD(11),  // Try empty
						APPEND_ARGUMENT,  // [], [[]]
						//6: Loop
						PARSE_PART_CASE_INSENSITIVELY(2),  // Next x
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(11),  // Try empty
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(6),  //11: Attempt. [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						APPEND_ARGUMENT)),  // [[...[]]]
				C(
					"«x»~",  // Should be the same as «x~»
					List(
						1,
						1,
						List(
							1,
							-1,
							List(0, 0))),
					A(
						"«",
						"x",
						"»",
						"~"),
					A(
						PARSE_PART_CASE_INSENSITIVELY(2),  // Hoisted 1st
						EMPTY_LIST,  // [], []
						EMPTY_LIST,  // [], [], []
						BRANCH_FORWARD(11),  // Try empty
						APPEND_ARGUMENT,  // [], [[]]
						//6: Loop
						PARSE_PART_CASE_INSENSITIVELY(2),  // Next x
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(11),  // Try empty
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(6),  //11: Attempt. [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						APPEND_ARGUMENT)),  // [[...[]]]
				C(
					"«x y»~#",
					List(
						1,
						1,
						Phrase(
							wholeNumbers)),
					A(
						"«",
						"x",
						"y",
						"»",
						"~",
						"#"),
					A(
						EMPTY_LIST,  // [], []
						BRANCH_FORWARD(15),  // Try zero occurrences
						PARSE_PART_CASE_INSENSITIVELY(2),  // Unrolled 1st x
						PARSE_PART_CASE_INSENSITIVELY(3),  // Unrolled 1st y
						EMPTY_LIST,  // [], [], []
						BRANCH_FORWARD(14),  // Try first occurrence
						APPEND_ARGUMENT,  // [], [[]]
						//8: Loop
						PARSE_PART_CASE_INSENSITIVELY(2),  // Unrolled 1st x
						PARSE_PART_CASE_INSENSITIVELY(3),  // Unrolled 1st y
						EMPTY_LIST,  // [], [...], []
						BRANCH_FORWARD(14),  // Try latest occurrence
						APPEND_ARGUMENT,  // [], [...[]]
						JUMP_BACKWARD(8),  //14: Latest occurrence. [], [...], []
						APPEND_ARGUMENT,  // [], [...[]]
						//15: Answer
						CONVERT(LIST_TO_SIZE.number),
						APPEND_ARGUMENT)),  // [[...]]
				C(
					"«x y»~?",
					List(
						1,
						1,
						Phrase(
							booleanType)),
					A(
						"«",
						"x",
						"y",
						"»",
						"~",
						"?"),
					A(
						BRANCH_FORWARD(6),
						PARSE_PART_CASE_INSENSITIVELY(2),
						PARSE_PART_CASE_INSENSITIVELY(3),
						PUSH_LITERAL(indexForTrue),
						JUMP_FORWARD(7),  //6:
						PUSH_LITERAL(indexForFalse),  //7:
						APPEND_ARGUMENT)),
				/* Alternation. */
				C(
					"hello|greetings",
					List(0, 0),
					A(
						"hello",
						"|",
						"greetings"),
					A(
						BRANCH_FORWARD(4),
						PARSE_PART(1),  // hello
						JUMP_FORWARD(5),
						//4:
						PARSE_PART(3))),  // greetings
						//5:
				C(
					"a|b|c|d|e|f|g",
					List(0, 0),
					A(
						"a",
						"|",
						"b",
						"|",
						"c",
						"|",
						"d",
						"|",
						"e",
						"|",
						"f",
						"|",
						"g"),
					A(
						BRANCH_FORWARD(4),
						PARSE_PART(1),  // a
						JUMP_FORWARD(20),  // 4:
						BRANCH_FORWARD(7),
						PARSE_PART(3),  // b
						JUMP_FORWARD(20),  // 7:
						BRANCH_FORWARD(10),
						PARSE_PART(5),  // c
						JUMP_FORWARD(20),  // 10:
						BRANCH_FORWARD(13),
						PARSE_PART(7),  // d
						JUMP_FORWARD(20),  // 13:
						BRANCH_FORWARD(16),
						PARSE_PART(9),  // e
						JUMP_FORWARD(20),  // 16:
						BRANCH_FORWARD(19),
						PARSE_PART(11),  // f
						JUMP_FORWARD(20),  // 19:
						PARSE_PART(13))),  // g
				// 20: (end-if)
				//		/* NOT YET SUPPORTED (no way to specify groups within alternations) */
				//			C("««fruit bats»|sloths|carp|«breakfast cereals»»",
				//				List(0, 0),
				//				A("«", "«", "fruit", "bats", "»", "|", "sloths", "|", "carp",
				//				  "|", "«", "breakfast", "cereals", "»", "»"),
				//				A(
				//					BRANCH_FORWARD(5),
				//					PARSE_PART(3), // fruit
				//					PARSE_PART(4), // bats
				//					JUMP_FORWARD(13),
				//					//5:
				//					BRANCH_FORWARD(8),
				//					PARSE_PART(7), // sloths
				//					JUMP_FORWARD(13),
				//					//8:
				//					BRANCH_FORWARD(11),
				//					PARSE_PART(9), // carp
				//					JUMP_FORWARD(13),
				//					//11:
				//					PARSE_PART(12), // breakfast
				//					PARSE_PART(13))) // cereals
				//					//13:
				C(
					"«x|y»!",
					List(
						1,
						1,
						Phrase(
							inclusive(1, 2))),
					A(
						"«",
						"x",
						"|",
						"y",
						"»",
						"!"),
					A(
						BRANCH_FORWARD(5),
						PARSE_PART(2),
						PUSH_LITERAL(indexForConstant(fromInt(1))),
						JUMP_FORWARD(7),
						//5:
						PARSE_PART(4),
						PUSH_LITERAL(indexForConstant(fromInt(2))),
						//7:
						typeCheckEncodingForPhrase(inclusive(1, 2)),
						APPEND_ARGUMENT))) // [N]
		}
	}

	/**
	 * Check that the [MessageSplitter] processes the information in the given
	 * [Case] as expected.
	 *
	 * @param splitCase
	 *   The [Case] to check.
	 * @throws MalformedMessageException
	 *   If the method name is determined to be malformed.
	 * @throws SignatureException
	 *   If the [Case]'s type signature is inconsistent with the method name.
	 */
	@DisplayName("Message Splitter")
	@ParameterizedTest(name = "{index} => case={0}")
	@MethodSource("casesProvider")
	@Throws(MalformedMessageException::class, SignatureException::class)
	fun testSplitCase(splitCase: Case)
	{
		val msgString = splitCase.message
		val message = stringFrom(msgString)
		val splitter = MessageSplitter.split(message)
		val parts = splitter.messageParts
		assert(splitCase.tokens.size == parts.size)
		for (i in parts.indices)
		{
			Assertions.assertEquals(
				splitCase.tokens[i],
				parts[i].asNativeString(),
				"Split was not as expected")
		}
		val tupleType = splitCase.listPhraseType.phraseTypeExpressionType
		val sizeRange = tupleType.sizeRange
		assert(sizeRange.lowerBound.equals(sizeRange.upperBound))
		val typeTuple = tupleType.tupleOfTypesFromTo(
			1, sizeRange.lowerBound.extractInt)
		splitter.checkImplementationSignature(
			functionType(
				typeTuple,
				TOP.o))
		val instructionsList =
			splitter.instructionsFor(splitCase.listPhraseType)
		if (splitCase.instructions.toString() != instructionsList.toString())
		{
			println(splitCase.instructions)
			println(instructionsList)
			println(
				dumpInstructions(
					instructionsList))
			Assertions.fail<Any>(
				String.format(
					"Generated parse code for \"%s\" was not as expected:%n%s%n"
						+ "instead it was:%n%s",
					msgString,
					dumpInstructions(splitCase.instructions),
					dumpInstructions(instructionsList)))
		}
	}
}
