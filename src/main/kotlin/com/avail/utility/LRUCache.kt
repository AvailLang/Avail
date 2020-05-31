/*
 * LRUCache.kt
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

package com.avail.utility

import java.lang.Boolean.parseBoolean
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.collections.Map.Entry
import kotlin.concurrent.withLock

/**
 * `LRUCache` implements a memory-sensitive least-recently-used cache. All
 * public operations support concurrent access. It avoids redundant simultaneous
 * computation of values by racing [threads][Thread] that present the same keys.
 *
 * @param K
 *   The type of the keys.
 * @param V
 *   The type of the values.
 * @property softCapacity
 *   The cardinality of the set of [softly held][SoftReference] cached values.
 *   This is the total capacity of the [cache][LRUCache], i.e. the capacity of
 *   [softMap].
 * @property strongCapacity
 *   The cardinality of the set of strongly held cached values, i.e. the
 *   capacity of [strongMap].
 * @property transformer
 *   The function responsible for producing new values from user-supplied keys.
 *   Must not produce `null`.
 * @property retirementAction
 *   The action responsible for retiring a binding expired from the
 *   [cache][LRUCache].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [LRUCache].
 *
 * @param softCapacity
 *   The capacity of the [cache][LRUCache]. This is the maximum number of cached
 *   values that will ever be retained simultaneously. Must be greater than zero
 *   (0).
 * @param strongCapacity
 *   The maximum number of cached values that will be strongly retained to
 *   prevent garbage collection. Must be less than or equal to the capacity.
 * @param transformer
 *   The function responsible for producing new values from user-supplied keys.
 *   Must not produce `null`.
 * @param retirementAction
 *   The action responsible for retiring a binding expired from the
 *   [cache][LRUCache], or `null` if no such action should be performed.
 */
