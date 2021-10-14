/*
 * Problem.kt
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

import avail.builder.ModuleName
import avail.descriptor.character.CharacterDescriptor
import java.nio.charset.Charset
import java.text.MessageFormat

/**
 * A `Problem` is produced when encountering an unexpected or less than ideal
 * situation during compilation.  Within an interactive system, the problem is
 * presumably presented to the user in some manner, whereas in batch usage
 * multiple problems may simply be collected and later presented in aggregate.
 *
 * Subclasses (typically anonymous) may override [continueCompilation] and
 * [abortCompilation] to specify how a particular problem site can continue or
 * abort compilation, respectively. It is the responsibility of any client
 * problem handler to *decide* whether to continue compiling or abort.  By
 * default, `continueCompilation()` simply invokes `abortCompilation()`.
 *
 * @property moduleName
 *   The [unresolved, canonical name][ModuleName] of the module in which the
 *   problem occurred.
 * @property lineNumber
 *   The one-based line number within the file in which the problem occurred.
 *   This may be approximate. If the problem involves the entire file (file not
 *   found, no read access, etc.), a line number of `0` can indicate this.
 * @property characterInFile
 *   The approximate location of the problem within the source file as a
 *   zero-based subscript of the full-Unicode
 *   [code&#32;points][CharacterDescriptor] of the file.
 *   [Surrogate&#32;pairs][Character.isSurrogate] are treated as a single code
 *   point. It is *strongly* recommended that Avail source files are always
 *   encoded in the UTF-8 [character&#32;set][Charset].  The current compiler as
 *   of 2014.01.26 *requires* source files to be in UTF-8 encoding.
 * @property type
 *   The [type][ProblemType] of problem that was encountered.
 * @property messagePattern
 *   A [String] summarizing the issue.  This string will have [MessageFormat]
 *   substitution applied to it, using the supplied [arguments].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Problem`.
 *
 * @param moduleName
 *   The name of the module in which the problem was encountered, or `null` if
 *   no module is in context.
 * @param lineNumber
 *   The one-based line number on which the problem occurred, or zero if there
 *   is no suitable line to blame.
 * @param characterInFile
 *   The zero-based code point position in the file.  Surrogate pairs are
 *   treated as a single code point.
 * @param type
 *   The [ProblemType] that classifies this problem.
 * @param messagePattern
 *   A [String] complying with the [MessageFormat] pattern specification.
 * @param arguments
 *   The arguments with which to parameterize the messagePattern.
 */
abstract class Problem constructor(
	val moduleName: ModuleName?,
	val lineNumber: Int,
	val characterInFile: Long,
	val type: ProblemType,
	private val messagePattern: String,
	vararg arguments: Any)
{
	/**
	 * The parameters to be applied to the [messagePattern], which is expected
	 * to comply with the grammar of a [MessageFormat].
	 */
	private val arguments: Array<out Any> = arguments.clone()

	/**
	 * Report this problem to the [handler][ProblemHandler]. Answer whether an
	 * attempt should be made to continue parsing past this problem.
	 *
	 * @param handler
	 *   The problem handler.
	 * @param decider
	 *   How to [continue][Problem.continueCompilation] or
	 *   [abort][Problem.abortCompilation] compilation. Accepts a
	 *   [boolean][Boolean] that is `true` iff compilation should continue.
	 */
	internal fun report(handler: ProblemHandler, decider: (Boolean) -> Unit) =
		type.report(this, handler, decider)

	/**
	 * Attempt to continue compiling past this problem.  If continuing to
	 * compile is inappropriate or impossible for the receiver, then as a
	 * convenience, this method simply calls [abortCompilation].
	 */
	open fun continueCompilation() = abortCompilation()

	/**
	 * Give up compilation.  Note that either [continueCompilation] or this
	 * method must be invoked by code handling `Problem`s.
	 */
	abstract fun abortCompilation()

	override fun toString(): String =
		MessageFormat.format(messagePattern, *arguments)
}
