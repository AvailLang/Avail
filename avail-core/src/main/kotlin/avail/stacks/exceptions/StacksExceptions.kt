/*
 * StacksException.kt
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

package avail.stacks.exceptions

import avail.stacks.comment.CommentBuilder
import avail.stacks.scanner.AbstractStacksScanner
import avail.stacks.scanner.StacksScanner

/**
 * A `StacksException` is an abstract [RuntimeException] that defines common
 * state amongst exceptions in Stacks.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
abstract class StacksException constructor(message: String)
	: RuntimeException(message)
{
	/** The name of the module that the failure occurred. */
	abstract val moduleName: String
}

/**
 * An exception that occurs in a [CommentBuilder]
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property failedBuilder
 *   The error [builder][CommentBuilder] in question
 *
 * @constructor
 * Construct a new `StacksCommentBuilderException`.
 *
 * @param message
 *   The error message.
 * @param failedBuilder
 *   The error [builder][CommentBuilder] in question
 */
class StacksCommentBuilderException constructor(
	message: String, private val failedBuilder: CommentBuilder)
	: StacksException(message)
{
	override val moduleName: String get() = failedBuilder.moduleName
}

/**
 * An Stacks scanner exception
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property failedScanner
 *   The AbstractStacksScanner that failed, positioned to the failure point.
 * @constructor
 * Construct a new [StacksScannerException].
 *
 * @param message
 *   The error message indicating why the [StacksScanner] failed.
 * @param failedScanner
 *   The AbstractStacksScanner that failed, positioned to the failure point.
 */
class StacksScannerException constructor(
	message: String,
	private val failedScanner: AbstractStacksScanner)
	: StacksException(message)
{
	override val moduleName: String
		get() = failedScanner.moduleName

	/**
	 * The file position at which the [StacksScanner] failed.
	 */
	val failurePosition: Int = failedScanner.position

	/**
	 * The line number at which the [StacksScanner] failed.
	 */
	val failureLineNumber: Int = failedScanner.lineNumber
}
