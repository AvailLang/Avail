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
import avail.utility.DirectoryWatcherInterface
import avail.utility.JvmDirectoryWatcher
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProject.Companion.STYLE_FILE_NAME
import org.availlang.artifact.environment.project.AvailProject.Companion.TEMPLATE_FILE_NAME
import org.availlang.artifact.environment.project.LocalSettings.Companion.LOCAL_SETTINGS_FILE
import java.io.File

/**
 * Responsible for watching changes to [AvailProject] configuration files.
 *
 * @author Richard Arriaga
 */
class ProjectWatcher constructor(val workbench: AvailWorkbench)
{
	private fun handleConfigUpdate(eventPath: java.nio.file.Path)
	{
		try
		{
			val parent = eventPath.toFile().parent
			val isRoot = workbench.projectConfigDirectory != parent
			when
			{
				eventPath.endsWith(TEMPLATE_FILE_NAME) ->
				{
					if (isRoot)
					{
						workbench.availProject
							.rootFromConfigDirPath(parent)
							?.refreshTemplates(parent)
					}
					else
					{
						workbench.availProject.refreshTemplates(
							workbench.projectConfigDirectory)
					}
					workbench.refreshTemplates()
					workbench.writeText(
						"configuration file refreshed: $eventPath\n",
						StreamStyle.INFO)
				}
				eventPath.endsWith(STYLE_FILE_NAME) ->
				{
					if (isRoot)
					{
						workbench.availProject
							.rootFromConfigDirPath(parent)
							?.refreshStyles(parent)
					}
					else
					{
						workbench.availProject.refreshStyles(
							workbench.projectConfigDirectory)
					}
					workbench.refreshStylesheetAction.runAction()
					workbench.writeText(
						"configuration file refreshed: $eventPath\n",
						StreamStyle.INFO)
				}
				eventPath.endsWith(LOCAL_SETTINGS_FILE) ->
				{
					if (isRoot)
					{
						workbench.availProject
							.rootFromConfigDirPath(parent)
							?.refreshLocalSettings(parent)
					}
					else
					{
						workbench.availProject.refreshLocalSettings(
							workbench.projectConfigDirectory)
					}
					workbench.refreshStylesheetAction.runAction()
					workbench.writeText(
						"configuration file refreshed: $eventPath\n",
						StreamStyle.INFO)
				}
			}
			workbench.refreshStylesheetAction.runAction()
			workbench.refreshTemplates()
			workbench.writeText(
				// TODO what do we need to report here?
				"configuration file refreshed: $eventPath\n",
				StreamStyle.INFO
			)
		}
		catch (e: Throwable)
		{
			workbench.errorStream().println(
				"Failed to process configuration file update: $eventPath:\n"
					+ e.stackTraceToString())
		}
	}

	/** The [DirectoryWatcherInterface] that observes configuration files. */
	@Suppress("unused")
	private val configurationWatcher: DirectoryWatcherInterface =
		JvmDirectoryWatcher(
			path = File(workbench.projectConfigDirectory).toPath(),
			onCreated = ::handleConfigUpdate,
			onModified = ::handleConfigUpdate,
			onDeleted = { eventPath ->
				workbench.errorStream().println(
					"configuration file deleted: $eventPath")
			}
		).launch("configuration watcher: ${workbench.projectConfigDirectory}")
}
