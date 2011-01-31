/**
 * compiler/AvailCompilerFragmentCache.java
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
import com.avail.compiler.AvailCompiler.ParserState;

public class AvailCompilerFragmentCache
{
	Map<ParserState, List<AvailCompilerCachedSolution>> solutions =
		new HashMap<ParserState, List<AvailCompilerCachedSolution>>(100);


	List<AvailCompilerCachedSolution> solutionsAt (
		final ParserState state)
	{
		// Answer the previously recorded solution for the recursive ascent
		// optimization.
		return solutions.get(state);
	}

	void addSolution (
			final ParserState state,
			final AvailCompilerCachedSolution solution)
	{
		//  Record a solution for the recursive ascent optimization.

		solutions.get(state).add(solution);
	}

	void clear ()
	{
		solutions.clear();
	}

	boolean hasComputedForState (
			final ParserState state)
	{
		//  Answer whether a parse has already occurred at the specified position.

		return solutions.containsKey(state);
	}

	void startComputingForState (
			final ParserState state)
	{
		//  Indicate that a parse at the given position has started.

		assert !hasComputedForState(state);
		solutions.put(state, new ArrayList<AvailCompilerCachedSolution>(3));
	}
}
