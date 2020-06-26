/*
 * AvailThread.kt
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
package com.avail

import com.avail.interpreter.execution.Interpreter
import com.avail.utility.cast
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * An `AvailThread` is a [thread][Thread] managed by a particular [Avail
 * runtime][AvailRuntime]. Instances may obtain the managing runtime through the
 * static accessor [AvailRuntime.currentRuntime]. New instances will be created
 * as necessary by an Avail runtime's [executor][ScheduledThreadPoolExecutor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property interpreter
 *   The [interpreter][Interpreter] permanently bound to this
 *   [thread][AvailThread].
 * @constructor
 * Construct a new `AvailThread`.
 *
 * @param runnable
 *   The `Runnable runnable` that the new thread should execute.
 * @param interpreter
 *   The [Interpreter] that this thread will temporarily bind to fibers while
 *   they are running in this thread.
 */
class AvailThread internal constructor(
	runnable: Runnable,
	val interpreter: Interpreter)
		: Thread(runnable, "AvailThread-" + interpreter.interpreterIndex)
{

	/**
	 * The [Avail runtime][AvailRuntime] that owns this [thread][AvailThread].
	 */
	@JvmField
	val runtime: AvailRuntime = interpreter.runtime()

	companion object
	{
		/**
		 * Answer the current [Thread] strengthened to an `AvailThread`, or
		 * `null` if it isn't actually an `AvailThread`.
		 *
		 * @return
		 *   The current `AvailThread`.
		 */
		fun currentOrNull(): AvailThread?
		{
			val current = currentThread()
			return if (current is AvailThread)
			{
				current
			}
			else
			{
				null
			}
		}

		/**
		 * Answer the current [Thread] strengthened to an `AvailThread`, or
		 * throw [ClassCastException] if it isn't actually an `AvailThread`.
		 *
		 * @return
		 *   The current `AvailThread`.
		 * @throws ClassCastException
		 *   If the current thread isn't an `AvailThread`.
		 */
		@JvmStatic
		@Throws(ClassCastException::class)
		fun current(): AvailThread = currentThread().cast()
	}
}
