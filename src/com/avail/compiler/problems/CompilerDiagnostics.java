/**
 * CompilerDiagnostics.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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

import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.Generator;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;
import com.avail.utility.Strings;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Describer;
import com.avail.utility.evaluation.SimpleDescriber;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.avail.compiler.problems.ProblemType.PARSE;

public class CompilerDiagnostics
{
	/**
	 * Create a {@link CompilerDiagnostics} suitable for tracking the potential
	 * problems encountered during compilation of a single module.
	 *
	 * @param source
	 *        The source code of the module.
	 */
	public CompilerDiagnostics (
		final String source,
		final ModuleName moduleName,
		final Generator<Boolean> pollForAbort,
		final ProblemHandler problemHandler)
	{
		this.source = source;
		this.moduleName = moduleName;
		this.pollForAbort = pollForAbort;
		this.problemHandler = problemHandler;
	}

	/** The source text of the module being compiled. */
	final String source;

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
	 * The rightmost few positions at which potential problems have been
	 * recorded.  The keys of this {@link Map} always agree with the values in
	 * the {@link #expectationsIndexHeap}.  The key is the one-based position of
	 * the start of token, and the value is a map from the tokens that were
	 * lexed at that position to the non-empty list of {@link Describer}s that
	 * indicate problems encountered at that token.
	 *
	 * <p>Lexing problems are recorded here as well, although a suitable invalid
	 * token is created for this purpose.</p>
	 */
	final Map<Integer, Map<A_Token, List<Describer>>> expectations =
		new HashMap<>();

	/**
	 * A priority heap that keeps track of the rightmost N positions at which a
	 * diagnostic message has been recorded.  The entries always agree with the
	 * keys of {@link #expectations}.
	 */
	final PriorityQueue<Integer> expectationsIndexHeap =
		new PriorityQueue<>();

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
		expectations.clear();
		expectationsIndexHeap.clear();
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
	public final Generator<Boolean> pollForAbort;

	/**
	 * This {@code boolean} is set when the {@link #problemHandler} decides that
	 * an encountered {@link Problem} is serious enough that code generation
	 * should be suppressed.  In Avail, that means the serialized module should
	 * not be written to its {@linkplain IndexedRepositoryManager repository}.
	 */
	public volatile boolean compilationIsInvalid = false;


	/** A bunch of dash characters, wide enough to catch the eye. */
	public final static String rowOfDashes =
		"---------------------------------------------------------------------";

	/** The 26 letters of the English alphabet, inside circles. */
	public final static String circledLetters =
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
	public final static String errorIndicatorSymbol =
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
	 * @param token
	 *        The token at which the expectation occurred.
	 */
	public synchronized void expectedAt (
		final Describer describer,
		final A_Token token)
	{
		final Integer position = token.start();
		Map<A_Token, List<Describer>> innerMap = expectations.get(position);
		if (innerMap == null)
		{
			if (expectationsIndexHeap.size() == expectationsCountToTrack
				&& position < expectationsIndexHeap.peek())
			{
				// We have the maximum number of expectation sites, and the new
				// one would come before them all, so ignore it.
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
		List<Describer> innerList = innerMap.get(token);
		if (innerList == null)
		{
			innerList = new ArrayList<>();
			innerMap.put(token, innerList);
		}
		innerList.add(describer);
	}

	/**
	 * Report the rightmost accumulated errors, then invoke {@code afterFail}.
	 *
	 * @param afterFail
	 *        What to do after actually reporting the error.
	 */
	public void reportError (
		final Continuation0 afterFail)
	{
		reportError("Expected at %s, line %d...", afterFail);
	}

	/**
	 * Report the rightmost accumulated errors, then invoke {@code afterFail}.
	 *
	 * @param headerMessagePattern
	 *        The message pattern that introduces each group of problems.  The
	 *        first argument is where the indicator string goes, and the second
	 *        is for the line number.
	 * @param afterFail
	 *        What to do after actually reporting the error.
	 */
	private synchronized void reportError (
		final String headerMessagePattern,
		final Continuation0 afterFail)
	{
		final List<ProblemsAtPosition> groupedProblems = new ArrayList<>();
		final List<Integer> descendingIndices =
			new ArrayList<>(expectationsIndexHeap);
		Collections.sort(
			descendingIndices, Collections.<Integer>reverseOrder());
		int letterOffset = 0;
		int supplementaryCounter = 0;
		for (final Integer sourcePosition : descendingIndices)
		{
			final Map<A_Token, List<Describer>> innerMap =
				expectations.get(sourcePosition);
			assert !innerMap.isEmpty();
			// Due to local lexer ambiguity, there may be multiple possible
			// tokens at this position.  Choose the largest for the purpose of
			// displaying the diagnostics.
			A_Token longestToken = null;
			for (final A_Token eachToken : innerMap.keySet())
			{
				if (longestToken == null
					|| eachToken.string().tupleSize()
						> longestToken.string().tupleSize())
				{
					longestToken = eachToken;
				}
			}
			assert longestToken != null;
			final int nextLetterOffset =
				circledLetters.offsetByCodePoints(letterOffset, 1);
			String indicator = circledLetters.substring(
				letterOffset, nextLetterOffset);
			if (supplementaryCounter > 0)
			{
				// Follow the circled Z with the value of an increasing
				// counter, plus a space to visually separate it from any
				// subsequent token.
				indicator += Integer.toString(supplementaryCounter) + " ";
			}
			// Keep using Ⓩ (circled Z) if we're looking back more than 26
			// tokens for problems.
			if (nextLetterOffset < circledLetters.length())
			{
				letterOffset = nextLetterOffset;
			}
			else
			{
				// Start using Ⓩ (circled Z) followed by an increasing
				// numeric value.
				supplementaryCounter++;
			}
			final List<Describer> describers = new ArrayList<>();
			for (final List<Describer> eachDescriberList : innerMap.values())
			{
				describers.addAll(eachDescriberList);
			}
			groupedProblems.add(
				new ProblemsAtPosition(
					sourcePosition,
					longestToken.lineNumber(),
					longestToken.string(),
					indicator,
					describers));
		}
		assert !groupedProblems.isEmpty();
		reportError(groupedProblems, headerMessagePattern, afterFail);
	}

	/**
	 * Report one specific terminal problem and call the failure continuation to
	 * abort compilation.
	 *
	 * @param token
	 *        The token at which to report this problem.
	 * @param headerMessagePattern
	 *        The problem header pattern, where the first pattern argument is
	 *        the indicator string (e.g., circled-A), and the second pattern
	 *        argument is the line number.
	 * @param message
	 *        The message text for this problem.
	 * @param failure
	 *        What to do after displaying the error message.
	 */
	public synchronized void reportError (
		final A_Token token,
		final String headerMessagePattern,
		final String message,
		final Continuation0 failure)
	{
		final int startPosition = token.start();
		expectations.clear();
		expectationsIndexHeap.clear();
		final Map<A_Token, List<Describer>> innerMap = new HashMap<>();
		innerMap.put(
			token,
			Collections.<Describer>singletonList(new SimpleDescriber(message)));
		expectations.put(startPosition, innerMap);
		expectationsIndexHeap.add(startPosition);
		reportError(headerMessagePattern, failure);
	}

	/**
	 * Report a parsing problem; after reporting it, execute afterFail.
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
	 * @param afterFail
	 *        What to do after the problems have actually been reported.
	 */
	public void reportError (
		final List<ProblemsAtPosition> groupedProblems,
		final String headerMessagePattern,
		final Continuation0 afterFail)
	{
		if (pollForAbort.value())
		{
			// Never report errors during a client-initiated abort.
			afterFail.value();
			return;
		}
		final List<ProblemsAtPosition> ascending =
			new ArrayList<>(groupedProblems);
		Collections.sort(ascending);

		// Figure out where to start showing the file content.  Never show the
		// content before the line on which startOfStatement resides.
		final int startOfFirstLine =
			source.lastIndexOf('\n', startOfStatement - 1) + 1;
		final int initialLineNumber =
			source.substring(0, startOfFirstLine).split("\n").length;
		// Now figure out the last line to show, which if possible should be the
		// line after the *end* of the last problem token.
		final ProblemsAtPosition lastProblem =
			ascending.get(ascending.size() - 1);
		final int finalLineNumber = lastProblem.lineNumber;
		int startOfNextLine =
			source.lastIndexOf('\n', lastProblem.tokenStart - 1) + 1;
		// Eat 1 + the number of embedded line breaks.
		int lineBreaksToEat =
			lastProblem.tokenString.asNativeString().split("\n").length;
		while (lineBreaksToEat-- > 0)
		{
			startOfNextLine = source.indexOf('\n', startOfNextLine) + 1;
			startOfNextLine = (startOfNextLine != 0)
				? startOfNextLine
				: source.length();
		}
		int startOfSecondNextLine = source.indexOf('\n', startOfNextLine) + 1;
		startOfSecondNextLine = (startOfSecondNextLine != 0)
			? startOfSecondNextLine
			: source.length();

		// Insert the problem location indicators...
		int sourcePosition = startOfFirstLine + 1;
		final StringBuilder unnumbered = new StringBuilder();
		for (final ProblemsAtPosition eachProblem : ascending)
		{
			final int newPosition = eachProblem.tokenStart;
			unnumbered.append(
				source,
				sourcePosition - 1,
				newPosition - 1);
			unnumbered.append(eachProblem.indicator);
			sourcePosition = newPosition;
		}
		unnumbered.append(source, sourcePosition - 1, startOfSecondNextLine);
		// Ensure the last character is a newline.
		if (unnumbered.length() == 0 ||
			unnumbered.codePointBefore(unnumbered.length()) != '\n')
		{
			unnumbered.append('\n');
		}

		// Insert line numbers...
		final int maxDigits = Integer.toString(finalLineNumber + 1).length();
		@SuppressWarnings("resource")
		final Formatter text = new Formatter();
		text.format(
			"%s",
			Strings.addLineNumbers(
				unnumbered.toString(),
				">>> %" + maxDigits + "d: %s",
				initialLineNumber));
		text.format(">>>%s", rowOfDashes);

		// Now output all the problems, in the original group order.  Start off
		// with an empty problemIterator to keep the code simple.
		final Iterator<ProblemsAtPosition> groupIterator =
			groupedProblems.iterator();
		final Mutable<Iterator<Describer>> problemIterator = new Mutable<>(
			Collections.<Describer>emptyIterator());
		final Set<String> alreadySeen = new HashSet<String>();
		final MutableOrNull<Continuation0> continueReport =
			new MutableOrNull<Continuation0>();
		continueReport.value = new Continuation0()
		{
			@Override
			public void value ()
			{
				if (!problemIterator.value.hasNext())
				{
					// Start a new problem group...
					if (!groupIterator.hasNext())
					{
						// Done everything.  Pass the complete text forward.
						compilationIsInvalid = true;
						// Generate the footer that indicates the module and
						// line where the last indicator was found.
						text.format(
							"%n(file=\"%s\", line=%d)",
							moduleName.qualifiedName(),
							lastProblem.lineNumber);
						text.format("%n>>>%s", rowOfDashes);
						handleProblem(new Problem(
							moduleName,
							lastProblem.lineNumber,
							lastProblem.tokenStart,
							PARSE,
							"{0}",
							text.toString())
						{
							@Override
							public void abortCompilation ()
							{
								isShuttingDown = true;
								afterFail.value();
							}
						});
						// Generate the footer that indicates the module and
						// line where the last indicator was found.
						return;
					}
					// Advance to the next problem group...
					final ProblemsAtPosition newGroup = groupIterator.next();
					text.format(
						"%n>>> " + headerMessagePattern,
						newGroup.indicator,
						newGroup.lineNumber);
					problemIterator.value = newGroup.describers.iterator();
					alreadySeen.clear();
					assert problemIterator.value.hasNext();
				}
				problemIterator.value.next().describeThen(
					new Continuation1<String>()
					{
						@Override
						public void value (final @Nullable String message)
						{
							assert message != null;
							// Suppress duplicate messages.
							if (!alreadySeen.contains(message))
							{
								alreadySeen.add(message);
								text.format(
									"%n>>>\t\t%s",
									message.replace("\n", "\n>>>\t\t"));
							}
							// Avoid using direct recursion to keep the stack
							// from getting too deep.
							AvailRuntime.current().execute(new AvailTask(
								FiberDescriptor.compilerPriority)
							{
								@Override
								public void value ()
								{
									continueReport.value().value();
								}
							});
						}
					});
			}
		};
		// Initiate all the grouped error printing.
		continueReport.value().value();
	}
}
