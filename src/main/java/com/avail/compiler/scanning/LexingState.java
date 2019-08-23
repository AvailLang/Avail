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
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.Interpreter;
import com.avail.utility.evaluation.*;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG;
import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType.END_OF_FILE;
import static com.avail.descriptor.TokenDescriptor.newToken;
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
	 * CompilationContext#getNoMoreWorkUnits() noMoreWorkUnits} is invoked only
	 * when all such queued actions for the {@link CompilationContext} have
	 * completed.  Ensure the queued count is increased prior to actually
	 * queueing any of the actions, to ensure a hasty execution of a prefix of
	 * the tasks doesn't cause the {@code noMoreWorkUnits} to be executed
	 * prematurely.
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
	 * CompilationContext#getNoMoreWorkUnits() noMoreWorkUnits} is invoked only
	 * when all such queued actions have completed.
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

	/** An Avail string for use as the lexeme in end-of-file tokens. */
	private static final A_String endOfFileLexeme =
		stringFrom("end-of-file").makeShared();

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
		nextTokens = new ArrayList<>(2);
		final A_String source = compilationContext.source();
		if (position > source.tupleSize())
		{
			// The end of the source code.  Produce an end-of-file token.
			assert position == source.tupleSize() + 1;
			final A_Token endOfFileToken = newToken(
				endOfFileLexeme, position, lineNumber, END_OF_FILE);
			nextTokens.add(endOfFileToken);
			for (final Continuation1NotNull<List<A_Token>> action : actions)
			{
				workUnitDo(action, nextTokens);
			}
			actions = null;
			return;
		}
		compilationContext.loader().lexicalScanner().getLexersForCodePointThen(
			this,
			source.tupleCodePointAt(position),
			this::evaluateLexers,
			this::reportLexerFilterFailures);
	}

	/**
	 * The lexer filter functions have produced a tuple of applicable {@link
	 * A_Lexer}s, so run them.  When they have all completed, there will be no
	 * outstanding tasks for the relevant {@link CompilationContext}, so it will
	 * automatically invoke {@link CompilationContext#getNoMoreWorkUnits()
	 * noMoreWorkUnits}, allowing parsing to continue.
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
				STRONG,
				format(
					"an applicable lexer, but all %d filter functions returned "
						+ "false (code point = %s(U+%04x))",
					scanner.allVisibleLexers.size(),
					(Character.isWhitespace(codePoint)
						 ? ""
						 : "¢" + codePoint + " "),
					codePoint));
			for (final Continuation1NotNull<List<A_Token>> action
				: stripNull(actions))
			{
				workUnitDo(action, theNextTokens);
			}
			actions = null;
			return;
		}
		// There's at least one applicable lexer.  Launch fibers which, when the
		// last one completes, will capture the list of tokens and run the
		// actions.
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
					expected(
						rej.level,
						rej.rejectionString().asNativeString());
				}
				else
				{
					// Report the problem as an expectation, with a stack trace.
					expected(
						STRONG,
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
				}
				decrementAndRunActionsWhenZero(countdown);
			};
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		setFiberContinuationsTrackingWork(fiber, onSuccess, onFailure);
		Interpreter.runOutermostFunction(
			loader.runtime(), fiber, lexer.lexerBodyFunction(), arguments);
	}

	/**
	 * Set up the given fiber to eventually invoke either the onSuccess or the
	 * onFailure continuation, but not both.  However, immediately record the
	 * fact that we're expecting one of these to be eventually invoked, and wrap
	 * the continuations with code that will invoke {@link
	 * CompilationContext#getNoMoreWorkUnits() noMoreWorkUnits} when the number
	 * of outstanding tasks reaches zero.
	 *
	 * @param fiber
	 *        The {@link A_Fiber} to set up.
	 * @param onSuccess
	 *        The {@link Continuation1NotNull} to invoke in the event of a
	 *        successful completion of the fiber.
	 * @param onFailure
	 *        The {@link Continuation1NotNull} to invoke in the event that the
	 *        fiber raises a {@link Throwable} during its execution.
	 */
	public void setFiberContinuationsTrackingWork (
		final A_Fiber fiber,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		assert compilationContext.getNoMoreWorkUnits() != null;
		// Wrap onSuccess and onFailure to maintain queued/completed counts.
		compilationContext.startWorkUnits(1);
		final AtomicBoolean oneWay = new AtomicBoolean();
		fiber.setSuccessAndFailureContinuations(
			compilationContext.workUnitCompletion(this, oneWay, onSuccess),
			compilationContext.workUnitCompletion(this, oneWay, onFailure));
	}

	/**
	 * A lexer body completed successfully with the given set of tuples of next
	 * tokens.  The set reflects different possibilities, and each tuple is of
	 * consecutive tokens from the input, all of which actually have to be
	 * physically present in the text.  Technically, some of the tokens might
	 * have empty lexemes to ensure the right number of characters are consumed.
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
			assert run.tupleSize() > 0;
			for (final A_Token token : run)
			{
				compilationContext.recordToken(token);
			}
			theNextTokens.add(run.tupleAt(1));
			LexingState state = this;
			final Iterator<AvailObject> iterator = run.iterator();
			A_Token token = iterator.next();
			token.setNextLexingStateFromPrior(state);
			while (iterator.hasNext())
			{
				state = token.nextLexingState();
				token = iterator.next();
				state.forceNextToken(token);
				token.setNextLexingStateFromPrior(state);
			}
		}
		decrementAndRunActionsWhenZero(countdown);
	}

	/**
	 * Force this lexing state to have exactly the one specified token following
	 * it.  This is used to string together the runs of tokens produced by some
	 * kinds of lexers.  The state must not yet have had any actions run on it,
	 * nor may it have had its {@link #nextTokens} {@link List} set yet.
	 *
	 * <p>No synchronization is performed, because this should take while wiring
	 * an explicit run of tokens together, prior to making them available for
	 * parsing.</p>
	 *
	 * @param token The sole token that follows this state.
	 */
	private void forceNextToken (final A_Token token)
	{
		assert nextTokens == null;
		assert actions != null && actions.isEmpty();
		nextTokens = singletonList(token);
		actions = null;
	}

	/**
	 * Decrement the supplied {@link AtomicInteger}.  If it reaches zero, queue
	 * the actions, transitioning to a state where new actions will simply run
	 * with the {@link #nextTokens}.
	 *
	 * @param countdown
	 *        The {@link AtomicInteger}.
	 */
	private void decrementAndRunActionsWhenZero (final AtomicInteger countdown)
	{
		final int newCount = countdown.decrementAndGet();
		assert newCount >= 0;
		if (newCount == 0)
		{
			// We just heard from the last lexer.  Run the actions once, and
			// ensure any new actions start immediately with nextTokens.
			workUnitsDo(consumeActions(), stripNull(nextTokens));
		}
	}

	/**
	 * Return the list of actions, writing null in its place to indicate new
	 * actions should run immediately rather than be queued.  This is
	 * synchronized.
	 *
	 * @return The list of actions prior to nulling the field.
	 */
	private synchronized List<Continuation1NotNull<List<A_Token>>>
		consumeActions ()
	{
		final List<Continuation1NotNull<List<A_Token>>> queuedActions =
			stripNull(actions);
		actions = null;
		return queuedActions;
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
			expected(
				STRONG,
				afterDescribing ->
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
	 * #expected(ParseNotificationLevel, String) another} version of this method
	 * that accepts a String directly.
	 * </p>
	 *
	 * @param level
	 *        The {@link ParseNotificationLevel} that indicates the priority
	 *        of the parse theory that failed.
	 * @param describer
	 *        The {@code describer} to capture.
	 */
	public void expected (
		final ParseNotificationLevel level,
		final Describer describer)
	{
		compilationContext.diagnostics.expectedAt(level, describer, this);
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost parse position constitute the error message
	 * in case the parse fails.
	 *
	 * @param level
	 *        The {@link ParseNotificationLevel} that indicates the priority
	 *        of the parse theory that failed.
	 * @param values
	 *        A list of arbitrary {@linkplain AvailObject Avail values} that
	 *        should be stringified.
	 * @param transformer
	 *        A {@linkplain Transformer1 transformer} that accepts the
	 *        stringified values and answers an expectation message.
	 */
	public void expected (
		final ParseNotificationLevel level,
		final List<? extends A_BasicObject> values,
		final Function<List<String>, String> transformer)
	{
		expected(
			level,
			continuation -> Interpreter.stringifyThen(
				compilationContext.loader().runtime(),
				compilationContext.getTextInterface(),
				values,
				list -> continuation.value(transformer.apply(list))));
	}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param level
	 *        The {@link ParseNotificationLevel} that indicates the priority
	 *        of the parse theory that failed.
	 * @param aString
	 *        The string describing something that was expected at this
	 *        position under some interpretation so far.
	 */
	public void expected (
		final ParseNotificationLevel level,
		final String aString)
	{
		expected(level, new SimpleDescriber(aString));
	}
}
