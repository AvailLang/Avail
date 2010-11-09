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

import com.avail.compiler.AvailCompilerCachedSolution;
import com.avail.compiler.AvailCompilerScopeStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvailCompilerFragmentCache
{
	Map<Integer, Map<AvailCompilerScopeStack, List<AvailCompilerCachedSolution>>> _tokenPositionToScopeToSolutions;


	// accessing

	List<AvailCompilerCachedSolution> atTokenPositionScopeStack (
			final int tokenPosition, 
			final AvailCompilerScopeStack scopeStack)
	{
		//  Answer the previously recorded solution for the recursive ascent optimization.

		return _tokenPositionToScopeToSolutions.get(tokenPosition).get(scopeStack);
	}

	void atTokenPositionScopeStackAddSolution (
			final int tokenPosition, 
			final AvailCompilerScopeStack scopeStack, 
			final AvailCompilerCachedSolution solution)
	{
		//  Record a solution for the recursive ascent optimization.

		_tokenPositionToScopeToSolutions.get(tokenPosition).get(scopeStack).add(solution);
	}

	void clear ()
	{
		_tokenPositionToScopeToSolutions.clear();
	}

	boolean hasComputedTokenPositionScopeStack (
			final int tokenPosition, 
			final AvailCompilerScopeStack scopeStack)
	{
		//  Answer whether a parse has already occurred at the specified position.

		return _tokenPositionToScopeToSolutions.containsKey(tokenPosition)
			&& _tokenPositionToScopeToSolutions.get(tokenPosition).containsKey(scopeStack);
	}

	void startComputingTokenPositionScopeStack (
			final int tokenPosition, 
			final AvailCompilerScopeStack scopeStack)
	{
		//  Indicate that a parse at the given position has started.

		assert !hasComputedTokenPositionScopeStack(tokenPosition, scopeStack);
		Map<AvailCompilerScopeStack, List<AvailCompilerCachedSolution>> innerMap;
		innerMap = _tokenPositionToScopeToSolutions.get(tokenPosition);
		if (innerMap == null)
		{
			innerMap = new HashMap<AvailCompilerScopeStack, List<AvailCompilerCachedSolution>>();
			_tokenPositionToScopeToSolutions.put(tokenPosition, innerMap);
		}
		innerMap.put(scopeStack, new ArrayList<AvailCompilerCachedSolution>(3));
	}





	// Constructor

	AvailCompilerFragmentCache ()
	{
		_tokenPositionToScopeToSolutions = new HashMap<
			Integer,
			Map<
				AvailCompilerScopeStack,
				List<AvailCompilerCachedSolution>>>(100);
	}

}
