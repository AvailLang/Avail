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

import avail.AvailRuntime
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.environment.AvailWorkbench
import avail.files.FileManager
import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.util.SystemInfo
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProject.Companion.CONFIG_FILE_NAME
import org.availlang.artifact.environment.AvailEnvironment.getProjectRootDirectory
import org.availlang.artifact.environment.AvailEnvironment.optionallyCreateAvailUserHome
import org.availlang.json.jsonObject
import java.io.File
import java.util.concurrent.Semaphore
import javax.swing.UIManager
import kotlin.concurrent.thread

/**
 * The representation of an Avail
 *
 * @author Richard Arriaga
 */
object AvailProjectRunner
{
	/**
	 * Launch an [AvailWorkbench] for a specific [AvailProjectRunner].
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
		val rootDirectory = getProjectRootDirectory(args)
		optionallyCreateAvailUserHome()
		val configFile = File("$rootDirectory/$CONFIG_FILE_NAME")
		val config =
			AvailProject.from(
				rootDirectory,
				jsonObject(configFile.readText(Charsets.UTF_8)))
		System.setProperty(
			AvailWorkbench.DARK_MODE_KEY, config.darkMode.toString())

		val fileManager = FileManager()
		val failedResolutions = mutableListOf<String>()
		val moduleRoots = ModuleRoots(fileManager, "") {}
		val semaphore = Semaphore(0)
		val rootResolutionStart = System.currentTimeMillis()
		config.roots.values.forEach {
			moduleRoots.addRoot(
				it.name,
				it.location.fullPath(rootDirectory))
			{ fails ->
				if (fails.isNotEmpty())
				{
					failedResolutions.addAll(fails)
				}
				semaphore.release()
			}
		}
		semaphore.acquireUninterruptibly()
		val resolutionTime =
			System.currentTimeMillis() - rootResolutionStart

		// Do the slow Swing setup in parallel with other things...
		val swingReady = Semaphore(0)
		val runtimeReady = Semaphore(0)
		if (SystemInfo.isMacOS)
		{
			// enable screen menu bar
			// (moves menu bar from JFrame window to top of screen)
			System.setProperty("apple.laf.useScreenMenuBar", "true")

			// appearance of window title bars
			// possible values:
			//   - "system": use current macOS appearance (light or dark)
			//   - "NSAppearanceNameAqua": use light appearance
			//   - "NSAppearanceNameDarkAqua": use dark appearance
			System.setProperty("apple.awt.application.appearance", "system")
		}
		thread(name = "Set up LAF") {
			if (AvailWorkbench.darkMode)
			{
				try
				{
					FlatDarculaLaf.setup()
				}
				catch (ex: Exception)
				{
					System.err.println("Failed to initialize LaF")
				}
				UIManager.put("ScrollPane.smoothScrolling", false)
			}
			swingReady.release()
		}

		lateinit var resolver: ModuleNameResolver
		lateinit var runtime: AvailRuntime
//		thread(name = "Parse renames")
//		{
//			var reader: Reader? = null
//			try
//			{
//				val renames = System.getProperty("availRenames", null)
//				reader = when (renames)
//				{
//					// Load the renames from preferences further down.
//					null -> StringReader("")
//					// Load renames from file specified on the command line...
//					else -> BufferedReader(
//						InputStreamReader(
//							FileInputStream(File(renames)),
//							StandardCharsets.UTF_8))
//				}
//				val renameParser = RenamesFileParser(reader, roots)
//				resolver = renameParser.parse()
//				if (renames === null)
//				{
//					// Now load the rename rules from preferences.
//					AvailWorkbench.loadRenameRulesInto(resolver)
//				}
//			}
//			finally
//			{
//				IO.closeIfNotNull(reader)
//			}
//			runtime = AvailRuntime(resolver, fileManager)
//			runtimeReady.release()
//		}

		runtimeReady.acquire()
		swingReady.acquire()
//		AvailWorkbench.launchWorkbench(
//			runtime,
//			fileManager,
//			resolver,
//			failedResolutions,
//			resolutionTime,
//			args)
	}
}
