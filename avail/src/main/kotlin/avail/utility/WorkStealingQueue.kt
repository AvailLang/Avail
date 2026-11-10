/*
 * WorkStealingQueue.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A [WorkStealingQueue] tracks [parallelism] separate subqueues of tasks.  Each
 * [Interpreter] feeds and consumes a distinct subqueue dedicated to that
 * interpreter thread, ideally without contention from other threads.  When a
 * subqueue is exhausted, the requesting thread will examine the other threads'
 * queues and steal some tasks for itself.  Only at that point will contention
 * occur.
 *
 * Each subqueue is a persistent [LeftistHeap] maintained in priority order,
 * but the aggregate collection of queues cannot be considered strictly
 * ordered.  A thief takes the victim's highest priority task, which it runs,
 * plus one of the two subheaps under it, which it merges into its own heap.
 * The other subheap stays with the victim.  The roots of both subheaps are
 * among the victim's second and third highest priority tasks, so stealing
 * respects the priority scheme roughly.
 *
 * A thread that finds no task anywhere blocks on [idlePermits].  Whenever a
 * thread makes tasks newly visible to other threads, by [offer]ing one or by
 * merging a stolen subheap into its own heap, it [signal]s that many permits,
 * capped at [parallelism] permits available.  A permit is retained even when
 * no thread is blocked yet, so a thread that has scanned the heaps, found
 * nothing, and is about to block still gets through and scans again.  That
 * closes the window in which a stolen subheap is held only in the thief's
 * locals, invisible to other thieves.  Since a woken thief steals roughly half
 * of a heap and signals in proportion to what it stole, a burst of tasks
 * spreads across the idle threads in about log₂([parallelism]) rounds, without
 * the thread that produced the burst having to wake them all itself.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property parallelism
 *   The number of subqueues, one per worker thread of the thread pool that
 *   this queue is plugged into.  It also caps the permits available in
 *   [idlePermits].
 * @constructor
 *   Construct a new [WorkStealingQueue] with the given number of subqueues.
 * @param parallelism
 *   The number of subqueues, one per worker thread.
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
	 * The [Semaphore] on which a thread blocks when it finds no task in any
	 * subqueue.  Its permits are released by [signal], and each permit lets one
	 * thread through to scan the subqueues again.  The semaphore's own queue of
	 * blocked threads decides which thread, if any, is woken.
	 */
	private val idlePermits = Semaphore(0)

	/** Choose the queue on which to add a task. */
	private val localQueue get() = queues[Interpreter.currentIndexOrZero()]

	/**
	 * Release up to [count] permits on [idlePermits], one for each task just
	 * made visible to other threads, but stop once [parallelism] permits are
	 * available.  At that point, enough threads will pass through
	 * [idlePermits] without blocking to find the tasks.
	 *
	 * Reading the permit count and releasing a permit is not atomic, so
	 * concurrent signalers can overshoot the cap.  Since each release is
	 * preceded by its own check, each signaler overshoots by at most one
	 * permit, so with [parallelism] worker threads signaling, at most
	 * 2 × [parallelism] permits are available.  The first [parallelism] are
	 * designed to let each thread scan once more, to catch tasks in flux, such
	 * as a stolen subheap that is momentarily visible to no other thread.  The
	 * overshoot costs at most about one more futile scan per thread.
	 *
	 * @param count
	 *   The number of tasks just made visible to other threads.
	 */
	private fun signal(count: Int)
	{
		repeat(count) {
			if (idlePermits.availablePermits() >= parallelism) return
			idlePermits.release()
		}
	}

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
		while (true)
		{
			val oldHeap = queue.get()
			if (queue.compareAndSet(oldHeap, oldHeap.with(element))) break
		}
		// The task is now visible to thieves, so let a thread through to find
		// it.
		signal(1)
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
				val taken = heap.leftSubheap
				val remains = heap.rightSubheap
				// We found something.  First try to replace the victim heap
				// with the remains.
				if (q.compareAndSet(heap, remains))
				{
					// We stole `taken` successfully, plus the top value.
					// Until `taken` is merged into the local heap, no other
					// thief can see it.
					val top = heap.first
					while (true)
					{
						val existing = localQueue.get()
						val replacement = existing.merge(taken)
						if (localQueue.compareAndSet(existing, replacement))
							break
					}
					// The stolen subheap is visible again, so let threads
					// through to steal from it.  This thread runs `top`
					// itself.
					signal(taken.size)
					return top
				}
			}
		}
		return null
	}

	override fun poll(timeout: Long, unit: TimeUnit): E?
	{
		assert(timeout >= 0)
		val expiry = System.nanoTime() + unit.toNanos(timeout)
		while (true)
		{
			poll()?.let { return it }
			val nanos = expiry - System.nanoTime()
			if (nanos <= 0L) return null
			// A permit released after the scan above is retained, so this
			// cannot miss a task that became visible since then.
			idlePermits.tryAcquire(nanos, TimeUnit.NANOSECONDS)
		}
	}

	override fun put(e: E)
	{
		offer(e)
	}

	override fun take(): E
	{
		while (true)
		{
			poll()?.let { return it }
			// A permit released after the scan above is retained, so this
			// cannot miss a task that became visible since then.
			idlePermits.acquire()
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
