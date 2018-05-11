/*
 * AvailCompilerFragmentCache.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@code AvailCompilerFragmentCache} implements a memoization mechanism for
 * a {@linkplain AvailCompiler compiler}.  The purpose is to ensure that
 * the effort to parse a subexpression starting at a specific token is reused
 * when backtracking.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCompilerFragmentCache
{
	/**
	 * Keeps track of the {@link CompilerSolution solutions} that
	 * have been found at various positions.  Technically at various {@linkplain
	 * ParserState parser states}, since we must take into account which
	 * variable declarations are in scope when looking for subexpressions.
	 *
	 * <p>This is implemented with a {@link ConcurrentHashMap} to minimize
	 * contention.</p>
	 */
	private final Map<ParserState, AvailCompilerBipartiteRendezvous> solutions =
		new ConcurrentHashMap<>(100);

	/**
	 * Look up the {@link AvailCompilerBipartiteRendezvous} at the given {@link
	 * ParserState}, creating one if necessary.
	 *
	 * @param parserState
	 *        The {@link ParserState} to look up.
	 * @return The requested {@link AvailCompilerBipartiteRendezvous}.
	 */
	public synchronized AvailCompilerBipartiteRendezvous getRendezvous (
		final ParserState parserState)
	{
		return solutions.computeIfAbsent(
			parserState, state -> new AvailCompilerBipartiteRendezvous());
	}

	/**
	 * Clear all cached solutions.  This should be invoked between top level
	 * module statements, since their execution may add new methods, thereby
	 * invalidating previous parse results.
	 */
	void clear ()
	{
		assert Thread.holdsLock(this);
		solutions.clear();
	}
}
