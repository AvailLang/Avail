/*
 * AbstractWorkbenchTask.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.tasks

import avail.anvil.AvailWorkbench
import avail.anvil.streams.StreamStyle
import avail.builder.AvailBuilder
import avail.persistence.cache.Repositories
import javax.swing.SwingWorker

/**
 * A [SwingWorker] foundation for long-running [AvailBuilder] operations.
 *
 * @property workbench
 *   The owning [AvailWorkbench].
 *
 * @constructor
 * Construct a new `AbstractWorkbenchModuleTask`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
abstract class AbstractWorkbenchTask constructor(
	val workbench: AvailWorkbench
) : SwingWorker<Void, Void>()
{
	/** The start time. */
	private var startTimeMillis: Long = 0

	/** The [exception][Throwable] that terminated the build. */
	private var terminator: Throwable? = null

	/** Cancel the current task. */
	fun cancel() = workbench.availBuilder.cancel()

	/**
	 * Report completion (and timing) to the
	 * [transcript][AvailWorkbench.transcript].
	 */
	protected fun reportDone()
	{
		val durationMillis = System.currentTimeMillis() - startTimeMillis
		val status: String?
		val t = terminator
		status = when
		{
			t !== null -> "Aborted (${t.javaClass.simpleName})"
			workbench.availBuilder.shouldStopBuild ->
				workbench.availBuilder.stopBuildReason
			else -> "Done"
		}
		workbench.writeText(
			java.lang.String.format(
				"%s (%d.%03ds).%n",
				status,
				durationMillis / 1000,
				durationMillis % 1000),
			StreamStyle.INFO)
	}

	@Throws(Exception::class)
	override fun doInBackground(): Void?
	{
		if (workbench.taskGate.getAndSet(true))
		{
			// task is running
			return null
		}
		startTimeMillis = System.currentTimeMillis()
		executeTaskThen {
			try
			{
				Repositories.closeAllRepositories()
			}
			finally
			{
				workbench.taskGate.set(false)
			}

		}
		return null
	}

	/**
	 * Execute this `AbstractWorkbenchModuleTask`.
	 *
	 * @param afterExecute
	 *   The lambda to run after the task completes.
	 * @throws Exception If anything goes wrong.
	 */
	@Throws(Exception::class)
	protected abstract fun executeTaskThen(afterExecute: ()->Unit)
}
