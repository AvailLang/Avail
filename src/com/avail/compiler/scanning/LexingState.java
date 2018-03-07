/*
 * LexingState.java
 * Copyright © 1993-2018, The Avail Foundation, LLC. All rights reserved.
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

package com.avail.compiler.scanning;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.compiler.CompilationContext;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Lexer;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.Interpreter;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Describer;
import com.avail.utility.evaluation.SimpleDescriber;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.evaluation.Transformer1NotNull;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TokenDescriptor.TokenType.END_OF_FILE;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

/**
 * {@link LexingState} instances represent the lexing state between tokens.
 * They bind together consecutive tokens into a directed acyclic graph, allowing
 * the {@link AvailCompiler} to process all possible paths (lexings), typically
 * in aggregate.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LexingState
{
	/**
	 * The compilation context for which this is a state of lexing.
	 */
	public final CompilationContext compilationContext;

	/**
	 * The position represented by this {@link LexingState}.  In particular,
	 * it's the (one-based) start of the current token within the source.
	 */
	public final int position;

	/**
	 * The one-based line number at which this state occurs in the source.
	 */
	public final int lineNumber;

	/**
	 * Every token, including whitespace, that has been parsed up to this point,
	 * from the start of the current top-level statement.  This should also
	 * include the initial leading whitespace, as we like to put related
	 * comments before statements.
	 */
	public final List<A_Token> allTokens;

	/**
	 * The immutable {@link List} of {@link A_Token tokens} that may each be
	 * next, starting at this lexing position.
	 *
	 * <p>This is generally {@code null} until it has been computed by lexing
	 * fibers.</p>
	 */
	private @Nullable List<A_Token> nextTokens = null;

	/**
	 * The collection of actions that should run when the {@link #nextTokens}
	 * list has been computed.
	 *
	 * <p>This is replaced by {@code null} when the list has been computed,
	 * running the waiting actions.  Newer actions are launched upon arrival
	 * after the tokens have been computed.</p>
	 */
	private  @Nullable List<Continuation1NotNull<List<A_Token>>> actions =
		new ArrayList<>();

	/**
	 * Construct a new immutable {@code LexingState}.  It starts in a state
	 * where the potential tokens at this position have not yet been computed,
	 * and no actions have been queued to run against that eventual list of
	 * tokens.
	 *
	 * @param compilationContext
	 *        The {@link CompilationContext} in which compilation is occurring.
	 * @param position
	 *        The one-based position in the source.
	 * @param lineNumber
	 *        The one-based line number of this position in the source.
	 * @param allTokens
	 *        The immutable list of {@link A_Token}s that have been parsed up to
	 *        this position, starting at the current top-level statement, but
	 *        including any leading whitespace and comment tokens.
	 */
	public LexingState (
		final CompilationContext compilationContext,
		final int position,
		final int lineNumber,
		final List<A_Token> allTokens)
	{
		this.compilationContext = compilationContext;
		this.position = position;
		this.lineNumber = lineNumber;
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		this.allTokens = allTokens;
	}

	/**
	 * Eventually invoke the given {@link Continuation1NotNull}s, each with the
	 * given argument.  Track them as outstanding actions, ensuring {@link
	 * CompilationContext#noMoreWorkUnits} is invoked only when all such queued
	 * actions for the {@link CompilationContext} have completed.  Ensure the
	 * queued count is increased prior to actually queueing any of the actions,
	 * to ensure a hasty execution of a prefix of the tasks doesn't cause the
	 * {@link CompilationContext#noMoreWorkUnits} to be executed prematurely.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param continuations
	 *        The non-empty list of continuations to execute, each with the
	 *        passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1 one-argument continuation}.
	 */
	private <ArgType> void workUnitsDo (
		final List<Continuation1NotNull<ArgType>> continuations,
		final ArgType argument)
	{
		compilationContext.workUnitsDo(this, continuations, argument);
	}

	/**
	 * Eventually invoke the given {@link Continuation1NotNull} with the given
	 * argument.  Track it as an outstanding action, ensuring {@link
	 * CompilationContext#noMoreWorkUnits} is invoked only when all such queued
	 * actions have completed.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1NotNull one-argument continuation}.
	 */
	public <ArgType> void workUnitDo (
		final Continuation1NotNull<ArgType> continuation,
		final ArgType argument)
	{
		compilationContext.workUnitsDo(
			this, singletonList(continuation), argument);
	}

	/**
	 * Eventually invoke the given {@link Continuation1NotNull} with the {@link
	 * List} of {@link A_Token tokens} at this position.  If necessary, launch
	 * {@link A_Fiber fibers} to run {@link A_Lexer lexers}, invoking the
	 * continuation only when all possible next tokens have been computed.
	 *
	 * @param newAction
	 *        What to do with the list of tokens.
	 */
	public synchronized void withTokensDo (
		final Continuation1NotNull<List<A_Token>> newAction)
	{
		if (actions == null)
		{
			// The complete list of tokens was already computed, and actions
			// that arrived before that completed have already been started.
			workUnitDo(newAction, stripNull(nextTokens));
			return;
		}
		// Postpone the action until the tokens have been computed.
		actions.add(newAction);
		if (actions.size() > 1)
		{
			// This wasn't the first action.  When the first action arrived, it
			// started computing the tokens, so having added the new action,
			// we're done.
			return;
		}
		// This was the first action, so launch the lexers to produce the list
		// of nextTokens and then run the queued actions.
		final List<A_Token> theNextTokens = new ArrayList<>(2);
		nextTokens = theNextTokens;
		final LexicalScanner scanner =
			compilationContext.loader().lexicalScanner();
		final A_String source = compilationContext.source();
		if (position > source.tupleSize())
		{
			// The end of the source code.  Produce an end-of-file token.
			assert position == source.tupleSize() + 1;
			theNextTokens.add(
				newToken(
					emptyTuple(),
					emptyTuple(),
					emptyTuple(),
					position,
					lineNumber,
					END_OF_FILE));
			for (final Continuation1NotNull<List<A_Token>> action : actions)
			{
				workUnitDo(action, theNextTokens);
			}
			actions = null;
			return;
		}
		final int codePoint =
			compilationContext.source().tupleCodePointAt(position);
		scanner.getLexersForCodePointThen(
			this,
			codePoint,
			this::evaluateLexers,
			this::reportLexerFilterFailures);
	}

	/**
	 * The lexer filters
	 * A lexer has just run and produced zero or more tokens at this position.
	 *
	 * @param applicableLexers
	 *        The lexers that passed their filter functions.
	 */
	private synchronized void evaluateLexers (
		final A_Tuple applicableLexers)
	{
		final List<A_Token> theNextTokens = stripNull(nextTokens);
		if (applicableLexers.tupleSize() == 0)
		{
			// No applicable lexers.
			final LexicalScanner scanner =
				compilationContext.loader().lexicalScanner();
			final int codePoint =
				compilationContext.source().tupleCodePointAt(position);
			expected(
				format(
					"an applicable lexer, but all %d filter functions returned "
						+ "false.  Code point = \'%c\' (U+%2$04x).",
					scanner.allVisibleLexers.size(),
					codePoint));
			for (final Continuation1NotNull<List<A_Token>> action
				: stripNull(actions))
			{
				workUnitDo(action, theNextTokens);
			}
			actions = null;
			return;
		}
		// There's at least one applicable lexer.  Launch
		// fibers which, when the last one completes, will
		// capture the list of tokens and run the actions.
		final AtomicInteger countdown =
			new AtomicInteger(applicableLexers.tupleSize());
		final List<A_BasicObject> arguments =
			Arrays.asList(
				compilationContext.source(),
				fromInt(position),
				fromInt(lineNumber));
		for (final A_Lexer lexer : applicableLexers)
		{
			evaluateLexerAndRunActionsWhenZero(lexer, arguments, countdown);
		}
	}

	/**
	 * Run a lexer.  When it completes, decrement the countdown.  If it reaches
	 * zero, run {@link #lexerBodyWasSuccessful(A_Set, AtomicInteger)} with
	 * all the possible tokens at this position to indicate success (otherwise
	 * indicate an expectation of a valid token).
	 *
	 * @param lexer
	 *        The lexer to execute.
	 * @param arguments
	 *        The arguments supplied to the lexer's body.
	 * @param countdown
	 *        A countdown to indicate completion of the group of lexers running
	 *        at the same source position in parallel.
	 */
	private void evaluateLexerAndRunActionsWhenZero (
		final A_Lexer lexer,
		final List<A_BasicObject> arguments,
		final AtomicInteger countdown)
	{
		final AvailLoader loader = compilationContext.loader();
		final A_Fiber fiber = newLoaderFiber(
			lexerBodyFunctionType().returnType(),
			loader,
			() -> formatString(
				"Lexer body on line %d for %s.", lineNumber, lexer));
		// TODO MvG - Set up fiber variables for lexing?
//		A_Map fiberGlobals = fiber.fiberGlobals();
//		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
//			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
//		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		final Continuation1NotNull<AvailObject> onSuccess =
			newTokenRuns -> lexerBodyWasSuccessful(newTokenRuns, countdown);
		final Continuation1NotNull<Throwable> onFailure =
			throwable ->
			{
				if (throwable instanceof AvailRejectedParseException)
				{
					final AvailRejectedParseException rej =
						(AvailRejectedParseException) throwable;
					expected(rej.rejectionString().asNativeString());
					return;
				}
				// Report the problem as an expectation, with a stack trace.
				expected(
					afterDescribing ->
					{
						final StringWriter writer = new StringWriter();
						throwable.printStackTrace(new PrintWriter(writer));
						final String text = format(
							"%s not to have failed while "
								+ "evaluating its body:\n%s",
							lexer.toString(),
							writer.toString());
						afterDescribing.value(text);
					});
			};
		assert compilationContext.getNoMoreWorkUnits() != null;
		// Wrap onSuccess and onFailure to maintain queued/completed counts.
		compilationContext.startWorkUnits(1);
		final AtomicBoolean oneWay = new AtomicBoolean();
		fiber.resultContinuation(
			compilationContext.workUnitCompletion(this, oneWay, onSuccess));
		fiber.failureContinuation(
			compilationContext.workUnitCompletion(this, oneWay, onFailure));
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		Interpreter.runOutermostFunction(
			loader.runtime(), fiber, lexer.lexerBodyFunction(), arguments);
	}

	/**
	 * A lexer body completed successfully with the given tuple of next tokens
	 * (all tokens that the lexer indicates might be the very next token).
	 *
	 * @param newTokenRuns
	 *        A set of sequences (tuples) of tokens that represent possible
	 *        future non-empty lineages of tokens, as produced by lexers.
	 * @param countdown
	 *        The {@link AtomicInteger} which counts down to zero with each
	 *        successful invocation (not each token), then runs all outstanding
	 *        actions once.
	 */
	private synchronized void lexerBodyWasSuccessful (
		final A_Set newTokenRuns,
		final AtomicInteger countdown)
	{
		final List<A_Token> theNextTokens = stripNull(nextTokens);
		for (final A_Tuple run : newTokenRuns)
		{
//			assert run.tupleSize() > 0;
			theNextTokens.add(run.tupleAt(1));
			LexingState state = this;
			for (final A_Token token : run)
			{
				token.setNextLexingStateFromPrior(state);
				state = token.nextLexingState();
			}
		}
		if (countdown.decrementAndGet() == 0)
		{
			// We just heard from the last lexer.  Run the actions once.
			workUnitsDo(stripNull(actions), theNextTokens);
			actions = null;
		}
	}

	/**
	 * Record the fact that at least one filter body failed.
	 *
	 * @param filterFailures
	 *        A non-empty map from each failed {@link A_Lexer} to the {@link
	 *        Throwable} that it threw.
	 */
	private void reportLexerFilterFailures (
		final Map<A_Lexer, Throwable> filterFailures)
	{
		for (final Entry<A_Lexer, Throwable> entry : filterFailures.entrySet())
		{
			expected(afterDescribing ->
			{
				final StringWriter stringWriter = new StringWriter();
				entry.getValue().printStackTrace(new PrintWriter(stringWriter));
				final String text = format(
					"%s not to have failed while evaluating its filter "
						+ "function:\n%s",
					entry.getKey().toString(),
					stringWriter.toString());
				afterDescribing.value(text);
			});
		}
	}

	/**
	 * If the {@link A_Token}s that start at this location have been computed,
	 * answer that {@link List}, otherwise answer {@code null}.  The list is
	 * copied to ensure it isn't modified by the client or other active lexers.
	 *
	 * <p>This should only used by diagnostics, specifically to avoid running
	 * lexers while producing failure messages.</p>
	 *
	 * @return The list of tokens or null.
	 */
	public final synchronized @Nullable List<A_Token>
		knownToBeComputedTokensOrNull ()
	{
		return nextTokens == null
			? null
			: unmodifiableList(new ArrayList<>(nextTokens));
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost few reached parse positions constitute the
	 * error message in case the parse fails.
	 *
	 * <p>
	 * The expectation is a {@linkplain Describer}, in case constructing a
	 * {@link String} frivolously would be prohibitive. There is also {@link
	 * #expected(String) another} version of this method that accepts a
	 * String directly.
	 * </p>
	 *
	 * @param describer
	 *        The {@code describer} to capture.
	 */
	public void expected (final Describer describer)
	{
		compilationContext.diagnostics.expectedAt(describer, this);
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost parse position constitute the error message
	 * in case the parse fails.
	 *
	 * @param values
	 *        A list of arbitrary {@linkplain AvailObject Avail values} that
	 *        should be stringified.
	 * @param transformer
	 *        A {@linkplain Transformer1 transformer} that accepts the
	 *        stringified values and answers an expectation message.
	 */
	public void expected (
		final List<? extends A_BasicObject> values,
		final Transformer1NotNull<List<String>, String> transformer)
	{
		expected(continuation ->
			Interpreter.stringifyThen(
				compilationContext.loader().runtime(),
				compilationContext.getTextInterface(),
				values,
				list -> continuation.value(transformer.value(list))));
	}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param aString
	 *        The string describing something that was expected at this
	 *        position under some interpretation so far.
	 */
	public void expected (final String aString)
	{
		expected(new SimpleDescriber(aString));
	}
}
