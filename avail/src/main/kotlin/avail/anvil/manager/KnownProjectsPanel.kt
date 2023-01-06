/*
 * KnownProjectsPanel.kt
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

import avail.anvil.createScrollPane
import avail.anvil.icons.ProjectManagerIcons.arrowDropDown
import avail.anvil.icons.ProjectManagerIcons.arrowDropUp
import avail.anvil.manager.AvailProjectManager.DisplayedPanel.CREATE_PROJECT
import avail.anvil.environment.GlobalAvailConfiguration
import avail.anvil.projects.KnownAvailProject
import avail.anvil.settings.SettingsView
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * A [JPanel] that displays the [GlobalAvailConfiguration.knownProjects].
 *
 * @author Richard Arriaga
 *
 * @property manager
 *   The parent [AvailProjectManager] window.
 * @property showCreateButton
 *   `true` indicates the create project button should be added to the view;
 *   `false` indicates no create project button should be added.
 */
internal class KnownProjectsPanel constructor(
	internal val manager: AvailProjectManager,
	private val showCreateButton: Boolean = true
) : JPanel()
{
	/**
	 * The [manager]'s [GlobalAvailConfiguration].
	 */
	private val globalConfig: GlobalAvailConfiguration
		get() = manager.globalConfig

	/**
	 * The mapping from [KnownAvailProject.id] to its represented
	 * [KnownProjectRow].
	 */
	private val rowMap = mutableMapOf<String, KnownProjectRow>()

	fun updateFavorite ()
	{
		rowMap.values.forEach { it.updateFavoriteButtonIcon() }
	}

	/**
	 * The appropriately sorted [GlobalAvailConfiguration.knownProjects].
	 */
	private var projectList = globalConfig.knownProjectsByLastOpenedDescending

	/**
	 * Display projects sorted [KnownAvailProject.lastOpened] if `true`;
	 * by [KnownAvailProject.name] if `false`.
	 */
	private var byLastOpened = true
		set(value)
		{
			if (field == value)
			{
				return
			}
			field = value
			repopulateProjects()
		}

	/**
	 * Display projects sorted in ascending order if `true`; descending order if
	 * `false`.
	 */
	private var sortAscending = false
		set(value)
		{
			if (field == value)
			{
				return
			}
			field = value
			repopulateProjects()
		}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val topPanel = JPanel().apply {
		layout = FlowLayout(FlowLayout.LEFT)
		minimumSize = Dimension(720, 50)
		preferredSize = Dimension(750, 50)
		maximumSize = Dimension(750, 50)
		val sortLabel = JLabel("Sort by: ").apply {
			font = font.deriveFont(font.style or Font.BOLD)
		}
		add(sortLabel)
	}

	/**
	 * The [ButtonGroup] that ensures only one sorting option can be selected.
	 */
	private val sortButtonGroup = ButtonGroup()

	/**
	 * Sort by alphabetical order of project name.
	 */
	@Suppress("unused")
	private val alphabetically = JRadioButton("Alphabetically").apply {
		addActionListener {
			byLastOpened = !isSelected
		}
		sortButtonGroup.add(this)
		topPanel.add(this)
	}

	/**
	 * Sort by order of most recently opened project.
	 */
	@Suppress("unused")
	private val lastOpened = JRadioButton("Last Opened").apply {
		isSelected = true
		addActionListener {
			byLastOpened = isSelected
		}
		sortButtonGroup.add(this)
		topPanel.add(this)
	}

	/**
	 * The button that toggles the sort order, [sortAscending], of the
	 * [projectList].
	 */
	@Suppress("unused")
	private val sortButton = JButton(arrowDropUp(19)).apply {
		isContentAreaFilled = false
		isBorderPainted = false
		addActionListener {
			val currentSortAscending = this@KnownProjectsPanel.sortAscending
			val newSortAscending = !currentSortAscending
			this@KnownProjectsPanel.sortAscending = newSortAscending
			SwingUtilities.invokeLater {
				icon =
					if (newSortAscending)
					{
						toolTipText = "Ascending"
						// up arrow ⬆︎
						arrowDropUp(19)
					}
					else
					{
						toolTipText = "Descending"
						// down arrow ⬇︎︎
						arrowDropDown(19)
					}
				repopulateProjects()
			}
		}
		topPanel.add(this)
	}

	/**
	 * The [JPanel] used to display all of the project rows.
	 */
	private val innerPanel: JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		projectList.forEach {
			val row = KnownProjectRow(it, globalConfig, this@KnownProjectsPanel)
			rowMap[it.id] = row
			add(row)
		}
	}

	/**
	 * The [JScrollPane] that contains the [innerPanel].
	 */
	private val scrollPane: JScrollPane = createScrollPane(innerPanel).apply {
		verticalScrollBarPolicy =
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
		horizontalScrollBarPolicy =
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
	}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val bottomPanel = JPanel().apply {
		layout = (FlowLayout(FlowLayout.RIGHT))
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(750, 50)
		maximumSize = Dimension(750, 50)
		background = Color(0x3C, 0x3F, 0x41)

		add(JButton("Settings").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
			preferredSize =
				Dimension(currentWidth + 100, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
			addActionListener {
				SettingsView(manager)
			}
		})
		if (showCreateButton)
		{
			add(JButton("Create").apply {
				isOpaque = true
				val currentHeight = height
				val currentWidth = width
				minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
				preferredSize =
					Dimension(currentWidth + 100, currentHeight + 40)
				maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
				addActionListener {
					manager.displayed = CREATE_PROJECT
					SwingUtilities.invokeLater {
						manager.redraw()
					}
				}
			})
		}
		add(JButton("Open").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
			addActionListener {
				manager.openProject()
			}
		})
	}

	init
	{
		layout = BoxLayout(this, BoxLayout.Y_AXIS).apply {
			alignmentX = Component.LEFT_ALIGNMENT
		}
		border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
		add(topPanel)
		add(scrollPane)
		add(bottomPanel)
	}

	/** Redraw the [KnownProjectRow]s. */
	internal fun repopulateProjects ()
	{
		projectList =
			when
			{
				byLastOpened and sortAscending ->
					globalConfig.knownProjectsByLastOpenedAscending
				byLastOpened and !sortAscending ->
					globalConfig.knownProjectsByLastOpenedDescending
				!byLastOpened and sortAscending ->
					globalConfig.knownProjectsByAlphaAscending
				else -> globalConfig.knownProjectsByAlphaDescending
			}
		rowMap.clear()
		SwingUtilities.invokeLater {
			innerPanel.removeAll()
			projectList.forEach {
				val row = KnownProjectRow(it, globalConfig, this)
				rowMap[it.id] = row
				innerPanel.add(row)
			}
			innerPanel.revalidate()
			innerPanel.repaint()
		}
	}
}
