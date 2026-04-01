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

import avail.anvil.AvailWorkbench
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.setupEnvironment
import avail.anvil.invokeAndWaitIfNecessary
import avail.anvil.manager.AvailProjectManager
import org.pushingpixels.radiance.theming.api.RadianceThemingCortex.GlobalScope.setSkin
import org.pushingpixels.radiance.theming.api.skin.NightShadeSkin
import org.pushingpixels.radiance.theming.api.skin.SaharaSkin
import javax.swing.UIManager

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
		System.setProperty("apple.awt.application.name", "Anvil")
		if (System.getProperty("os.name").startsWith("Mac"))
		{
			// enable screen menu bar
			// (moves menu bar from JFrame window to top of screen)
			System.setProperty("apple.laf.useScreenMenuBar", "true")
			System.setProperty(
				"com.apple.mrj.application.apple.menu.about.name", "Anvil")
			System.setProperty("apple.awt.application.appearance", "system")
		}

		// Set up Radiance skin synchronously on EDT before any Swing components
		invokeAndWaitIfNecessary {
			try
			{
				val skin = when
				{
					AvailWorkbench.darkMode -> NightShadeSkin()
					else -> SaharaSkin()
				}
				setSkin(skin)
				UIManager.put("ScrollPane.smoothScrolling", false)
			}
			catch (ex: Exception)
			{
				System.err.println(
					"Failed to initialize Look and Feel: ${ex.message}")
				ex.printStackTrace()
			}
		}

		setupEnvironment()
		invokeAndWaitIfNecessary {
			try
			{
				AvailProjectManager(GlobalEnvironmentSettings.getGlobalSettings())
			}
			catch (e: Exception)
			{
				e.printStackTrace()
			}
		}
	}
}
