/*
 * ProblemsAtPosition.kt
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

package avail.compiler.problems

import avail.compiler.scanning.LexingState
import avail.utility.evaluation.Describer

/**
 * An aggregate of problems at a specific token.  It also includes the [String]
 * used to mark the location of the problem in the source text.
 *
 * @property lexingState
 *   The lexing position at which the problems occurred.
 * @property lexingStateAfterToken
 *   The [LexingState] right after the token where the problems occurred.  Use
 *   the longest token if the lexing is ambiguous.  This ensures the source code
 *   that gets output with an error doesn't cut off a multi-line token.
 * @property indicator
 *   The indicator [String] that marks the location of the problems in the
 *   source text.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ProblemsAtPosition`.
 *
 * @param lexingState
 *   The [LexingState] where the problems occurred.
 * @param lexingStateAfterToken
 *   The [LexingState] right after the token where the problems occurred.  Use
 *   the longest token if the lexing is ambiguous.  This ensures the source code
 *   that gets output with an error doesn't cut off a multi-line token.
 * @param indicator
 *   The [String] that marks the problems in the source.
 * @param describers
 *   The [List] of [Describer]s that describe the problems at the specified
 *   token.
 */
class ProblemsAtPosition internal constructor(
	private val lexingState: LexingState,
	internal val lexingStateAfterToken: LexingState,
	internal val indicator: String,
	describers: List<Describer>) : Comparable<ProblemsAtPosition>
{
	/** A list of [Describer]s able to describe the problems. */
	internal val describers = describers.toList()

	override fun compareTo(other: ProblemsAtPosition): Int =
		lexingState.position.compareTo(other.position)

	/** The one-based index into the source. */
	val position get() = lexingState.position

	/** The one-based line number. */
	val lineNumber get() = lexingState.lineNumber
}
