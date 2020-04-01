/*
 * ParsingOperation.kt
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

package com.avail.compiler

import com.avail.compiler.ParsingConversionRule.Companion.ruleNumber
import com.avail.compiler.ParsingOperation.PARSE_PART
import com.avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import com.avail.compiler.splitter.MessageSplitter
import com.avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import com.avail.compiler.splitter.MessageSplitter.Companion.permutationAtIndex
import com.avail.descriptor.AvailObject
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.ListPhraseDescriptor.emptyListNode
import com.avail.descriptor.phrases.ListPhraseDescriptor.newListNode
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.literalNodeFromToken
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.newMacroSubstitution
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.newPermutedListNode
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.phrases.ReferencePhraseDescriptor.referenceNodeFromUse
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.LiteralTokenDescriptor.literalToken
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.*
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.EXPANDING_PARSING_INSTRUCTIONS
import com.avail.performance.StatisticReport.RUNNING_PARSING_INSTRUCTIONS
import com.avail.utility.PrefixSharingList.*
import com.avail.utility.StackPrinter.trace
import com.avail.utility.evaluation.Describer
import java.util.*
import java.util.Collections.reverse
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `ParsingOperation` describes the operations available for parsing Avail
 * message names.
 *
 * @property modulus
 *   The modulus that represents the operation uniquely for its arity.
 * @property commutesWithParsePart
 *   Whether this instance commutes with `PARSE_PART` instructions.
 * @property canRunIfHasFirstArgument
 *   Whether this operation can run successfully if there is a pre-parsed first
 *   argument that has not yet been consumed.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ParsingOperation` for this enum.
 *
 * @param modulus
 *   The modulus that represents the operation uniquely for its arity.
 * @param commutesWithParsePart
 *   Whether a [PARSE_PART] or [PARSE_PART_CASE_INSENSITIVELY] instructions can
 *   be moved safely leftward over this instruction.
 * @param canRunIfHasFirstArgument
 *   Whether this instruction can be run if the first argument has been parsed
 *   but not yet consumed by a PARSE_ARGUMENT instruction.
 */
