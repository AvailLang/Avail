/*
 * AvailRuntimeSupport.java
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

package com.avail;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.FiberDescriptor;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A static class for common Avail utility operations.
 */
public final class AvailRuntimeSupport
{
	/** Disallow instantiation. */
	private AvailRuntimeSupport ()
	{
		//Don't instantiate.
	}

	/**
	 * A general purpose {@linkplain Random pseudo-random number generator}.
	 */
	private static final Random rng = new Random();

	/**
	 * Answer a new value suitable for use as the {@linkplain AvailObject#hash()
	 * hash code} for an immutable {@linkplain AvailObject value}.
	 *
	 * <p>Note that the implementation uses opportunistic locking internally, so
	 * explicit synchronization here is not required. However, synchronization
	 * is included anyhow since that behavior is not part of Random's
	 * specification.</p>
	 *
	 * @return A 32-bit pseudo-random number.
	 */
	@ThreadSafe
	public static synchronized int nextHash ()
	{
		return rng.nextInt();
	}

	/**
	 * The source of {@linkplain FiberDescriptor fiber} identifiers.
	 */
	private static final AtomicInteger fiberIdGenerator =
		new AtomicInteger(1);

	/**
	 * Answer the next unused {@linkplain FiberDescriptor fiber} identifier.
	 * Fiber identifiers will not repeat for 2^32 invocations.
	 *
	 * @return The next fiber identifier.
	 */
	@ThreadSafe
	public static int nextFiberId ()
	{
		return fiberIdGenerator.getAndIncrement();
	}

	/**
	 * Capture the current time with nanosecond precision (but not necessarily
	 * accuracy).  If per-thread accounting is available, use it.
	 *
	 * @return The current value of the nanosecond counter, or if supported, the
	 *         number of nanoseconds of CPU time that the current thread has
	 *         consumed.
	 */
	public static long captureNanos ()
	{
		return System.nanoTime();
	}

	/**
	 * Utility class for wrapping a volatile counter that can be polled.
	 */
	public static class Clock
	{
		/**
		 * The current value of the monotonic counter.
		 */
		private final AtomicLong counter = new AtomicLong(0);

		/**
		 * Advance the clock.
		 */
		public void increment ()
		{
			counter.incrementAndGet();
		}

		/**
		 * Poll the monotonic counter of the clock.
		 *
		 * @return The current clock value.
		 */
		public long get ()
		{
			return counter.get();
		}
	}
}
