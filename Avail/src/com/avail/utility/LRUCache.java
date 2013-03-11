/**
 * com.avail.utility/LRUCache.java
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

package com.avail.utility;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.annotations.*;

/**
 * {@code LRUCache} implements a memory-sensitive least-recently-used cache.
 * All public operations support concurrent access. It avoids redundant
 * simultaneous computation of values by racing {@linkplain Thread threads} that
 * present the same keys.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <K> The type of the keys.
 * @param <V> The type of the values.
 */
public class LRUCache<K, V>
{
	/**
	 * The {@linkplain ReentrantLock lock} responsible for guarding
	 * access to internal {@linkplain LRUCache cache} structures.
	 */
	@InnerAccess final ReentrantLock lock = new ReentrantLock();

	/**
	 * Acquire the {@linkplain ReentrantLock lock}.
	 */
	private void lock ()
	{
		lock.lock();
	}

	/**
	 * Release the {@linkplain ReentrantLock lock}.
	 */
	private void unlock ()
	{
		lock.unlock();
	}

	/**
	 * A {@linkplain ReferenceQueue reference queue} of defunct {@linkplain
	 * SoftReference soft references} to previously garbage-collected cached
	 * values.
	 */
	private final ReferenceQueue<V> defunctReferences =
		new ReferenceQueue<V>();

	/**
	 * A {@code StrongCacheMap} subclasses {@link LinkedHashMap} to override
	 * {@link #removeEldestEntry(java.util.Map.Entry) removeEldestEntry}.
	 */
	private final class StrongCacheMap
	extends LinkedHashMap<K, V>
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 5184227991121201660L;

		/** The capacity of the {@linkplain LRUCache.StrongCacheMap map}. */
		private final int capacity;

		/**
		 * Construct a new {@link LRUCache.StrongCacheMap} with the specified
		 * capacity.
		 *
		 * @param capacity The capacity of the {@linkplain
		 *                 LRUCache.StrongCacheMap map}.
		 */
		public StrongCacheMap (final int capacity)
		{
			super(capacity, 0.75f, true);
			this.capacity = capacity;
		}

