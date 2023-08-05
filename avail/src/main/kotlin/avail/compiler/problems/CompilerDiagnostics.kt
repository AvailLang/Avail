/*
 * CompilerDiagnostics.kt
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

package avail.compiler.problems

import avail.AvailRuntime.Companion.currentRuntime
import avail.builder.ModuleName
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.SILENT
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.ProblemType.PARSE
import avail.compiler.scanning.LexingState
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_String.SurrogateIndexConverter
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.concatenateTuplesCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.firstIndexOf
import avail.descriptor.tuples.A_Tuple.Companion.lastIndexOf
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.persistence.cache.Repository
import avail.utility.Mutable
import avail.utility.Strings.addLineNumbers
import avail.utility.Strings.lineBreakPattern
import avail.utility.evaluation.Combinator.recurse
import avail.utility.evaluation.Describer
import avail.utility.evaluation.SimpleDescriber
import avail.utility.safeWrite
import java.lang.String.format
import java.util.Collections.emptyIterator
import java.util.Collections.reverseOrder
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.BooleanSupplier
import java.util.regex.Matcher
import javax.annotation.concurrent.GuardedBy
import kotlin.collections.set
import kotlin.concurrent.read
import kotlin.math.min

/**
 * This tracks the problems encountered while compiling a single module.
 *
 * @property source
 *   The source text as an Avail [A_String].
 * @property moduleName
 *   The name of the module being compiled.
 * @property pollForAbort
 *   A way to quickly test if the client wishes to shut down prematurely.
 * @property problemHandler
 *   The [ProblemHandler] used for reporting compilation problems.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a `CompilerDiagnostics` suitable for tracking the potential problems
 * encountered during compilation of a single module.
 *
 * @param source
 *   The source code of the module.
 * @param moduleName
 *   The [ModuleName] of the module being compiled.
 * @param pollForAbort
 *   A [BooleanSupplier] to indicate whether to abort.
 * @param problemHandler
 *   A [ProblemHandler] for, well, handling problems during compilation.
 */
