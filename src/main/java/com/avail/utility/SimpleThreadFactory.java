/*
 * SimpleThreadFactory.java
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

package com.avail.utility;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ThreadFactory} that creates {@link Thread}s named with a prefix,
 * a dash, and a practically unique counter.
 */
public final class SimpleThreadFactory implements ThreadFactory
{
	/**
	 * The prefix to prepend to the counter to create {@link Thread} names.
	 */
	private final String prefixWithDash;

	/**
	 * A counter for making practically unique {@link Thread} names. An
	 * overflow of the counter simply makes debugging slightly more
	 * confusing.
	 */
	private final AtomicInteger counter = new AtomicInteger();

	/**
	 * Create a new thread factor using the given prefix.
	 *
	 * @param prefix
	 *        The prefix to use for {@link Thread} names.
	 */
	public SimpleThreadFactory (final String prefix)
	{
		prefixWithDash = prefix + "-";
	}

	@Override
	public Thread newThread (final Runnable runnable)
	{
		return new Thread(
			runnable, prefixWithDash + counter.incrementAndGet());
	}
}
