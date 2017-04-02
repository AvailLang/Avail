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
import com.avail.annotations.InnerAccess;
import com.avail.compiler.CompilationContext;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.utility.evaluation.Describer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An aggregate of problems at a specific token.  It also includes the
 * {@link String} used to mark the location of the problem in the source
 * text.
 */
@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
@InnerAccess
public final class ProblemsAtPosition
	implements Comparable<ProblemsAtPosition>
{
	/**
	 * The one-based index into the source {@link A_String string} at which
	 * to say the problem occurred.
	 */
	final int tokenStart;

	/**
	 * The one-based line number in the source {@link A_String string} at
	 * which to say the problem occurred.
	 */
	final int lineNumber;

	/**
	 * The text of the token at which the problem occurred.
	 */
	final A_String tokenString;

	/**
	 * The indicator {@link String} that marks the location of the problems
	 * in the source text.
	 */
	final @InnerAccess String indicator;

	/** A list of {@link Describer}s able to describe the problems. */
	final @InnerAccess
	List<Describer> describers;

	/**
	 * Construct a new {@link ProblemsAtPosition}.
	 *
	 * @param tokenStart
	 *        The one-based offset of the start of the token within the source.
	 * @param lineNumber
	 *        The line number on which the token starts.
	 * @param tokenString
	 *        The text of the token.
	 * @param indicator
	 *        The {@link String} that marks the problems in the source.
	 * @param describers
	 *        The {@link List} of {@link Describer}s that describe the
	 *        problems at the specified token.
	 */
	ProblemsAtPosition (
		final int tokenStart,
		final int lineNumber,
		final A_String tokenString,
		final String indicator,
		final List<Describer> describers)
	{
		this.tokenStart = tokenStart;
		this.lineNumber = lineNumber;
		this.tokenString = tokenString;
		this.indicator = indicator;
		this.describers = describers;
	}

	@Override
	public int compareTo (final @Nullable ProblemsAtPosition otherProblems)
	{
		assert otherProblems != null;
		return Integer.compare(tokenStart, otherProblems.tokenStart);
	}
}
