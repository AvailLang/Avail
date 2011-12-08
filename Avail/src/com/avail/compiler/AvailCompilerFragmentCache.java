/**
 * AvailCompilerFragmentCache.java
 * Copyright (c) 2010, Mark van Gulik.
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

import java.util.*;
import com.avail.compiler.AbstractAvailCompiler.ParserState;

/**
 * An {@code AvailCompilerFragmentCache} implements a memoization mechanism for
 * a {@linkplain AbstractAvailCompiler compiler}.  The purpose is to ensure that
 * the effort to parse a subexpression starting at a specific token is reused
 * when backtracking.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCompilerFragmentCache
{
	/**
	 * Keeps track of the {@link AvailCompilerCachedSolution solutions} that
	 * have been found at various positions.  Technically at various {@linkplain
	 * ParserState parser states}, since we must take into account which
	 * variable declarations are in scope when looking for subexpressions.
	 */
	private final Map<ParserState, List<AvailCompilerCachedSolution>>
		solutions =
			new HashMap<ParserState, List<AvailCompilerCachedSolution>>(100);


	/**
	 * Answer a {@link List} of {@linkplain AvailCompilerCachedSolution
	 * solutions} that have been previously parsed at the specified position.
	 *
	 * @param state The {@link ParserState} at which parsing is to take place.
	 * @return The list of solutions.  Do not modify the list.
	 */
	List<AvailCompilerCachedSolution> solutionsAt (
		final ParserState state)
	{
		return solutions.get(state);
	}

	/**
	 * Add one more parse solution at the specified position.
	 *
	 * @param state The {@link ParserState} at which parsing took place.
	 * @param solution The solution found starting at that position.
	 */
	void addSolution (
			final ParserState state,
			final AvailCompilerCachedSolution solution)
	{
		solutions.get(state).add(solution);
	}

	/**
	 * Clear all cached solutions.  This should be invoked between top level
	 * module statements, since their execution may add new methods, thereby
	 * invalidating previous parse results.
	 */
	void clear ()
	{
		solutions.clear();
	}

	/**
	 * Answer whether an attempt has already been made to parse solutions at the
	 * specified {@linkplain ParserState parse position}.
	 *
	 * @param state
	 *            The state starting at which subexpression parsing may have
	 *            already taken place.
	 * @return
	 *            Whether a previous parse attempt has taken place starting at
	 *            the specified state, the results having been cached.
	 */
	boolean hasComputedForState (
			final ParserState state)
	{
		return solutions.containsKey(state);
	}

	/**
	 * Record the fact that no solutions have yet been found starting at the
	 * specified {@link ParserState}.  Subsequent successful parses of
	 * subexpressions starting there may be added to this list via {@link
	 * #addSolution(ParserState, AvailCompilerCachedSolution)}.
	 *
	 * @param state The state at which parsing solutions will be attempted.
	 */
	void startComputingForState (
			final ParserState state)
	{
		assert !hasComputedForState(state);
		solutions.put(state, new ArrayList<AvailCompilerCachedSolution>(3));
	}
}
