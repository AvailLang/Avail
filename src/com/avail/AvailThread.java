/*
 * AvailThread.java
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

package com.avail;

import com.avail.interpreter.Interpreter;

import javax.annotation.Nullable;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * An {@code AvailThread} is a {@linkplain Thread thread} managed by a
 * particular {@linkplain AvailRuntime Avail runtime}. Instances may obtain the
 * managing runtime through the static accessor {@link AvailRuntime#currentRuntime()}.
 * New instances will be created as necessary by an Avail runtime's {@linkplain
 * ScheduledThreadPoolExecutor executor}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailThread
extends Thread
{
	/**
	 * The {@linkplain AvailRuntime Avail runtime} that owns this {@linkplain
	 * AvailThread thread}.
	 */
	public final AvailRuntime runtime;

	/**
	 * The {@linkplain Interpreter interpreter} permanently bound to this
	 * {@linkplain AvailThread thread}.
	 */
	public final Interpreter interpreter;

	/**
	 * Construct a new {@code AvailThread}.
	 *
	 * @param runnable
	 *        The {@code Runnable runnable} that the new thread should execute.
	 * @param interpreter
	 *        The {@link Interpreter} that this thread will temporarily bind to
	 *        fibers while they are running in this thread.
	 */
	AvailThread (
		final Runnable runnable,
		final Interpreter interpreter)
	{
		super(runnable, "AvailThread-" + interpreter.interpreterIndex);
		this.runtime = interpreter.runtime();
		this.interpreter = interpreter;
	}

	/**
	 * Answer the current {@link Thread} strengthened to an {@code AvailThread},
	 * or {@code null} if it isn't actually an {@code AvailThread}.
	 *
	 * @return The current {@code AvailThread}.
	 */
	public static @Nullable AvailThread currentOrNull ()
	{
		final Thread current = Thread.currentThread();
		if (current instanceof AvailThread)
		{
			return (AvailThread) current;
		}
		else
		{
			return null;
		}
	}

	/**
	 * Answer the current {@link Thread} strengthened to an {@code AvailThread},
	 * or throw {@link ClassCastException} if it isn't actually an {@code
	 * AvailThread}.
	 *
	 * @return The current {@code AvailThread}.
	 * @throws ClassCastException
	 *         If the current thread isn't an {@code AvailThread}.
	 */
	public static AvailThread current ()
	throws ClassCastException
	{
		return (AvailThread) Thread.currentThread();
	}
}
