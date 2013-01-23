/**
 * AvailThread.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

/**
 * An {@code AvailThread} is a {@linkplain Thread thread} managed by a
 * particular {@linkplain AvailRuntime Avail runtime}. Instances may obtain the
 * managing runtime through the static accessor {@link AvailRuntime#current()}.
 * New instances should be obtained through the factory method
 * {@link AvailRuntime#newThread(Runnable)}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailThread
extends Thread
{
	/**
	 * Construct a new {@link AvailThread}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime Avail runtime} responsible for the
	 *        new thread.
	 * @param runnable
	 *        The {@code Runnable runnable} that the new thread should execute.
	 */
	AvailThread (
		final AvailRuntime runtime,
		final Runnable runnable)
	{
		super(runnable);
		AvailRuntime.setCurrent(runtime);
		setDaemon(false);
		setName(String.format(
			"%s-%d [from %s]",
			this.getClass().getSimpleName(),
			System.identityHashCode(this),
			Thread.currentThread().getName()));
	}
}
