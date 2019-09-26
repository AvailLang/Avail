/*
 * SimpleCompletionHandler.kt
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

package com.avail.io

import java.nio.channels.CompletionHandler

/**
 * A convenient [CompletionHandler] implementation that takes two lambdas at
 * construction, avoiding the hideous inner class notation.
 *
 * @param V
 *   The kind of values produced on success.
 * @param A
 *   A memento to pass back on success or failure.
 */
class SimpleCompletionHandler<V, A> : CompletionHandler<V, A>
{
	/** What to do on successful completion.  */
	private val completed: (V, A) -> Unit

	/** What to do upon failure.  */
	private val failed: (Throwable, A) -> Unit

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 *
	 * @param completed
	 *   What to do upon success.
	 * @param
	 *   failed What to do upon failure.
	 */
	constructor(completed: (V, A) -> Unit, failed: (Throwable, A) -> Unit)
	{
		this.completed = completed
		this.failed = failed
	}

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 * These lambdas take single arguments, for the common case that the
	 * "attachment" value can be ignored.
	 *
	 * @param completed
	 *   What to do upon success.
	 * @param failed
	 *   What to do upon failure.
	 */
	constructor(completed: (V) -> Unit, failed: (Throwable) -> Unit)
	{
		this.completed = { v, _ -> completed(v) }
		this.failed = { t, _ -> failed(t) }
	}

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 * These lambdas take three arguments, for the common case that the handler
	 * itself is needed inside one of the lambdas.
	 *
	 * @param completed
	 *   What to do upon success.
	 * @param failed
	 *   What to do upon failure.
	 */
	constructor(
		completed: (V, A, SimpleCompletionHandler<V, A>) -> Unit,
		failed: (Throwable, A, SimpleCompletionHandler<V, A>) -> Unit)
	{
		this.completed = { v, a -> completed(v, a, this) }
		this.failed = { t, a -> failed(t, a, this) }
	}

	override fun completed(result: V, attachment: A) =
		completed.invoke(result, attachment)

	override fun failed(exc: Throwable, attachment: A) =
		failed.invoke(exc, attachment)
}
