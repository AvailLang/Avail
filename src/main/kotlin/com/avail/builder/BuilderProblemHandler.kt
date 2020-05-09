/*
 * BuilderProblemHandler.java
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

package com.avail.builder

import com.avail.compiler.problems.Problem
import com.avail.compiler.problems.ProblemHandler
import com.avail.compiler.problems.ProblemType
import com.avail.io.SimpleCompletionHandler
import java.lang.String.format
import java.util.*

/**
 * The `BuilderProblemHandler` handles [problems][Problem]
 * encountered during a build.
 *
 * @property availBuilder
 *   The [AvailBuilder] for which we handle problems.
 * @property pattern
 *   The [pattern][Formatter] with which to format [problem][Problem] reports.
 *   The pattern will be applied to the following problem components:
 *
 *   1. The [problem type][ProblemType].
 *   2. The [module name][Problem.moduleName], or `null` if there is no specific
 *   module in context.
 *   3. The [line number][Problem.lineNumber] in the source at which the problem
 *   occurs.
 *   4. A [general description][Problem.toString] of the problem.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `BuilderProblemHandler`.  The supplied pattern
 * is used to format the problem text as specified [here][pattern].
 *
 * @param availBuilder
 *   The [AvailBuilder] for which we handle problems.
 * @param pattern
 *   The [String] with which to report the problem.
 */
internal open class BuilderProblemHandler constructor(
	private val availBuilder: AvailBuilder,
	val pattern: String) : ProblemHandler
{
	@Suppress("RedundantLambdaArrow")
	override fun handleGeneric(problem: Problem, decider: (Boolean)->Unit)
	{
		availBuilder.stopBuildReason = "Build failed"
		val formatted = format(
			pattern,
			problem.type,
			problem.moduleName,
			problem.lineNumber,
			problem.toString())
		SimpleCompletionHandler<Int>(
			{ decider(false) },
			{ decider(false) }
		).guardedDo {
			availBuilder.textInterface.errorChannel.write(
				formatted, dummy, handler)
		}
	}
}
