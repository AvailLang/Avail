/*
 * AvailCompilerBipartiteRendezvous.kt
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

package avail.compiler

import avail.descriptor.phrases.A_Phrase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An `AvailCompilerBipartiteRendezvous` comes at parsing from both sides to
 * maximize the freedom of implementation of the parser.  It uses dynamic
 * programming to avoid parsing the same subexpression multiple times.  When a
 * new continuation needs to run against all possible subexpressions, it looks
 * up the current parser state in a map to get the bipartite rendezvous.  That
 * contains the list of subexpressions that have been parsed so far.  They are
 * all run against the new continuation.  The continuation is then added to the
 * bipartite rendezvous's list of actions.  When a new complete subexpression is
 * found it is run against all waiting actions and added to the list of
 * subexpressions.
 *
 * These two cases ensure each continuation runs with each subexpression –
 * without requiring any particular order of execution of the continuations.
 * That allows us to reorder the continuations arbitrarily, including forcing
 * them to run basically in lock-step with the lexical scanner, avoiding
 * scanning too far ahead in the cases of dynamic scanning rules and power
 * strings.  It also allows parallel execution of the parser.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AvailCompilerBipartiteRendezvous
{
	/**
	 * The first solution, if any.  This is an optimization to avoid having
	 * to hash the solution
	 */
	private var solution1: CompilerSolution? = null

	/** The second solution, if any. */
	private var solution2: CompilerSolution? = null

	/**
	 * The third and subsequent solutions that have been encountered so far,
	 * which, like the first two, will be passed to new actions when they
	 * arrive.
	 */
	private var otherSolutions: MutableList<CompilerSolution>? = null

	/**
	 * The actions that are waiting to run when new solutions arrive.
	 */
	private val actions = mutableListOf<(ParserState, A_Phrase)->Unit>()

	/** Whether we've started parsing at this position. */
	private val hasStarted = AtomicBoolean(false)

	/**
	 * Atomically read hasStartedParsing, make it true, then answer the value
	 * that was read.
	 *
	 * @return
	 *   `true` if parsing had already been started at this position, `false`
	 *   otherwise.
	 */
	internal fun getAndSetStartedParsing(): Boolean = hasStarted.getAndSet(true)

	/**
	 * Record a new solution, and also run any waiting actions with it.
	 *
	 * TODO(MvG) - Should throw DuplicateSolutionException.
	 *   For the moment, suppress duplicates if they're send phrases. We
	 *   temporarily (9/29/2016) allow these duplicates because of the way
	 *   definition parsing plans work.  The parsing instructions for
	 *   repeated arguments can be unrolled, which causes the plans for
	 *   different definitions of the same method to have diverging
	 *   instructions.  More than one of these paths might complete
	 *   successfully.  The resulting send phrases don't indicate which
	 *   plan completed, just the bundle and argument phrases, hence the
	 *   duplicate solutions.
	 *
	 * @param endState
	 *   The [ParserState] after the specified phrase's tokens.
	 * @param phrase
	 *   The [phrase][A_Phrase] that ends at the specified `endState`.
	 */
	@Synchronized
	internal fun addSolution(
		endState: ParserState,
		phrase: A_Phrase)
	{
		val solution = CompilerSolution(endState, phrase)
		when
		{
			solution1 == null -> solution1 = solution
			solution1 == solution -> return //TODO DuplicateSolutionException
			solution2 == null -> solution2 = solution
			solution2 == solution -> return //TODO DuplicateSolutionException
			otherSolutions == null -> otherSolutions = mutableListOf(solution)
			solution in otherSolutions!! ->
				return //TODO DuplicateSolutionException
			else -> otherSolutions!!.add(solution)
		}
		// If it reaches here, the solution was added.
		for (action in actions)
		{
			endState.workUnitDo {
				action(endState, phrase)
			}
		}
	}

	/**
	 * Record a new action, and also run any stored solutions through it.
	 *
	 * @param action
	 *   The new action.
	 */
	@Synchronized
	internal fun addAction(action: (ParserState, A_Phrase)->Unit)
	{
		actions.add(action)
		solution1?.let { (after, phrase) -> action(after, phrase) }
		solution2?.let { (after, phrase) -> action(after, phrase) }
		otherSolutions?.forEach { (after, phrase) -> action(after, phrase) }
	}
}
