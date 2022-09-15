/*
 * AvailLaunchWindow.kt
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

package avail.environment.launcher

import avail.environment.projects.GlobalAvailConfiguration
import org.availlang.artifact.environment.project.AvailProject
import java.awt.Color
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * The Avail start up window. This window is displayed when an Avail development
 * environment is started with no particular [AvailProject] file.
 *
 * @author Richard Arriaga
 */
class AvailProjectManagerWindow constructor(
	internal val globalConfig: GlobalAvailConfiguration
): JFrame("Avail")
{
	fun hideLauncher ()
	{
		isVisible = false
	}
	init
	{
		defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
		val projectPane = KnownProjectsPanel(globalConfig, this)
		background = Color(0x3C, 0x3F, 0x41)
		add(projectPane)
		minimumSize = Dimension(600, 400)
		preferredSize = Dimension(750, 600)
		pack()
		isVisible = true
	}
}