		@Override
		protected boolean removeEldestEntry (
			final @Nullable Map.Entry<K, V> eldest)
		{
			return size() > capacity;
		}
	}

	/**
	 * The {@linkplain System#getProperty(String) system property} that enables
	 * detailed invariant checking. This causes significant slowdown and should
	 * not be used in a production application.
	 */
	private static final String checkInvariantsProperty =
		String.format(
			"%s.checkInvariants",
			LRUCache.class.getCanonicalName());

	/**
	 * Should the invariants be {@linkplain #checkInvariants() checked}? Enabled
	 * by setting the system property {@link #checkInvariantsProperty} to {@code
	 * "true"}.
	 */
	private static final boolean checkInvariants;

	// Initialize checkInvariants.
	static
	{
		boolean value = false;
		try
		{
			final String property = System.getProperty(checkInvariantsProperty);
			value = Boolean.parseBoolean(property);
		}
		catch (final Exception e)
		{
			// Just don't check the invariants.
		}

		checkInvariants = value;
	}

	/**
	 * Check the integrity of the {@linkplain LRUCache cache}.
	 */
	@InnerAccess void checkInvariants ()
	{
		if (checkInvariants)
		{
			assert lock.isHeldByCurrentThread();

			assert strongMap.size() <= strongCapacity;
			assert softMap.size() == keysBySoftReference.size();
			assert softMap.size() <= softCapacity;
			for (final Map.Entry<K, SoftReference<V>> entry
					: softMap.entrySet())
			{
				assert entry.getKey() != null;
				assert entry.getValue() != null;
				assert keysBySoftReference.containsKey(entry.getValue());
				assert
					keysBySoftReference.get(entry.getValue()) == entry.getKey();
			}
			for (final Map.Entry<K, V> entry : strongMap.entrySet())
			{
				assert entry.getKey() != null;
				assert entry.getValue() != null;
			}
		}
	}

	/**
	 * A {@code SoftCacheMap} subclasses {@link LinkedHashMap} to override
	 * {@link #removeEldestEntry(java.util.Map.Entry) removeEldestEntry}.
	 */
	private final class SoftCacheMap
	extends LinkedHashMap<K, SoftReference<V>>
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 9157534562182014165L;

		/** The capacity of the {@linkplain LRUCache.StrongCacheMap map}. */
		private final int capacity;

		/**
		 * Construct a new {@link LRUCache.StrongCacheMap} with the specified
		 * capacity.
		 *
		 * @param capacity The capacity of the {@linkplain
		 *                 LRUCache.StrongCacheMap map}.
		 */
		public SoftCacheMap (final int capacity)
		{
			super(capacity, 0.75f, true);
			this.capacity = capacity;
		}

		@Override
		protected boolean removeEldestEntry (
			final @Nullable Map.Entry<K, SoftReference<V>> eldest)
		{
			assert lock.isHeldByCurrentThread();
			assert this == softMap;

			if (size() > capacity)
			{
				assert eldest != null;
				final K key = eldest.getKey();
				final SoftReference<V> reference = eldest.getValue();
				assert softMap.containsKey(key);
				assert keysBySoftReference.containsKey(reference);
				softMap.remove(key);
				keysBySoftReference.remove(reference);

				final V referent = reference.get();
				if (referent != null && retirementAction != null)
				{
					retirementAction.value(key, referent);
				}
			}

			return false;
		}
	}

	/**
	 * The cardinality of the set of {@linkplain SoftReference softly held}
	 * cached values. This is the total capacity of the {@linkplain LRUCache
	 * cache}, i.e. the capacity of {@link #softMap}.
	 */
	@InnerAccess final int softCapacity;

	/**
	 * Answer the capacity of the {@linkplain LRUCache cache}.
	 *
	 * @return The capacity of the {@linkplain LRUCache cache}.
	 */
	public int capacity ()
	{
		return softCapacity;
	}

	/**
	 * The access-ordered {@linkplain SoftCacheMap map} which maps access keys
	 * to {@linkplain SoftReference softly held} cached values. All cached
	 * values are ultimately retrieved from this map.
	 */
	@InnerAccess final SoftCacheMap softMap;

	/**
	 * A mapping from {@linkplain SoftReference softly held} cached values to
	 * their associated keys. This data structure is necessary to clean up the
	 * {@linkplain #softMap primary map} after the garbage collector has
	 * reclaimed the cached values.
	 */
	@InnerAccess final Map<SoftReference<V>, K> keysBySoftReference =
		new HashMap<SoftReference<V>, K>();

	/**
	 * The cardinality of the set of strongly held cached values, i.e. the
	 * capacity of {@link #strongMap}.
	 */
	@InnerAccess final int strongCapacity;

	/**
	 * Answer the cardinality of the set of strongly held cached values.
	 *
	 * @return The cardinality of the set of strongly held cached values.
	 */
	public int strongCapacity ()
	{
		return strongCapacity;
	}

	/**
	 * The access-ordered {@linkplain StrongCacheMap map} which maps access keys
	 * to strongly held cached values.
	 */
	private final StrongCacheMap strongMap;

	/**
	 * The {@linkplain Transformer1 transformer} responsible for producing new
	 * values from user-supplied keys. Must not produce {@code null}.
	 */
	@InnerAccess final Transformer1<K, V> transformer;

	/**
	 * Answer the {@linkplain Transformer1 transformer} responsible for
	 * producing new values from user-supplied keys.
	 *
	 * @return A {@linkplain Transformer1 transformer}.
	 */
	public Transformer1<K, V> transformer ()
	{
		return transformer;
	}

	/**
	 * The {@linkplain Continuation2 action} responsible for retiring a binding
	 * expired from the {@linkplain LRUCache cache}.
	 */
	@InnerAccess final Continuation2<K, V> retirementAction;

	/**
	 * A {@code ValueFuture} synchronously provides a value for the key
	 * specified at instantiation time. Redundant simultaneous computation
	 * by concurrent threads is prevented by the implementation. {@link
	 * #cancel(boolean) cancel} and {@link #get(long, TimeUnit)} are
	 * unsupported operations.
	 */
	private final class ValueFuture
	implements Future<V>
	{
		/**
		 * Construct a new {@link ValueFuture}.
		 */
		public ValueFuture ()
		{
			// No implementation required.
		}

		/**
		 * The {@linkplain ReentrantLock lock} that guards access to this
		 * {@linkplain ValueFuture future}.
		 */
		private final ReentrantLock computationLock =
			new ReentrantLock();

		/**
		 * The {@linkplain Condition condition} on which threads should wait to
		 * be notified that execution of the user-supplied {@linkplain
		 * Transformer1 transformer} has completed.
		 */
		private final Condition completionCondition =
			computationLock.newCondition();

		@Override
		public boolean cancel (final boolean mayInterruptIfRunning)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isCancelled ()
		{
			return false;
		}

		/** Has the result been computed? */
		private volatile boolean isDone;

		@Override
		public boolean isDone ()
		{
			return isDone;
		}

		/**
		 * The result of the computation of the {@linkplain
		 * LRUCache.ValueFuture future}.
		 */
		private V result;

		/**
		 * Establish the argument as the result of the {@linkplain ValueFuture
		 * future}. Notify all {@linkplain Thread waiters} that a result is now
		 * available.
		 *
		 * @param result The result.
		 */
		void setResult (final @Nullable V result)
		{
			computationLock.lock();
			try
			{
				this.result = result;
				isDone = true;
				completionCondition.signalAll();
			}
			finally
			{
				computationLock.unlock();
			}
		}

		/**
		 * The {@linkplain RuntimeException exception}, if any, that was
		 * encountered during execution of the user-supplied {@linkplain
		 * Transformer1 transformer}.
		 */
		private volatile RuntimeException exception;

		/**
		 * Establish the argument as the {@linkplain RuntimeException
		 * exception} that thwarted computation of the {@linkplain ValueFuture
		 * future}. Notify all {@linkplain Thread waiters} that an exception
		 * has occurred.
		 *
		 * @param exception An {@linkplain RuntimeException exception}.
		 */
		void setException (final RuntimeException exception)
		{
			computationLock.lock();
			try
			{
				this.exception = exception;
				isDone = true;
				completionCondition.signalAll();
			}
			finally
			{
				computationLock.unlock();
			}
		}

		@Override
		public V get () throws RuntimeException
		{
			// Because the completion flag is volatile and one-way, we can
			// safely check it without first acquiring the lock.
			if (!isDone)
			{
				computationLock.lock();
				try
				{
					// While the computation is in progress, await its
					// completion.
					while (!isDone)
					{
						completionCondition.await();
					}
				}
				catch (final InterruptedException e)
				{
					// No special action is required.
				}
				finally
				{
					computationLock.unlock();
				}
			}

			// If an exception was set, then we now rethrow that exception.
			if (exception != null)
			{
				throw new RuntimeException(exception);
			}

			return result;
		}

		@Override
		public V get (final long timeout, final @Nullable TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * A {@linkplain Map mapping} from keys to {@linkplain ValueFuture futures}
	 * whose values are currently being computed by threads accessing the
	 * {@linkplain LRUCache cache}.
	 */
	private final Map<K, ValueFuture> futures;

	/**
	 * Construct a new {@link LRUCache}.
	 *
	 * @param capacity The capacity of the {@linkplain LRUCache cache}. This is
	 *                 the maximum number of cached values that will ever be
	 *                 retained simultaneously. Must be greater than zero (0).
	 * @param strongCapacity The maximum number of cached values that will be
	 *                       strongly retained to prevent garbage collection.
	 *                       Must be less than or equal to the capacity.
	 * @param transformer The {@linkplain Transformer1 transformer} responsible
	 *                    for producing new values from user-supplied keys. Must
	 *                    not produce {@code null}.
	 * @param retirementAction The {@linkplain Continuation2 action} responsible
	 *                         for retiring a binding expired from the
	 *                         {@linkplain LRUCache cache}, or {@code null} if
	 *                         no such action should be performed.
	 */
	public LRUCache (
		final int capacity,
		final int strongCapacity,
		final Transformer1<K, V> transformer,
		final @Nullable Continuation2<K, V> retirementAction)
	{
		assert capacity > 0;
		assert strongCapacity <= capacity;

		this.softCapacity     = capacity;
		this.strongCapacity   = strongCapacity;
		this.transformer      = transformer;
		this.retirementAction = retirementAction;

		softMap   = new SoftCacheMap(softCapacity);
		strongMap = new StrongCacheMap(strongCapacity);
		futures   = new HashMap<K, ValueFuture>();
	}

	/**
	 * Construct a new {@link LRUCache}.
	 *
	 * @param capacity The capacity of the {@linkplain LRUCache cache}. This is
	 *                 the maximum number of cached values that will ever be
	 *                 retained simultaneously. Must be greater than zero (0).
	 * @param strongCapacity The maximum number of cached values that will be
	 *                       strongly retained to prevent garbage collection.
	 *                       Must be less than or equal to the capacity.
	 * @param transformer The {@linkplain Transformer1 transformer} responsible
	 *                    for producing new values from user-supplied keys. Must
	 *                    not produce {@code null}.
	 */
	public LRUCache (
		final int capacity,
		final int strongCapacity,
		final Transformer1<K, V> transformer)
	{
		this(capacity, strongCapacity, transformer, null);
	}

	/**
	 * Expunge any {@linkplain SoftReference references} from the {@linkplain
	 * SoftCacheMap primary cache} whose referents have been reclaimed by the
	 * garbage collector. This operation is cheap and may be called from most
	 * API functions without negatively impacting performance.
	 */
	private void expungeDefunctReferences ()
	{
		assert lock.isHeldByCurrentThread();

		checkInvariants();

		Reference<? extends V> reference;
		while ((reference = defunctReferences.poll()) != null)
		{
			assert reference.get() == null;
			final K key = keysBySoftReference.remove(reference);
			if (key != null)
			{
				assert softMap.containsKey(key);
				softMap.remove(key);
			}
			else
			{
				assert !softMap.containsKey(key);
			}
		}

		checkInvariants();
	}

	/**
	 * The {@link Condition} used to make a thread wait until all futures have
	 * been completed. A thread waiting on it will be signaled every time a
	 * future is removed from {@linkplain #futures the map of futures}.
	 */
	final Condition futuresCondition = lock.newCondition();

	/**
	 * Completely clear the cache, forcing retirement of the contents before
	 * removing them.
	 * @throws InterruptedException
	 *         If the current thread is interrupted.
	 */
	public void clear () throws InterruptedException
	{
		lock();
		try
		{
			expungeDefunctReferences();
			while (!futures.isEmpty())
			{
				futuresCondition.await();
			}
			final Set<Entry<K, SoftReference<V>>> entries =
				new HashSet<Entry<K, SoftReference<V>>>(softMap.entrySet());

			softMap.clear();
			keysBySoftReference.clear();
			strongMap.clear();

			for (final Entry<K, SoftReference<V>> entry : entries)
			{
				final K key = entry.getKey();
				final SoftReference<V> reference = entry.getValue();
				final V referent = reference.get();
				if (referent != null && retirementAction != null)
				{
					retirementAction.value(key, referent);
				}
			}
		}
		finally
		{
			unlock();
		}
	}

	/**
	 * Answer the number of values currently in {@linkplain LRUCache cache}.
	 *
	 * @return The number of values currently in {@linkplain LRUCache cache}.
	 */
	public int size ()
	{
		lock();
		try
		{
			expungeDefunctReferences();
			return softMap.size();
		}
		finally
		{
			unlock();
		}
	}

	/**
	 * Immediately answer the value already associated with the specified key.
	 * Do not execute the user-supplied {@linkplain Transformer1 transformer}
	 * under any circumstances. That is, only answer an already cached value.
	 *
	 * <p>This method answers {@code null} if <strong>1)</strong> the cached
	 * value associated with the specified key is actually {@code null} or
	 * <strong>2></strong> no value has been cached for the specified key.</p>
	 *
	 * <p>Note that this method is not reentrant. The transformer must not
	 * reenter any public operation while computing a value for a specified
	 * key.</p>
	 *
	 * @param key The key whose associated value should be answered.
	 * @return The value to which the specified key is mapped, or {@code null}
	 *         if no value has been mapped.
	 */
	public @Nullable V poll (final K key)
	{
		lock();
		try
		{
			expungeDefunctReferences();

			V result = null;
			final SoftReference<V> reference = softMap.get(key);
			if (reference != null && (result = reference.get()) != null)
			{
				// Update the map containing strong references to the most
				// recently accessed cached values. The eldest entry will be
				// automatically retired if necessary.
				strongMap.put(key, result);
			}

			checkInvariants();

			return result;
		}
		finally
		{
			unlock();
		}
	}

	/**
	 * Answer the value associated with the specified key, computing the value
	 * from the user-supplied {@linkplain Transformer1 transformer} if the value
	 * is not already present in the {@linkplain LRUCache cache}.
	 *
	 * <p>This method only returns {@code null} for those keys explicitly
	 * mapped to {@code null} by the transformer.</p>
	 *
	 * <p>Note that this method is not reentrant. The transformer must not
	 * reenter any public operation while computing a value for a specified
	 * key.</p>
	 *
	 * @param key The key whose associated value should be answered.
	 * @return The value to which the specified key is mapped.
	 * @throws RuntimeException
	 *         If an exception occurred as a result of an error in the
	 *         execution of the user-supplied {@linkplain Transformer1
	 *         transformer}.
	 */
	public @Nullable V get (final K key) throws RuntimeException
	{
		assert key != null;

		lock();
		try
		{
			V result = null;

			checkInvariants();

			// Before searching the primary cache map for the key, expunge all
			// defunct references from it.
			expungeDefunctReferences();

			SoftReference<V> reference = softMap.get(key);
			if (reference == null || (result = reference.get()) == null)
			{
				// We didn't find the desired value in the cache, so now we
				// check the set of futures to see if some other thread is
				// already computing a value for our key.
				ValueFuture future = futures.get(key);

				// No other thread is computing a value for our key, so it is
				// our responsibility.
				if (future == null)
				{
					future = new ValueFuture();
					futures.put(key, future);

					RuntimeException exception = null;

					// We must not hold any locks while computing the future.
					unlock();
					try
					{
						result = transformer.value(key);
					}

					// Record the exception locally. We are not holding the lock
					// at this point, and we really ought to set the exception
					// when we *are* holding it.
					catch (final RuntimeException e)
					{
						exception = e;
					}

					// If the transformer throws an exception, then attach the
					// exception to the future so that waiters can react
					// appropriately.
					finally
					{
						lock();
						if (exception == null)
						{
							future.setResult(result);
						}
						else
						{
							future.setException(exception);
						}
						futures.remove(key);
						futuresCondition.signalAll();
					}

					// If the computation failed due to an exception, then
					// attempting to retrieve the result from the future will
					// throw a CacheException.
					result = future.get();
					reference = new SoftReference<V>(
						result, defunctReferences);

					// Establish a binding between the key and its value in the
					// cache's primary map. The eldest entry will be
					// automatically retired if necessary.
					softMap.put(key, reference);
					keysBySoftReference.put(reference, key);
				}

				// Some other thread is computing a value for our key, so just
				// wait until it becomes available.
				else
				{
					// We must not hold any locks while awaiting completion of
					// the future.
					unlock();
					try
					{
						result = future.get();
					}
					finally
					{
						lock();
					}
				}
			}

			checkInvariants();

			// Update the map containing strong references to the most recently
			// accessed cached values. The eldest entry will be automatically
			// retired if necessary.
			strongMap.put(key, result);

			checkInvariants();

			return result;
		}
		finally
		{
			unlock();
		}
	}
}
