/*
 * ProblemHandler.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler.problems

import com.avail.descriptor.phrases.PhraseDescriptor

/**
 * A [Problem] has a [ProblemType], indicating its basic nature and severity.
 * This helps a `ProblemHandler` decide how best to respond to the Problem, such
 * as deciding whether to continue parsing to discover subsequent problems or to
 * give up building the modules.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface ProblemHandler
{
	/**
	 * The corresponding [Problem] is not actually an unexpected
	 * condition, but it may still be useful to report the information to the
	 * user.  It's unclear whether this will actually be used for anything other
	 * than debugging the compiler.
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.INFORMATION].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleInformation(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * The corresponding [Problem] indicates a situation that is less than
	 * ideal.  A `ProblemHandler` may choose to present this warning, and then
	 * [continue&#32;compilation][Problem.continueCompilation].
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.WARNING].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleWarning(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * A [Problem] occurred while tracing a module's dependencies.
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.TRACE].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleTrace(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * A [Problem] occurred while parsing a module's body.  This includes both
	 * malformed tokens and assemblies of tokens that could not be successfully
	 * transformed into [phrases][PhraseDescriptor].
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.PARSE].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleParse(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * A [Problem] occurred while executing Avail code.  Typically this means an
	 * unhandled exception, or invoking a bootstrap primitive before the
	 * relevant exception handling mechanisms are in place.
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.EXECUTION].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleExecution(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * An internal [Problem] occurred in the virtual machine.  This should not
	 * happen, and is not the fault of the Avail code, but rather Yours Truly,
	 * the Avail virtual machine authors.
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.INTERNAL].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleInternal(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * An external [Problem] occurred.  This indicates a failure in something
	 * outside the control of the virtual machine, for example a disk read
	 * failure.
	 *
	 * @param problem
	 *   The problem whose type is [ProblemType.EXTERNAL].
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleExternal(problem: Problem, decider: (Boolean) -> Unit) =
		handleGeneric(problem, decider)

	/**
	 * One of the [ProblemType]-specific handler methods was invoked, but (1) it
	 * was not specifically overridden in the subclass, and (2) this method was
	 * not specifically overridden in the subclass.  Always fail in this
	 * circumstance.
	 *
	 * @param problem
	 *   The problem being handled generically.
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	@JvmDefault
	fun handleGeneric(problem: Problem, decider: (Boolean) -> Unit)
	{
		throw UnsupportedOperationException(
			"Failed to reimplement either a problem type-specific handler"
			+ " or handleGeneric")
	}

	/**
	 * Handle the specified [problem][Problem],
	 * [continuing][Problem.continueCompilation] or
	 * [aborting][Problem.abortCompilation] as indicated by the appropriate
	 * [problem&#32;type][ProblemType] handler.
	 *
	 * @param problem
	 *   A problem.
	 */
	@JvmDefault
	fun handle(problem: Problem) =
		problem.report(this) { shouldContinue ->
			if (shouldContinue)
			{
				problem.continueCompilation()
			}
			else
			{
				problem.abortCompilation()
			}
		}
}
