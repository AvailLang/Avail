/**
 * ProblemsAtPosition.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.compiler.problems;
import com.avail.compiler.scanning.LexingState;
import com.avail.utility.evaluation.Describer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An aggregate of problems at a specific token.  It also includes the {@link
 * String} used to mark the location of the problem in the source text.
 */
@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
public final class ProblemsAtPosition
	implements Comparable<ProblemsAtPosition>
{
	/**
	 * The lexing position at which the problems occurred.
	 */
	final LexingState lexingState;

	/**
	 * The {@link LexingState} right after the token where the problems
	 * occurred.  Use the longest token if the lexing is ambiguous.  This
	 * ensures the source code that gets output with an error doesn't cut off a
	 * multi-line token.
	 */
	final LexingState lexingStateAfterToken;

	/**
	 * The indicator {@link String} that marks the location of the problems
	 * in the source text.
	 */
	final String indicator;

	/** A list of {@link Describer}s able to describe the problems. */
	final List<Describer> describers;

	/**
	 * Construct a new {@link ProblemsAtPosition}.
	 *
	 * @param lexingState
	 *        The {@link LexingState} where the problems occurred.
	 * @param lexingStateAfterToken
	 *        The {@link LexingState} right after the token where the problems
	 *        occurred.  Use the longest token if the lexing is ambiguous.  This
	 *        ensures the source code that gets output with an error doesn't cut
	 *        off a multi-line token.
	 * @param indicator
	 *        The {@link String} that marks the problems in the source.
	 * @param describers
	 *        The {@link List} of {@link Describer}s that describe the
	 *        problems at the specified token.
	 */
	ProblemsAtPosition (
		final LexingState lexingState,
		final LexingState lexingStateAfterToken,
		final String indicator,
		final List<Describer> describers)
	{
		this.lexingState = lexingState;
		this.lexingStateAfterToken = lexingStateAfterToken;
		this.indicator = indicator;
		this.describers = describers;
	}

	@Override
	public int compareTo (final @Nullable ProblemsAtPosition otherProblems)
	{
		assert otherProblems != null;
		return Integer.compare(
			lexingState.position, otherProblems.position());
	}

	/**
	 * Answer the start of this problem in the source.
	 *
	 * @return The one-based index into the source.
	 */
	public int position ()
	{
		return lexingState.position;
	}

	/**
	 * Answer the one-based line number of the start of this problem.
	 *
	 * @return The one-based line number.
	 */
	public int lineNumber ()
	{
		return lexingState.lineNumber;
	}
}
