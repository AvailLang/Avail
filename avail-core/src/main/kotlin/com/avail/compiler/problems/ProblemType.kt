/*
 * ProblemType.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
 * A [Problem] has a `ProblemType`, indicating its basic nature
 * and severity.  This helps a [ProblemHandler] decide how best to respond
 * to the Problem, such as deciding whether to proceed and look for subsequent
 * problems or to give up building the modules.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class ProblemType
{
	/**
	 * The corresponding [Problem] is not actually an unexpected condition, but
	 * it may still be useful to report the information to the user.  It's
	 * unclear whether this will actually be used for anything other than
	 * debugging the compiler.
	 */
	INFORMATION
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleInformation(problem, decider)
		}
	},

	/**
	 * The corresponding [Problem] indicates a situation that is less than
	 * ideal.  A [ProblemHandler] may choose to present this warning, and then
	 * [continue&#32;compilation][Problem.continueCompilation].
	 */
	WARNING
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleWarning(problem, decider)
		}
	},

	/**
	 * A [Problem] occurred while tracing a module's dependencies.  This
	 * condition only indicates a narrow range of problems, such as unresolved
	 * or recursive dependencies.  Syntax errors in the module header that
	 * preclude a correct trace are treated as [PARSE] problems.
	 */
	TRACE
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleTrace(problem, decider)
		}
	},

	/**
	 * A [Problem] occurred while parsing a module's body.  This includes both
	 * malformed tokens and assemblies of tokens that could not be successfully
	 * transformed into [phrases][PhraseDescriptor].
	 */
	PARSE
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleParse(problem, decider)
		}
	},

	/**
	 * A [Problem] occurred while executing Avail code.  Typically this means an
	 * unhandled exception, or invoking a bootstrap primitive before the
	 * relevant exception handling mechanisms are in place.  This should be
	 * treated as a fatal problem.
	 */
	EXECUTION
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleExecution(problem, decider)
		}
	},

	/**
	 * An internal [Problem] occurred in the virtual machine.  This should not
	 * happen, and is not the fault of the Avail code, but rather Yours Truly,
	 * the Avail virtual machine authors.
	 */
	INTERNAL
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleInternal(problem, decider)
		}
	},

	/**
	 * An external [Problem] occurred.  This indicates a failure in something
	 * outside the control of the virtual machine, for example a disk read
	 * failure.
	 */
	EXTERNAL
	{
		override fun report(
			problem: Problem,
			handler: ProblemHandler,
			decider: (Boolean) -> Unit)
		{
			assert(problem.type === this)
			handler.handleExternal(problem, decider)
		}
	};

	/**
	 * Report the given [Problem] to the [ProblemHandler]. The problem's type
	 * must be the receiver.
	 *
	 * @param problem
	 *   The problem to report.
	 * @param handler
	 *   The problem handler to notify.
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	internal abstract fun report(
		problem: Problem,
		handler: ProblemHandler,
		decider: (Boolean) -> Unit)
}
