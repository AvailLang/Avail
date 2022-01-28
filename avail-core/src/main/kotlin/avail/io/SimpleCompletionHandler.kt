/*
 * SimpleCompletionHandler.kt
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

package avail.io

import java.nio.channels.CompletionHandler

/**
 * A convenient [CompletionHandler] implementation that takes two lambdas at
 * construction, avoiding the hideous inner class notation.
 *
 * @constructor
 *
 * @property completed
 *   What to do on successful completion.
 * @property failed
 *   What to do upon failure.
 * @param V
 *   The kind of values produced on success.
 */
class SimpleCompletionHandler<V> constructor (
	private val completed: SuccessHelper<V>.() -> Unit,
	private val failed: FailureHelper<V>.() -> Unit
) : CompletionHandler<V, Unit>
{
	override fun completed(result: V, attachment: Unit) =
		SuccessHelper(result, this).completed()

	override fun failed(exc: Throwable, attachment: Unit) =
		FailureHelper(exc, this).failed()

	/**
	 * Perform the specified I/O operation, guarded by a `try/catch` that
	 * invokes the same failure handler as the operation itself would in the
	 * event of an asynchronous failure.
	 *
	 * @param op
	 *   The I/O operation.
	 */
	fun guardedDo(op: GuardHelper<V>.() -> Unit) =
		try
		{
			GuardHelper(this).op()
		}
		catch (e: Throwable)
		{
			FailureHelper(e, this).failed()
		}

	companion object {
		/**
		 * A helper syntax class for completion successes.
		 *
		 * @constructor
		 * @property value
		 *   The value produced by the successful completion handler.
		 * @property handler
		 *   The current completion handler itself.
		 */
		class SuccessHelper<V>(
			val value: V,
			val handler: SimpleCompletionHandler<V>)

		/**
		 * A helper syntax class for completion failures.
		 *
		 * @constructor
		 * @property throwable
		 *   The exception that indicates failure.
		 * @property
		 *   The current [SimpleCompletionHandler] itself.
		 */
		class FailureHelper<V>(
			val throwable: Throwable,
			val handler: SimpleCompletionHandler<V>)

		/**
		 * A helper syntax class for [guardedDo] invocations.
		 *
		 * @constructor
		 * @property handler
		 *   The current [SimpleCompletionHandler] itself.
		 */
		class GuardHelper<V>(
			val handler: SimpleCompletionHandler<V>)
	}
}
