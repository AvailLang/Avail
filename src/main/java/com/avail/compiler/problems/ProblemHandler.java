/*
 * ProblemHandler.java
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

package com.avail.compiler.problems;

import com.avail.descriptor.PhraseDescriptor;
import com.avail.utility.evaluation.Continuation1NotNull;


/**
 * A {@link Problem} has a {@link ProblemType}, indicating its basic nature
 * and severity.  This helps a {@code ProblemHandler} decide how best to respond
 * to the Problem, such as deciding whether to continue parsing to discover
 * subsequent problems or to give up building the modules.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("AbstractClassWithoutAbstractMethods")
public abstract class ProblemHandler
{
	/**
	 * The corresponding {@link Problem} is not actually an unexpected
	 * condition, but it may still be useful to report the information to the
	 * user.  It's unclear whether this will actually be used for anything other
	 * than debugging the compiler.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#INFORMATION}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleInformation (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * The corresponding {@link Problem} indicates a situation that is less than
	 * ideal.  A {@link ProblemHandler} may choose to present this warning, and
	 * then {@linkplain Problem#continueCompilation() continue compilation}.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#WARNING}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleWarning (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * A {@link Problem} occurred while tracing a module's dependencies.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#TRACE}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleTrace (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * A {@link Problem} occurred while parsing a module's body.  This includes
	 * both malformed tokens and assemblies of tokens that could not be
	 * successfully transformed into {@linkplain PhraseDescriptor phrases}.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#PARSE}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleParse (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * A {@link Problem} occurred while executing Avail code.  Typically this
	 * means an unhandled exception, or invoking a bootstrap primitive before
	 * the relevant exception handling mechanisms are in place.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#EXECUTION}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleExecution (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * An internal {@link Problem} occurred in the virtual machine.  This should
	 * not happen, and is not the fault of the Avail code, but rather Yours
	 * Truly, the Avail virtual machine authors.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#INTERNAL}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleInternal (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * An external {@link Problem} occurred.  This indicates a failure in
	 * something outside the control of the virtual machine, for example a disk
	 * read failure.
	 *
	 * @param problem
	 *        The problem whose type is {@link ProblemType#EXTERNAL}.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleExternal (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		handleGeneric(problem, decider);
	}

	/**
	 * One of the {@link ProblemType}-specific handler methods was invoked, but
	 * (1) it was not specifically overridden in the subclass, and (2) this
	 * method was not specifically overridden in the subclass.  Always fail in
	 * this circumstance.
	 *
	 * @param problem
	 *        The problem being handled generically.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	protected void handleGeneric (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider)
	{
		throw new UnsupportedOperationException(
			"Failed to reimplement either a problem type-specific handler"
				+ " or handleGeneric");
	}

	/**
	 * Handle the specified {@linkplain Problem problem}, {@linkplain
	 * Problem#continueCompilation() continuing} or {@linkplain
	 * Problem#abortCompilation() aborting} as indicated by the appropriate
	 * {@linkplain ProblemType problem type} handler.
	 *
	 * @param problem
	 *        A problem.
	 */
	public final void handle (final Problem problem)
	{
		problem.report(
			this,
			shouldContinue ->
			{
				if (shouldContinue)
				{
					problem.continueCompilation();
				}
				else
				{
					problem.abortCompilation();
				}
			});
	}
}
