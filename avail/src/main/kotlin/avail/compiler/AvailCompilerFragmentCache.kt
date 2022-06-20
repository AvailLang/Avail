/*
 * AvailCompilerFragmentCache.kt
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

import java.util.concurrent.ConcurrentHashMap

/**
 * An `AvailCompilerFragmentCache` implements a memoization mechanism for a
 * [compiler][AvailCompiler].  The purpose is to ensure that the effort to parse
 * a subexpression starting at a specific token is reused when backtracking.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AvailCompilerFragmentCache
{
	/**
	 * Keeps track of the [solutions][CompilerSolution] that have been found at
	 * various positions.  Technically at various
	 * [parser&#32;states][ParserState], since we must take into account which
	 * variable declarations are in scope when looking for subexpressions.
	 *
	 * This is implemented with a [ConcurrentHashMap] to minimize contention.
	 */
	private val solutions =
		ConcurrentHashMap<ParserState, AvailCompilerBipartiteRendezvous>(100)

	/**
	 * Look up the [AvailCompilerBipartiteRendezvous] at the given
	 * [parser&#32;state][ParserState], creating one if necessary.
	 *
	 * @param parserState
	 *   The [ParserState] to look up.
	 * @return
	 *   The requested [AvailCompilerBipartiteRendezvous].
	 */
	@Synchronized
	fun getRendezvous(parserState: ParserState) =
		solutions.computeIfAbsent(parserState) {
			AvailCompilerBipartiteRendezvous()
		}

	/**
	 * Clear all cached solutions.  This should be invoked between top level
	 * module statements, since their execution may add new methods, thereby
	 * invalidating previous parse results.
	 */
	internal fun clear()
	{
		assert(Thread.holdsLock(this))
		solutions.clear()
	}
}
