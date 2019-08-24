/*
 * SimpleCompletionHandler.java
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

package com.avail.io;

import com.avail.utility.evaluation.Continuation3NotNullNullNotNull;

import java.nio.channels.CompletionHandler;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A convenient {@link CompletionHandler} implementation that takes two lambdas
 * at construction, avoiding the hideous inner class notation.
 *
 * @param <V> The kind of values produced on success.
 * @param <A> A memento to pass back on success or failure.
 */
public final class SimpleCompletionHandler<V, A>
implements CompletionHandler<V, A>
{
	/** What to do on successful completion. */
	private final BiConsumer<V, A> completed;

	/** What to do upon failure. */
	private final BiConsumer<Throwable, A> failed;

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 *
	 * @param completed What to do upon success.
	 * @param failed What to do upon failure.
	 */
	public SimpleCompletionHandler(
		final BiConsumer<V, A> completed,
		final BiConsumer<Throwable, A> failed)
	{
		this.completed = completed;
		this.failed = failed;
	}

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 * These lambdas take single arguments, for the common case that the
	 * "attachment" value can be ignored.
	 *
	 * @param completed What to do upon success.
	 * @param failed What to do upon failure.
	 */
	public SimpleCompletionHandler(
		final Consumer<V> completed,
		final Consumer<Throwable> failed)
	{
		this.completed = (v, a) -> completed.accept(v);
		this.failed = (t, a) -> failed.accept(t);
	}

	/**
	 * Create a completion handler with the given completed and failed lambdas.
	 * These lambdas take three arguments, for the common case that the handler
	 * itself is needed inside one of the lambdas.
	 *
	 * @param completed What to do upon success.
	 * @param failed What to do upon failure.
	 */
	public SimpleCompletionHandler(
		final Continuation3NotNullNullNotNull
			<V, A, SimpleCompletionHandler<V, A>> completed,
		final Continuation3NotNullNullNotNull
			<Throwable, A, SimpleCompletionHandler<V, A>> failed)
	{
		this.completed = (v, a) -> completed.value(v, a, this);
		this.failed = (t, a) -> failed.value(t, a, this);
	}

	@Override
	public void completed (final V result, final A attachment)
	{
		completed.accept(result, attachment);
	}

	@Override
	public void failed (final Throwable exc, final A attachment)
	{
		failed.accept(exc, attachment);
	}
}
