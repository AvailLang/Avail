/*
 * LexingState.kt
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

package com.avail.compiler.scanning

import com.avail.compiler.AvailCompiler
import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.CompilationContext
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Lexer
import com.avail.descriptor.A_Set
import com.avail.descriptor.A_Token
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.AvailObject
import com.avail.descriptor.CharacterDescriptor
import com.avail.descriptor.FiberDescriptor.GeneralFlag
import com.avail.descriptor.FiberDescriptor.newLoaderFiber
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TokenDescriptor.TokenType.END_OF_FILE
import com.avail.descriptor.TokenDescriptor.newToken
import com.avail.interpreter.Interpreter
import com.avail.utility.Nulls.stripNull
import com.avail.utility.evaluation.Continuation1NotNull
import com.avail.utility.evaluation.Describer
import com.avail.utility.evaluation.SimpleDescriber
import com.avail.utility.evaluation.Transformer1
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.String.format
import java.util.*
import java.util.Collections.unmodifiableList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * [LexingState] instances represent the lexing state between tokens. They bind
 * together consecutive tokens into a directed acyclic graph, allowing the
 * [AvailCompiler] to process all possible paths (lexings), typically in
 * aggregate.
 *
 * @property compilationContext
 *   The compilation context for which this is a state of lexing.
 * @property position
 *   The position represented by this [LexingState].  In particular, it's the
 *   (one-based) start of the current token within the source.
 * @property lineNumber
 *   The one-based line number at which this state occurs in the source.
 * @property allTokens
 *   Every token, including whitespace, that has been parsed up to this point,
 *   from the start of the current top-level statement.  This should also
 *   include the initial leading whitespace, as we like to put related comments
 *   before statements.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new immutable `LexingState`.  It starts in a state where the
 * potential tokens at this position have not yet been computed, and no actions
 * have been queued to run against that eventual list of tokens.
 *
 * @param compilationContext
 *   The [CompilationContext] in which compilation is occurring.
 * @param position
 *   The one-based position in the source.
 * @param lineNumber
 *   The one-based line number of this position in the source.
 * @param allTokens
 *   The immutable list of [A_Token]s that have been parsed up to this position,
 *   starting at the current top-level statement, but including any leading
 *   whitespace and comment tokens.
 */
