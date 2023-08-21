/*
 * WorkStealingQueue.kt
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

package avail.utility

import avail.interpreter.execution.Interpreter
import avail.utility.structures.LeftistHeap
import avail.utility.structures.leftistLeaf
import java.util.AbstractQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A [WorkStealingQueue] tracks [parallelism] separate subqueues of tasks.  Each
 * [Interpreter] feeds and consumes a distinct subqueue dedicated to that
 * interpreter thread, ideally without contention from other threads.  When a
 * subqueue is exhausted, the requesting thread will examine the other threads'
 * queues and steal one for itself.  Only at that point will contention occur.
 *
 * Each subqueue is maintained in priority order, but the aggregate collection
 * of queues cannot be considered strictly ordered.
 */
class WorkStealingQueue<E : Comparable<E>>
constructor(
	private val parallelism: Int
) : BlockingQueue<E>, AbstractQueue<E>()
{
	/**
	 * An array of wait-free priority queues, one dedicated to each worker
	 * thread of the thread pool that this instance is plugged into. New tasks
	 * from thread #N always gets added to queue #N, but other threads may steal
	 * the task (and run it) if they're otherwise idle.
	 */
	private val queues = Array(parallelism) {
		AtomicReference(leftistLeaf<E>())
	}

	/**
	 * A Java [Object] to use as a monitor for threads to block on, waiting for
	 * a suitable task to become available.
	 */
	private val monitor = Object()

	/** Choose the queue on which to add a task. */
	private val localQueue get() = queues[Interpreter.currentIndexOrZero()]

	override val size: Int
		get() = queues.sumOf { it.get().size }

	override fun drainTo(c: MutableCollection<in E>): Int =
		queues.sumOf {
			val heap = it.getAndSet(leftistLeaf())
			c.addAll(heap.toList())
			heap.size
		}

	override fun drainTo(c: MutableCollection<in E>, maxElements: Int): Int
	{
		var remaining = maxElements
		outer@ for (queue in queues)
		{
			while (true)
			{
				if (remaining <= 0) break@outer
				val oldHeap = queue.get()
				if (oldHeap.isEmpty) break
				if (oldHeap.size <= remaining)
				{
					// Try to remove them all.
					if (queue.compareAndSet(oldHeap, leftistLeaf()))
					{
						// We removed them all.
						c.addAll(oldHeap.toList())
						remaining -= oldHeap.size
						break
					}
				}
				else
				{
					// Try to remove them one at a time.
					val newHeap = oldHeap.withoutFirst
					if (queue.compareAndSet(oldHeap, newHeap))
					{
						c.add(oldHeap.first)
						remaining--
						// Fall through to do more if they're available.
					}
				}
			}
		}
		return maxElements - remaining
	}

	override fun offer(element: E): Boolean
	{
		val queue = localQueue
		lateinit var oldHeap: LeftistHeap<E>
		while (true)
		{
			oldHeap = queue.get()
			if (queue.compareAndSet(oldHeap, oldHeap.with(element))) break
		}
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
		when (oldHeap.size + 1)
		{
			1, 2 -> synchronized(monitor) { monitor.notify() }
			5 -> synchronized(monitor) {
				monitor.notify()
				monitor.notify()
			}
			parallelism + 1 -> synchronized(monitor) { monitor.notifyAll() }
		}
		return true
	}

	override fun offer(e: E, timeout: Long, unit: TimeUnit): Boolean = offer(e)

	override fun poll(): E?
	{
		// First try the queue dedicated to the current thread.  When the number
		// of threads is stable (due to low, high, or constant load), the queue
		// for the current thread won't be under significant contention.
		val queue = localQueue
		while (true)
		{
			val heap = queue.get()
			if (heap.isEmpty) break
			if (queue.compareAndSet(heap, heap.withoutFirst)) return heap.first
		}
		// Local queue didn't have anything.  Look for something to steal.
		for (q in queues)
		{
			while (true)
			{
				val heap = q.get()
				if (heap.isEmpty) break
				if (q.compareAndSet(heap, heap.withoutFirst)) return heap.first
			}
		}
		return null
	}

	override fun poll(timeout: Long, unit: TimeUnit): E?
	{
		assert(timeout >= 0)
		// First try it without timeout.
		poll()?.let { return it }
		if (timeout == 0L)
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
		val expiry = System.nanoTime() + unit.toNanos(timeout)
		synchronized(monitor) {
			while (true)
			{
				val nanos = expiry - System.nanoTime()
				if (nanos <= 0L)
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

	override fun remove(element: E): Boolean = queues.any { queue ->
		while (true)
		{
			val oldHeap = queue.get()
			val newHeap = oldHeap.without(element)
			if (newHeap === oldHeap) break
			if (queue.compareAndSet(oldHeap, newHeap)) return true
		}
		return false
	}

	override fun iterator(): MutableIterator<E>
	{
		// Not efficient, but technically correct.
		val list = mutableListOf<E>()
		queues.forEach { q -> list.addAll(q.get().toList()) }
		return list.iterator()
	}

	override fun peek(): E?
	{
		val heap = localQueue.get()
		if (!heap.isEmpty) return heap.first
		queues.forEach { q ->
			val h = q.get()
			if (!h.isEmpty) return h.first
		}
		return null
	}
}
