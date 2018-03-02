/*
 * ParsingOperation.java
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

package com.avail.compiler;

import com.avail.compiler.AvailCompiler.PartialSubexpressionList;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.evaluation.Describer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.avail.compiler.AvailCompiler.Con;
import static com.avail.compiler.ParsingConversionRule.ruleNumber;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ListPhraseDescriptor.emptyListNode;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor
	.newMacroSubstitution;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PermutedListPhraseDescriptor
	.newPermutedListNode;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
	.VARIABLE_USE_PHRASE;
import static com.avail.descriptor.ReferencePhraseDescriptor
	.referenceNodeFromUse;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.utility.PrefixSharingList.*;
import static com.avail.utility.StackPrinter.trace;

/**
 * {@code ParsingOperation} describes the operations available for parsing Avail
 * message names.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("VariableNotUsedInsideIf")
public enum ParsingOperation
{
	/*
	 * Arity zero entries:
	 */

	/**
	 * {@code 0} - Push a new {@linkplain ListPhraseDescriptor list} that
	 * contains an {@linkplain TupleDescriptor#emptyTuple() empty tuple} of
	 * {@linkplain PhraseDescriptor phrases} onto the parse stack.
	 */
	EMPTY_LIST(0, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			// Push an empty list phrase and continue.
			assert successorTrees.tupleSize() == 1;
			final List<A_Phrase> newArgsSoFar =
				append(argsSoFar, emptyListNode());
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 1} - Pop an argument from the parse stack of the current
	 * potential message send. Pop a {@linkplain ListPhraseDescriptor list} from
	 * the parse stack. Append the argument to the list. Push the resultant list
	 * onto the parse stack.
	 */
	APPEND_ARGUMENT(1, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final A_Phrase value = last(argsSoFar);
			final List<A_Phrase> poppedOnce = withoutLast(argsSoFar);
			final A_Phrase oldNode = last(poppedOnce);
			final A_Phrase listNode = oldNode.copyWith(value);
			final List<A_Phrase> newArgsSoFar =
				append(withoutLast(poppedOnce), listNode);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 2} - Push the current parse position onto the mark stack.
	 */
	SAVE_PARSE_POSITION(2, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final int marker =
				firstArgOrNull == null
					? start.position()
					: initialTokenPosition.position();
			final List<Integer> newMarksSoFar = append(marksSoFar, marker);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				newMarksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 3} - Pop the top marker off the mark stack.
	 */
	DISCARD_SAVED_PARSE_POSITION(3, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				withoutLast(marksSoFar),
				continuation);
		}
	},

	/**
	 * {@code 4} - Pop the top marker off the mark stack and compare it to the
	 * current parse position.  If they're the same, abort the current parse,
	 * otherwise push the current parse position onto the mark stack in place of
	 * the old marker and continue parsing.
	 */
	ENSURE_PARSE_PROGRESS(4, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final int oldMarker = last(marksSoFar);
			if (oldMarker == start.position())
			{
				// No progress has been made.  Reject this path.
				return;
			}
			final int newMarker = start.position();
			final List<Integer> newMarksSoFar =
				append(withoutLast(marksSoFar), newMarker);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				newMarksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 5} - Parse an ordinary argument of a message send, pushing the
	 * expression onto the parse stack.
	 */
	PARSE_ARGUMENT(5, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final A_BundleTree successorTree = successorTrees.tupleAt(1);
			final @Nullable PartialSubexpressionList partialSubexpressionList =
				firstArgOrNull == null
					? continuation.superexpressions().advancedTo(successorTree)
					: continuation.superexpressions;
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"argument",
				firstArgOrNull,
				firstArgOrNull == null
					&& initialTokenPosition.lexingState != start.lexingState,
				false,
				Con(
					partialSubexpressionList,
					solution ->
					{
						final List<A_Phrase> newArgsSoFar =
							append(argsSoFar, solution.phrase());
						compiler.eventuallyParseRestOfSendNode(
							solution.endState(),
							successorTree,
							null,
							initialTokenPosition,
							// The argument counts as something that was
							// consumed if it's not a leading argument...
							firstArgOrNull == null,
							consumedStaticTokens,
							newArgsSoFar,
							marksSoFar,
							continuation);
					}));
		}
	},

	/**
	 * {@code 6} - Parse an expression, even one whose expressionType is ⊤, then
	 * push <em>a literal phrase wrapping this expression</em> onto the parse
	 * stack.
	 *
	 * <p>If we didn't wrap the phrase inside a literal phrase, we wouldn't be
	 * able to process sequences of statements in macros, since they would each
	 * have an expressionType of ⊤ (or if one was ⊥, the entire expressionType
	 * would also be ⊥).  Instead, they will have the expressionType phrase⇒⊤
	 * (or phrase⇒⊥), which is perfectly fine to put inside a list phrase during
	 * parsing.</p>
	 */
	PARSE_TOP_VALUED_ARGUMENT(6, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final @Nullable PartialSubexpressionList partialSubexpressionList =
				firstArgOrNull == null
					? continuation.superexpressions().advancedTo(
						successorTrees.tupleAt(1))
					: continuation.superexpressions;
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"top-valued argument",
				firstArgOrNull,
				firstArgOrNull == null
					&& initialTokenPosition.lexingState != start.lexingState,
				true,
				Con(
					partialSubexpressionList,
					solution ->
					{
						final List<A_Phrase> newArgsSoFar =
							append(argsSoFar, solution.phrase());
						compiler.eventuallyParseRestOfSendNode(
							solution.endState(),
							successorTrees.tupleAt(1),
							null,
							initialTokenPosition,
							// The argument counts as something that was
							// consumed if it's not a leading argument...
							firstArgOrNull == null,
							consumedStaticTokens,
							newArgsSoFar,
							marksSoFar,
							continuation);
					}));
		}
	},

	/**
	 * {@code 7} - Parse a {@linkplain TokenDescriptor raw token}. It should
	 * correspond to a {@linkplain VariableDescriptor variable} that is
	 * in scope. Push a {@linkplain ReferencePhraseDescriptor variable reference
	 * phrase} onto the parse stack.
	 */
	PARSE_VARIABLE_REFERENCE(7, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final @Nullable PartialSubexpressionList partialSubexpressionList =
				firstArgOrNull == null
					? continuation.superexpressions().advancedTo(
						successorTrees.tupleAt(1))
					: continuation.superexpressions;
			compiler.parseSendArgumentWithExplanationThen(
				start,
				"variable reference",
				firstArgOrNull,
				firstArgOrNull == null
					&& initialTokenPosition.lexingState != start.lexingState,
				false,
				Con(
					partialSubexpressionList,
					variableUseSolution ->
					{
						assert successorTrees.tupleSize() == 1;
						final A_Phrase variableUse =
							variableUseSolution.phrase();
						final A_Phrase rawVariableUse =
							variableUse.stripMacro();
						final ParserState afterUse =
							variableUseSolution.endState();
						if (!rawVariableUse.phraseKindIsUnder(
							VARIABLE_USE_PHRASE))
						{
							if (consumedAnything)
							{
								// At least one token besides the variable
								// use has been encountered, so go ahead and
								// report that we expected a variable.
								afterUse.expected(
									describeWhyVariableUseIsExpected(
										successorTrees.tupleAt(1)));
							}
							// It wasn't a variable use phrase, so give up.
							return;
						}
						// Make sure taking a reference is appropriate.
						final DeclarationKind declarationKind =
							rawVariableUse.declaration().declarationKind();
						if (!declarationKind.isVariable())
						{
							if (consumedAnything)
							{
								// Only complain about this not being a
								// variable if we've parsed something
								// besides the variable reference argument.
								afterUse.expected(
									"variable for reference argument to be "
										+ "assignable, not "
										+ declarationKind.nativeKindName());
							}
							return;
						}
						// Create a variable reference from this use.
						final A_Phrase rawVariableReference =
							referenceNodeFromUse(rawVariableUse);
						final A_Phrase variableReference =
							variableUse.isMacroSubstitutionNode()
								?
								newMacroSubstitution(
									variableUse.macroOriginalSendNode(),
									rawVariableReference)
								: rawVariableReference;
						compiler.eventuallyParseRestOfSendNode(
							afterUse,
							successorTrees.tupleAt(1),
							null,
							initialTokenPosition,
							// The argument counts as something that was
							// consumed if it's not a leading argument...
							firstArgOrNull == null,
							consumedStaticTokens,
							append(argsSoFar, variableReference),
							marksSoFar,
							continuation);
					}));
		}
	},

	/**
	 * {@code 8} - Parse an argument of a message send, using the <em>outermost
	 * (module) scope</em>.  Leave it on the parse stack.
	 */
	PARSE_ARGUMENT_IN_MODULE_SCOPE(8, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			compiler.parseArgumentInModuleScopeThen(
				start,
				firstArgOrNull,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				initialTokenPosition,
				successorTrees,
				continuation);
		}
	},

	/**
	 * {@code 9} - Parse <em>any</em> {@linkplain TokenDescriptor raw token},
	 * leaving it on the parse stack.  In particular, push a literal phrase
	 * whose token is a synthetic literal token whose value is the actual token
	 * that was parsed.
	 */
	PARSE_ANY_RAW_TOKEN(9, false, false)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			if (firstArgOrNull != null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument
				// has been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression),
				// then reject the parse.
				return;
			}
			compiler.skipWhitespaceAndComments(
				start,
				statesAfterWhitespace ->
				{
					for (final ParserState state : statesAfterWhitespace)
					{
						state.lexingState.withTokensDo(
							nextTokens ->
							{
								for (final A_Token token : nextTokens)
								{
									final A_Token syntheticToken =
										literalToken(
											token.string(),
											token.leadingWhitespace(),
											token.trailingWhitespace(),
											token.start(),
											token.lineNumber(),
											SYNTHETIC_LITERAL,
											token);
									final A_Phrase literalNode =
										literalNodeFromToken(syntheticToken);
									final List<A_Phrase> newArgsSoFar =
										append(argsSoFar, literalNode);
									compiler.eventuallyParseRestOfSendNode(
										new ParserState(
											token.nextLexingStateIn(
												compiler.compilationContext),
											start.clientDataMap,
											start.capturedCommentTokens),
										successorTrees.tupleAt(1),
										null,
										initialTokenPosition,
										true,
										append(
											consumedStaticTokens,
											syntheticToken),
										newArgsSoFar,
										marksSoFar,
										continuation);
								}
							});
					}
				},
				new AtomicBoolean(false));
		}
	},

	/**
	 * {@code 10} - Parse a raw <em>{@linkplain TokenType#KEYWORD keyword}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 */
	PARSE_RAW_KEYWORD_TOKEN(10, false, false)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			if (firstArgOrNull != null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument
				// has been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression),
				// then reject the parse.
				return;
			}
			compiler.skipWhitespaceAndComments(
				start,
				statesAfterWhitespace ->
				{
					for (final ParserState state : statesAfterWhitespace)
					{
						state.lexingState.withTokensDo(
							nextTokens ->
							{
								for (final A_Token token : nextTokens)
								{
									final TokenType tokenType = token.tokenType();
									if (tokenType != KEYWORD)
									{
										if (consumedAnything)
										{
											start.expected(
												"a keyword token, not "
													+ token.string());
										}
										continue;
									}
									final A_Token syntheticToken =
										literalToken(
											token.string(),
											token.leadingWhitespace(),
											token.trailingWhitespace(),
											token.start(),
											token.lineNumber(),
											SYNTHETIC_LITERAL,
											token);
									final A_Phrase literalNode =
										literalNodeFromToken(syntheticToken);
									final List<A_Phrase> newArgsSoFar =
										append(argsSoFar, literalNode);
									compiler.eventuallyParseRestOfSendNode(
										new ParserState(
											token.nextLexingStateIn(
												compiler.compilationContext),
											start.clientDataMap,
											start.capturedCommentTokens),
										successorTrees.tupleAt(1),
										null,
										initialTokenPosition,
										true,
										append(
											consumedStaticTokens,
											syntheticToken),
										newArgsSoFar,
										marksSoFar,
										continuation);
								}
							});
					}
				},
				new AtomicBoolean(false));
		}
	},

	/**
	 * {@code 11} - Parse a raw <em>{@linkplain TokenType#LITERAL literal}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 */
	PARSE_RAW_STRING_LITERAL_TOKEN(11, false, false)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			if (firstArgOrNull != null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument
				// has been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression),
				// then reject the parse.
				return;
			}
			compiler.skipWhitespaceAndComments(
				start,
				statesAfterWhitespace ->
				{
					for (final ParserState state : statesAfterWhitespace)
					{
						state.lexingState.withTokensDo(nextTokens ->
						{
							for (final A_Token token : nextTokens)
							{
								final TokenType tokenType = token.tokenType();
								if (tokenType != LITERAL
									|| !token.literal().isInstanceOf(
										stringType()))
								{
									if (consumedAnything)
									{
										start.expected(
											"a string literal token, not "
												+ (tokenType != LITERAL
													   ? token.string()
													   : token.literal()));
									}
									continue;
								}
								final A_Token syntheticToken =
									literalToken(
										token.string(),
										token.leadingWhitespace(),
										token.trailingWhitespace(),
										token.start(),
										token.lineNumber(),
										SYNTHETIC_LITERAL,
										token);
								final A_Phrase literalNode =
									literalNodeFromToken(syntheticToken);
								final List<A_Phrase> newArgsSoFar =
									append(argsSoFar, literalNode);
								compiler.eventuallyParseRestOfSendNode(
									new ParserState(
										token.nextLexingStateIn(
											compiler.compilationContext),
										start.clientDataMap,
										start.capturedCommentTokens),
									successorTrees.tupleAt(1),
									null,
									initialTokenPosition,
									true,
									append(
										consumedStaticTokens,
										syntheticToken),
									newArgsSoFar,
									marksSoFar,
									continuation);
							}
						});
					}
				},
				new AtomicBoolean(false));
		}
	},

	/**
	 * {@code 12} - Parse a raw <em>{@linkplain TokenType#LITERAL literal}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 */
	PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN(12, false, false)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			if (firstArgOrNull != null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument
				// has been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression),
				// then reject the parse.
				return;
			}
			compiler.skipWhitespaceAndComments(
				start,
				statesAfterWhitespace ->
				{
					for (final ParserState state : statesAfterWhitespace)
					{
						state.lexingState.withTokensDo(
							nextTokens ->
							{
								for (final A_Token token : nextTokens)
								{
									final TokenType tokenType =
										token.tokenType();
									if (tokenType != LITERAL
										|| !token.literal().isInstanceOf(
											wholeNumbers()))
									{
										if (consumedAnything)
										{
											start.expected(
												"a whole number literal token, "
													+ "not "
													+ (token.tokenType()
														   != LITERAL
													   ? token.string()
													   : token.literal()));
										}
										continue;
									}
									final A_Token syntheticToken =
										literalToken(
											token.string(),
											token.leadingWhitespace(),
											token.trailingWhitespace(),
											token.start(),
											token.lineNumber(),
											SYNTHETIC_LITERAL,
											token);
									final A_Phrase literalNode =
										literalNodeFromToken(syntheticToken);
									final List<A_Phrase> newArgsSoFar =
										append(argsSoFar, literalNode);
									compiler.eventuallyParseRestOfSendNode(
										new ParserState(
											token.nextLexingStateIn(
												compiler.compilationContext),
											start.clientDataMap,
											start.capturedCommentTokens),
										successorTrees.tupleAt(1),
										null,
										initialTokenPosition,
										true,
										append(
											consumedStaticTokens,
											syntheticToken),
										newArgsSoFar,
										marksSoFar,
										continuation);
								}
							});
					}
				},
				new AtomicBoolean(false));
		}
	},

	/**
	 * {@code 13} - Concatenate the two lists that have been pushed previously.
	 */
	CONCATENATE(13, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final A_Phrase right = last(argsSoFar);
			final List<A_Phrase> popped1 = withoutLast(argsSoFar);
			A_Phrase concatenated = last(popped1);
			final List<A_Phrase> popped2 = withoutLast(popped1);
			for (final A_Phrase rightElement : right.expressionsTuple())
			{
				concatenated = concatenated.copyWith(rightElement);
			}
			final List<A_Phrase> newArgsSoFar = append(popped2, concatenated);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 14} - Reserved for future use.
	 */
	RESERVED_14(14, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert false : "Illegal reserved parsing operation";
		}
	},

	/**
	 * {@code 15} - Reserved for future use.
	 */
	RESERVED_15(15, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert false : "Illegal reserved parsing operation";
		}
	},

	/*
	 * Arity one entries:
	 */

	/**
	 * {@code 16*N+0} - Branch to instruction N, which must be after the current
	 * instruction. Attempt to continue parsing at both the next instruction and
	 * instruction N.
	 */
	BRANCH_FORWARD(0, false, true)
	{
		@Override
		public List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Arrays.asList(operand(instruction), currentPc + 1);
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			for (final A_BundleTree successorTree : successorTrees)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation);
			}
		}
	},

	/**
	 * {@code 16*N+1} - Jump to instruction N, which must be after the current
	 * instruction. Attempt to continue parsing only at instruction N.
	 */
	JUMP_FORWARD(1, false, true)
	{
		@Override
		public List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Collections.singletonList(operand(instruction));
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+2} - Jump to instruction N, which must be before the current
	 * instruction. Attempt to continue parsing only at instruction N.
	 */
	JUMP_BACKWARD(2, false, true)
		{
			@Override
			public List<Integer> successorPcs (
				final int instruction,
				final int currentPc)
			{
				return Collections.singletonList(operand(instruction));
			}

			@Override
			void execute (
				final AvailCompiler compiler,
				final int instruction,
				final A_Tuple successorTrees,
				final ParserState start,
				final @Nullable A_Phrase firstArgOrNull,
				final List<A_Phrase> argsSoFar,
				final List<Integer> marksSoFar,
				final ParserState initialTokenPosition,
				final boolean consumedAnything,
				final List<A_Token> consumedStaticTokens,
				final Con continuation)
			{
				assert successorTrees.tupleSize() == 1;
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation);
			}
		},

	/**
	 * {@code 16*N+3} - Parse the Nth {@linkplain MessageSplitter#messageParts()
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}. It should be matched case sensitively against the
	 * source token.
	 */
	PARSE_PART(3, false, false)
	{
		@Override
		public int keywordIndex (final int instruction)
		{
			return operand(instruction);
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert false : name() + " instruction should not be dispatched";
		}
	},

	/**
	 * {@code 16*N+4} - Parse the Nth {@linkplain MessageSplitter#messagePartsList
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}. It should be matched case insensitively against
	 * the source token.
	 */
	PARSE_PART_CASE_INSENSITIVELY(4, false, false)
	{
		@Override
		public int keywordIndex (final int instruction)
		{
			return operand(instruction);
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert false : name() + " instruction should not be dispatched";
		}
	},

	/**
	 * {@code 16*N+5} - Apply grammatical restrictions to the Nth leaf argument
	 * (underscore/ellipsis) of the current message, which is on the stack.
	 */
	CHECK_ARGUMENT(5, true, true)
	{
		@Override
		public int checkArgumentIndex (final int instruction)
		{
			return operand(instruction);
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			assert firstArgOrNull == null;
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				null,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				argsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+6} - Pop an argument from the parse stack and apply the
	 * {@linkplain ParsingConversionRule conversion rule} specified by N.
	 */
	CONVERT(6, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final A_Phrase input = last(argsSoFar);
			final AtomicBoolean sanityFlag = new AtomicBoolean();
			final ParsingConversionRule conversionRule =
				ruleNumber(operand(instruction));
			conversionRule.convert(
				compiler.compilationContext,
				start.lexingState,
				input,
				replacementExpression ->
				{
					assert sanityFlag.compareAndSet(false, true);
					final List<A_Phrase> newArgsSoFar =
						append(withoutLast(argsSoFar), replacementExpression);
					compiler.eventuallyParseRestOfSendNode(
						start,
						successorTrees.tupleAt(1),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						consumedStaticTokens,
						newArgsSoFar,
						marksSoFar,
						continuation);
				},
				e ->
				{
					// Deal with a failed conversion.  As of 2016-08-28,
					// this can only happen during an expression
					// evaluation.
					assert sanityFlag.compareAndSet(false, true);
					start.expected(withString -> withString.value(
						"evaluation of expression not to have "
							+ "thrown Java exception:\n"
							+ trace(e)));
				});
		}
	},

	/**
	 * {@code 16*N+7} - A macro has been parsed up to a section checkpoint (§).
	 * Make a copy of the parse stack, then perform the equivalent of an {@link
	 * #APPEND_ARGUMENT} on the copy, the specified number of times minus one
	 * (because zero is not a legal operand).  Make it into a single {@linkplain
	 * ListPhraseDescriptor list phrase} and push it onto the original parse
	 * stack. It will be consumed by a subsequent {@link #RUN_PREFIX_FUNCTION}.
	 *
	 * <p>This instruction is detected specially by the {@linkplain
	 * MessageBundleTreeDescriptor message bundle tree}'s {@linkplain
	 * A_BundleTree#expand(A_Module)} operation.  Its successors are separated
	 * into distinct message bundle trees, one per message bundle.</p>
	 */
	PREPARE_TO_RUN_PREFIX_FUNCTION(7, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			List<A_Phrase> stackCopy = argsSoFar;
			// Only do N-1 steps.  We simply couldn't encode zero as an
			// operand, so we always bias by one automatically.
			final int fixupDepth = operand(instruction);
			for (int i = fixupDepth; i > 1; i--)
			{
				// Pop the last element and append it to the second last.
				final A_Phrase value = last(stackCopy);
				final List<A_Phrase> poppedOnce = withoutLast(stackCopy);
				final A_Phrase oldNode = last(poppedOnce);
				final A_Phrase listNode = oldNode.copyWith(value);
				stackCopy = append(withoutLast(poppedOnce), listNode);
			}
			assert stackCopy.size() == 1;
			final A_Phrase newListNode = stackCopy.get(0);
			final List<A_Phrase> newStack = append(argsSoFar, newListNode);
			for (final A_BundleTree successorTree : successorTrees)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedStaticTokens,
					newStack,
					marksSoFar,
					continuation);
			}
		}
	},

	/**
	 * {@code 16*N+8} - A macro has been parsed up to a section checkpoint (§),
	 * and a copy of the cleaned up parse stack has been pushed, so invoke the
	 * Nth prefix function associated with the macro.  Consume the previously
	 * pushed copy of the parse stack.  The current {@link ParserState}'s
	 * {@linkplain ParserState#clientDataMap} is stashed in the new {@link
	 * FiberDescriptor fiber}'s {@linkplain AvailObject#fiberGlobals()} and
	 * retrieved afterward, so the prefix function and macros can alter the
	 * scope or communicate with each other by manipulating this {@linkplain
	 * MapDescriptor map}.  This technique prevents chatter between separate
	 * fibers (i.e., parsing can still be done in parallel) and between separate
	 * linguistic abstractions (the keys are atoms and are therefore modular).
	 */
	RUN_PREFIX_FUNCTION(8, false, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final A_BundleTree successorTree = successorTrees.tupleAt(1);
			// Look inside the only successor to find the only bundle.
			final A_Map bundlesMap = successorTree.allParsingPlansInProgress();
			assert bundlesMap.mapSize() == 1;
			final A_Map submap = bundlesMap.mapIterable().next().value();
			assert submap.mapSize() == 1;
			final A_Definition definition = submap.mapIterable().next().key();
			final A_Tuple prefixFunctions = definition.prefixFunctions();
			final int prefixIndex = operand(instruction);
			final A_Function prefixFunction =
				prefixFunctions.tupleAt(prefixIndex);
			final A_Phrase prefixArgumentsList = last(argsSoFar);
			final List<A_Phrase> withoutPrefixArguments =
				withoutLast(argsSoFar);
			final List<AvailObject> listOfArgs = toList(
				prefixArgumentsList.expressionsTuple());
			compiler.runPrefixFunctionThen(
				start,
				successorTree,
				prefixFunction,
				listOfArgs,
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				withoutPrefixArguments,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+9} - Permute the elements of the list phrase on the top of
	 * the stack via the permutation found via {@linkplain
	 * MessageSplitter#permutationAtIndex(int)}.  The list phrase must be the
	 * same size as the permutation.
	 */
	PERMUTE_LIST(9, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			final int permutationIndex = operand(instruction);
			final A_Tuple permutation =
				MessageSplitter.permutationAtIndex(permutationIndex);
			final A_Phrase poppedList = last(argsSoFar);
			List<A_Phrase> stack = withoutLast(argsSoFar);
			stack = append(stack, newPermutedListNode(poppedList, permutation));
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				stack,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+10} - Check that the list phrase on the top of the stack has
	 * at least the specified size.  Proceed to the next instruction only if
	 * this is the case.
	 */
	CHECK_AT_LEAST(10, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			final int limit = operand(instruction);
			final A_Phrase top = last(argsSoFar);
			if (top.expressionsSize() >= limit)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation);
			}
		}
	},

	/**
	 * {@code 16*N+11} - Check that the list phrase on the top of the stack has
	 * at most the specified size.  Proceed to the next instruction only if this
	 * is the case.
	 */
	CHECK_AT_MOST(11, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			final int limit = operand(instruction);
			final A_Phrase top = last(argsSoFar);
			if (top.expressionsSize() <= limit)
			{
				compiler.eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedStaticTokens,
					argsSoFar,
					marksSoFar,
					continuation);
			}
		}
	},

	/**
	 * {@code 16*N+12} - Use the type of the argument just parsed to select
	 * among successor message bundle trees.  Those message bundle trees are
	 * filtered by the allowable leaf argument type.  This test is
	 * <em>precise</em>, and requires repeated groups to be unrolled for the
	 * tuple type specific to that argument slot of that definition, or at least
	 * until the {@link A_Type#defaultType()} of the tuple type has been
	 * reached.
	 */
	TYPE_CHECK_ARGUMENT(12, true, true)
	{
		@Override
		public int typeCheckArgumentIndex (final int instruction)
		{
			return operand(instruction);
		}

		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert false : name() + " instruction should not be dispatched";
		}
	},

	/**
	 * {@code 16*N+13} - Pop N arguments from the parse stack of the current
	 * potential message send. Create an N-element {@linkplain
	 * ListPhraseDescriptor list} with them, and push the list back onto the
	 * parse stack.
	 *
	 * <p>This is the equivalent of pushing an empty list prior to pushing those
	 * arguments, then using {@link #APPEND_ARGUMENT} after each argument is
	 * parsed to add them to the list.  The advantage of using this operation
	 * instead is to allow the pure stack manipulation operations to occur after
	 * parsing an argument and/or fixed tokens, which increases the conformity
	 * between the non-repeating and repeating clauses, which in turn reduces
	 * (at least) the number of actions executed each time the root bundle tree
	 * is used to start parsing a subexpression.</p>
	 */
	WRAP_IN_LIST(13, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final int listSize = operand(instruction);
			final int totalSize = argsSoFar.size();
			final List<A_Phrase> unpopped =
				argsSoFar.subList(0, totalSize - listSize);
			final List<A_Phrase> popped =
				argsSoFar.subList(totalSize - listSize, totalSize);
			final A_Phrase newListNode = newListNode(
				tupleFromList(popped));
			final List<A_Phrase> newArgsSoFar =
				append(unpopped, newListNode);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+14} - Push a {@link LiteralPhraseDescriptor literal phrase}
	 * containing the constant found at the position in the type list indicated
	 * by the operand.
	 */
	PUSH_LITERAL(14, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			final AvailObject constant = MessageSplitter.constantForIndex(
				operand(instruction));
			final A_Token token = literalToken(
				stringFrom(constant.toString()),
				emptyTuple(),
				emptyTuple(),
				initialTokenPosition.position(),
				initialTokenPosition.lineNumber(),
				LITERAL,
				constant);
			final A_Phrase literalNode = literalNodeFromToken(token);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				append(argsSoFar, literalNode),
				marksSoFar,
				continuation);
		}
	},

	/**
	 * {@code 16*N+15} - Reverse the N top elements of the stack.  The new stack
	 * has the same depth as the old stack.
	 */
	REVERSE_STACK(15, true, true)
	{
		@Override
		void execute (
			final AvailCompiler compiler,
			final int instruction,
			final A_Tuple successorTrees,
			final ParserState start,
			final @Nullable A_Phrase firstArgOrNull,
			final List<A_Phrase> argsSoFar,
			final List<Integer> marksSoFar,
			final ParserState initialTokenPosition,
			final boolean consumedAnything,
			final List<A_Token> consumedStaticTokens,
			final Con continuation)
		{
			assert successorTrees.tupleSize() == 1;
			final int depthToReverse = operand(instruction);
			final int totalSize = argsSoFar.size();
			final List<A_Phrase> unpopped =
				argsSoFar.subList(0, totalSize - depthToReverse);
			final List<A_Phrase> popped =
				new ArrayList<>(
					argsSoFar.subList(totalSize - depthToReverse, totalSize));
			Collections.reverse(popped);
			final List<A_Phrase> newArgsSoFar = new ArrayList<>(unpopped);
			newArgsSoFar.addAll(popped);
			compiler.eventuallyParseRestOfSendNode(
				start,
				successorTrees.tupleAt(1),
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedStaticTokens,
				newArgsSoFar,
				marksSoFar,
				continuation);
		}
	};

	/**
	 * My array of values, since {@link Enum}.values() makes a copy every time.
	 */
	static final ParsingOperation[] all = values();

	/**
	 * The binary logarithm of the number of distinct instructions supported by
	 * the coding scheme.  It must be integral.
	 */
	static final int distinctInstructionsShift = 4;

	/**
	 * The number of distinct instructions supported by the coding scheme.  It
	 * must be a power of two.
	 */
	public static final int distinctInstructions =
		1 << distinctInstructionsShift;

	/** The modulus that represents the operation uniquely for its arity. */
	private final int modulus;

	/** Whether this instance commutes with PARSE_PART instructions. */
	private final boolean commutesWithParsePart;

	/**
	 * Whether this operation can run successfully if there is a pre-parsed
	 * first argument that has not yet been consumed.
	 */
	public final boolean canRunIfHasFirstArgument;

	/**
	 * A {@link Statistic} that records the number of nanoseconds spent while
	 * executing occurrences of this {@link ParsingOperation}.
	 */
	public final Statistic parsingStatisticInNanoseconds = new Statistic(
		name(), StatisticReport.RUNNING_PARSING_INSTRUCTIONS);

	/**
	 * A {@link Statistic} that records the number of nanoseconds spent while
	 * expanding occurrences of this {@link ParsingOperation}.
	 */
	public final Statistic expandingStatisticInNanoseconds = new Statistic(
		name(), StatisticReport.EXPANDING_PARSING_INSTRUCTIONS);

	/**
	 * Construct a new {@code ParsingOperation} for this enum.
	 *
	 * @param modulus
	 *        The modulus that represents the operation uniquely for its arity.
	 * @param commutesWithParsePart
	 *        Whether a PARSE_PART instruction can be moved safely leftward over
	 *        this instruction.
	 * @param canRunIfHasFirstArgument
	 *        Whether this instruction can be run if the first argument has been
	 *        parsed but not yet consumed by a PARSE_ARGUMENT instruction.
	 */
	ParsingOperation (
		final int modulus,
		final boolean commutesWithParsePart,
		final boolean canRunIfHasFirstArgument)
	{
		this.modulus = modulus;
		this.commutesWithParsePart = commutesWithParsePart;
		this.canRunIfHasFirstArgument = canRunIfHasFirstArgument;
	}

	/**
	 * Answer the instruction coding of the receiver.
	 *
	 * @return The instruction coding.
	 */
	public final int encoding ()
	{
		if (ordinal() >= distinctInstructions)
		{
			throw new UnsupportedOperationException();
		}
		return modulus;
	}

	/**
	 * Answer the instruction coding of the receiver for the given operand. The
	 * receiver must be arity one ({@code 1}), which is equivalent to its
	 * ordinal being greater than or equal to {@code #distinctInstructions}.
	 *
	 * @param operand The operand.
	 * @return The instruction coding.
	 */
	public final int encoding (final int operand)
	{
		if (ordinal() < distinctInstructions)
		{
			throw new UnsupportedOperationException();
		}
		// The operand should be positive, but allow -1 to represent undefined
		// branch targets.  The generated code with a -1 operand will be wrong,
		// but the first pass of code emission calculates the correct branch
		// targets, and the second pass uses the correct targets.
		assert operand > 0 || operand == -1;
		final int result = (operand << distinctInstructionsShift) + modulus;
		assert operand(result) == operand : "Overflow detected";
		return result;
	}

	/**
	 * Answer the operand given a coded instruction (that represents the same
	 * operation as the receiver).
	 *
	 * @param instruction A coded instruction.
	 * @return The operand.
	 */
	public static int operand (final int instruction)
	{
		return instruction >> distinctInstructionsShift;
	}

	/**
	 * Assume that the instruction encodes an operand that represents a
	 * {@linkplain MessageSplitter#messagePartsList message part} index: answer
	 * the operand.  Answer 0 if the operand does not represent a message part.
	 *
	 * @param instruction A coded instruction.
	 * @return The message part index, or {@code 0} if the assumption was false.
	 */
	public int keywordIndex (final int instruction)
	{
		return 0;
	}

	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored. For example, a {@link #BRANCH_FORWARD}
	 * instruction will need to visit both the next program counter <em>and</em>
	 * the branch target.
	 *
	 * @param instruction The encoded parsing instruction at the specified
	 *                    program counter.
	 * @param currentPc The current program counter.
	 * @return The list of successor program counters.
	 */
	public List<Integer> successorPcs (
		final int instruction,
		final int currentPc)
	{
		return Collections.singletonList(currentPc + 1);
	}

	/**
	 * Assume that the instruction encodes an operand that represents the index
	 * of an argument to be checked (for grammatical restrictions): answer the
	 * operand.
	 *
	 * @param instruction A coded instruction.
	 * @return The argument index, or {@code 0} if the assumption was false.
	 */
	public int checkArgumentIndex (final int instruction)
	{
		return 0;
	}

	/**
	 * Extract the index of the type check argument for a {@link
	 * #TYPE_CHECK_ARGUMENT} parsing instruction.  This indexes the static
	 * {@link MessageSplitter#constantForIndex(int)}.
	 *
	 * @param instruction A coded instruction
	 * @return The index of the type to be checked against.
	 */
	public int typeCheckArgumentIndex (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Decode the specified instruction into a {@code ParsingOperation}.
	 *
	 * @param instruction A coded instruction.
	 * @return The decoded operation.
	 */
	public static ParsingOperation decode (final int instruction)
	{
		if (instruction < distinctInstructions)
		{
			return all[instruction];
		}
		// It's parametric, so it resides in the next 'distinctInstructions'
		// region of enum values.  Mask it and add the offset.
		final int subscript = (instruction & (distinctInstructions - 1))
			+ distinctInstructions;
		return all[subscript];
	}

	/**
	 * Answer whether this operation can commute with an adjacent {@link
	 * #PARSE_PART} or {@link #PARSE_PART_CASE_INSENSITIVELY} operation without
	 * changing the semantics of the parse.
	 *
	 * @return A {@code boolean}.
	 */
	public boolean commutesWithParsePart ()
	{
		return commutesWithParsePart;
	}

	/**
	 * Perform one parsing instruction.
	 *
	 * @param compiler
	 *        The {@link AvailCompiler} which is parsing.
	 * @param instruction
	 *        An int encoding the {@code ParsingOperation} to execute.
	 * @param successorTrees
	 *        The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        MessageBundleTreeDescriptor bundle trees} at which to continue
	 *        parsing.
	 * @param start
	 *        Where to start parsing.
	 * @param firstArgOrNull
	 *        Either the already-parsed first argument or null. If we're looking
	 *        for leading-argument message sends to wrap an expression then this
	 *        is not-null before the first argument position is encountered,
	 *        otherwise it's null and we should reject attempts to start with an
	 *        argument (before a keyword).
	 * @param argsSoFar
	 *        The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *        The parsing markers that have been recorded so far.
	 * @param initialTokenPosition
	 *        The position at which parsing of this message started. If it was
	 *        parsed as a leading argument send (i.e., firstArgOrNull started
	 *        out non-null) then the position is of the token following the
	 *        first argument.
	 * @param consumedAnything
	 *        Whether any tokens or arguments have been consumed yet.
	 * @param consumedStaticTokens
	 *        The immutable {@link List} of "static" {@link A_Token}s that have
	 *        been encountered and consumed for the current method or macro
	 *        invocation being parsed.  These are the tokens that correspond
	 *        with tokens that occur verbatim inside the name of the method or
	 *        macro.
	 * @param continuation
	 *        What to do with a complete {@linkplain SendPhraseDescriptor
	 *        message send}.
	 */
	abstract void execute (
		final AvailCompiler compiler,
		final int instruction,
		final A_Tuple successorTrees,
		final ParserState start,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Token> consumedStaticTokens,
		final Con continuation);

	/**
	 * Produce a {@link Describer} that says a variable use was expected, and
	 * indicates why.
	 *
	 * @param successorTree
	 *        The next {@link A_BundleTree} after the current instruction.
	 * @return The {@link Describer}.
	 */
	static Describer describeWhyVariableUseIsExpected (
		final A_BundleTree successorTree)
	{
		return continuation ->
		{
			final A_Set bundles =
				successorTree.allParsingPlansInProgress().keysAsSet();
			final StringBuilder builder = new StringBuilder();
			builder.append("a variable use, for one of:");
			if (bundles.setSize() > 2)
			{
				builder.append("\n\t");
			}
			else
			{
				builder.append(' ');
			}
			boolean first = true;
			for (final A_Bundle bundle : bundles)
			{
				if (!first)
				{
					builder.append(", ");
				}
				builder.append(bundle.message().atomName());
				first = false;
			}
			continuation.value(builder.toString());
		};
	}
}
