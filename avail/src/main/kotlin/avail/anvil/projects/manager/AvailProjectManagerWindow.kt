/*
 * AvailProjectManagerWindow.kt
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

package avail.anvil.projects.manager

import avail.anvil.AvailWorkbench
import avail.anvil.projects.manager.AvailProjectManagerWindow.DisplayedPanel.*
import avail.anvil.projects.GlobalAvailConfiguration
import org.availlang.artifact.environment.project.AvailProject
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * The Avail start up window. This window is displayed when an Avail development
 * environment is started with no particular [AvailProject] file.
 *
 * @author Richard Arriaga
 *
 * @property globalConfig
 *   The [GlobalAvailConfiguration] that provides information about the Avail
 *   environment for the entire computer.
 */
class AvailProjectManagerWindow constructor(
	private val globalConfig: GlobalAvailConfiguration
): JFrame("Avail")
{
	/**
	 * Set the window's preferred size for displaying
	 * [known projects][KnownProjectsPanel].
	 */
	private fun setKnownProjectsSize ()
	{
		minimumSize = Dimension(750, 400)
		preferredSize = Dimension(750, 600)
		maximumSize = Dimension(750, 900)
	}

	/**
	 * Set the window's preferred size for displaying
	 * [create project view][CreateProjectPanel].
	 */
	private fun setCreateProjectsSize ()
	{
		minimumSize = Dimension(750, 300)
		preferredSize = Dimension(750, 300)
		maximumSize = Dimension(750, 300)
	}

	init
	{
		setKnownProjectsSize()
	}
	var displayed = KNOWN_PROJECTS

	/**
	 * Hide this [AvailProjectManagerWindow].
	 */
	fun hideProjectManager()
	{
		isVisible = false
	}

	/**
	 * The [JComponent] being displayed, either [KnownProjectsPanel] or
	 * [CreateProjectPanel].
	 */
	private var displayedComponent: JComponent =
		KnownProjectsPanel(globalConfig, this)

	/**
	 * Draw this [AvailProjectManagerWindow].
	 */
	private fun draw ()
	{
		defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

		var newHeight = height
		displayedComponent =
			when (displayed)
			{
				KNOWN_PROJECTS ->
				{
					title = "Avail Projects"
					newHeight = 600
					setKnownProjectsSize()
					KnownProjectsPanel(globalConfig, this)
				}
				CREATE_PROJECT ->
				{
					title = "Create Project"
					setCreateProjectsSize()
					newHeight = 300
					CreateProjectPanel(
						globalConfig,
						{ project, path ->
							AvailWorkbench.launchWorkbenchWithProject(
								project, globalConfig, path)
							SwingUtilities.invokeLater {
								displayed = KNOWN_PROJECTS
								hideProjectManager()
							}
						})
					{
						displayed = KNOWN_PROJECTS
						SwingUtilities.invokeLater {
							redraw()
						}
					}
				}
			}
		add(displayedComponent)
		setBounds(x, y, width, newHeight)
		isVisible = true
	}

	/**
	 * Redraw this [AvailProjectManagerWindow].
	 */
	internal fun redraw ()
	{
		remove(displayedComponent)
		draw()
	}

	/**
	 * An enumeration that refers to the [JComponent]s that can be displayed
	 * inside this [AvailProjectManagerWindow].
	 */
	enum class DisplayedPanel
	{
		/** Represents the [KnownProjectsPanel]. */
		KNOWN_PROJECTS,

		/** Represents the [CreateProjectPanel]. */
		CREATE_PROJECT
	}

	init
	{
		draw()
	}
}
