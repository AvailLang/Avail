/**
 * AvailCompilerFragmentCache.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import com.avail.compiler.AbstractAvailCompiler.Con;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.A_Phrase;

/**
 * An {@code AvailCompilerFragmentCache} implements a memoization mechanism for
 * a {@linkplain AbstractAvailCompiler compiler}.  The purpose is to ensure that
 * the effort to parse a subexpression starting at a specific token is reused
 * when backtracking.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCompilerFragmentCache
{
	/**
	 * Keeps track of the {@link AvailCompilerCachedSolution solutions} that
	 * have been found at various positions.  Technically at various {@linkplain
	 * ParserState parser states}, since we must take into account which
	 * variable declarations are in scope when looking for subexpressions.
	 */
	private final Map<ParserState, AvailCompilerBipartiteRendezvous> solutions =
		new HashMap<>(100);


	/**
	 * Answer whether expression parsing has started at this position.
	 *
	 * @param state
	 *            The {@linkplain ParserState} at which parsing may have started
	 * @return
	 *            Whether parsing has started at that position.
	 */
	boolean hasStartedParsingAt (
		final ParserState state)
	{
		return solutions.containsKey(state);
	}

	/**
	 * Indicate that parsing has started at this position.
	 *
	 * @param state
	 *            The {@linkplain ParserState} at which parsing has started.
	 */
	void indicateParsingHasStartedAt (
		final ParserState state)
	{
		assert !hasStartedParsingAt(state);
		solutions.put(state, new AvailCompilerBipartiteRendezvous());
		assert hasStartedParsingAt(state);
	}

	/**
	 * Add one more parse solution at the specified position, running any
	 * waiting actions with it.
	 *
	 * @param state
	 *            The {@link ParserState} at which parsing took place.
	 * @param solution
	 *            The {@link AvailCompilerCachedSolution solution} found
	 *            starting at that position.
	 */
	void addSolution (
		final ParserState state,
		final AvailCompilerCachedSolution solution)
	{
		solutions.get(state).addSolution(solution);
	}

	/**
	 * Add one more action at the specified position, running it for any
	 * solutions that now exist or will exist later.
	 *
	 * @param state
	 *            The {@link ParserState} at which the action is expecting
	 *            solutions to show up.
	 * @param action
	 *            The {@link Con action} to run with each solution found at the
	 *            specified position.
	 */
	void addAction (
		final ParserState state,
		final Con<A_Phrase> action)
	{
		solutions.get(state).addAction(action);
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

}
