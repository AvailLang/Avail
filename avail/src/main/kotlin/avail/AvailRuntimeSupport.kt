/*
 * AvailRuntimeSupport.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.descriptor.fiber.FiberDescriptor.Companion.compilerPriority
import avail.descriptor.representation.AvailObject
import avail.utility.ifZero
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.withLock

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
	 * Answer a new *non-zero* value suitable for use as the
	 * [hash][AvailObject.hash] code for an immutable [value][AvailObject].
	 *
	 * @return
	 *   A 32-bit pseudo-random number that isn't zero (0).
	 */
	@ThreadSafe
	fun nextNonzeroHash(): Int = nextHash().ifZero { 123456789 }

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

	/**
	 * The basic states of an [AvailLazyFuture].
	 */
	private enum class AvailLazyFutureState
	{
		/** The value has not yet been requested, and is not being computed. */
		UNSTARTED,

		/** The value is being computed. */
		RUNNING,

		/** The value has already been computed. */
		FINISHED;
	}

	/**
	 * An implementation of a lazy future, using the [runtime]'s execution pool.
	 * If the value is never requested, it never not run the [computation].
	 * Otherwise, the first invocation of [withValue] causes the [computation]
	 * to run in a task (when it's safe to run interpreters).
	 *
	 * When the [computation] completes, any actions queued in the [waiters]
	 * list by [withValue] will run, inside their own tasks.
	 *
	 * @constructor
	 * Create an [AvailLazyFuture] which runs the [computation], once, if
	 * requested.
	 *
	 * @property runtime
	 *   The [AvailRuntime] responsible for executing the computation and
	 *   actions.
	 * @property priority
	 *   The priority at which to run tasks.
	 * @property computation
	 *   The function to evaluate if anyone requests the lazy future's value.
	 *   The function takes another function, supplied by the lazy future's
	 *   internals, responsible for running any waiting actions.
	 */
	class AvailLazyFuture<T>
	constructor(
		val runtime: AvailRuntime,
		val priority: Int = compilerPriority,
		val computation: ((T)->Unit)->Unit)
	{
		/**
		 * The lock that must be held when examining or manipulating the
		 * future's internal state.
		 */
		private val mutex = ReentrantLock()

		/** Which state the future is in. Only access when holding the mutex. */
		@GuardedBy("mutex")
		private var state = AvailLazyFutureState.UNSTARTED

		/** The computed value, if any. Only access when holding the mutex. */
		@GuardedBy("mutex")
		private var value: T? = null

		/**
		 * The queued actions awaiting completion of the [computation]. Only
		 * access when holding the mutex.
		 */
		@GuardedBy("mutex")
		private var waiters: MutableList<(T)->Unit>? = null

		/**
		 * Ensure the given action will run with the result of the
		 * [computation]. If this is the first request for this future, launch a
		 * task to run the [computation], evaluating all [waiters] when
		 * complete.  If the value has not yet been computed, add the action to
		 * the [waiters].  If the value has been computed, just run the action.
		 * Note that all evaluation takes place while interpreters may run.
		 */
		fun withValue(action: (T)->Unit) = mutex.withLock {
			when (state)
			{
				AvailLazyFutureState.UNSTARTED ->
				{
					// Queue the action.
					state = AvailLazyFutureState.RUNNING
					assert(waiters === null)
					waiters = mutableListOf(action)
					// Start computing the value.
					runtime.whenRunningInterpretersDo(priority) {
						computation { newValue ->
							val waitersToRun: List<(T)->Unit>
							mutex.withLock {
								assert(state == AvailLazyFutureState.RUNNING)
								state = AvailLazyFutureState.FINISHED
								value = newValue
								waitersToRun = waiters!!
								waiters = null
							}
							waitersToRun.forEach { waiter ->
								runtime.whenRunningInterpretersDo(priority) {
									waiter(newValue)
								}
							}
						}
					}
				}
				// Just queue the action.
				AvailLazyFutureState.RUNNING -> waiters!!.add(action)
				// Just run the action.
				AvailLazyFutureState.FINISHED ->
					runtime.whenRunningInterpretersDo(priority) {
						action(value!!)
					}
			}
		}
	}
}