class LexingState constructor(
	val compilationContext: CompilationContext,
	val position: Int,
	val lineNumber: Int,
	val allTokens: List<A_Token>)
{
	/**
	 * The immutable [List] of [tokens][A_Token] that may each be next, starting
	 * at this lexing position.
	 *
	 * This is generally `null` until it has been computed by lexing fibers.
	 */
	private var nextTokens: MutableList<A_Token>? = null

	/**
	 * The collection of actions that should run when the [nextTokens] list has
	 * been computed.
	 *
	 * This is replaced by `null` when the list has been computed, running the
	 * waiting actions.  Newer actions are launched upon arrival after the
	 * tokens have been computed.
	 */
	private var actions: MutableList<(List<A_Token>)->Unit>? = ArrayList()

	/**
	 * Eventually invoke the given functions, each with the given argument.
	 * Track them as outstanding actions, ensuring
	 * [CompilationContext.noMoreWorkUnits] is invoked only when all such queued
	 * actions for the [CompilationContext] have completed.  Ensure the queued
	 * count is increased prior to actually queueing any of the actions, to
	 * ensure a hasty execution of a prefix of the tasks doesn't cause the
	 * `noMoreWorkUnits` to be executed prematurely.
	 *
	 * @param ArgType
	 *   The type of argument to the given continuation.
	 * @param continuations
	 *   The non-empty list of continuations to execute, each with the passed
	 *   argument.
	 * @param argument
	 *   What to pass as an argument to the provided functions.
	 */
	private fun <ArgType> workUnitsDo(
		continuations: List<(ArgType)->Unit>,
		argument: ArgType)
	{
		compilationContext.workUnitsDo(this, continuations, argument)
	}

	/**
	 * Eventually invoke the given function with the given argument.  Track it
	 * as an outstanding action, ensuring [CompilationContext.noMoreWorkUnits]
	 * is invoked only when all such queued actions have completed.
	 *
	 * @param ArgType
	 *   The type of argument to the given continuation.
	 * @param continuation
	 *   What to execute with the passed argument.
	 * @param argument
	 *   What to pass as an argument to the provided functions.
	 */
	fun <ArgType> workUnitDo(
		continuation: (ArgType)->Unit,
		argument: ArgType)
	{
		compilationContext.workUnitsDo(this, listOf(continuation), argument)
	}

	/**
	 * Eventually invoke the given function with the list of [tokens][A_Token]
	 * at this position.  If necessary, launch [fibers][A_Fiber] to run
	 * [lexers][A_Lexer], invoking the continuation only when all possible next
	 * tokens have been computed.
	 *
	 * @param newAction
	 *   What to do with the list of tokens.
	 */
	@Synchronized
	fun withTokensDo(newAction: (List<A_Token>)->Unit)
	{
		if (actions == null)
		{
			// The complete list of tokens was already computed, and actions
			// that arrived before that completed have already been started.
			workUnitDo(newAction, nextTokens!!)
			return
		}
		// Postpone the action until the tokens have been computed.
		val actions = actions!!
		actions.add(newAction)
		if (actions.size > 1)
		{
			// This wasn't the first action.  When the first action arrived, it
			// started computing the tokens, so having added the new action,
			// we're done.
			return
		}
		// This was the first action, so launch the lexers to produce the list
		// of nextTokens and then run the queued actions.
		nextTokens = ArrayList(2)
		val nextTokens = nextTokens!!
		val source = compilationContext.source
		if (position > source.tupleSize())
		{
			// The end of the source code.  Produce an end-of-file token.
			assert(position == source.tupleSize() + 1)
			val endOfFileToken = newToken(
				endOfFileLexeme, position, lineNumber, END_OF_FILE)
			nextTokens.add(endOfFileToken)
			for (action in actions)
			{
				workUnitDo(action, nextTokens)
			}
			this.actions = null
			return
		}
		compilationContext.loader!!.lexicalScanner().getLexersForCodePointThen(
			this,
			source.tupleCodePointAt(position),
			{ this.evaluateLexers(it) },
			{ this.reportLexerFilterFailures(it) })
	}

	/**
	 * The lexer filter functions have produced a tuple of applicable
	 * [lexers][A_Lexer], so run them.  When they have all completed, there will
	 * be no outstanding tasks for the relevant [CompilationContext], so it will
	 * automatically invoke
	 * [noMoreWorkUnits][CompilationContext.noMoreWorkUnits], allowing parsing
	 * to continue.
	 *
	 * @param applicableLexers
	 *   The lexers that passed their filter functions.
	 */
	@Synchronized
	private fun evaluateLexers(applicableLexers: A_Tuple)
	{
		val theNextTokens = stripNull<List<A_Token>>(nextTokens)
		if (applicableLexers.tupleSize() == 0)
		{
			// No applicable lexers.
			val scanner = compilationContext.loader!!.lexicalScanner()
			val codePoint =
				compilationContext.source.tupleCodePointAt(position)
			val charString =
				CharacterDescriptor.fromCodePoint(codePoint).toString()
			expected(
				STRONG,
				format(
					"an applicable lexer, but all %d filter functions returned "
					+ "false (code point = %s (U+%04x))",
					scanner.allVisibleLexers.size,
					charString,
					codePoint))
			for (action in actions!!)
			{
				workUnitDo(action, theNextTokens)
			}
			actions = null
			return
		}
		// There's at least one applicable lexer.  Launch fibers which, when the
		// last one completes, will capture the list of tokens and run the
		// actions.
		val countdown = AtomicInteger(applicableLexers.tupleSize())
		val arguments = listOf<A_BasicObject>(
			compilationContext.source,
			fromInt(position),
			fromInt(lineNumber))
		for (lexer in applicableLexers)
		{
			evaluateLexerAndRunActionsWhenZero(lexer, arguments, countdown)
		}
	}

	/**
	 * Run a lexer.  When it completes, decrement the countdown.  If it reaches
	 * zero, run [lexerBodyWasSuccessful] with all the possible tokens at this
	 * position to indicate success (otherwise indicate an expectation of a
	 * valid token).
	 *
	 * @param lexer
	 *   The lexer to execute.
	 * @param arguments
	 *   The arguments supplied to the lexer's body.
	 * @param countdown
	 *   A countdown to indicate completion of the group of lexers running at
	 *   the same source position in parallel.
	 */
	private fun evaluateLexerAndRunActionsWhenZero(
		lexer: A_Lexer,
		arguments: List<A_BasicObject>,
		countdown: AtomicInteger)
	{
		val loader = compilationContext.loader!!
		val fiber = newLoaderFiber(
			lexerBodyFunctionType().returnType(),
			loader
		) {
			formatString(
				"Lexer body on line %d for %s.", lineNumber, lexer)
		}
		// TODO MvG - Set up fiber variables for lexing?
		//		A_Map fiberGlobals = fiber.fiberGlobals();
		//		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
		//			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
		//		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.textInterface)
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		setFiberContinuationsTrackingWork(
			fiber,
			{ newTokenRuns -> lexerBodyWasSuccessful(newTokenRuns, countdown) },
			{ throwable ->
				if (throwable is AvailRejectedParseException)
				{
					expected(
						throwable.level,
						throwable.rejectionString.asNativeString())
				}
				else
				{
					// Report the problem as an expectation, with a stack trace.
					expected(STRONG) { afterDescribing ->
						val writer = StringWriter()
						throwable.printStackTrace(PrintWriter(writer))
						val text = format(
							"%s not to have failed while "
								+ "evaluating its body:\n%s",
							lexer.toString(),
							writer.toString())
						afterDescribing(text)
					}
				}
				decrementAndRunActionsWhenZero(countdown)
			})
		Interpreter.runOutermostFunction(
			loader.runtime(), fiber, lexer.lexerBodyFunction(), arguments)
	}

	/**
	 * Set up the given fiber to eventually invoke either the `onSuccess` or the
	 * `onFailure` continuation, but not both.  However, immediately record the
	 * fact that we're expecting one of these to be eventually invoked, and wrap
	 * the continuations with code that will invoke
	 * [getNoMoreWorkUnits][CompilationContext.noMoreWorkUnits] when the number
	 * of outstanding tasks reaches zero.
	 *
	 * @param fiber
	 *   The [A_Fiber] to set up.
	 * @param onSuccess
	 *   The [Continuation1NotNull] to invoke in the event of a successful
	 *   completion of the fiber.
	 * @param onFailure
	 *   The [Continuation1NotNull] to invoke in the event that the
	 *   fiber raises a [Throwable] during its execution.
	 */
	fun setFiberContinuationsTrackingWork(
		fiber: A_Fiber,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit)
	{
		assert(compilationContext.noMoreWorkUnits != null)
		// Wrap onSuccess and onFailure to maintain queued/completed counts.
		compilationContext.startWorkUnits(1)
		val oneWay = AtomicBoolean()
		fiber.setSuccessAndFailureContinuations(
			compilationContext.workUnitCompletion(this, oneWay, onSuccess),
			compilationContext.workUnitCompletion(this, oneWay, onFailure))
	}

	/**
	 * A lexer body completed successfully with the given set of tuples of next
	 * tokens.  The set reflects different possibilities, and each tuple is of
	 * consecutive tokens from the input, all of which actually have to be
	 * physically present in the text.  Technically, some of the tokens might
	 * have empty lexemes to ensure the right number of characters are consumed.
	 *
	 * @param newTokenRuns
	 *   A set of sequences (tuples) of tokens that represent possible future
	 *   non-empty lineages of tokens, as produced by lexers.
	 * @param countdown
	 *   The [AtomicInteger] which counts down to zero with each successful
	 *   invocation (not each token), then runs all outstanding actions once.
	 */
	@Synchronized
	private fun lexerBodyWasSuccessful(
		newTokenRuns: A_Set,
		countdown: AtomicInteger)
	{
		val nextTokens = nextTokens!!
		for (run in newTokenRuns)
		{
			assert(run.tupleSize() > 0)
			for (token in run)
			{
				compilationContext.recordToken(token)
			}
			nextTokens.add(run.tupleAt(1))
			var state = this
			val iterator = run.iterator()
			var token: A_Token = iterator.next()
			token.setNextLexingStateFromPrior(state)
			while (iterator.hasNext())
			{
				state = token.nextLexingState()
				token = iterator.next()
				state.forceNextToken(token)
				token.setNextLexingStateFromPrior(state)
			}
		}
		decrementAndRunActionsWhenZero(countdown)
	}

	/**
	 * Force this lexing state to have exactly the one specified token following
	 * it.  This is used to string together the runs of tokens produced by some
	 * kinds of lexers.  The state must not yet have had any actions run on it,
	 * nor may it have had its [nextTokens] [List] set yet.
	 *
	 * No synchronization is performed, because this should take while wiring an
	 * explicit run of tokens together, prior to making them available for
	 * parsing.
	 *
	 * @param token
	 *   The sole token that follows this state.
	 */
	private fun forceNextToken(token: A_Token)
	{
		assert(nextTokens == null)
		assert(actions != null && actions!!.isEmpty())
		nextTokens = mutableListOf(token)
		actions = null
	}

	/**
	 * Decrement the supplied [AtomicInteger].  If it reaches zero, queue the
	 * actions, transitioning to a state where new actions will simply run with
	 * the [nextTokens].
	 *
	 * @param countdown
	 *   The [AtomicInteger].
	 */
	private fun decrementAndRunActionsWhenZero(countdown: AtomicInteger)
	{
		val newCount = countdown.decrementAndGet()
		assert(newCount >= 0)
		if (newCount == 0)
		{
			// We just heard from the last lexer.  Run the actions once, and
			// ensure any new actions start immediately with nextTokens.
			workUnitsDo(consumeActions, nextTokens!!)
		}
	}

	/**
	 * Return the list of actions, writing null in its place to indicate new
	 * actions should run immediately rather than be queued.  This is
	 * synchronized.
	 *
	 * @return
	 *   The list of actions prior to nulling the field.
	 */
	private val consumeActions: List<(List<A_Token>)->Unit>
		@Synchronized
		get()
		{
			val queuedActions = actions!!
			actions = null
			return queuedActions
		}

	/**
	 * Record the fact that at least one filter body failed.
	 *
	 * @param filterFailures
	 *   A non-empty map from each failed [A_Lexer] to the [Throwable] that it
	 *   threw.
	 */
	private fun reportLexerFilterFailures(
		filterFailures: Map<A_Lexer, Throwable>)
	{
		for ((key, value) in filterFailures)
		{
			expected(STRONG) { afterDescribing ->
				val stringWriter = StringWriter()
				value.printStackTrace(PrintWriter(stringWriter))
				val text = format(
					"%s not to have failed while evaluating its filter "
						+ "function:\n%s",
					key.toString(),
					stringWriter.toString())
				afterDescribing(text)
			}
		}
	}

	/**
	 * If the [A_Token]s that start at this location have been computed, answer
	 * that [List], otherwise answer `null`.  The list is copied to ensure it
	 * isn't modified by the client or other active lexers.
	 *
	 * This should only used by diagnostics, specifically to avoid running
	 * lexers while producing failure messages.
	 *
	 * @return
	 *   The list of tokens or `null`.
	 */
	val knownToBeComputedTokensOrNull: List<A_Token>?
		@Synchronized
		get()
		{
			return if (nextTokens == null)
				null
			else
				unmodifiableList(ArrayList(nextTokens!!))
		}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost few reached parse positions constitute the
	 * error message in case the parse fails.
	 *
	 * The expectation is a [Describer], in case constructing a [String]
	 * frivolously would be prohibitively expensive. There is also
	 * [another][expected] version of this method that accepts a String
	 * directly.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param describer
	 *   The `describer` to capture.
	 */
	fun expected(
		level: ParseNotificationLevel,
		describer: Describer)
	{
		compilationContext.diagnostics.expectedAt(level, describer, this)
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost parse position constitute the error message
	 * in case the parse fails.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param values
	 *   A list of arbitrary [Avail values][AvailObject] that should be
	 *   stringified.
	 * @param transformer
	 *   A [transformer][Transformer1] that accepts the stringified values and
	 *   answers an expectation message.
	 */
	fun expected(
		level: ParseNotificationLevel,
		values: List<A_BasicObject>,
		transformer: Function<List<String>, String>)
	{
		expected(level) { continuation ->
			Interpreter.stringifyThen(
				compilationContext.loader!!.runtime(),
				compilationContext.textInterface,
				values
			) { list -> continuation(transformer.apply(list)) }
		}
	}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param aString
	 *   The string describing something that was expected at this position
	 *   under some interpretation so far.
	 */
	fun expected(level: ParseNotificationLevel, aString: String)
	{
		expected(level, SimpleDescriber(aString))
	}

	companion object
	{
		/** An Avail string for use as the lexeme in end-of-file tokens.  */
		private val endOfFileLexeme = stringFrom("end-of-file").makeShared()
	}
}
