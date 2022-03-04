/*
 * ObservableState.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil.utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * `ObservableState` wraps a value that notifies its observers when the
 * wrapped value changes.
 *
 * TODO Add some form of cleanup functionality that allows for forcing a
 *  remove notifier call for all handlers and to "remove" them from? Could this
 *  be abused to create memory leaks by abusing this?
 *
 * @author Richard Arriaga
 *
 * @param T
 *   The type of the wrapped object.
 * @property scope
 *   The coroutine scope to use to run updates.
 *
 * @constructor
 * Create a new [ObservableState].
 *
 * @param state
 *   The initial state.
 * @param scope
 *   The coroutine scope to use to run updates.
 */
class ObservableState<T> constructor(
	state: T,
	private val scope: CoroutineScope =
		CoroutineScope(SupervisorJob() + Dispatchers.Default))
{
	/**
	 * The wrapped state that, when changes, notifies all interested observers.
	 */
	var state: T = state
		set(value)
		{
			field = value
			scope.launch {
				observers.values.forEach {
					scope.launch {
						it.second(value)
					}
				}
			}
		}

	/**
	 * The map from [Handle.id] to notifier suspend functions for the observers.
	 */
	private val observers =
		ConcurrentHashMap<Int, Pair<Handle<T>, suspend (T) -> Unit>>()

	/**
	 * The generator for [Handle] ids.
	 */
	private val idGenerator = AtomicInteger(0)

	/**
	 * Observe this [ObservableState].
	 *
	 * @param notifier
	 *  The suspend function that accepts the wrapped value that has just been
	 *  updated.
	 */
	fun observe (notifier: suspend (T) -> Unit): Handle<T>
	{
		val handle = Handle(this)
		observers[handle.id] = Pair(handle, notifier)
		return handle
	}

	/**
	 * Answer a new [Handle] for this [ObservableState].
	 */
	fun acquireHandle (): Handle<T> = Handle(this)

	/**
	 * [Remove the handler notifiers][Handle.removeNotifier] for all [Handle]s
	 * observing this [ObservableState].
	 */
	fun cleanUpObservers ()
	{
		observers.clear()
	}

	/**
	 * `Handle` links to the owning [ObservableState] providing a reference
	 * back to the `ObservableState`.
	 */
	class Handle<T> constructor(private var observableState: ObservableState<T>?)
	{
		/**
		 * The integer that uniquely identifies this [Handle].
		 */
		val id: Int = observableState!!.idGenerator.getAndIncrement()

		/**
		 * Remove this interest from the [ObservableState].
		 */
		fun removeNotifier ()
		{
			observableState?.observers?.remove(id)
		}

		/**
		 * Resume observation with the provided notifier function.
		 *
		 * @param notifier
		 *  The suspend function that accepts the wrapped value that has just
		 *  been updated.
		 */
		fun reObserve (notifier: suspend (T) -> Unit)
		{
			observableState?.let {
				it.observers[id] = Pair(this, notifier)
			}
		}
	}
}