enum class ParsingOperation constructor(
	private val modulus: Int,
	val commutesWithParsePart: Boolean,
	val canRunIfHasFirstArgument: Boolean)
{
	/*
	 * Arity zero entries:
	 */

	/**
	 * `0` - Push a new [list][ListPhraseDescriptor] that contains an [empty
	 * tuple][TupleDescriptor.emptyTuple] of [phrases][PhraseDescriptor] onto
	 * the parse stack.
	 */
	EMPTY_LIST(0, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			// Push an empty list phrase and continue.
			assert(successorTrees.tupleSize() == 1)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				append(argsSoFar, emptyListNode()),
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `1` - Pop an argument from the parse stack of the current potential
	 * message send. Pop a [list][ListPhraseDescriptor] from the parse stack.
	 * Append the argument to the list. Push the resultant list onto the parse
	 * stack.
	 */
	APPEND_ARGUMENT(1, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val value = last(argsSoFar)
			val poppedOnce = withoutLast(argsSoFar)
			val oldNode = last(poppedOnce)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				append(withoutLast(poppedOnce), oldNode.copyWith(value)),
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `2` - Push the current parse position onto the mark stack.
	 */
	SAVE_PARSE_POSITION(2, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val marker = if (firstArgOrNull === null)
				start.position
			else
				initialTokenPosition.position
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				append(marksSoFar, marker),
				continuation)
		}
	},

	/**
	 * `3` - Pop the top marker off the mark stack.
	 */
	DISCARD_SAVED_PARSE_POSITION(3, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				withoutLast(marksSoFar),
				continuation)
		}
	},

	/**
	 * `4` - Pop the top marker off the mark stack and compare it to the current
	 * parse position.  If they're the same, abort the current parse, otherwise
	 * push the current parse position onto the mark stack in place of the old
	 * marker and continue parsing.
	 */
	ENSURE_PARSE_PROGRESS(4, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val oldMarker = last(marksSoFar)
			if (oldMarker == start.position)
			{
				// No progress has been made.  Reject this path.
				return
			}
			val newMarker = start.position
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				append(withoutLast(marksSoFar), newMarker),
				continuation)
		}
	},

	/**
	 * `5` - Parse an ordinary argument of a message send, pushing the
	 * expression onto the parse stack.
	 */
	PARSE_ARGUMENT(5, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val successorTree = successorTrees.tupleAt(1)
			val partialSubexpressionList =
				if (firstArgOrNull === null)
					continuation.superexpressions!!.advancedTo(successorTree)
				else
					continuation.superexpressions
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"argument",
				firstArgOrNull,
				firstArgOrNull === null
					&& initialTokenPosition.lexingState != start.lexingState,
				false,
				Con1(partialSubexpressionList) { solution ->
					compiler.eventuallyParseRestOfSendNode(
						solution.endState,
						successorTree,
						null,
						initialTokenPosition,
						// The argument counts as something that was consumed if
						// it's not a leading argument...
						firstArgOrNull === null,
						// We're about to parse an argument, so whatever was in
						// consumedAnything should be moved into
						// consumedAnythingBeforeLatestArgument.
						consumedAnything,
						consumedStaticTokens,
						append(argsSoFar, solution.phrase),
						marksSoFar,
						continuation)
				})
		}
	},

	/**
	 * `6` - Parse an expression, even one whose expressionType is ⊤, then push
	 * *a literal phrase wrapping this expression* onto the parse stack.
	 *
	 * If we didn't wrap the phrase inside a literal phrase, we wouldn't be able
	 * to process sequences of statements in macros, since they would each have
	 * an expressionType of ⊤ (or if one was ⊥, the entire expressionType would
	 * also be ⊥).  Instead, they will have the expressionType phrase⇒⊤ (or
	 * phrase⇒⊥), which is perfectly fine to put inside a list phrase during
	 * parsing.
	 */
	PARSE_TOP_VALUED_ARGUMENT(6, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val partialSubexpressionList =
				if (firstArgOrNull === null)
					continuation.superexpressions!!.advancedTo(
						successorTrees.tupleAt(1))
				else
					continuation.superexpressions
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"top-valued argument",
				firstArgOrNull,
				firstArgOrNull === null
					&& initialTokenPosition.lexingState != start.lexingState,
				true,
				Con1(partialSubexpressionList) { solution ->
					compiler.eventuallyParseRestOfSendNode(
						solution.endState,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						// The argument counts as something that was consumed if
						// it's not a leading argument...
						firstArgOrNull === null,
						// We're about to parse an argument, so whatever was in
						// consumedAnything should be moved into
						// consumedAnythingBeforeLatestArgument.
						consumedAnything,
						consumedStaticTokens,
						append(argsSoFar, solution.phrase),
						marksSoFar,
						continuation)
				})
		}
	},

	/**
	 * `7` - Parse a [raw token][TokenDescriptor]. It should correspond to a
	 * [variable][VariableDescriptor] that is in scope. Push a [variable
	 * reference][ReferencePhraseDescriptor] onto the parse stack.
	 */
	PARSE_VARIABLE_REFERENCE(7, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val partialSubexpressionList =
				if (firstArgOrNull === null)
					continuation.superexpressions!!.advancedTo(
						successorTrees.tupleAt(1))
				else
					continuation.superexpressions
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"variable reference",
				firstArgOrNull,
				firstArgOrNull === null
					&& initialTokenPosition.lexingState != start.lexingState,
				false,
				Con1(partialSubexpressionList) { variableUseSolution ->
					assert(successorTrees.tupleSize() == 1)
					val variableUse = variableUseSolution.phrase
					val rawVariableUse = variableUse.stripMacro()
					val afterUse = variableUseSolution.endState
					if (!rawVariableUse.phraseKindIsUnder(
							VARIABLE_USE_PHRASE))
					{
						if (consumedAnything)
						{
							// At least one token besides the variable use has
							// been encountered, so go ahead and report that we
							// expected a variable.
							afterUse.expected(
								if (consumedStaticTokens.isEmpty()) WEAK
								else STRONG,
								describeWhyVariableUseIsExpected(
									successorTrees.tupleAt(1)))
						}
						// It wasn't a variable use phrase, so give up.
						return@Con1
					}
					// Make sure taking a reference is appropriate.
					val declarationKind =
						rawVariableUse.declaration().declarationKind()
					if (!declarationKind.isVariable)
					{
						if (consumedAnything)
						{
							// Only complain about this not being a variable if
							// we've parsed something besides the variable
							// reference argument.
							afterUse.expected(
								STRONG,
								"variable for reference argument to be "
								+ "assignable, not "
								+ declarationKind.nativeKindName())
						}
						return@Con1
					}
					// Create a variable reference from this use.
					val rawVariableReference =
						referenceNodeFromUse(rawVariableUse)
					val variableReference =
						if (variableUse.isMacroSubstitutionNode)
							newMacroSubstitution(
								variableUse.macroOriginalSendNode(),
								rawVariableReference)
						else
							rawVariableReference
					compiler.eventuallyParseRestOfSendNode(
						afterUse,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						// The argument counts as something that was consumed if
						// it's not a leading argument...
						firstArgOrNull === null,
						// We're about to parse an argument, so whatever was in
						// consumedAnything should be moved into
						// consumedAnythingBeforeLatestArgument.
						consumedAnything,
						consumedStaticTokens,
						append(argsSoFar, variableReference),
						marksSoFar,
						continuation)
				})
		}
	},

	/**
	 * `8` - Parse an argument of a message send, using the *outermost (module)
	 * scope*.  Leave it on the parse stack.
	 */
	PARSE_ARGUMENT_IN_MODULE_SCOPE(8, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			compiler.parseArgumentInModuleScopeThen(
				start,
				initialTokenPosition,
				firstArgOrNull,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				successorTrees,
				continuation)
		}
	},

	/**
	 * `9` - Parse *any* [raw token][TokenDescriptor], leaving it on the parse
	 * stack.  In particular, push a literal phrase whose token is a synthetic
	 * literal token whose value is the actual token that was parsed.
	 */
	PARSE_ANY_RAW_TOKEN(9, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			if (firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(start) { token ->
				val tokenType = token.tokenType()
				assert(tokenType != WHITESPACE && tokenType != COMMENT)
				if (tokenType == END_OF_FILE)
				{
					start.expected(
						if (consumedStaticTokens.isEmpty()) WEAK else STRONG,
						"any token, not end-of-file")
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				compiler.compilationContext.recordToken(syntheticToken)
				val newArgsSoFar =
					append(argsSoFar, literalNodeFromToken(syntheticToken))
				compiler.eventuallyParseRestOfSendNode(
					ParserState(token.nextLexingState(), start.clientDataMap),
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					true,
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnything,
					// Don't count it as a static token.
					consumedStaticTokens,
					newArgsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `10` - Parse a raw *[keyword][TokenType.KEYWORD]*
	 * [token][TokenDescriptor], leaving it on the parse stack.
	 */
	PARSE_RAW_KEYWORD_TOKEN(10, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			if (firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(start) { token ->
				val tokenType = token.tokenType()
				if (tokenType != KEYWORD)
				{
					if (consumedAnything)
					{
						start.expected(
							if (consumedStaticTokens.isEmpty()) WEAK else STRONG
						) {
							it(
								"a keyword token, not " +
									when (tokenType)
									{
										END_OF_FILE -> "end-of-file"
										LITERAL -> token.literal()
										else -> token.string()
									})
						}
					}
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				compiler.compilationContext.recordToken(syntheticToken)
				val newArgsSoFar =
					append(argsSoFar, literalNodeFromToken(syntheticToken))
				compiler.eventuallyParseRestOfSendNode(
					ParserState(
						token.nextLexingState(), start.clientDataMap),
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					true,
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnything,
					// Don't count it as a static token.
					consumedStaticTokens,
					newArgsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `11` - Parse a raw *[literal][TokenType.LITERAL]*
	 * [token][TokenDescriptor], leaving it on the parse stack.
	 */
	PARSE_RAW_STRING_LITERAL_TOKEN(11, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			if (firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(start) { token ->
				val tokenType = token.tokenType()
				if (tokenType != LITERAL
					|| !token.literal().isInstanceOf(stringType()))
				{
					if (consumedAnything)
					{
						start.expected(
							if (consumedStaticTokens.isEmpty())WEAK
							else STRONG
						) {
							it(
								"a string literal token, not " +
									when (tokenType)
									{
										END_OF_FILE -> "end-of-file"
										LITERAL -> token.literal()
										else -> token.string()
									})
						}
					}
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				compiler.compilationContext.recordToken(syntheticToken)
				val newArgsSoFar =
					append(argsSoFar, literalNodeFromToken(syntheticToken))
				compiler.eventuallyParseRestOfSendNode(
					ParserState(
						token.nextLexingState(), start.clientDataMap),
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					true,
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnything,
					// Don't count it as a static token.
					consumedStaticTokens,
					newArgsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `12` - Parse a raw *[literal][TokenType.LITERAL]*
	 * [token][TokenDescriptor], leaving it on the parse stack.
	 */
	PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN(12, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			if (firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(
				start
			) { token ->
				val tokenType = token.tokenType()
				if (tokenType != LITERAL
					|| !token.literal().isInstanceOf(wholeNumbers()))
				{
					if (consumedAnything)
					{
						start.expected(
							if (consumedStaticTokens.isEmpty()) WEAK
							else STRONG
						) {
							it(
								"a whole number literal token, not " +
									when (tokenType)
									{
										END_OF_FILE -> "end-of-file"
										LITERAL -> token.literal()
										else -> token.string()
									})
						}
					}
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				compiler.compilationContext.recordToken(syntheticToken)
				val newArgsSoFar = append(
					argsSoFar, literalNodeFromToken(syntheticToken))
				compiler.eventuallyParseRestOfSendNode(
					ParserState(
						token.nextLexingState(), start.clientDataMap),
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					true,
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnything,
					// Don't count it as a static token.
					consumedStaticTokens,
					newArgsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `13` - Concatenate the two lists that have been pushed previously.
	 */
	CONCATENATE(13, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val right = last(argsSoFar)
			val popped1 = withoutLast(argsSoFar)
			val left = last(popped1)
			val popped2 = withoutLast(popped1)
			val concatenated = when {
				left.expressionsSize() == 0 -> right
				else -> left.copyConcatenating(right)
			}
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				append(popped2, concatenated),
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `14` - Reserved for future use.
	 */
	@Suppress("unused")
	RESERVED_14(14, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(false) { "Illegal reserved parsing operation" }
		}
	},

	/**
	 * `15` - Reserved for future use.
	 */
	@Suppress("unused")
	RESERVED_15(15, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(false) { "Illegal reserved parsing operation" }
		}
	},

	/*
	 * Arity one entries:
	 */

	/**
	 * `16*N+0` - Branch to instruction `N`, which must be after the current
	 * instruction. Attempt to continue parsing at both the next instruction and
	 * instruction `N`.
	 */
	BRANCH_FORWARD(0, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction), currentPc + 1)

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			for (successorTree in successorTrees)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `16*N+1` - Jump to instruction `N`, which must be after the current
	 * instruction. Attempt to continue parsing only at instruction `N`.
	 */
	JUMP_FORWARD(1, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction))

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+2` - Jump to instruction `N`, which must be before the current
	 * instruction. Attempt to continue parsing only at instruction `N`.
	 */
	JUMP_BACKWARD(2, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction))

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+3` - Parse the `N`<sup>th</sup> [message
	 * part][MessageSplitter.messagePartsTuple] of the current message. This
	 * will be a specific [token][TokenDescriptor]. It should be matched case
	 * sensitively against the source token.
	 */
	PARSE_PART(3, false, false)
	{
		override fun keywordIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(false) { "$name instruction should not be dispatched" }
		}
	},

	/**
	 * `16*N+4` - Parse the `N`<sup>th</sup> [message
	 * part][MessageSplitter.messagePartsTuple] of the current message. This
	 * will be a specific [token][TokenDescriptor]. It should be matched case
	 * insensitively against the source token.
	 */
	PARSE_PART_CASE_INSENSITIVELY(4, false, false)
	{
		override fun keywordIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(false) { "$name instruction should not be dispatched" }
		}
	},

	/**
	 * `16*N+5` - Apply grammatical restrictions to the `N`<sup>th</sup> leaf
	 * argument (underscore/ellipsis) of the current message, which is on the
	 * stack.
	 */
	CHECK_ARGUMENT(5, true, true)
	{
		override fun checkArgumentIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			assert(firstArgOrNull === null)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				null,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+6` - Pop an argument from the parse stack and apply the
	 * [conversion rule][ParsingConversionRule] specified by `N`.
	 */
	CONVERT(6, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val input = last(argsSoFar)
			val sanityFlag = AtomicBoolean()
			val conversionRule = ruleNumber(operand(instruction))
			conversionRule.convert(
				compiler.compilationContext,
				start.lexingState,
				input,
				{ replacementExpression ->
					assert(sanityFlag.compareAndSet(false, true))
					compiler.eventuallyParseRestOfSendNode(
						start,
						successorTrees.tupleAt(1),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						consumedAnythingBeforeLatestArgument,
						consumedStaticTokens,
						append(withoutLast(argsSoFar), replacementExpression),
						marksSoFar,
						continuation)
				},
				{ e ->
					// Deal with a failed conversion.  As of 2016-08-28, this
					// can only happen during an expression evaluation.
					assert(sanityFlag.compareAndSet(false, true))
					start.expected(STRONG) {
						it(
							"evaluation of expression not to have "
								+ "thrown Java exception:\n${trace(e)}")
					}
				})
		}
	},

	/**
	 * `16*N+7` - A macro has been parsed up to a section checkpoint (§). Make a
	 * copy of the parse stack, then perform the equivalent of an
	 * [APPEND_ARGUMENT] on the copy, the specified number of times minus one
	 * (because zero is not a legal operand).  Make it into a single [list
	 * phrase][ListPhraseDescriptor] and push it onto the original parse stack.
	 * It will be consumed by a subsequent [RUN_PREFIX_FUNCTION].
	 *
	 * This instruction is detected specially by the [message bundle
	 * tree][A_BundleTree]'s [expand][A_BundleTree.expand] operation.  Its
	 * successors are separated into distinct message bundle trees, one per
	 * message bundle.
	 */
	PREPARE_TO_RUN_PREFIX_FUNCTION(7, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			var stackCopy = argsSoFar
			// Only do N-1 steps.  We simply couldn't encode zero as an operand,
			// so we always bias by one automatically.
			val fixupDepth = operand(instruction)
			for (i in fixupDepth downTo 2)
			{
				// Pop the last element and append it to the second last.
				val value = last(stackCopy)
				val poppedOnce = withoutLast(stackCopy)
				val oldNode = last(poppedOnce)
				val listNode = oldNode.copyWith(value)
				stackCopy = append(withoutLast(poppedOnce), listNode)
			}
			assert(stackCopy.size == 1)
			for (successorTree in successorTrees)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedStaticTokens,
					append(argsSoFar, stackCopy[0]),
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `16*N+8` - A macro has been parsed up to a section checkpoint (§), and a
	 * copy of the cleaned up parse stack has been pushed, so invoke the
	 * N<sup>th</sup> prefix function associated with the macro.  Consume the
	 * previously pushed copy of the parse stack.  The current [ParserState]'s
	 * [ParserState.clientDataMap] is stashed in the new
	 * [fiber][FiberDescriptor]'s [globals map][AvailObject.fiberGlobals] and
	 * retrieved afterward, so the prefix function and macros can alter the
	 * scope or communicate with each other by manipulating this
	 * [map][MapDescriptor].  This technique prevents chatter between separate
	 * fibers (i.e., parsing can still be done in parallel) and between separate
	 * linguistic abstractions (the keys are atoms and are therefore modular).
	 */
	RUN_PREFIX_FUNCTION(8, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val successorTree = successorTrees.tupleAt(1)
			// Look inside the only successor to find the only bundle.
			val bundlesMap = successorTree.allParsingPlansInProgress()
			assert(bundlesMap.mapSize() == 1)
			val submap = bundlesMap.mapIterable().next().value()
			assert(submap.mapSize() == 1)
			val definition = submap.mapIterable().next().key()
			val prefixFunctions = definition.prefixFunctions()
			val prefixIndex = operand(instruction)
			val prefixFunction = prefixFunctions.tupleAt(prefixIndex)
			compiler.runPrefixFunctionThen(
				start,
				successorTree,
				prefixFunction,
				toList(last(argsSoFar).expressionsTuple()),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				withoutLast(argsSoFar),
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+9` - Permute the elements of the list phrase on the top of the
	 * stack via the permutation found via [MessageSplitter.permutationAtIndex].
	 *  The list phrase must be the same size as the permutation.
	 */
	PERMUTE_LIST(9, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			val permutationIndex = operand(instruction)
			val permutation = permutationAtIndex(permutationIndex)
			val poppedList = last(argsSoFar)
			var stack = withoutLast(argsSoFar)
			stack = append(stack, newPermutedListNode(poppedList, permutation))
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				stack,
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+10` - Check that the list phrase on the top of the stack has at
	 * least the specified size.  Proceed to the next instruction only if this
	 * is the case.
	 */
	CHECK_AT_LEAST(10, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			val limit = operand(instruction)
			val top = last(argsSoFar)
			if (top.expressionsSize() >= limit)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `16*N+11` - Check that the list phrase on the top of the stack has at
	 * most the specified size.  Proceed to the next instruction only if this is
	 * the case.
	 */
	CHECK_AT_MOST(11, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			val limit = operand(instruction)
			val top = last(argsSoFar)
			if (top.expressionsSize() <= limit)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation)
			}
		}
	},

	/**
	 * `16*N+12` - Use the type of the argument just parsed to select among
	 * successor message bundle trees.  Those message bundle trees are filtered
	 * by the allowable leaf argument type.  This test is *precise*, and
	 * requires repeated groups to be unrolled for the tuple type specific to
	 * that argument slot of that definition, or at least until the
	 * [A_Type.defaultType] of the tuple type has been reached.
	 */
	TYPE_CHECK_ARGUMENT(12, true, true)
	{
		override fun typeCheckArgumentIndex(instruction: Int) =
			operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(false) { "$name instruction should not be dispatched" }
		}
	},

	/**
	 * `16*N+13` - Pop N arguments from the parse stack of the current potential
	 * message send. Create an N-element [list][ListPhraseDescriptor] with them,
	 * and push the list back onto the parse stack.
	 *
	 * This is the equivalent of pushing an empty list prior to pushing those
	 * arguments, then using [APPEND_ARGUMENT] after each argument is parsed to
	 * add them to the list.  The advantage of using this operation instead is
	 * to allow the pure stack manipulation operations to occur after parsing an
	 * argument and/or fixed tokens, which increases the conformity between the
	 * non-repeating and repeating clauses, which in turn reduces (at least) the
	 * number of actions executed each time the root bundle tree is used to
	 * start parsing a subexpression.
	 */
	WRAP_IN_LIST(13, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val listSize = operand(instruction)
			val totalSize = argsSoFar.size
			val unpopped = argsSoFar.subList(0, totalSize - listSize)
			val popped = argsSoFar.subList(totalSize - listSize, totalSize)
			val newListNode = newListNode(tupleFromList(popped))
			val newArgsSoFar = append(unpopped, newListNode)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+14` - Push a [literal phrase][LiteralPhraseDescriptor] containing
	 * the constant found at the position in the type list indicated by the
	 * operand.
	 */
	PUSH_LITERAL(14, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			val constant = constantForIndex(operand(instruction))
			val token = literalToken(
				stringFrom(constant.toString()),
				initialTokenPosition.position,
				initialTokenPosition.lineNumber,
				constant)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				append(argsSoFar, literalNodeFromToken(token)),
				marksSoFar,
				continuation)
		}
	},

	/**
	 * `16*N+15` - Reverse the `N` top elements of the stack.  The new stack has
	 * the same depth as the old stack.
	 */
	REVERSE_STACK(15, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			instruction: Int,
			successorTrees: A_Tuple,
			start: ParserState,
			firstArgOrNull: A_Phrase?,
			argsSoFar: List<A_Phrase>,
			marksSoFar: List<Int>,
			initialTokenPosition: ParserState,
			consumedAnything: Boolean,
			consumedAnythingBeforeLatestArgument: Boolean,
			consumedStaticTokens: List<A_Token>,
			continuation: Con1)
		{
			assert(successorTrees.tupleSize() == 1)
			val depthToReverse = operand(instruction)
			val totalSize = argsSoFar.size
			val unpopped = argsSoFar.subList(0, totalSize - depthToReverse)
			val popped = ArrayList(
				argsSoFar.subList(totalSize - depthToReverse, totalSize))
			reverse(popped)
			val newArgsSoFar = ArrayList(unpopped)
			newArgsSoFar.addAll(popped)
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation)
		}
	};

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * executing occurrences of this [ParsingOperation].
	 */
	val parsingStatisticInNanoseconds: Statistic = Statistic(
		name, RUNNING_PARSING_INSTRUCTIONS)

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * expanding occurrences of this [ParsingOperation].
	 */
	val expandingStatisticInNanoseconds: Statistic = Statistic(
		name, EXPANDING_PARSING_INSTRUCTIONS)

	/** The instruction coding of the receiver. */
	val encoding: Int
		get()
		{
			if (ordinal >= distinctInstructions)
			{
				throw UnsupportedOperationException()
			}
			return modulus
		}

	/**
	 * Answer the instruction coding of the receiver for the given operand. The
	 * receiver must be arity one (`1`), which is equivalent to its
	 * ordinal being greater than or equal to [distinctInstructions].
	 *
	 * @param operand
	 *   The operand.
	 * @return
	 *   The instruction coding.
	 */
	fun encoding(operand: Int): Int
	{
		if (ordinal < distinctInstructions)
		{
			throw UnsupportedOperationException()
		}
		// The operand should be positive, but allow -1 to represent undefined
		// branch targets.  The generated code with a -1 operand will be wrong,
		// but the first pass of code emission calculates the correct branch
		// targets, and the second pass uses the correct targets.
		assert(operand > 0 || operand == -1)
		val result = (operand shl distinctInstructionsShift) + modulus
		assert(operand(result) == operand) { "Overflow detected" }
		return result
	}

	/**
	 * Assume that the instruction encodes an operand that represents a [message
	 * part][MessageSplitter.messagePartsTuple] index: answer the operand.
	 * Answer 0 if the operand does not represent a message part.
	 *
	 * @param instruction
	 *   A coded instruction.
	 * @return
	 *   The message part index, or `0` if the assumption was false.
	 */
	open fun keywordIndex(instruction: Int): Int = 0

	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored. For example, a [BRANCH_FORWARD]
	 * instruction will need to visit both the next program counter *and* the
	 * branch target.
	 *
	 * @param instruction
	 *   The encoded parsing instruction at the specified program counter.
	 * @param currentPc
	 *   The current program counter.
	 * @return
	 *   The list of successor program counters.
	 */
	open fun successorPcs(instruction: Int, currentPc: Int): List<Int> =
		listOf(currentPc + 1)

	/**
	 * Assume that the instruction encodes an operand that represents the index
	 * of an argument to be checked (for grammatical restrictions): answer the
	 * operand.
	 *
	 * @param instruction
	 *   A coded instruction.
	 * @return
	 *   The argument index, or `0` if the assumption was false.
	 */
	open fun checkArgumentIndex(instruction: Int): Int = 0

	/**
	 * Extract the index of the type check argument for a [TYPE_CHECK_ARGUMENT]
	 * parsing instruction.  This indexes the static
	 * [MessageSplitter.constantForIndex].
	 *
	 * @param instruction
	 *   A coded instruction
	 * @return
	 *   The index of the type to be checked against.
	 */
	open fun typeCheckArgumentIndex(instruction: Int): Int
	{
		throw RuntimeException("Parsing instruction is inappropriate")
	}

	/**
	 * Perform one parsing instruction.
	 *
	 * @param compiler
	 *   The [AvailCompiler] which is parsing.
	 * @param instruction
	 *   An [Int] encoding the `ParsingOperation` to execute.
	 * @param successorTrees
	 *   The [tuple][TupleDescriptor] of [message bundle trees][A_BundleTree] at
	 *   which to continue parsing.
	 * @param start
	 *   Where to start parsing.
	 * @param firstArgOrNull
	 *   Either the already-parsed first argument or `null`. If we're looking
	 *   for leading-argument message sends to wrap an expression then this is
	 *   not-`null` before the first argument position is encountered, otherwise
	 *   it's `null` and we should reject attempts to start with an argument
	 *   (before a keyword).
	 * @param argsSoFar
	 *   The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *   The parsing markers that have been recorded so far.
	 * @param initialTokenPosition
	 *   The position at which parsing of this message started. If it was parsed
	 *   as a leading argument send (i.e., firstArgOrNull started out
	 *   non-`null`) then the position is of the token following the first
	 *   argument.
	 * @param consumedAnything
	 *   Whether any tokens or arguments have been consumed yet.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedStaticTokens
	 *   The immutable [List] of "static" [A_Token]s that have been encountered
	 *   and consumed for the current method or macro invocation being parsed.
	 *   These are the tokens that correspond with tokens that occur verbatim
	 *   inside the name of the method or macro.
	 * @param continuation
	 *   What to do with a complete [message send][SendPhraseDescriptor].
	 */
	internal abstract fun execute(
		compiler: AvailCompiler,
		instruction: Int,
		successorTrees: A_Tuple,
		start: ParserState,
		firstArgOrNull: A_Phrase?,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		initialTokenPosition: ParserState,
		consumedAnything: Boolean,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedStaticTokens: List<A_Token>,
		continuation: Con1)

	companion object
	{
		/**
		 * My array of values, since [ParsingOperation.values] makes a copy
		 * every time.
		 */
		internal val all = values()

		/**
		 * The binary logarithm of the number of distinct instructions supported
		 * by the coding scheme.  It must be integral.
		 */
		internal const val distinctInstructionsShift = 4

		/**
		 * The number of distinct instructions supported by the coding scheme.
		 * It must be a power of two.
		 */
		const val distinctInstructions = 1 shl distinctInstructionsShift

		/**
		 * Answer the operand given a coded instruction (that represents the
		 * same operation as the receiver).
		 *
		 * @param instruction
		 *   A coded instruction.
		 * @return
		 *   The operand.
		 */
		@JvmStatic
		fun operand(instruction: Int) =
			instruction shr distinctInstructionsShift

		/**
		 * Decode the specified instruction into a `ParsingOperation`.
		 *
		 * @param instruction
		 *   A coded instruction.
		 * @return
		 *   The decoded operation.
		 */
		@JvmStatic
		fun decode(instruction: Int): ParsingOperation
		{
			if (instruction < distinctInstructions)
			{
				return all[instruction]
			}
			// It's parametric, so it resides in the next 'distinctInstructions'
			// region of enum values.  Mask it and add the offset.
			val subscript =
				(instruction and distinctInstructions - 1) +
					distinctInstructions
			return all[subscript]
		}

		/**
		 * Produce a [Describer] that says a variable use was expected, and
		 * indicates why.
		 *
		 * @param successorTree
		 *   The next [A_BundleTree] after the current instruction.
		 * @return
		 *   The [Describer].
		 */
		private fun describeWhyVariableUseIsExpected(
				successorTree: A_BundleTree): Describer =
			{ continuation ->
				val bundles = successorTree.allParsingPlansInProgress().keysAsSet()
				val builder = StringBuilder()
				builder.append("a variable use, for one of:")
				if (bundles.setSize() > 2)
				{
					builder.append("\n\t")
				}
				else
				{
					builder.append(' ')
				}
				var first = true
				for (bundle in bundles)
				{
					if (!first)
					{
						builder.append(", ")
					}
					builder.append(bundle.message().atomName())
					first = false
				}
				continuation(builder.toString())
			}
	}
}
