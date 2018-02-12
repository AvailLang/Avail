/*
 * Con.java
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
import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler.PartialSubexpressionList;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;

import static com.avail.utility.Nulls.stripNull;

/**
 * This is a subtype of {@link Continuation1NotNull}, but it also tracks the
 * {@link PartialSubexpressionList} that describes the nesting of parse
 * expressions that led to this particular continuation.  A {@link
 * Continuation1NotNull} is also supplied to the constructor.  It will only
 * be invoked with a non-null argument.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class Con
implements Continuation1NotNull<CompilerSolution>
{
	/**
	 * A {@link PartialSubexpressionList} containing all enclosing
	 * incomplete expressions currently being parsed along this history.
	 */
	@InnerAccess
	final @Nullable
	PartialSubexpressionList superexpressions;

	/**
	 * Ensure this is not a root {@link PartialSubexpressionList} (i.e., its
	 * {@link #superexpressions} list is not {@code null}), then answer
	 * the parent list.
	 *
	 * @return The (non-null) parent superexpressions list.
	 */
	@InnerAccess PartialSubexpressionList superexpressions ()
	{
		return stripNull(superexpressions);
	}

	/**
	 * A {@link Continuation1NotNull} to invoke.
	 */
	private final Continuation1NotNull<CompilerSolution> innerContinuation;

	/**
	 * Construct a new {@code Con}.
	 *
	 * @param superexpressions
	 *        The enclosing partially-parsed expressions.
	 * @param innerContinuation
	 *        A {@link Continuation1NotNull} that will be invoked.
	 */
	@InnerAccess
	Con (
		final @Nullable PartialSubexpressionList superexpressions,
		final Continuation1NotNull<CompilerSolution> innerContinuation)
	{
		this.superexpressions = superexpressions;
		this.innerContinuation = innerContinuation;
	}

	@Override
	public void value (final CompilerSolution solution)
	{
		innerContinuation.value(solution);
	}
}
