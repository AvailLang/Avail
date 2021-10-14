/*
 * AvailRuntimeSupport.kt
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
package avail

import avail.annotations.ThreadSafe
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.representation.AvailObject
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A static class for common Avail utility operations.
 */
object AvailRuntimeSupport
{
	/**
	 * A general purpose [pseudo-random number generator][Random].
	 */
	private val rng = Random()

	/**
	 * Answer a new value suitable for use as the [hash code][AvailObject.hash]
	 * for an immutable [value][AvailObject].
	 *
	 * Note that the implementation uses opportunistic locking internally, so
	 * explicit synchronization here is not required. However, synchronization
	 * is included anyhow since that behavior is not part of Random's
	 * specification.
	 *
	 * @return
	 *   A 32-bit pseudo-random number.
	 */
	@ThreadSafe
	@Synchronized
	fun nextHash(): Int = rng.nextInt()

	/**
	 * Answer a new *non-zero* value suitable for use as the [hash
	 * code][AvailObject.hash] for an immutable [value][AvailObject].
	 *
	 * @return
	 *   A 32-bit pseudo-random number that isn't zero (0).
	 */
	@ThreadSafe
	fun nextNonzeroHash(): Int
	{
		val hash = nextHash()
		return if (hash != 0) hash else 123456789
	}

	/**
	 * The source of [fiber][FiberDescriptor] identifiers.
	 */
	private val fiberIdGenerator = AtomicInteger(1)

	/**
	 * Answer the next unused [fiber][FiberDescriptor] identifier. Fiber
	 * identifiers will not repeat for 2^32 invocations.
	 *
	 * @return
	 *   The next fiber identifier.
	 */
	@Suppress("unused")
	@ThreadSafe
	fun nextFiberId(): Int = fiberIdGenerator.getAndIncrement()

	/**
	 * Capture the current time with nanosecond precision (but not necessarily
	 * accuracy).  If per-thread accounting is available, use it.
	 *
	 * @return
	 *   The current value of the nanosecond counter, or if supported, the
	 *   number of nanoseconds of CPU time that the current thread has consumed.
	 */
	fun captureNanos(): Long = System.nanoTime()

	/**
	 * Utility class for wrapping a volatile counter that can be polled.
	 */
	class Clock
	{
		/**
		 * The current value of the monotonic counter.
		 */
		private val counter = AtomicLong(0)

		/**
		 * Advance the clock.
		 */
		fun increment()
		{
			counter.incrementAndGet()
		}

		/**
		 * Poll the monotonic counter of the clock.
		 *
		 * @return
		 *   The current clock value.
		 */
		fun get(): Long = counter.get()
	}
}
