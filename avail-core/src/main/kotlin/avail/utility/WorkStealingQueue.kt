/*
 * WorkStealingQueue.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.utility

import avail.interpreter.execution.Interpreter
import java.util.AbstractQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * WIP – The intent is to use this queue to store tasks in a way that a
 * ThreadPoolExecutor can fetch them in a mostly-priority order, but with a
 * preference for keeping work local to a thread if the other threads already
 * have their own work to do.
 */
class WorkStealingQueue<E>
constructor(
	private val parallelism: Int
) : BlockingQueue<E>, AbstractQueue<E>()
{
	/**
	 * An array of regular [PriorityBlockingQueue]s, one dedicated to each
	 * worker thread of the thread pool that this instance is plugged into.
	 * New tasks from thread #N always gets added to queue #N, but other threads
	 * may steal the task (and run it) if they're otherwise idle.
	 */
	private val queues = Array(parallelism) { PriorityBlockingQueue<E>() }

	/**
	 * A Java [Object] to use as a monitor for threads to block on, waiting for
	 * a suitable task to become available.
	 */
	private val monitor = Object()

	/** Choose the queue on which to add a task. */
	private val localQueue get() = queues[Interpreter.currentIndexOrZero()]

	override val size: Int
		get() = queues.sumOf(PriorityBlockingQueue<E>::size)

	override fun drainTo(c: MutableCollection<in E>): Int =
		queues.sumOf { it.drainTo(c) }

	override fun drainTo(c: MutableCollection<in E>, maxElements: Int): Int
	{
		var remaining = maxElements
		for (queue in queues)
		{
			remaining -= queue.drainTo(c, remaining)
			if (remaining <= 0) break
		}
		return maxElements - remaining
	}

	override fun offer(element: E): Boolean
	{
		val queue = localQueue
		val changed = queue.add(element)
		// When the local queue reaches specific sizes, allow another thread to
		// wake up, if it's waiting.  If no thread is waiting, this has no
		// effect.
		//
		// The notify is necessary when the queue reaches size 1, since all
		// threads might be blocked, and we need to wake up one of them to scan
		// for this newly added task.
		//
		// If a task adds items fast enough that a queue gets to 2, we wake
		// another thread in the hope that it can steal one of these tasks and
		// start processing the resulting tree of tasks generated from it.
		//
		// If the local queue manages to get 5 elements, we wake up two other
		// threads, to accelerate processing.
		//
		// If we've queued about as many items in the local queue as there are
		// threads, wake them all up.
		when (queue.size)
		{
			1, 2 -> synchronized(monitor) { monitor.notify() }
			5 -> synchronized(monitor) {
				monitor.notify()
				monitor.notify()
			}
			parallelism + 1 -> synchronized(monitor) { monitor.notifyAll() }
		}
		return changed
	}

	override fun offer(e: E, timeout: Long, unit: TimeUnit): Boolean = offer(e)

	override fun poll(): E?
	{
		// First try the queue dedicated to the current thread.  When the number
		// of threads is stable (due to low, high, or constant load), the queue
		// for the current thread won't be under significant contention.
		localQueue.poll()?.let { return it }
		// Local queue didn't have anything.  Look for something to steal.
		for (q in queues)
		{
			q.poll()?.let { return it }
		}
		return null
	}

	override fun poll(timeout: Long, unit: TimeUnit): E?
	{
		// First try it without timeout.
		poll()?.let { return it }
		when (timeout)
		{
			0L ->
			{
				synchronized(monitor) {
					while (true)
					{
						monitor.wait()
						// Re-check, because someone woke us up.
						poll()?.let { return it }
					}
				}
			}
			else ->
			{
				val expiry = System.nanoTime() + unit.toNanos(timeout)
				synchronized(monitor) {
					while (true)
					{
						val nanos = when (timeout)
						{
							0L -> 0L
							else -> max(1L, expiry - System.nanoTime())
						}
						if (nanos == 0L && timeout > 0L)
						{
							// Actually timed out.
							return null
						}
						monitor.wait(
							nanos / 1_000_000,
							(nanos % 1_000_000).toInt())
						// Re-check, even if it just timed out.
						poll()?.let { return it }
					}
				}
			}
		}
	}

	override fun put(e: E)
	{
		offer(e)
	}

	override fun take(): E
	{
		// First try it without timeout.
		poll()?.let { return it }
		synchronized(monitor) {
			while (true)
			{
				monitor.wait()
				// Re-check, because someone woke us up.
				poll()?.let { return it }
			}
		}
	}

	override fun remainingCapacity(): Int = Int.MAX_VALUE

	override fun remove(element: E): Boolean = queues.any { it.remove(element) }

	override fun iterator(): MutableIterator<E>
	{
		// Not efficient, but technically correct.
		val list = mutableListOf<E>()
		queues.forEach { q -> q.forEach { e -> list.add(e) } }
		return list.iterator()
	}

	override fun peek(): E? =
		localQueue.peek() ?: queues.firstNotNullOfOrNull { it.peek() }
}

