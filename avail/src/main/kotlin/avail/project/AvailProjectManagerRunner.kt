/*
 * AvailProjectManagerRunner.kt
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

import avail.anvil.manager.AvailProjectManager
import avail.anvil.environment.GlobalAvailSettings
import avail.anvil.environment.setupEnvironment
import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.util.SystemInfo
import java.util.concurrent.Semaphore
import javax.swing.UIManager
import kotlin.concurrent.thread

/**
 * A launcher of the [AvailProjectManager].
 *
 * @author Richard Arriaga
 */
object AvailProjectManagerRunner
{
	/**
	 * Launch an [AvailProjectManager].
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
	// Do the slow Swing setup in parallel with other things...
		val swingReady = Semaphore(0)
		if (SystemInfo.isMacOS)
		{
			// enable screen menu bar
			// (moves menu bar from JFrame window to top of screen)
			System.setProperty("apple.laf.useScreenMenuBar", "true")
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Anvil")

			// appearance of window title bars
			// possible values:
			//   - "system": use current macOS appearance (light or dark)
			//   - "NSAppearanceNameAqua": use light appearance
			//   - "NSAppearanceNameDarkAqua": use dark appearance
			System.setProperty("apple.awt.application.appearance", "system")
		}
		thread(name = "Set up LAF") {
			try
			{
				FlatDarculaLaf.setup()
			}
			catch (ex: Exception)
			{
				System.err.println("Failed to initialize LaF")
			}
			UIManager.put("ScrollPane.smoothScrolling", false)
			swingReady.release()
		}
		swingReady.acquire()
		setupEnvironment()
		AvailProjectManager(GlobalAvailSettings.getGlobalSettings())
	}
}
