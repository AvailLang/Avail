/*
 * KnownProjectRow.java
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

import avail.environment.AvailWorkbench
import avail.environment.icons.LauncherIcons
import avail.environment.projects.GlobalAvailConfiguration
import avail.environment.projects.KnownAvailProject
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * A `KnownProjectRow` is TODO: Document this!
 *
 * @author Richard Arriaga
 */
internal class KnownProjectRow constructor(
	val project: KnownAvailProject,
	val config: GlobalAvailConfiguration,
	val parentPanel: KnownProjectsPanel
): JPanel()
{
	init
	{
		background = Color(0x3C, 0x3F, 0x41, 1)
		border = BorderFactory.createEmptyBorder()
		layout = BoxLayout(this, BoxLayout.X_AXIS)
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(750, 50)
		maximumSize = Dimension(750, 50)
	}

	/**
	 * The [ImageIcon] representing whether or not [project] is the [config]
	 * [favorite][GlobalAvailConfiguration.favorite] or not. Filled indicates
	 * it is; unfilled indicates it is not.
	 */
	private var favoriteIcon =
		if (config.favorite == project.id)
		{
			favoriteIconChosen
		}
		else
		{
			favoriteIconNotChosen
		}

	/**
	 * The button to toggle to either choose the [project] as the
	 * [GlobalAvailConfiguration.favorite] or to deselect it.
	 */
	internal val toggleFavorite: JButton =
		JButton(favoriteIcon).apply {
			isContentAreaFilled = false
			isBorderPainted = false
			background = Color(0x3C, 0x3F, 0x41)
			addActionListener { _ ->
				favoriteIcon =
					if (config.favorite == project.id)
					{
						config.favorite = null
						favoriteIconNotChosen
					}
					else
					{
						config.favorite = project.id
						favoriteIconChosen
					}
				config.saveToDisk()
				parentPanel.updateFavorite()
			}
			this@KnownProjectRow.add(this)
		}

	internal fun updateFavoriteButtonIcon ()
	{
		favoriteIcon =
			if (config.favorite == project.id)
			{
				favoriteIconChosen
			}
			else
			{
				favoriteIconNotChosen
			}
		SwingUtilities.invokeLater {
			toggleFavorite.apply {
				icon = favoriteIcon
				isContentAreaFilled = false
				isBorderPainted = false
				background = Color(0x3C, 0x3F, 0x41)
			}
		}
	}


	/**
	 * The [JPanel] that displays the [project] [name][KnownAvailProject.name]
	 * and the [project] [file location][KnownAvailProject.projectConfigFile].
	 */
	private val namePanel: JPanel = JPanel().apply {
		background = Color(0x3C, 0x3F, 0x41)
		border = BorderFactory.createEmptyBorder()
		toolTipText = "Last Opened: ${project.lastOpenedTimestamp}"
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		addMouseListener(
			object : MouseAdapter()
			{
				override fun mouseClicked(e: MouseEvent)
				{
					if (e.clickCount == 2
						&& e.button == MouseEvent.BUTTON1)
					{
						e.consume()
						openProject()
					}
				}
			})
	}

	internal fun openProject()
	{
		val availProject =
			project.availProject() ?: return
		config.add(availProject, project.projectConfigFile)
		// TODO investigate starting in different process.
//		val pb = ProcessBuilder("java", "-classpath", "B.jar", "B.BMain")
//		val p = pb.start()

		AvailWorkbench.launchWorkbenchWithProject(
			availProject, config, project.projectConfigFile)
		SwingUtilities.invokeLater {
			parentPanel.launcher.hideLauncher()
		}
	}

	/**
	 * The [project] [name][KnownAvailProject.name].
	 */
	@Suppress("unused")
	private val projectName: JLabel = JLabel(project.name).apply {
		foreground = Color(242, 234, 234)
		font = font.deriveFont(font.style or Font.BOLD)
		namePanel.add(this)
	}

	/**
	 * The [project] [file location][KnownAvailProject.projectConfigFile].
	 */
	@Suppress("unused")
	private val location: JLabel = JLabel(project.projectConfigFile).apply {
		font = font.deriveFont(
			font.style or Font.ITALIC,
			(font.size - 3).toFloat())
		foreground = Color(0x80, 0x80, 0x80)
		namePanel.add(this)
	}

	init
	{
		add(namePanel)
	}

	/**
	 * The button to remove the project from the view.
	 */
	@Suppress("unused")
	private val removeProject: JButton =
		JButton(LauncherIcons.cancelFilled(scaledIconHeight)).apply {
			isContentAreaFilled = false
			isBorderPainted = false
			addActionListener {
				if (project.id == config.favorite)
				{
					config.favorite = null
				}
				config.removeProject(project.id)
				parentPanel.repopulateProjects()
			}
			this@KnownProjectRow.add(this)
		}

	companion object
	{
		private const val scaledIconHeight = 20

		/**
		 * Icon indicating the [project] is the
		 * [GlobalAvailConfiguration.favorite].
		 */
		private val favoriteIconChosen =
			LauncherIcons.yellowStarFilled(scaledIconHeight)

		/**
		 * Icon indicating the [project] is not the
		 * [GlobalAvailConfiguration.favorite].
		 */
		private val favoriteIconNotChosen =
			LauncherIcons.yellowStarUnfilled(scaledIconHeight)
	}
}
