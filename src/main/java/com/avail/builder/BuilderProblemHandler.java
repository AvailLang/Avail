/*
 * BuilderProblemHandler.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.builder;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.problems.ProblemType;
import com.avail.io.SimpleCompletionHandler;
import com.avail.utility.evaluation.Continuation1NotNull;

import java.util.Formatter;

import static java.lang.String.format;

/**
 * The {@code BuilderProblemHandler} handles {@linkplain Problem problems}
 * encountered during a build.
 */
class BuilderProblemHandler implements ProblemHandler
{
	/** The {@link AvailBuilder} for which we handle problems. */
	private final AvailBuilder availBuilder;

	/**
	 * The {@linkplain Formatter pattern} with which to format {@linkplain
	 * Problem problem} reports. The pattern will be applied to the
	 * following problem components:
	 *
	 * <ol>
	 * <li>The {@linkplain ProblemType problem type}.</li>
	 * <li>The {@linkplain Problem#moduleName module name}, or {@code null}
	 *     if there is no specific module in context.</li>
	 * <li>The {@linkplain Problem#lineNumber line number} in the source at
	 *     which the problem occurs.</li>
	 * <li>A {@linkplain Problem#toString() general description} of the
	 *     problem.</li>
	 * </ol>
	 */
	final String pattern;

	/**
	 * Construct a new {@code BuilderProblemHandler}.  The supplied pattern
	 * is used to format the problem text as specified {@linkplain #pattern
	 * here}.
	 *
	 * @param availBuilder
	 *        The {@link AvailBuilder} for which we handle problems.
	 * @param pattern
	 *        The {@link String} with which to report the problem.
	 */
	BuilderProblemHandler (
		final AvailBuilder availBuilder,
		final String pattern)
	{
		this.availBuilder = availBuilder;
		this.pattern = pattern;
	}

	@Override
	public void handleGeneric (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		availBuilder.stopBuildReason("Build failed");
		final String formatted = format(
			pattern,
			problem.type,
			problem.moduleName,
			problem.lineNumber,
			problem.toString());
		availBuilder.textInterface.errorChannel().write(
			formatted,
			null,
			new SimpleCompletionHandler<Integer, Void>(
				r -> decider.value(false),
				t -> decider.value(false)));
	}
}
