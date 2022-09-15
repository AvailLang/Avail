/*
 * KnownProjectsPanel.java
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
import avail.environment.projects.GlobalAvailConfiguration
import avail.environment.projects.KnownAvailProject
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.jsonObject
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * A [JPanel] that displays the [GlobalAvailConfiguration.knownProjects].
 *
 * @author Richard Arriaga
 *
 * @property config
 *   The [GlobalAvailConfiguration] to display content from.
 */
internal class KnownProjectsPanel constructor(
	internal val config: GlobalAvailConfiguration,
	internal val launcher: AvailProjectManagerWindow
) : JPanel()
{
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
	private var projectList = config.knownProjectsByLastOpenedDescending

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
		layout = (FlowLayout(FlowLayout.LEFT))
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(750, 50)
		maximumSize = Dimension(750, 50)
		background = Color(0x3C, 0x3F, 0x41)
		val sortLabel = JLabel("Sort by: ").apply {
			foreground = Color(0xA9, 0xB7, 0xC6)
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
		foreground = Color(242, 234, 234)
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
		foreground = Color(242, 234, 234)
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
	private val sortButton = JButton("\u2B06\uFE0E").apply {
		foreground = Color(0xA9, 0xB7, 0xC6)
		isContentAreaFilled = false
		isBorderPainted = false
		addActionListener {
			val currentSortAscending = this@KnownProjectsPanel.sortAscending
			val newSortAscending = !currentSortAscending
			this@KnownProjectsPanel.sortAscending = newSortAscending
			SwingUtilities.invokeLater {
				text =
					if (newSortAscending)
					{
						toolTipText = "Ascending"
						// up arrow ⬆︎
						"\u2B06\uFE0E"
					}
					else
					{
						toolTipText = "Descending"
						// down arrow ⬇︎︎
						"\u2B07\uFE0E"
					}
				repopulateProjects()
			}
		}
		topPanel.add(this)
	}

	/**
	 * The [JButton] to open a project from the file system.
	 */
	@Suppress("unused")
	val openProjectButton = JButton("Open").apply {
		foreground = Color(0xBB, 0xBB, 0xBB)
		background = Color(0x55, 0x58, 0x5A)
		isOpaque = true
		border = BorderFactory.createLineBorder(
			Color(0xBB, 0xBB, 0xBB), 1, true)
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 50, currentHeight + 30)
		preferredSize = Dimension(currentWidth + 50, currentHeight + 30)
		maximumSize = Dimension(currentWidth + 50, currentHeight + 30)
		addActionListener {
			JFileChooser().apply chooser@ {
				dialogTitle = "Select Avail Project Configuration File to Open"
				fileSelectionMode = JFileChooser.FILES_ONLY
				fileFilter = FileNameExtensionFilter("*.json", "json")
				addChoosableFileFilter(
					object : FileFilter()
					{
						override fun getDescription(): String
						{
							return "Avail Project Config (*.json)"
						}

						override fun accept(f: File?): Boolean
						{
							assert(f !== null)
							return f!!.isFile
								&& f.canWrite()
								&& f.absolutePath.lowercase().endsWith(".json")
						}
					})
				val result = showDialog(
					this@KnownProjectsPanel, "Set Documentation Path")
				if (result == JFileChooser.APPROVE_OPTION)
				{
					val configFile = selectedFile
					val projectPath = configFile.absolutePath.removeSuffix(configFile.name)
						.removeSuffix(File.separator)
					val project = try
						{
							AvailProject.from(
								projectPath,
								jsonObject(configFile.readText(Charsets.UTF_8)))
						}
						catch (e: Throwable)
						{
							e.printStackTrace()
							null
						} ?: return@chooser
					config.add(project, configFile.absolutePath)
					AvailWorkbench.launchWorkbenchWithProject(
						project, config, configFile.absolutePath)
					SwingUtilities.invokeLater {
						launcher.hideLauncher()
					}
				}
			}
		}
		topPanel.add(this)
	}

	/**
	 * The [JScrollPane] that contains the [innerPanel].
	 */
	private val scrollPane: JScrollPane = JScrollPane().apply {
		background = Color(0x3C, 0x3F, 0x41)
	}

	/**
	 * The [JPanel] used to display all of the project rows.
	 */
	private val innerPanel: JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Color(0x3C, 0x3F, 0x41)
		projectList.forEach {
			val row = KnownProjectRow(it, config, this@KnownProjectsPanel)
			rowMap[it.id] = row
			add(row)
			if (it.id == config.favorite)
			{
				row.openProject()
			}
		}
		scrollPane.setViewportView(this)
	}

	init
	{
		background = Color(0x3C, 0x3F, 0x41)
		layout = BoxLayout(this, BoxLayout.Y_AXIS).apply {
			alignmentX = Component.LEFT_ALIGNMENT
		}
		border = BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(Color(0xA9, 0xB7, 0xC6)),
			"Projects",
			TitledBorder.LEADING,
			TitledBorder.DEFAULT_POSITION,
			font.deriveFont(font.style or Font.BOLD),
			Color(0xA9, 0xB7, 0xC6))
		add(topPanel)
		add(scrollPane)
	}

	/**
	 *
	 */
	internal fun repopulateProjects ()
	{
		projectList =
			when
			{
				byLastOpened and sortAscending ->
					config.knownProjectsByLastOpenedAscending
				byLastOpened and !sortAscending ->
					config.knownProjectsByLastOpenedDescending
				!byLastOpened and sortAscending ->
					config.knownProjectsByAlphaAscending
				else -> config.knownProjectsByAlphaDescending
			}
		rowMap.clear()
		SwingUtilities.invokeLater {
			innerPanel.removeAll()
			projectList.forEach {
				val row = KnownProjectRow(it, config, this)
				rowMap[it.id] = row
				innerPanel.add(row)
			}
			innerPanel.revalidate()
			innerPanel.repaint()
		}
	}
}
