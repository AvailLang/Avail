/*
 * Locks.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.utility

import java.util.concurrent.locks.Lock
import java.util.function.Supplier

/**
 * `Locks` is a utility class for coordinating locking operations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object Locks
{
	/**
	 * Execute the given [Runnable] while holding the given [Lock].
	 *
	 * @param lock
	 *   The [Lock] to be acquired.
	 * @param lambda
	 *   The lambda to execute while holding the lock.
	 */
	fun lockWhile(lock: Lock, lambda: () -> Unit)
	{
		lock.lock()
		try
		{
			lambda()
		}
		finally
		{
			lock.unlock()
		}
	}

	/**
	 * Execute the given [Supplier] while holding the given [Lock].  Return the
	 * value produced by the supplier.
	 *
	 * @param lock
	 *   The [Lock] to be acquired.
	 * @param supplier
	 *   The lambda that answer `T` to execute while holding the lock.
	 * @return
	 *   The result of running the `supplier`, which must not be `null`.
	 * @param T
	 *   The type of value to pass through from the supplier.
	 */
	fun <T> lockWhile(lock: Lock, supplier: () ->T): T
	{
		lock.lock()
		return try
		{
			supplier()
		}
		finally
		{
			lock.unlock()
		}
	}

	/**
	 * Execute the given [Supplier] while holding the given [Lock]. Return the
	 * value produced by the supplier.  The result may be `null`.
	 *
	 * @param lock
	 *   The [Lock] to be acquired.
	 * @param supplier
	 *   The [Supplier] to execute while holding the lock.
	 * @return
	 *   The result of running the supplier, which may be `null`.
	 * @param T
	 *   The type of `nullable` value to pass through from the supplier.
	 */
	fun <T> lockWhileNullable(lock: Lock, supplier: Supplier<T>): T?
	{
		lock.lock()
		return try
		{
			supplier.get()
		}
		finally
		{
			lock.unlock()
		}
	}

	/**
	 * Acquire the [Lock], and return an [AutoCloseable] which will carry the
	 * responsibility for releasing the lock.  This mechanism allows locking to
	 * use the much more convenient try-with-resources mechanism.
	 *
	 * @param lock
	 *   The lock to lock.
	 * @return
	 *   An [AutoCloseable] which will unlock the lock when closed.
	 */
	fun auto(lock: Lock): Auto
	{
		lock.lock()
		return Auto(lock)
	}

	/**
	 * A convenient non-throwing form of [AutoCloseable].
	 *
	 * @property lock
	 *   The lock to unlock during a [close].
	 * @constructor
	 * Create an Auto to unlock a given [Lock].
	 *
	 * @param lock
	 *   The [Lock] to be eventually unlocked.
	 */
	class Auto constructor(private val lock: Lock) : AutoCloseable
	{
		override fun close()
		{
			lock.unlock()
		}
	}
}