class CompilerDiagnostics constructor(
	private val source: A_String,
	private val moduleName: ModuleName,
	val pollForAbort: () -> Boolean,
	private val problemHandler: ProblemHandler)
{
	/**
	 * The position in the source at which the current top-level statement
	 * starts.
	 */
	private var startOfStatement: LexingState? = null

	/**
	 * The non-silent expectations collected during a top-level expression
	 * parsing.
	 */
	private val expectationsList = ExpectationsList(1)

	/**
	 * Allow the positionsToTrack to be adjusted.  Only do this when there is
	 * no compilation active for the receiver.
	 */
	var positionsToTrack: Int by expectationsList::positionsToTrack

	/**
	 * The [ParseNotificationLevel.SILENT] expectations collected during a
	 * top-level expression parsing.  These are tracked separately from the
	 * other expectations, and are only presented if there are no non-silent
	 * expectations at all, anywhere in the top-level expression being parsed.
	 */
	private val silentExpectationsList = ExpectationsList(2)

	/**
	 * Allow the positionsToTrack for [ParseNotificationLevel.SILENT] entries to
	 * be adjusted.  Only do this when there is no compilation active for the
	 * receiver.
	 */
	var silentPositionsToTrack: Int by silentExpectationsList::positionsToTrack

	/**
	 * A collection of tokens that have been encountered during parsing since
	 * the last time this list was cleared.
	 */
	private var liveTokens = mutableListOf<A_Token>()

	/** A lock to protect access to [liveTokens]. */
	private val liveTokensLock = ReentrantReadWriteLock()

	/**
	 * This `boolean` is set when the [problemHandler] decides that an
	 * encountered [Problem] is serious enough that code generation should be
	 * suppressed.  In Avail, that means the serialized module should not be
	 * written to its [repository][Repository].
	 */
	@Volatile
	var compilationIsInvalid = false

	/**
	 * The continuation that reports success of compilation.
	 */
	@Volatile
	var successReporter: (()->Unit)? = null

	/**
	 * The continuation that runs after compilation fails.
	 */
	@Volatile
	private var failureReporter: (()->Unit)? = null

	/**
	 * A tool for converting character positions between Avail's Unicode strings
	 * and Java/Kotlin's UTF-16 representation.
	 */
	val surrogateIndexConverter: SurrogateIndexConverter by lazy {
		SurrogateIndexConverter(source.asNativeString())
	}

	/**
	 * These enumeration values form a priority scheme for reporting parsing
	 * problems.
	 */
	enum class ParseNotificationLevel
	{
		/**
		 * Never report the parse problem, and don't include the current error
		 * location as a potential place at which to describe expectations
		 * (potential syntax errors) unless there is a stronger (i.e., non-{code
		 * SILENT}) problem at the same position.
		 */
		SILENT,

		/**
		 * Only report the parse problem if there is no [MEDIUM] or [STRONG]
		 * theory about what went wrong at the current location.
		 */
		WEAK,

		/**
		 * Only report the parse problem if there is no [STRONG] theory about
		 * what went wrong at the current location.
		 */
		MEDIUM,

		/**
		 * Always report the parse problem at this location, unless there are
		 * too many places in the parse sequence past this, at which
		 * non-[silent][SILENT] problems have been recorded.
		 */
		STRONG;

		companion object
		{
			/** Capture the values once in an array. */
			private val all = entries.toTypedArray()

			/**
			 * Convert the zero-based int to a `ParseNotificationLevel`.
			 *
			 * @param level
			 *   The ordinal of the level.
			 * @return
			 *   The `ParseNotificationLevel` with that ordinal.
			 */
			fun levelFromInt(level: Int) = all[level]
		}
	}

	/**
	 * A collection of potential problems that have been collected at a
	 * particular source location.  If parsing can't proceed more than a few
	 * tokens past this point, the problems will be presented as compiler error
	 * output.
	 */
	internal class ExpectationsAtPosition
	{
		/**
		 * The highest level of [ParseNotificationLevel] encountered at this
		 * position.  Note that it's initialized to
		 * [ParseNotificationLevel.SILENT], but must not be stored in the
		 * [CompilerDiagnostics] unless a stronger notification level is
		 * recorded.
		 */
		private var level = SILENT

		/**
		 * The [List] of [Describer]s that describe problems at this position.
		 */
		val problems = mutableListOf<Describer>()

		/**
		 * All [LexingState]s at which problems for the current notification
		 * level occur.  These are all at the same position, but some may have
		 * different interpretations of which tokens might follow. These will be
		 * examined during error production to ensure the longest such potential
		 * successor token will be included in its entirety in the contextual
		 * code excerpt during error reporting.  This is relevant for multi-line
		 * tokens like string literals.
		 */
		val lexingStates = mutableSetOf<LexingState>()

		/**
		 * Record a new problem in this instance.  Discard problems that have a
		 * lower notification level than the maximum encountered.
		 *
		 * @param lexingState
		 *   The [LexingState] at which this problem occurred.
		 * @param newLevel
		 *   The [ParseNotificationLevel] of this new problem.
		 * @param describer
		 *   A [Describer] for the new problem.
		 */
		fun recordProblem(
			lexingState: LexingState,
			newLevel: ParseNotificationLevel,
			describer: Describer)
		{
			if (newLevel.ordinal < level.ordinal)
			{
				return
			}
			if (newLevel.ordinal > level.ordinal)
			{
				level = newLevel
				problems.clear()
				lexingStates.clear()
			}
			problems.add(describer)
			lexingStates.add(lexingState)
		}
	}

	/**
	 * A helper class for tracking the expectations at the rightmost `N`
	 * positions at which expectations have been recorded.
	 *
	 * There's a private access lock, a map from character position in the
	 * source to an [ExpectationsAtPosition] structure, and a [PriorityQueue]
	 * that keeps track of the N rightmost positions for which an expectation
	 * has been recorded.
	 *
	 * @property positionsToTrack
	 *   The number of distinct (rightmost) positions for which to record
	 *   expectations.  Note that [ParseNotificationLevel.SILENT] entries are
	 *   recorded in their own `ExpectationsList`, and they are only presented
	 *   if there are no non-silent expectations recorded.
	 */
	private inner class ExpectationsList constructor(
		var positionsToTrack: Int)
	{
		/** Guard access to [expectations] and [expectationsIndexHeap]. */
		val expectationsLock = ReentrantReadWriteLock()

		/**
		 * The rightmost few positions at which potential problems have been
		 * recorded.  The keys of this [Map] always agree with the values in the
		 * [expectationsIndexHeap].  The key is the one-based position of the
		 * start of token, and the value is an [ExpectationsAtPosition] that
		 * tracks the most likely causes of problems at this position.
		 *
		 * Lexing problems are recorded here as well, although a suitable
		 * invalid token is created for this purpose.  This allows positioning
		 * the problem at an exact character position.
		 */
		@GuardedBy("expectationsLock")
		private val expectations = mutableMapOf<Int, ExpectationsAtPosition>()

		/**
		 * A priority heap that keeps track of the rightmost `N` positions at
		 * which a diagnostic message has been recorded.  The entries always
		 * agree with the keys of [expectations].
		 */
		@GuardedBy("expectationsLock")
		private val expectationsIndexHeap = PriorityQueue<Int>()

		/** `true` iff there are no expectations recorded herein. */
		val isEmpty: Boolean
			get() = expectationsLock.read { expectations.isEmpty() }

		/**
		 * Remove all expectations in preparation for parsing another top-level
		 * expression.
		 */
		fun clear() =
			expectationsLock.safeWrite {
				expectations.clear()
				expectationsIndexHeap.clear()
			}

		/**
		 * Record an expectation at the given token.
		 *
		 * @param level
		 *   The [ParseNotificationLevel] which indicates the priority of this
		 *   theory about a failed parse.
		 * @param describer
		 *   A [Describer], something which can be evaluated (including running
		 *   Avail code) to produce a String, which is then passed to a provided
		 *   continuation.
		 * @param lexingState
		 *   The [LexingState] at which the expectation occurred.
		 */
		fun expectedAt(
			level: ParseNotificationLevel,
			describer: Describer,
			lexingState: LexingState)
		{
			expectationsLock.safeWrite {
				val position = lexingState.position
				var localExpectations = expectations[position]
				if (localExpectations === null)
				{
					if (expectationsIndexHeap.size == positionsToTrack
						&& position < expectationsIndexHeap.peek())
					{
						// We have the maximum number of expectation sites,
						// and the new one would come before them all, so
						// ignore it.
						return
					}
					localExpectations = ExpectationsAtPosition()
					expectations[position] = localExpectations
					// Also update expectationsIndexHeap.
					expectationsIndexHeap.add(position)
					if (expectationsIndexHeap.size > positionsToTrack)
					{
						val removed = expectationsIndexHeap.remove()
						expectations.remove(removed)!!
					}
				}
				localExpectations.recordProblem(lexingState, level, describer)
			}
		}

		/**
		 * Report a parsing problem.  After reporting it, execute the
		 * [failureReporter].
		 *
		 * @param groupedProblems
		 *   The [List] of [ProblemsAtPosition] to report.  Each
		 *   `ProblemsAtPosition` describes the problems that occurred at some
		 *   token's position.
		 * @param headerMessagePattern
		 *   The message pattern to be populated and written before each group
		 *   of problems.  Its arguments are the group's
		 *   [indicator][ProblemsAtPosition.indicator] and the problematic
		 *   token's line number.
		 */
		private fun reportGroupedErrors(
			groupedProblems: List<ProblemsAtPosition>,
			headerMessagePattern: String)
		{
			if (pollForAbort())
			{
				// Never report errors during a client-initiated abort.
				failureReporter!!()
				return
			}
			val ascending = groupedProblems.sorted()

			// Figure out where to start showing the file content.  Never show
			// the content before the line on which startOfStatement resides.
			val startOfFirstLine = 1 + source.lastIndexOf(
				fromCodePoint('\n'.code),
				startOfStatement!!.position - 1,
				1)
			val initialLineNumber = 1 + occurrencesInRange(
				source,
				'\n'.code,
				1,
				min(source.tupleSize, startOfFirstLine))
			// Now figure out the last line to show, which, if possible, should
			// be the line after the *end* of the last problem token.
			val lastProblem = ascending[ascending.size - 1]
			val finalLineNumber = lastProblem.lineNumber
			var startOfNextLine = 1 + source.firstIndexOf(
				fromCodePoint('\n'.code),
				lastProblem.lexingStateAfterToken.position,
				source.tupleSize)
			startOfNextLine =
				if (startOfNextLine != 1)
					startOfNextLine
				else
					source.tupleSize + 1
			var startOfSecondNextLine = 1 + source.firstIndexOf(
				fromCodePoint('\n'.code),
				startOfNextLine,
				source.tupleSize)
			startOfSecondNextLine =
				if (startOfSecondNextLine != 1)
					startOfSecondNextLine
				else
					source.tupleSize + 1

			// Insert the problem location indicators...
			var sourcePosition = startOfFirstLine
			val parts = mutableListOf<A_String>()
			for (eachProblem in ascending)
			{
				val newPosition = eachProblem.position
				parts.add(
					source.copyStringFromToCanDestroy(
						sourcePosition, newPosition - 1, false))
				parts.add(stringFrom(eachProblem.indicator))
				sourcePosition = newPosition
			}
			parts.add(
				source.copyStringFromToCanDestroy(
					sourcePosition, startOfSecondNextLine - 1, false))
			// Ensure the last character is a newline.
			var unnumbered =
				tupleFromList(parts).concatenateTuplesCanDestroy(true)
			if (unnumbered.tupleSize == 0
				|| unnumbered.tupleCodePointAt(unnumbered.tupleSize)
					!= '\n'.code)
			{
				unnumbered = unnumbered.appendCanDestroy(
					fromCodePoint('\n'.code), true)
			}

			// Insert line numbers...
			val maxDigits = (finalLineNumber + 1).toString().length
			StringBuilder(500).run {
				append(
					addLineNumbers(
						(unnumbered as A_String).asNativeString(),
						">>> %${maxDigits}d: %s",
						initialLineNumber))
				append(">>>$rowOfDashes")

				// Now output all the problems, in the original group order.  Start
				// off with an empty problemIterator to keep the code simple.
				val groupIterator = groupedProblems.iterator()
				val problemIterator = Mutable(emptyIterator<Describer>())
				val alreadySeen = mutableSetOf<String>()
				// Initiate all the grouped error printing.
				recurse { continueReport ->
					if (!problemIterator.value.hasNext())
					{
						// End this group.
						if (!groupIterator.hasNext())
						{
							// Done everything.  Pass the complete text forward.
							compilationIsInvalid = true
							// Generate the footer that indicates the module and
							// line where the last indicator was found.
							append(
								format(
									"%n(file=\"%s\", line=%d)%n>>>%s",
									moduleName.qualifiedName,
									lastProblem.lineNumber,
									rowOfDashes))
							handleProblem(object : Problem(
								moduleName,
								lastProblem.lineNumber,
								lastProblem.position.toLong(),
								PARSE,
								"{0}",
								this@run.toString().replace("\t", "    ")) {
									override fun abortCompilation()
									{
										failureReporter?.invoke()
									}
								})
							// Generate the footer that indicates the module and
							// line where the last indicator was found.
							return@recurse
						}
						// Advance to the next problem group...
						val newGroup = groupIterator.next()
						append("\n>>> ")
						append(
							format(
								headerMessagePattern,
								newGroup.indicator,
								newGroup.lineNumber))
						problemIterator.value = newGroup.describers.iterator()
						alreadySeen.clear()
						assert(problemIterator.value.hasNext())
					}
					problemIterator.value.next().invoke { message ->
						// Suppress duplicate messages.
						if (!alreadySeen.contains(message))
						{
							alreadySeen.add(message)
							append("\n>>>\t\t")
							append(
								lineBreakPattern.matcher(message).replaceAll(
									Matcher.quoteReplacement("\n>>>\t\t")))
						}
						// Avoid using direct recursion to keep the stack
						// from getting too deep.
						currentRuntime().execute(
							FiberDescriptor.compilerPriority,
							continueReport)
					}
				}
			}
		}

		/**
		 * Report the rightmost accumulated errors, then invoke the
		 * [failureReporter].  The receiver must not be [isEmpty].
		 *
		 * @param headerMessagePattern
		 *   The message pattern that introduces each group of problems. The
		 *   first argument is where the indicator string goes, and the second
		 *   is for the line number.
		 */
		fun reportError(headerMessagePattern: String)
		{
			assert(!isEmpty)
			val descendingIndices =
				expectationsLock.read {
					expectationsIndexHeap.toList()
				}.sortedWith(reverseOrder())
			accumulateErrorsThen(
				descendingIndices.iterator(),
				IndicatorGenerator(),
				mutableListOf()
			) { groups -> reportGroupedErrors(groups, headerMessagePattern) }
		}

		/**
		 * Report one specific terminal problem and call the failure
		 * continuation to abort compilation.
		 *
		 * @param lexingState
		 *   The position at which to report the problem.
		 * @param headerMessagePattern
		 *   The problem header pattern, where the first pattern argument is the
		 *   indicator string (e.g., circled-A), and the second pattern argument
		 *   is the line number.
		 * @param message
		 *   The message text for this problem.
		 */
		fun reportError(
			lexingState: LexingState,
			headerMessagePattern: String,
			message: String)
		{
			expectationsLock.safeWrite {
				// Delete any potential parsing errors already encountered,
				// and replace them with the new error.
				expectations.clear()
				expectationsIndexHeap.clear()
				val localExpectations = ExpectationsAtPosition()
				localExpectations.recordProblem(
					lexingState, STRONG, SimpleDescriber(message))
				expectations[lexingState.position] = localExpectations
				expectationsIndexHeap.add(lexingState.position)
			}
			reportError(headerMessagePattern)
		}

		/**
		 * Accumulate the errors, then pass the [List] of [ProblemsAtPosition]s
		 * to the given function.
		 *
		 * @param descendingIterator
		 *   An [Iterator] that supplies source positions at which problems have
		 *   been recorded, ordered by descending position.
		 * @param indicatorGenerator
		 *   An [IndicatorGenerator] for producing marker strings to label the
		 *   successive (descending) positions in the source where problems
		 *   occurred.
		 * @param groupedProblems
		 *   The [List] of [ProblemsAtPosition]s accumulated so far.
		 * @param afterGrouping
		 *   What to do after producing the grouped error reports.
		 */
		private fun accumulateErrorsThen(
			descendingIterator: Iterator<Int>,
			indicatorGenerator: IndicatorGenerator,
			groupedProblems: MutableList<ProblemsAtPosition>,
			afterGrouping: (List<ProblemsAtPosition>) -> Unit)
		{
			if (!descendingIterator.hasNext())
			{
				// Done assembling each of the problems.  Report them.
				assert(groupedProblems.isNotEmpty())
				afterGrouping(groupedProblems)
				return
			}
			val sourcePosition = descendingIterator.next()
			lateinit var describers: List<Describer>
			lateinit var lexingStates: Set<LexingState>
			expectationsLock.read {
				val localExpectations = expectations[sourcePosition]!!
				describers = localExpectations.problems.toList()
				lexingStates = localExpectations.lexingStates.toSet()
			}
			assert(describers.isNotEmpty())
			// Due to local lexer ambiguity, there may be multiple possible
			// tokens at this position.  Choose the longest for the purpose
			// of displaying the diagnostics.  We only care about the tokens
			// that have already been formed, not ones in progress.
			findLongestTokenThen(lexingStates) { longestToken ->
				val before = lexingStates.first()
				groupedProblems.add(
					ProblemsAtPosition(
						before,
						if (longestToken.tokenType() == END_OF_FILE)
							before
						else
							longestToken.nextLexingState(),
						indicatorGenerator.next(),
						describers))
				accumulateErrorsThen(
					descendingIterator,
					indicatorGenerator,
					groupedProblems,
					afterGrouping)
			}
		}
	}

	/**
	 * Record the fact that we're starting to parse a top-level statement at the
	 * indicated one-based position in the source.  Clear any already recorded
	 * expectations.
	 *
	 * @param initialPositionInSource
	 *   The [LexingState] at the earliest source position for which we should
	 *   record problem information.
	 */
	fun startParsingAt(initialPositionInSource: LexingState)
	{
		startOfStatement = initialPositionInSource
		expectationsList.clear()
		silentExpectationsList.clear()
		// Tidy up all tokens from the previous top-level statement.
		val priorTokens = liveTokensLock.safeWrite {
			val old = liveTokens
			liveTokens = mutableListOf()
			old
		}
		for (token in priorTokens)
		{
			token.clearLexingState()
		}
	}

	/**
	 * Handle a [problem][Problem] via the
	 * [problem&#32;handler][problemHandler].
	 *
	 * @param problem
	 *   The problem to handle.
	 */
	fun handleProblem(problem: Problem) = problemHandler.handle(problem)

	/**
	 * Set the success reporter and failure reporter.
	 *
	 * @param theSuccessReporter
	 *   What to do when the module compilation completes successfully.
	 * @param theFailureReporter
	 *   What to do when the module compilation completes unsuccessfully.
	 */
	fun setSuccessAndFailureReporters(
		theSuccessReporter: () -> Unit,
		theFailureReporter: () -> Unit)
	{
		val hasRun = AtomicBoolean(false)
		this.successReporter = {
			val ran = hasRun.getAndSet(true)
			assert(!ran) { "Success/failure reporter ran twice" }
			theSuccessReporter()
		}
		this.failureReporter = {
			val ran = hasRun.getAndSet(true)
			assert(!ran) { "Success/failure reporter ran twice" }
			theFailureReporter()
		}
	}

	/**
	 * Record an expectation at the given token.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] which indicates the priority of this
	 *   theory about a failed parse.
	 * @param describer
	 *   A [Describer], something which can be evaluated (including running
	 *   Avail code) to produce a String, which is then passed to a provided
	 *   continuation.
	 * @param lexingState
	 *   The [LexingState] at which the expectation occurred.
	 */
	fun expectedAt(
		level: ParseNotificationLevel,
		describer: Describer,
		lexingState: LexingState)
	{
		val list =
			if (level == SILENT) silentExpectationsList else expectationsList
		list.expectedAt(level, describer, lexingState)
	}

	/**
	 * Record the fact that this token was encountered while parsing the current
	 * top-level statement.
	 *
	 * @param token
	 *   The token that was encountered.
	 */
	fun recordToken(token: A_Token): Unit =
		liveTokensLock.safeWrite { liveTokens.add(token) }

	/**
	 * An `IndicatorGenerator` creates successive circled letters via its [next]
	 * method.  When it hits circled-Z, it stays at that letter and starts
	 * suffixing consecutive integers and a space.
	 */
	internal inner class IndicatorGenerator
	{
		/**
		 * The zero-based index of the next `char` within [circledLetters].  If
		 * it points past the end, use a Ⓩ (circled Z) and a decimal number
		 * taken from the [supplementaryCounter].
		 */
		private var letterOffset = 0

		/**
		 * The counter to use after the 26 circled letters have been exhausted.
		 */
		private var supplementaryCounter = 0

		/**
		 * Produce the next indicator [String].
		 *
		 * @return
		 *   A circled letter, optionally followed by a decimal numeral if it's
		 *   past the 26th entry.
		 */
		operator fun next(): String
		{
			val nextLetterOffset = circledLetters.offsetByCodePoints(
				letterOffset, 1)
			var indicator = circledLetters.substring(
				letterOffset, nextLetterOffset)
			if (supplementaryCounter > 0)
			{
				// Follow the circled Z with the value of an increasing counter,
				// plus a space to visually separate it from any subsequent
				// token.
				indicator += "$supplementaryCounter "
			}
			// Keep using Ⓩ (circled Z) if we're looking back more than 26
			// tokens for problems (unlikely).
			if (nextLetterOffset < circledLetters.length)
			{
				letterOffset = nextLetterOffset
			}
			else
			{
				// Start using Ⓩ (circled Z) followed by an increasing numeric
				// value.
				supplementaryCounter++
			}
			return indicator
		}
	}

	/**
	 * Report the rightmost accumulated errors, then invoke the
	 * [failureReporter].
	 */
	fun reportError()
	{
		var list = expectationsList
		if (list.isEmpty)
		{
			// Only silent expectations were recorded.  Show them.
			list = silentExpectationsList
			if (list.isEmpty)
			{
				// No expectations of any strength were record.  Synthesize
				// something to say about it.
				list = ExpectationsList(1)
				list.expectedAt(
					STRONG,
					{ then ->
						then(
							"to be able to parse a top-level statement here, "
							+ "but undescribed impediments were encountered.")
					},
					startOfStatement!!)
			}
		}
		assert(!list.isEmpty)
		list.reportError(expectationHeaderMessagePattern)
	}

	/**
	 * Report one specific terminal problem and call the failure continuation to
	 * abort compilation.
	 *
	 * @param lexingState
	 *   The position at which to report the problem.
	 * @param headerMessagePattern
	 *   The problem header pattern, where the first pattern argument is the
	 *   indicator string (e.g., circled-A), and the second pattern argument is
	 *   the line number.
	 * @param message
	 *   The message text for this problem.
	 */
	fun reportError(
		lexingState: LexingState,
		headerMessagePattern: String,
		message: String
	) = expectationsList.reportError(lexingState, headerMessagePattern, message)

	companion object
	{
		/** A bunch of dash characters, wide enough to catch the eye. */
		private const val rowOfDashes =
			"---------------------------------------------------------------------"

		/** The 26 letters of the English alphabet, inside circles. */
		private const val circledLetters =
			"ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ"

		/**
		 * Despite, what, a hundred thousand employees(?), Microsoft seems
		 * unable to support the simplest things in Windows.  Like including a
		 * font that has a significant portion of Unicode supported, or devising
		 * (or even copying!) a mechanism to substitute glyphs from fonts that
		 * *do* have those code points.  So if you're doomed to be on Windows
		 * you'll get an inferior right arrow with hook to indicate the location
		 * of errors.
		 *
		 * That will teach you.
		 */
		val errorIndicatorSymbol =
			if (System.getProperty("os.name").startsWith("Windows"))
				"↪"
			else
				"⤷"

		/**
		 * Given a collection of [LexingState]s, examine the tokens starting at
		 * each lexing state, choosing the longest one.  Invoke the passed
		 * continuation with that longest token.
		 *
		 * This should only be used during final reporting of errors, so don't
		 * attempt to run the lexers, or they might produce additional problems
		 * at further positions than appropriate.  Instead, use whatever tokens
		 * have been accumulated so far, ignoring whether this is a complete
		 * list or not, and synthesizing an empty token if necessary.
		 *
		 * @param startLexingStates
		 *   The starting [LexingState]s.
		 * @param continuation
		 *   What to do when the longest [A_Token] has been found.
		 */
		private fun findLongestTokenThen(
			startLexingStates: Collection<LexingState>,
			continuation: (A_Token) -> Unit)
		{
			val candidates = startLexingStates
				.mapNotNull { it.knownToBeComputedTokensOrNull }
				.flatten()
			if (candidates.isEmpty())
			{
				val state = startLexingStates.first()
				val emptyToken = newToken(
					emptyTuple,
					state.position,
					state.lineNumber,
					WHITESPACE,
					nil)
				emptyToken.setNextLexingStateFromPrior(state)
				continuation(emptyToken.makeShared())
				return
			}
			continuation(candidates.maxByOrNull { it.string().tupleSize }!!)
		}

		/**
		 * The message pattern that introduces each group of problems.  The
		 * first pattern argument is where the indicator string goes, and the
		 * second is for the line number.
		 */
		private const val expectationHeaderMessagePattern =
			"Expected at %s, line %d..."

		/**
		 * Search for a particular code point in the given range of the Avail
		 * string, answering how many occurrences were found.
		 *
		 * @param string
		 *   The string in which to search.
		 * @param codePoint
		 *   The code point to search for.
		 * @param startIndex
		 *   The position at which to start scanning.
		 * @param endIndex
		 *   The position at which to stop scanning.
		 * @return
		 *   The number of occurrences of the codePoint in the specified range
		 *   of the [A_String].
		 */
		@Suppress("SameParameterValue")
		private fun occurrencesInRange(
			string: A_String,
			codePoint: Int,
			startIndex: Int,
			endIndex: Int): Int
		{
			var count = 0
			for (i in startIndex .. endIndex)
			{
				if (string.tupleCodePointAt(i) == codePoint)
				{
					count++
				}
			}
			return count
		}
	}
}
