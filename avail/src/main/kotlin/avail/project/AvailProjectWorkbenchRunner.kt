/*
 * AvailProjectWorkbenchRunner.kt
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

package avail.project

import avail.anvil.AvailWorkbench
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.setupEnvironment
import org.availlang.artifact.environment.AvailEnvironment.getProjectRootDirectory
import org.availlang.artifact.environment.location.InvalidLocation
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProject.Companion.CONFIG_FILE_NAME
import org.availlang.artifact.environment.project.AvailProjectV1
import org.availlang.artifact.environment.project.LocalSettings
import java.io.File

/**
 * An [AvailWorkbench] runner that uses an [AvailProject] configuration file to
 * start the workbench with the appropriate Avail roots and project-specific
 * configurations.
 *
 * @author Richard Arriaga
 */
object AvailProjectWorkbenchRunner
{
	/**
	 * Launch an [AvailWorkbench] for a specific [AvailProjectWorkbenchRunner].
	 *
	 * @param args
	 *   The command line arguments.
	 * @throws Exception
	 *   If something goes wrong.
	 */
	@Throws(Exception::class)
	@JvmStatic
	fun main(args: Array<String>)
	{
		val pair =
			when (args.size)
			{
				0 ->
				{
					Pair(File(
						getProjectRootDirectory("") +
							File.separator +
							CONFIG_FILE_NAME),
						"Anvil")
				}
				1 ->
				{
					Pair(File(args[0]),  "Anvil")
				}
				2 ->
				{
					System.setProperty("apple.awt.application.name", args[1])
					File(args[0])
					Pair(File(args[0]),  args[1])
				}
				else -> throw RuntimeException(
					"Avail project runner expects either" +
						"\n\t0 arguments: The Avail Project config file, " +
						"`environment-config.json`, is at the project directory " +
						"where this is being run from" +
						"\n\t1 argument: The path, with name, of the project " +
						"config file." +
						"\n\t2 arguments: The path, with name, of the project " +
						"config file and the name of the app.")
			}
		val configFile = pair.first
		System.setProperty("apple.awt.application.name", pair.second)
		setupEnvironment()
		val availProject = try
		{
			AvailProject.from(configFile.absolutePath)
		}
		catch (e: Exception)
		{
			System.err.println(
				"Error parsing project:\n${e.stackTraceToString()}")
			// Hobble on.
			AvailProjectV1(
				"Unknown project",
				true,
				InvalidLocation("", "Unable to parse config file: $e", ""),
				LocalSettings(""))
		}
		System.setProperty(
			AvailWorkbench.DARK_MODE_KEY, availProject.darkMode.toString())

		AvailWorkbench.launchSoloWorkbench(
			GlobalEnvironmentSettings.getGlobalSettings().apply {
				add(availProject, configFile.absolutePath)
			},
			availProject,
			configFile.absolutePath)
	}
}
