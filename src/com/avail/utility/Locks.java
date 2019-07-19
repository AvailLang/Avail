/*
 * Locks.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import javax.annotation.Nullable;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * {@code Locks} is a utility class for coordinating locking operations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class Locks
{
	/** Prevent accidental instantiation. */
	private Locks ()
	{
	}

	/**
	 * Execute the given {@link Runnable} while holding the given {@link Lock}.
	 *
	 * @param lock
	 *        The {@link Lock} to be acquired.
	 * @param runnable
	 *        The {@link Runnable} to execute while holding the lock.
	 */
	public static void lockWhile (
		final Lock lock,
		final Runnable runnable)
	{
		lock.lock();
		try
		{
			runnable.run();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Execute the given {@link Supplier} while holding the given {@link Lock}.
	 * Return the value produced by the supplier.
	 *
	 * @param lock
	 *        The {@link Lock} to be acquired.
	 * @param supplier
	 *        The {@link Supplier} to execute while holding the lock.
	 * @return The result of running the supplier, which must not be {@code
	 *         null}.
	 * @param <T> The type of value to pass through from the supplier.
	 */
	public static <T> T lockWhile (
		final Lock lock,
		final Supplier<T> supplier)
	{
		lock.lock();
		try
		{
			return supplier.get();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Execute the given {@link Supplier} while holding the given {@link Lock}.
	 * Return the value produced by the supplier.  The result may be {@code
	 * null}.
	 *
	 * @param lock
	 *        The {@link Lock} to be acquired.
	 * @param supplier
	 *        The {@link Supplier} to execute while holding the lock.
	 * @return The result of running the supplier, which may be {@code null}.
	 * @param <T>
	 *        The type of {@link Nullable} value to pass through from the
	 *        supplier.
	 */
	public static @Nullable <T> T lockWhileNullable (
		final Lock lock,
		final Supplier<T> supplier)
	{
		lock.lock();
		try
		{
			return supplier.get();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Acquire the {@link Lock}, and return an {@link AutoCloseable} which will
	 * carry the responsibility for releasing the lock.  This mechanism allows
	 * locking to use the much more convenient try-with-resources mechanism.
	 *
	 * @param lock
	 *        The lock to lock.
	 * @return An {@link AutoCloseable} which will unlock the lock when closed.
	 */
	@SuppressWarnings("LockAcquiredButNotSafelyReleased")
	public static Auto auto (
		final Lock lock)
	{
		lock.lock();
		return new Auto(lock);
	}

	/** A convenient non-throwing form of {@link AutoCloseable}. */
	public static class Auto implements AutoCloseable
	{
		/** The lock to unlock during a {@link #close()}. */
		private final Lock lock;

		/**
		 * Create an Auto to unlock a given {@link Lock}.
		 *
		 * @param lock The {@link Lock} to be eventually unlocked.
		 */
		public Auto (final Lock lock)
		{
			this.lock = lock;
		}

		@Override
		public void close ()
		{
			lock.unlock();
		}
	}
}