@Suppress("unused")
class LRUCache<K, V> @JvmOverloads constructor(
	private val softCapacity: Int,
	private val strongCapacity: Int,
	private val transformer: (K)->V,
	private val retirementAction: ((K, V)->Unit)? = null)
{
	/**
	 * The [lock][ReentrantLock] responsible for guarding access to internal
	 * [cache][LRUCache] structures.
	 */
	private val lock = ReentrantLock()

	/**
	 * A [reference&#32;queue][ReferenceQueue] of defunct
	 * [soft&#32;references][SoftReference] to previously garbage-collected
	 * cached values.
	 */
	private val defunctReferences = ReferenceQueue<V>()

	/**
	 * The access-ordered [map][SoftCacheMap] which maps access keys to
	 * [softly&#32;held][SoftReference] cached values. All cached values are
	 * ultimately retrieved from this map.
	 */
	private val softMap: SoftCacheMap

	/**
	 * A mapping from [softly&#32;held][SoftReference] cached values to their
	 * associated keys. This data structure is necessary to clean up the
	 * [primary&#32;map][softMap] after the garbage collector has reclaimed the
	 * cached values.
	 */
	private val keysBySoftReference = HashMap<SoftReference<out V>, K>()

	/**
	 * The access-ordered [map][StrongCacheMap] which maps access keys to
	 * strongly held cached values.
	 */
	private val strongMap: StrongCacheMap

	/**
	 * A [mapping][Map] from keys to [futures][ValueFuture] whose values are
	 * currently being computed by threads accessing the [cache][LRUCache].
	 */
	private val futures: MutableMap<K, ValueFuture>

	/**
	 * The [Condition] used to make a thread wait until all futures have been
	 * completed. A thread waiting on it will be signaled every time a future is
	 * removed from [the&#32;map&#32;of&#32;futures][futures].
	 */
	private val futuresCondition = lock.newCondition()

	/**
	 * A `StrongCacheMap` subclasses [LinkedHashMap] to override
	 * `removeEldestEntry`.
	 *
	 * @property capacity
	 *   The capacity of the [map][StrongCacheMap].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [StrongCacheMap] with the specified capacity.
	 *
	 * @param capacity
	 *   The capacity of the [map][StrongCacheMap].
	 */
	private inner class StrongCacheMap internal constructor(
		private val capacity: Int) : LinkedHashMap<K, V>(capacity, 0.75f, true)
	{
		override fun removeEldestEntry(
			eldest: MutableMap.MutableEntry<K, V>?): Boolean
		{
			return this@StrongCacheMap.size > capacity
		}
	}

	/**
	 * Check the integrity of the [cache][LRUCache].
	 */
	private fun checkInvariants()
	{
		if (checkInvariants)
		{
			assert(lock.isHeldByCurrentThread)
			assert(strongMap.size <= strongCapacity)
			assert(softMap.size == keysBySoftReference.size)
			assert(softMap.size <= softCapacity)
			for ((key, value) in softMap)
			{
				assert(key != null)
				assert(keysBySoftReference.containsKey(value))
				assert(keysBySoftReference[value] === key)
			}
			for ((key, value) in strongMap)
			{
				assert(key != null)
				assert(value != null)
			}
		}
	}

	/**
	 * A `SoftCacheMap` subclasses [LinkedHashMap] to override
	 * `removeEldestEntry`.
	 *
	 * @property capacity
	 *   The capacity of the [map][SoftCacheMap].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [SoftCacheMap] with the specified capacity.
	 *
	 * @param capacity
	 *   The capacity of the [map][SoftCacheMap].
	 */
	internal inner class SoftCacheMap constructor(
		private val capacity: Int) : LinkedHashMap<K, SoftReference<V>>(
			capacity, 0.75f, true)
	{
		override fun removeEldestEntry(
			eldest: MutableMap.MutableEntry<K, SoftReference<V>>?): Boolean
		{
			assert(lock.isHeldByCurrentThread)
			assert(this === softMap)
			if (this@SoftCacheMap.size > capacity)
			{
				assert(eldest != null)
				val key = eldest!!.key
				val reference = eldest.value
				assert(softMap.containsKey(key))
				assert(keysBySoftReference.containsKey(reference))
				softMap.remove(key)
				keysBySoftReference.remove(reference)
				val referent = reference.get()
				if (referent != null)
				{
					retire(key, referent)
				}
			}
			return false
		}
	}

	/**
	 * A `ValueFuture` synchronously provides a value for the key specified at
	 * instantiation time. Redundant simultaneous computation by concurrent
	 * threads is prevented by the implementation. [cancel] and [get] are
	 * unsupported operations.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [ValueFuture].
	 */
	private inner class ValueFuture internal constructor() : Future<V>
	{
		/**
		 * The [lock][ReentrantLock] that guards access to this
		 * [future][ValueFuture].
		 */
		private val computationLock = ReentrantLock()

		/**
		 * The [condition][Condition] on which threads should wait to be
		 * notified that execution of the user-supplied [Function] has
		 * completed.
		 */
		private val completionCondition = computationLock.newCondition()

		/** Has the result been computed?  */
		@Volatile
		private var isDone: Boolean = false

		/** The result of the computation of the [ValueFuture]. */
		internal var result: V? = null
			set (result)
			{
				computationLock.withLock {
					field = result
					isDone = true
					completionCondition.signalAll()
				}
			}

		/**
		 * The [exception][RuntimeException], if any, that was encountered
		 * during execution of the user-supplied [Function].
		 */
		@Volatile
		internal var exception: RuntimeException? = null
			set (exception)
			{
				computationLock.withLock {
					field = exception
					isDone = true
					completionCondition.signalAll()
				}
			}

		override fun cancel(mayInterruptIfRunning: Boolean): Boolean
		{
			throw UnsupportedOperationException()
		}

		override fun isCancelled() = false

		override fun isDone() = isDone

		@Throws(RuntimeException::class)
		override fun get(): V?
		{
			// Because the completion flag is volatile and one-way, we can
			// safely check it without first acquiring the lock.
			if (!isDone)
			{
				computationLock.withLock {
					// While the computation is in progress, await its
					// completion.
					while (!isDone)
					{
						completionCondition.await()
					}
				}
			}

			// If an exception was set, then we now rethrow that exception.
			if (exception != null)
			{
				throw RuntimeException(exception)
			}

			return result
		}

		override fun get(timeout: Long, unit: TimeUnit?): V
		{
			throw UnsupportedOperationException()
		}
	}

	init
	{
		assert(softCapacity > 0)
		assert(strongCapacity <= softCapacity)

		softMap = SoftCacheMap(softCapacity)
		strongMap = StrongCacheMap(strongCapacity)
		futures = HashMap()
	}

	/**
	 * Expunge any [references][SoftReference] from the
	 * [primary&#32;cache][SoftCacheMap] whose referents have been reclaimed by
	 * the garbage collector. This operation is cheap and may be called from
	 * most API functions without negatively impacting performance.
	 */
	private fun expungeDefunctReferences()
	{
		assert(lock.isHeldByCurrentThread)
		checkInvariants()
		while (true)
		{
			val reference = defunctReferences.poll() ?: break
			assert(reference.get() == null)
			val key = keysBySoftReference.remove(reference)
			if (key != null)
			{
				assert(softMap.containsKey(key))
				softMap.remove(key)
			}
		}
		checkInvariants()
	}

	/**
	 * Remove the specific key (and value) from the cache.
	 *
	 * @param key
	 *   The key to remove.
	 * @param referent
	 *   The value at that key.
	 */
	private fun retire(key: K, referent: V) =
		retirementAction?.invoke(key, referent)

	/**
	 * Completely clear the cache, forcing retirement of the contents before
	 * removing them.
	 *
	 * @throws InterruptedException
	 *   If the current thread is interrupted.
	 */
	@Throws(InterruptedException::class)
	fun clear()
	{
		lock.withLock {
			expungeDefunctReferences()
			while (futures.isNotEmpty())
			{
				futuresCondition.await()
			}
			val entries = ArrayList<Entry<K, SoftReference<V>>>(softMap.entries)
			softMap.clear()
			keysBySoftReference.clear()
			strongMap.clear()
			for ((key, reference) in entries)
			{
				val referent = reference.get()
				if (referent != null)
				{
					retire(key, referent)
				}
			}
		}
	}

	/** The number of values currently in [cache][LRUCache]. */
	val size: Int
		get()
		{
			lock.withLock {
				expungeDefunctReferences()
				return softMap.size
			}
		}

	/**
	 * Immediately answer the value already associated with the specified key.
	 * Do not execute the user-supplied transformer under any circumstances.
	 * That is, only answer an already cached value.
	 *
	 * This method answers `null` if **1)** the cached value associated with the
	 * specified key is actually `null` or **2)** no value has been cached for
	 * the specified key.
	 *
	 * Note that this method is not reentrant. The transformer must not reenter
	 * any public operation while computing a value for a specified key.
	 *
	 * @param key
	 *   The key whose associated value should be answered.
	 * @return
	 *   The value to which the specified key is mapped, or `null` if no value
	 *   has been mapped.
	 */
	fun poll(key: K): V?
	{
		lock.withLock {
			expungeDefunctReferences()
			val result: V? = softMap[key]?.get()
			if (result != null)
			{
				// Update the map containing strong references to the most
				// recently accessed cached values. The eldest entry will be
				// automatically retired if necessary.
				strongMap[key] = result
			}
			checkInvariants()
			return result
		}
	}

	/**
	 * Answer the value associated with the specified key, computing the value
	 * from the user-supplied [Function] if the value is not already
	 * present in the [cache][LRUCache].
	 *
	 * Note that this method is not reentrant. The transformer must not
	 * reenter any public operation while computing a value for a specified
	 * key.
	 *
	 * TODO: This needs to become getThen(), to prevent a race with retirement
	 * for a resource still actively in use (like a file handle that might get
	 * closed automatically by a retirement action).
	 *
	 * @param key
	 *   The key whose associated value should be answered.
	 * @return
	 *   The value to which the specified key is mapped.
	 * @throws RuntimeException
	 *   If an exception occurred as a result of an error in the execution of
	 *   the user-supplied [Function].
	 */
	@Throws(RuntimeException::class)
	operator fun get(key: K): V
	{
		lock.lock()
		try
		{
			checkInvariants()

			// Before searching the primary cache map for the key, expunge all
			// defunct references from it.
			expungeDefunctReferences()

			var reference: SoftReference<V>? = softMap[key]
			var result: V? = reference?.get()

			if (reference == null || result == null)
			{
				// We didn't find the desired value in the cache, so now we
				// check the set of futures to see if some other thread is
				// already computing a value for our key.
				var future = futures[key]

				// No other thread is computing a value for our key, so it is
				// our responsibility.
				if (future == null)
				{
					future = ValueFuture()
					futures[key] = future

					// We must not hold any locks while computing the future.
					lock.unlock()
					var exception: RuntimeException? = null
					try
					{
						result = transformer(key)
					}
					catch (e: RuntimeException)
					{
						// Record the exception locally. We are not holding the
						// lock at this point, and we really ought to set the
						// exception when we *are* holding it.
						exception = e
					}
					finally
					{
						// If the transformer throws an exception, then attach
						// the exception to the future so that waiters can react
						// appropriately.
						lock.lock()
						if (exception == null)
						{
							future.result = result
						}
						else
						{
							future.exception = exception
						}
						futures.remove(key)
						futuresCondition.signalAll()
					}

					// If the computation failed due to an exception, then
					// attempting to retrieve the result from the future will
					// throw a CacheException.
					result = future.get()
					reference = SoftReference<V>(result!!, defunctReferences)

					// Establish a binding between the key and its value in the
					// cache's primary map. The eldest entry will be
					// automatically retired if necessary.
					softMap[key] = reference
					keysBySoftReference[reference] = key
				}
				else
				{
					// Some other thread is computing a value for our key, so
					// just wait until it becomes available. We must not hold
					// any locks while awaiting completion of the future.
					lock.unlock()
					try
					{
						result = future.get()
					}
					finally
					{
						lock.lock()
					}
				}
			}

			checkInvariants()

			// Update the map containing strong references to the most recently
			// accessed cached values. The eldest entry will be automatically
			// retired if necessary.
			strongMap[key] = result!!

			checkInvariants()

			return result
		}
		finally
		{
			lock.unlock()
		}
	}

	/**
	 * Remove the specified key and any value associated with it. If the key is
	 * present, and the [softly&#32;held][SoftReference] corresponding value has
	 * not been reclaimed by the garbage collector, then perform the retirement
	 * action, if any.
	 *
	 * @param key
	 *   A key.
	 * @return
	 *   The value associated with the key, or `null` if the key is not present
	 *   or if the value has already been reclaimed.
	 */
	fun remove(key: K): V?
	{
		lock.withLock {
			// Before searching the primary cache map for the key, expunge all
			// defunct references from it.
			expungeDefunctReferences()
			val reference = softMap.remove(key)
			strongMap.remove(key)
			val result: V? = reference?.get()
			if (result != null)
			{
				retire(key, result)
			}
			checkInvariants()
			return result
		}
	}

	companion object
	{
		/**
		 * The [system&#32;property][System.getProperty] that enables detailed
		 * invariant checking. This causes significant slowdown and should not
		 * be used in a production application.
		 */
		private val checkInvariantsProperty = String.format(
			"%s.checkInvariants",
			LRUCache::class.java.canonicalName)

		/**
		 * Should the invariants be [checked][checkInvariants]? Enabled by
		 * setting the system property [checkInvariantsProperty] to `"true"`.
		 */
		private val checkInvariants: Boolean

		// Initialize checkInvariants.
		init
		{
			var value = false
			try
			{
				val property = System.getProperty(checkInvariantsProperty)
				value = parseBoolean(property)
			}
			catch (e: Exception)
			{
				// Just don't check the invariants.
			}

			checkInvariants = value
		}
	}
}
