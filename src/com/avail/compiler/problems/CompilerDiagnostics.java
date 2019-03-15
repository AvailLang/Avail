/*
 * CompilerDiagnostics.java
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

package com.avail.compiler.problems;

import com.avail.annotations.InnerAccess;
import com.avail.builder.ModuleName;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.FiberDescriptor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Describer;
import com.avail.utility.evaluation.SimpleDescriber;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.compiler.problems.ProblemType.PARSE;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType.END_OF_FILE;
import static com.avail.descriptor.TokenDescriptor.TokenType.WHITESPACE;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.utility.Locks.lockWhile;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.addLineNumbers;
import static com.avail.utility.Strings.lineBreakPattern;
import static com.avail.utility.evaluation.Combinator.recurse;
import static java.lang.String.format;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static java.util.Collections.reverseOrder;
import static java.util.Collections.singletonList;
import static java.util.Collections.sort;

public class CompilerDiagnostics
{
	/**
	 * Create a {@code CompilerDiagnostics} suitable for tracking the potential
	 * problems encountered during compilation of a single module.
	 *
	 * @param source
	 *        The source code of the module.
	 * @param moduleName
	 *        The {@link ModuleName} of the module being compiled.
	 * @param pollForAbort
	 *        A {@link BooleanSupplier} to indicate whether to abort.
	 * @param problemHandler
	 *        A {@link ProblemHandler} for, well, handling problems during
	 *        compilation.
	 */
	public CompilerDiagnostics (
		final A_String source,
		final ModuleName moduleName,
		final BooleanSupplier pollForAbort,
		final ProblemHandler problemHandler)
	{
		this.source = source;
		this.moduleName = moduleName;
		this.pollForAbort = pollForAbort;
		this.problemHandler = problemHandler;
	}

	/** The source text as an Avail {@link A_String}. */
	final A_String source;

	/** The name of the module being compiled. */
	final ModuleName moduleName;

	/**
	 * The number of distinct (rightmost) positions for which to record
	 * expectations.
	 */
	static final int expectationsCountToTrack = 3;

	/**
	 * The one-based position in the source at which the current statement
	 * starts.
	 */
	int startOfStatement;

	/**
	 * Guards access to {@link #expectations} and {@link
	 * #expectationsIndexHeap}.
	 */
	final ReadWriteLock expectationsLock = new ReentrantReadWriteLock();

	/**
	 * The rightmost few positions at which potential problems have been
	 * recorded.  The keys of this {@link Map} always agree with the values in
	 * the {@link #expectationsIndexHeap}.  The key is the one-based position of
	 * the start of token, and the value is a map from the tokens that were
	 * lexed at that position to the non-empty list of {@link Describer}s that
	 * indicate problems encountered at that token.
	 *
	 * <p>Lexing problems are recorded here as well, although a suitable invalid
	 * token is created for this purpose.  This allows positioning the problem
	 * at an exact character position.</p>
	 */
	private final Map<Integer, Map<LexingState, List<Describer>>> expectations =
		new HashMap<>();

	/**
	 * A priority heap that keeps track of the rightmost N positions at which a
	 * diagnostic message has been recorded.  The entries always agree with the
	 * keys of {@link #expectations}.
	 */
	private final PriorityQueue<Integer> expectationsIndexHeap =
		new PriorityQueue<>();

	/**
	 * A collection of tokens that have been encountered during parsing since
	 * the last time this list was cleared.
	 */
	private List<A_Token> liveTokens = new ArrayList<>(100);

	/** A lock to protect access to {@link #liveTokens}. */
	private final ReadWriteLock liveTokensLock = new ReentrantReadWriteLock();

	/**
	 * Record the fact that we're starting to parse a top-level statement at the
	 * indicated one-based position in the source.  Clear any already recorded
	 * expectations.
	 *
	 * @param initialPosition
	 *        The position at which we're starting to parse a statement.
	 */
	public void startParsingAt (final int initialPosition)
	{
		startOfStatement = initialPosition;
		lockWhile(
			expectationsLock.writeLock(),
			() ->
			{
				expectations.clear();
				expectationsIndexHeap.clear();
			});
		// Tidy up all tokens from the previous top-level statement.
		final List<A_Token> priorTokens =
			lockWhile(
				liveTokensLock.writeLock(),
				() ->
				{
					final List<A_Token> old = liveTokens;
					liveTokens = emptyList();
					return old;
				});

		for (final A_Token token : priorTokens)
		{
			token.clearLexingState();
		}

		lockWhile(
			liveTokensLock.writeLock(),
			() -> liveTokens = new ArrayList<>(100));
	}

	/**
	 * The {@link ProblemHandler} used for reporting compilation problems.
	 */
	final ProblemHandler problemHandler;

	/**
	 * Handle a {@linkplain Problem problem} via the {@linkplain #problemHandler
	 * problem handler}.
	 *
	 * @param problem
	 *        The problem to handle.
	 */
	public void handleProblem (final Problem problem)
	{
		problemHandler.handle(problem);
	}

	/**
	 * This {@code boolean} is set when the {@link #problemHandler} decides that
	 * an encountered {@link Problem} is sufficient to abort compilation of this
	 * module at the earliest convenience.
	 */
	public volatile boolean isShuttingDown = false;

	/** A way to quickly test if the client wishes to shut down prematurely. */
	public final BooleanSupplier pollForAbort;

	/**
	 * This {@code boolean} is set when the {@link #problemHandler} decides that
	 * an encountered {@link Problem} is serious enough that code generation
	 * should be suppressed.  In Avail, that means the serialized module should
	 * not be written to its {@linkplain IndexedRepositoryManager repository}.
	 */
	public volatile boolean compilationIsInvalid = false;


	/**
	 * The {@linkplain Continuation0 continuation} that reports success of
	 * compilation.
	 */
	@InnerAccess volatile @Nullable Continuation0 successReporter;

	/**
	 * Get the success reporter.
	 *
	 * @return What to do when the module compilation completes successfully.
	 */
	public Continuation0 getSuccessReporter ()
	{
		return stripNull(successReporter);
	}

	/**
	 * The {@linkplain Continuation0 continuation} that runs after compilation
	 * fails.
	 */
	@InnerAccess volatile @Nullable Continuation0 failureReporter;

	/**
	 * Set the success reporter and failure reporter.
	 *
	 * @param theSuccessReporter
	 *        What to do when the module compilation completes successfully.
	 * @param theFailureReporter
	 *        What to do when the module compilation completes unsuccessfully.
	 */
	public void setSuccessAndFailureReporters (
		final Continuation0 theSuccessReporter,
		final Continuation0 theFailureReporter)
	{
		final AtomicBoolean hasRun = new AtomicBoolean(false);
		this.successReporter = () ->
		{
			final boolean ran = hasRun.getAndSet(true);
			assert !ran : "Success/failure reporter ran twice";
			theSuccessReporter.value();
		};
		this.failureReporter = () ->
		{
			final boolean ran = hasRun.getAndSet(true);
			assert !ran : "Success/failure reporter ran twice";
			theFailureReporter.value();
		};
	}

	/** A bunch of dash characters, wide enough to catch the eye. */
	public static final String rowOfDashes =
		"---------------------------------------------------------------------";

	/** The 26 letters of the English alphabet, inside circles. */
	public static final String circledLetters =
		"ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ";

	/**
	 * Despite, what, a hundred thousand employees(?), Microsoft seems unable to
	 * support the simplest things in Windows.  Like including a font that has
	 * a significant portion of Unicode supported, or devising (or even
	 * copying!) a mechanism to substitute glyphs from fonts that <em>do</em>
	 * have those code points.  So if you're doomed to be on Windows you'll get
	 * an inferior right arrow with hook to indicate the location of errors.
	 *
	 * <p>That will teach you.</p>
	 */
	public static final String errorIndicatorSymbol =
		System.getProperty("os.name").startsWith("Windows")
			? "↪"
			: "⤷";

	/**
	 * Record an expectation at the given token.
	 *
	 * @param describer
	 *        A {@link Describer}, something which can be evaluated (including
	 *        running Avail code) to produce a String, which is then passed to a
	 *        provided continuation.
	 * @param lexingState
	 *        The {@link LexingState} at which the expectation occurred.
	 */
	public void expectedAt (
		final Describer describer,
		final LexingState lexingState)
	{
		lockWhile(
			expectationsLock.writeLock(),
			() ->
			{
				final Integer position = lexingState.position;
				Map<LexingState, List<Describer>> innerMap =
					expectations.get(position);
				if (innerMap == null)
				{
					//noinspection ConstantConditions
					if (expectationsIndexHeap.size() == expectationsCountToTrack
						&& position < expectationsIndexHeap.peek())
					{
						// We have the maximum number of expectation sites, and
						// the new one would come before them all, so ignore it.
						return;
					}
					innerMap = new HashMap<>();
					expectations.put(position, innerMap);
					// Also update expectationsIndexHeap.
					expectationsIndexHeap.add(position);
					if (expectationsIndexHeap.size() > expectationsCountToTrack)
					{
						expectationsIndexHeap.remove();
					}
				}
				final List<Describer> innerList = innerMap.computeIfAbsent(
					lexingState, k -> new ArrayList<>());
				innerList.add(describer);
			});
	}

	/**
	 * Record the fact that this token was encountered while parsing the current
	 * top-level statement.
	 *
	 * @param token The token that was encountered.
	 */
	public void recordToken (final A_Token token)
	{
		lockWhile(liveTokensLock.writeLock(), () -> liveTokens.add(token));
	}

	/**
	 * An {@code IndicatorGenerator} creates successive circled letters via its
	 * {@link #next()} method.  When it hits circled-Z, it stays at that letter
	 * and starts suffixing consecutive integers and a space.
	 */
	class IndicatorGenerator
	{
		int letterOffset = 0;

		int supplementaryCounter = 0;

		@InnerAccess
		@Nonnull String next ()
		{
			final int nextLetterOffset =
				circledLetters.offsetByCodePoints(letterOffset, 1);
			String indicator = circledLetters.substring(
				letterOffset, nextLetterOffset);
			if (supplementaryCounter > 0)
			{
				// Follow the circled Z with the value of an
				// increasing counter, plus a space to visually
				// separate it from any subsequent token.
				indicator += supplementaryCounter + " ";
			}
			// Keep using Ⓩ (circled Z) if we're looking back
			// more than 26 tokens for problems.
			if (nextLetterOffset < circledLetters.length())
			{
				letterOffset = nextLetterOffset;
			}
			else
			{
				// Start using Ⓩ (circled Z) followed by an
				// increasing numeric value.
				supplementaryCounter++;
			}
			return indicator;
		}
	}

	/**
	 * Given a collection of {@link LexingState}s, examine the tokens starting
	 * at each lexing state, choosing the longest one.  Invoke the passed
	 * continuation with that longest token.
	 *
	 * <p>This should only be used during final reporting of errors, so don't
	 * attempt to run the lexers, or they might produce additional problems at
	 * further positions than appropriate.  Instead, use whatever tokens have
	 * been accumulated so far, ignoring whether this is a complete list or not,
	 * and synthesizing an empty token if necessary.</p>
	 *
	 * @param startLexingStates
	 *        The starting {@link LexingState}s.
	 * @param continuation
	 *        What to do when the longest {@link A_Token} has been found.
	 */
	static void findLongestTokenThen (
		final Collection<LexingState> startLexingStates,
		final Continuation1NotNull<A_Token> continuation)
	{
		final List<A_Token> candidates = new ArrayList<>();
		for (final LexingState startState : startLexingStates)
		{
			final @Nullable List<A_Token> known =
				startState.knownToBeComputedTokensOrNull();
			if (known != null)
			{
				candidates.addAll(known);
			}
		}
		if (candidates.isEmpty())
		{
			final LexingState state = startLexingStates.iterator().next();
			final A_Token emptyToken = newToken(
				emptyTuple(),
				state.position,
				state.lineNumber,
				WHITESPACE);
			emptyToken.setNextLexingStateFromPrior(state);
			continuation.value(emptyToken.makeShared());
			return;
		}
		continuation.value(
			candidates.stream()
				.max(Comparator.comparing(t -> t.string().tupleSize()))
				.get());
	}

	/**
	 * Report the rightmost accumulated errors, then invoke the {@link
	 * #failureReporter}.
	 */
	public void reportError ()
	{
		reportError("Expected at %s, line %d...");
	}

	/**
	 * Report the rightmost accumulated errors, then invoke the {@link
	 * #failureReporter}.
	 *
	 * @param headerMessagePattern
	 *        The message pattern that introduces each group of problems.  The
	 *        first argument is where the indicator string goes, and the second
	 *        is for the line number.
	 */
	private void reportError (
		final String headerMessagePattern)
	{
		final List<Integer> descendingIndices = lockWhile(
			expectationsLock.readLock(),
			() -> new ArrayList<>(expectationsIndexHeap));
		descendingIndices.sort(reverseOrder());
		accumulateErrorsThen(
			descendingIndices.iterator(),
			new IndicatorGenerator(),
			new ArrayList<>(),
			groups -> reportGroupedErrors(groups, headerMessagePattern));
	}

	/**
	 * Accumulate the errors, then pass the {@link List} of {@link
	 * ProblemsAtPosition}s to the given {@link Continuation1NotNull}.
	 *
	 * @param descendingIterator
	 *        An {@link Iterator} that supplies source positions at which
	 *        problems have been recorded, ordered by descending position.
	 * @param indicatorGenerator
	 *        An {@link IndicatorGenerator} for producing marker strings to
	 *        label the successive (descending) positions in the source where
	 *        problems occurred.
	 * @param groupedProblems
	 *        The {@link List} of {@link ProblemsAtPosition}s accumulated so
	 *        far.
	 * @param afterGrouping
	 *        What to do after producing the grouped error reports.
	 */
	private void accumulateErrorsThen (
		final Iterator<Integer> descendingIterator,
		final IndicatorGenerator indicatorGenerator,
		final List<ProblemsAtPosition> groupedProblems,
		final Continuation1NotNull<List<ProblemsAtPosition>> afterGrouping)
	{
		if (!descendingIterator.hasNext())
		{
			// Done assembling each of the problems.  Report them.
			assert !groupedProblems.isEmpty();
			afterGrouping.value(groupedProblems);
			return;
		}
		final int sourcePosition = descendingIterator.next();
		final Map<LexingState, List<Describer>> innerMap = lockWhile(
			expectationsLock.readLock(),
			() ->
			{
				final Map<LexingState, List<Describer>> originalMap =
					expectations.get(sourcePosition);
				final Map<LexingState, List<Describer>> safeMap =
					new HashMap<>();
				originalMap.forEach(
					(lexingState, listOfDescribers) ->
						safeMap.put(
							lexingState, new ArrayList<>(listOfDescribers)));
				return safeMap;
			});
		assert !innerMap.isEmpty();
		// Due to local lexer ambiguity, there may be multiple possible
		// tokens at this position.  Choose the longest for the purpose
		// of displaying the diagnostics.  We only care about the tokens
		// that have already been formed, not ones in progress.
		findLongestTokenThen(
			innerMap.keySet(),
			longestToken ->
			{
				final List<Describer> describers = new ArrayList<>();
				for (final List<Describer> eachDescriberList
					: innerMap.values())
				{
					describers.addAll(eachDescriberList);
				}
				final LexingState before =
					innerMap.keySet().iterator().next();
				groupedProblems.add(
					new ProblemsAtPosition(
						before,
						longestToken.tokenType() == END_OF_FILE
							? before
							: longestToken.nextLexingState(),
						indicatorGenerator.next(),
						describers));
				accumulateErrorsThen(
					descendingIterator,
					indicatorGenerator,
					groupedProblems,
					afterGrouping);
			});
	}

	/**
	 * Report one specific terminal problem and call the failure continuation to
	 * abort compilation.
	 *
	 * @param lexingState
	 *        The position at which to report the problem.
	 * @param headerMessagePattern
	 *        The problem header pattern, where the first pattern argument is
	 *        the indicator string (e.g., circled-A), and the second pattern
	 *        argument is the line number.
	 * @param message
	 *        The message text for this problem.
	 */
	public void reportError (
		final LexingState lexingState,
		final String headerMessagePattern,
		final String message)
	{
		final int startPosition = lexingState.position;
		final Map<LexingState, List<Describer>> innerMap = new HashMap<>();
		innerMap.put(lexingState, singletonList(new SimpleDescriber(message)));
		lockWhile(
			expectationsLock.writeLock(),
			() ->
			{
				expectations.clear();
				expectationsIndexHeap.clear();
				expectations.put(startPosition, innerMap);
				expectationsIndexHeap.add(startPosition);
			});
		reportError(headerMessagePattern);
	}

	/**
	 * Search for a particular code point in the Avail string, starting at the
	 * given index and working forward.
	 *
	 * @param string
	 *        The string in which to search.
	 * @param codePoint
	 *        The code point to search for.
	 * @param startIndex
	 *        The position at which to start scanning.
	 * @param endIndex
	 *        The position at which to stop scanning, which should be &ge; the
	 *        startIndex for a non-empty range.
	 * @return
	 *         The first encountered index of the code point when scanning
	 *         backward from the given initial index.  If the code point is not
	 *         found, answer 0.
	 */
	@SuppressWarnings("SameParameterValue")
	static int firstIndexOf (
		final A_String string,
		final int codePoint,
		final int startIndex,
		final int endIndex)
	{
		for (int i = startIndex; i <= endIndex; i++)
		{
			if (string.tupleCodePointAt(i) == codePoint)
			{
				return i;
			}
		}
		return 0;
	}

	/**
	 * Search for a particular code point in the Avail string, starting at the
	 * given one-based index and working backward.
	 *
	 * @param string
	 *        The string in which to search.
	 * @param codePoint
	 *        The code point to search for.
	 * @param startIndex
	 *        The position at which to start scanning backward.
	 * @param endIndex
	 *        The position at which to stop scanning; should be &le; the
	 *        startIndex for a non-empty range.
	 * @return
	 *         The first encountered index of the code point when scanning
	 *         backward from the given initial index.  If the code point is not
	 *         found, answer 0.
	 */
	@SuppressWarnings("SameParameterValue")
	static int lastIndexOf (
		final A_String string,
		final int codePoint,
		final int startIndex,
		final int endIndex)
	{
		for (int i = startIndex; i >= endIndex; i--)
		{
			if (string.tupleCodePointAt(i) == codePoint)
			{
				return i;
			}
		}
		return 0;
	}

	/**
	 * Search for a particular code point in the given range of the Avail
	 * string, answering how many occurrences were found.
	 *
	 * @param string
	 *        The string in which to search.
	 * @param codePoint
	 *        The code point to search for.
	 * @param startIndex
	 *        The position at which to start scanning.
	 * @param endIndex
	 *        The position at which to stop scanning.
	 * @return
	 *         The number of occurrences of the codePoint in the specified range
	 *         of the {@link A_String}.
	 */
	@SuppressWarnings("SameParameterValue")
	static int occurrencesInRange (
		final A_String string,
		final int codePoint,
		final int startIndex,
		final int endIndex)
	{
		int count = 0;
		for (int i = startIndex; i <= endIndex; i++)
		{
			if (string.tupleCodePointAt(i) == codePoint)
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Report a parsing problem.  After reporting it, execute the {@link
	 * #failureReporter}.
	 *
	 * @param groupedProblems
	 *        The {@link List} of {@link ProblemsAtPosition} to report.  Each
	 *        {@code ProblemsAtPosition} describes the problems that occurred at
	 *        some token's position.
	 * @param headerMessagePattern
	 *        The message pattern to be populated and written before each group
	 *        of problems.  Its arguments are the group's {@linkplain
	 *        ProblemsAtPosition#indicator} and the problematic token's line
	 *        number.
	 */
	public void reportGroupedErrors (
		final List<ProblemsAtPosition> groupedProblems,
		final String headerMessagePattern)
	{
		if (pollForAbort.getAsBoolean())
		{
			// Never report errors during a client-initiated abort.
			stripNull(failureReporter).value();
			return;
		}
		final List<ProblemsAtPosition> ascending =
			new ArrayList<>(groupedProblems);
		sort(ascending);

		// Figure out where to start showing the file content.  Never show the
		// content before the line on which startOfStatement resides.
		final int startOfFirstLine =
			lastIndexOf(source, '\n', startOfStatement - 1, 1) + 1;
		final int initialLineNumber = 1 + occurrencesInRange(
			source, '\n', 1, Math.min(source.tupleSize(), startOfFirstLine));
		// Now figure out the last line to show, which if possible should be the
		// line after the *end* of the last problem token.
		final ProblemsAtPosition lastProblem =
			ascending.get(ascending.size() - 1);
		final int finalLineNumber = lastProblem.lineNumber();
		int startOfNextLine = 1 + firstIndexOf(
			source,
			'\n',
			lastProblem.lexingStateAfterToken.position,
			source.tupleSize());
		startOfNextLine = startOfNextLine != 1
			? startOfNextLine
			: source.tupleSize() + 1;
		int startOfSecondNextLine = 1 + firstIndexOf(
			source, '\n', startOfNextLine, source.tupleSize());
		startOfSecondNextLine = startOfSecondNextLine != 1
			? startOfSecondNextLine
			: source.tupleSize() + 1;

		// Insert the problem location indicators...
		int sourcePosition = startOfFirstLine;
		final List<A_Tuple> parts = new ArrayList<>(10);
		for (final ProblemsAtPosition eachProblem : ascending)
		{
			final int newPosition = eachProblem.position();
			parts.add(
				source.copyTupleFromToCanDestroy(
					sourcePosition, newPosition - 1, false));
			parts.add(stringFrom(eachProblem.indicator));
			sourcePosition = newPosition;
		}
		parts.add(
			source.copyTupleFromToCanDestroy(
				sourcePosition, startOfSecondNextLine - 1, false));
		// Ensure the last character is a newline.
		A_Tuple unnumbered =
			tupleFromList(parts).concatenateTuplesCanDestroy(true);
		if (unnumbered.tupleSize() == 0
			|| unnumbered.tupleCodePointAt(unnumbered.tupleSize()) != '\n')
		{
			unnumbered = unnumbered.appendCanDestroy(fromCodePoint('\n'), true);
		}

		// Insert line numbers...
		final int maxDigits = Integer.toString(finalLineNumber + 1).length();
		final StringBuilder builder = new StringBuilder();
		//noinspection StringConcatenationMissingWhitespace
		builder.append(
			addLineNumbers(
				((A_String) unnumbered).asNativeString(),
				">>> %" + maxDigits + "d: %s",
				initialLineNumber));
		builder.append(">>>").append(rowOfDashes);

		// Now output all the problems, in the original group order.  Start off
		// with an empty problemIterator to keep the code simple.
		final Iterator<ProblemsAtPosition> groupIterator =
			groupedProblems.iterator();
		final Mutable<Iterator<Describer>> problemIterator =
			new Mutable<>(emptyIterator());
		final Set<String> alreadySeen = new HashSet<>();
		// Initiate all the grouped error printing.
		recurse(continueReport ->
		{
			if (!problemIterator.value.hasNext())
			{
				// End this group.
				if (!groupIterator.hasNext())
				{
					// Done everything.  Pass the complete text forward.
					compilationIsInvalid = true;
					// Generate the footer that indicates the module and
					// line where the last indicator was found.
					builder.append(
						format(
							"%n(file=\"%s\", line=%d)%n>>>%s",
							moduleName.qualifiedName(),
							lastProblem.lineNumber(),
							rowOfDashes));
					handleProblem(new Problem(
						moduleName,
						lastProblem.lineNumber(),
						lastProblem.position(),
						PARSE,
						"{0}",
						builder.toString())
					{
						@Override
						public void abortCompilation ()
						{
							isShuttingDown = true;
							stripNull(failureReporter).value();
						}
					});
					// Generate the footer that indicates the module and
					// line where the last indicator was found.
					return;
				}
				// Advance to the next problem group...
				final ProblemsAtPosition newGroup = groupIterator.next();
				builder.append("\n>>> ");
				builder.append(
					format(
						headerMessagePattern,
						newGroup.indicator,
						newGroup.lineNumber()));
				problemIterator.value = newGroup.describers.iterator();
				alreadySeen.clear();
				assert problemIterator.value.hasNext();
			}
			problemIterator.value.next().describeThen(
				message ->
				{
					// Suppress duplicate messages.
					if (!alreadySeen.contains(message))
					{
						alreadySeen.add(message);
						builder.append("\n>>>\t\t");
						builder.append(
							lineBreakPattern.matcher(message).replaceAll(
								Matcher.quoteReplacement("\n>>>\t\t")));
					}
					// Avoid using direct recursion to keep the stack
					// from getting too deep.
					currentRuntime().execute(
						FiberDescriptor.compilerPriority,
						continueReport);
				});
		});
	}
}
