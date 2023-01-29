/*
 * KnownProjectRow.kt
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
package avail.anvil.manager

import avail.anvil.AvailWorkbench
import avail.anvil.icons.ProjectManagerIcons
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.projects.KnownAvailProject
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.EAST
import java.awt.GridBagLayout
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
 * The [JPanel] that displays a [KnownAvailProject] in
 * [GlobalEnvironmentSettings.knownProjects].
 *
 * @author Richard Arriaga
 *
 * @property project
 *   The [KnownAvailProject] to show.
 * @property config
 *   The [GlobalEnvironmentSettings].
 * @property parentPanel
 *   The [KnownProjectsPanel] this [KnownProjectRow] belongs to.
 */
internal class KnownProjectRow constructor(
	val project: KnownAvailProject,
	val config: GlobalEnvironmentSettings,
	private val parentPanel: KnownProjectsPanel
): JPanel(GridBagLayout())
{
	/**
	 * The [GridBagConstraints] used for all components in [KnownProjectRow].
	 */
	private val constraints = GridBagConstraints().apply {
		anchor = GridBagConstraints.WEST
	}
	init
	{
		border = BorderFactory.createEmptyBorder()
		minimumSize = Dimension(720, 50)
		preferredSize = Dimension(720, 50)
		maximumSize = Dimension(720, 50)
	}

	/**
	 * The [ImageIcon] representing whether or not [project] is the [config]
	 * [favorite][GlobalEnvironmentSettings.favorite] or not. Filled indicates
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
	 * [GlobalEnvironmentSettings.favorite] or to deselect it.
	 */
	private val toggleFavorite: JButton =
		JButton(favoriteIcon).apply {
			isContentAreaFilled = false
			isBorderPainted = false
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
			this@KnownProjectRow.add(
				this,
				constraints)
		}

	/**
	 * Update which icon is displayed for the [toggleFavorite] button.
	 */
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
			}
		}
	}

	/**
	 * The [JPanel] that displays the [project] [name][KnownAvailProject.name]
	 * and the [project] [file location][KnownAvailProject.projectConfigFile].
	 */
	private val namePanel: JPanel = JPanel().apply {
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

	/**
	 * Open this project in the [AvailWorkbench].
	 */
	internal fun openProject()
	{
		parentPanel.manager.openKnownProject(project)
		{
			parentPanel.manager.openKnownProjectDialog?.close()
		}
	}

	/**
	 * The [project] [name][KnownAvailProject.name].
	 */
	@Suppress("unused")
	private val projectName: JLabel = JLabel(project.name).apply {
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
		if (!project.exists)
		{
			foreground = Color(0x73706F)
		}
		namePanel.add(this)
	}

	init
	{
		add(
			namePanel,
			constraints.apply {
				weightx = 1.0
			})
	}

	/**
	 * The button to remove the project from the view.
	 */
	@Suppress("unused")
	private val removeProject: JButton =
		JButton(ProjectManagerIcons.cancelFilled(20)).apply {
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
			this@KnownProjectRow.add(
				this,
				constraints.apply {
					weightx = 0.0
					anchor = EAST
				})
		}

	companion object
	{
		/**
		 * The height to use for the favorite icons.
		 */
		private const val scaledIconHeight = 25

		/**
		 * Icon indicating the [project] is the
		 * [GlobalEnvironmentSettings.favorite].
		 */
		private val favoriteIconChosen =
			ProjectManagerIcons.yellowStarFilled(scaledIconHeight)

		/**
		 * Icon indicating the [project] is not the
		 * [GlobalEnvironmentSettings.favorite].
		 */
		private val favoriteIconNotChosen =
			ProjectManagerIcons.yellowStarUnfilled(scaledIconHeight)
	}
}
