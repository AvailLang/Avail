/*
 * ProjectWatcher.kt
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

package avail.anvil

import avail.anvil.streams.StreamStyle
import avail.utility.launch
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.methvin.watcher.hashing.FileHasher
import org.availlang.artifact.environment.project.AvailProject
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * Responsible for watching changes to [AvailProject] configuration files on
 * disk using a [DirectoryWatcher].
 *
 * @author Richard Arriaga
 */
class ProjectWatcher constructor(val workbench: AvailWorkbench)
{
	/**
	 * The [DirectoryWatcher] that observes the [project][AvailProject]
	 * configuration files for changes.
	 */
	@Suppress("unused")
	private val configurationWatcher = DirectoryWatcher.builder()
		.logger(NOPLogger.NOP_LOGGER)
		.fileHasher(FileHasher.LAST_MODIFIED_TIME)
		.path(File(workbench.projectConfigDirectory).toPath())
		.listener { event ->
			try
			{
				when (event.eventType()!!)
				{
					DirectoryChangeEvent.EventType.DELETE ->
					{

						workbench.errorStream().println(
							"configuration file deleted: ${event.path()}")
					}
					DirectoryChangeEvent.EventType.CREATE,
					DirectoryChangeEvent.EventType.MODIFY ->
					{
						workbench.refreshStylesheetAction.runAction()
						workbench.writeText(
							// TODO what do we need to report here
							"configuration file refreshed: "
								+ "${event.path()}\n",
							StreamStyle.INFO
						)
					}
					DirectoryChangeEvent.EventType.OVERFLOW ->
					{
						// No implementation required.
					}
				}
			}
			catch (e: Throwable)
			{
				workbench.errorStream().println(
					"Failed to process configuration file update: "
						+ "$event.eventType():\n"
						+ "${event.path()}:\n"
						+ e.stackTraceToString()
				)
			}
		}
		.build()
		.launch("configuration watcher: ${workbench.projectConfigDirectory}")
}
