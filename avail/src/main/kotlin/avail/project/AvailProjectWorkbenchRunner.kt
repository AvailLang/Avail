/*
 * AvailProject.kt
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

import avail.environment.AvailWorkbench
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProject.Companion.CONFIG_FILE_NAME
import org.availlang.artifact.environment.AvailEnvironment.getProjectRootDirectory
import org.availlang.artifact.environment.AvailEnvironment.optionallyCreateAvailUserHome
import org.availlang.json.jsonObject
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
		val configFile =
			when (args.size)
			{
				0 ->
				{
					File("${getProjectRootDirectory("")}/$CONFIG_FILE_NAME")
				}
				1 -> File(args[0])
				else -> throw RuntimeException(
					"Avail project runner expects either" +
						"\n\t0 arguments: The Avail Project config file, " +
						"`avail-config.json`, is at the project directory " +
						"where this is being run from" +
						"\n\t1 argument: The path, with name, of the project " +
						"config file.")
			}
		optionallyCreateAvailUserHome()
		val projectPath = configFile.absolutePath.removeSuffix(configFile.name)
		val availProject =
			AvailProject.from(
				projectPath,
				jsonObject(configFile.readText(Charsets.UTF_8)))
		System.setProperty(
			AvailWorkbench.DARK_MODE_KEY, availProject.darkMode.toString())

		AvailWorkbench.launchWorkbenchWithProject(availProject)
	}
}
