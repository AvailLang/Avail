/*
 * AvailProjectManager.kt
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

import avail.AvailRuntimeConfiguration
import avail.anvil.AvailWorkbench
import avail.anvil.manager.AvailProjectManager.DisplayedPanel.*
import avail.anvil.environment.GlobalAvailConfiguration
import avail.anvil.environment.projectManagerLayoutFile
import avail.anvil.projects.KnownAvailProject
import avail.anvil.settings.SettingsView
import avail.anvil.versions.MavenCentralAPI
import avail.anvil.versions.SearchResponse
import avail.anvil.window.LayoutConfiguration
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.jsonObject
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Taskbar
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

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
class AvailProjectManager constructor(
	val globalConfig: GlobalAvailConfiguration
): JFrame("Avail")
{
	/**
	 * The latest Avail Standard Library version.
	 */
	var latestVersion = ""

	/**
	 * `true` if this [AvailProjectManager] has been opened and the init block
	 * run; `false` indicates this [AvailProjectManager] is brand new and is
	 * starting up.
	 */
	private var initialOpenComplete = false

	/**
	 * The set of [AvailWorkbench]s opened by this [AvailProjectManager].
	 */
	private val openWorkbenches = mutableSetOf<AvailWorkbench>()

	/**
	 * The opened [OpenKnownProjectDialog] or `null` if dialog not open.
	 */
	internal var openKnownProjectDialog: OpenKnownProjectDialog? = null

	/**
	 * The opened [CreateProjectDialog] or `null` if dialog not open.
	 */
	internal var createProjectDialog: CreateProjectDialog? = null

	/**
	 * The [LayoutConfiguration] that describes the position of this
	 * [AvailProjectManager].
	 */
	private val layoutConfiguration: LayoutConfiguration =
		LayoutConfiguration.from(File(projectManagerLayoutFile).readText())

	/**
	 * Answer the already opened [AvailWorkbench] that is running the
	 * [AvailProject] with the given [AvailProject.id].
	 *
	 * @param projectId
	 *   The [AvailProject.id] to use to locate an opened [AvailWorkbench].
	 * @return
	 *   The open [AvailWorkbench] or `null` if that project is not open in an
	 *   [AvailWorkbench].
	 */
	private fun openedWorkbench (projectId: String): AvailWorkbench? =
		openWorkbenches.firstOrNull { it.availProject.id == projectId }

	/**
	 * The action to perform when an [AvailWorkbench] is launched from this
	 * [AvailProjectManager].
	 *
	 * @param workbench
	 *   The launched [AvailWorkbench].
	 */
	fun onWorkbenchOpen (workbench: AvailWorkbench)
	{
		openWorkbenches.add(workbench)
		hideProjectManager()
	}

	/**
	 * The action to perform when an [AvailWorkbench] launched from this
	 * [AvailProjectManager] is closed.
	 *
	 * @param workbench
	 *   The closed [AvailWorkbench].
	 */
	fun onWorkbenchClose (workbench: AvailWorkbench)
	{
		openWorkbenches.remove(workbench)
		if (openWorkbenches.isEmpty())
		{
			showProjectManager()
		}
	}

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
		minimumSize = Dimension(750, 350)
		preferredSize = Dimension(750, 350)
		maximumSize = Dimension(750, 350)
	}

	/**
	 * Save the window position of this [AvailProjectManager].
	 */
	fun saveWindowPosition()
	{
		layoutConfiguration.extendedState = extendedState
		if (extendedState == NORMAL)
		{
			// Only capture the bounds if it's not zoomed or minimized.
			layoutConfiguration.placement = bounds
		}
		File(projectManagerLayoutFile).writeText(
			layoutConfiguration.stringToStore())
	}

	init
	{
		setKnownProjectsSize()
		addWindowListener(object: WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent?)
			{
				saveWindowPosition()
				globalConfig.saveToDisk()
			}
		})
		layoutConfiguration.placement?.let {
			this.bounds = it
		}
	}

	/**
	 * The [DisplayedPanel] presently occupying the view of this
	 * [AvailProjectManager].
	 */
	var displayed = KNOWN_PROJECTS

	/**
	 * Hide this [AvailProjectManager].
	 */
	fun hideProjectManager()
	{
		isVisible = false
	}

	/**
	 * Show this [AvailProjectManager].
	 */
	private fun showProjectManager()
	{
		isVisible = true
		// TODO this means all workbenches have been closed so we need to rebuild
		//  the menu bar to remove the workbench menus.
	}

	/**
	 * The [JComponent] being displayed, either [KnownProjectsPanel] or
	 * [CreateProjectPanel].
	 */
	private var displayedComponent: JComponent =
		KnownProjectsPanel(this)

	/**
	 * Draw this [AvailProjectManager].
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
					KnownProjectsPanel(this)
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
								project,
								globalConfig,
								path,
								projectManager = this)
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
		if (!initialOpenComplete)
		{
			if (!openFavorite())
			{
				isVisible = true
			}
		}
		else
		{
			isVisible = true
		}
	}

	/**
	 * Redraw this [AvailProjectManager].
	 */
	internal fun redraw ()
	{
		remove(displayedComponent)
		draw()
	}

	/**
	 * An enumeration that refers to the [JComponent]s that can be displayed
	 * inside this [AvailProjectManager].
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
		MavenCentralAPI.searchAvailStdLib(
		{
			val rsp = SearchResponse.parse(it)
			if (rsp == null)
			{
				System.err.println("Couldn't parse response:\n$it")
				return@searchAvailStdLib
			}
			latestVersion = rsp.latestLibVersion
		}
		){ c, m ->
			System.err.println("Failed to get latest version: $c\n$m")
		}
	}

	/**
	 * Open the [CreateProjectDialog].
	 *
	 * @param workbench
	 *   The [AvailWorkbench] that is launching the dialog to create a project.
	 */
	fun createProject (workbench: AvailWorkbench)
	{
		createProjectDialog = createProjectDialog?.let {
			it.toFront()
			it
		} ?: CreateProjectDialog(this, workbench)
	}

	/**
	 * Open the provided [KnownAvailProject] in an [AvailWorkbench].
	 *
	 * @param project
	 *   The [AvailProject] to open.
	 * @param configPath
	 *   The [KnownAvailProject.configFilePath] of the [AvailProject].
	 * @param invokeLater
	 *   The lambda to run inside [SwingUtilities.invokeLater] once the project
	 *   is opened.
	 * @return
	 *   `true` if the project is being opened or already opened; `false`
	 *   otherwise.
	 */
	private fun openProject (
		project: AvailProject,
		configPath: String,
		invokeLater: () -> Unit
	): Boolean
	{
		openedWorkbench(project.id)?.let {
			it.toFront()
			SwingUtilities.invokeLater {
				invokeLater()
			}
			return true
		}
		globalConfig.add(project, configPath)
		AvailWorkbench.launchWorkbenchWithProject(
			project,
			globalConfig,
			configPath,
			projectManager = this@AvailProjectManager)
		SwingUtilities.invokeLater {
			hideProjectManager()
			invokeLater()
		}
		return true
	}

	/**
	 * Open a [KnownAvailProject] in an [AvailWorkbench].
	 *
	 * @param workbench
	 *   The [AvailWorkbench] that is launching the dialog to open a known
	 *   project.
	 */
	fun openKnownProject (workbench: AvailWorkbench)
	{
		openKnownProjectDialog = openKnownProjectDialog?.let {
			it.toFront()
			it
		} ?: OpenKnownProjectDialog(this, workbench)
	}

	/**
	 * Open the provided [KnownAvailProject] in an [AvailWorkbench].
	 *
	 * @param knownAvailProject
	 *   The [KnownAvailProject] to open.
	 * @param invokeLater
	 *   The lambda to run inside [SwingUtilities.invokeLater] once the project
	 *   is opened.
	 * @return
	 *   `true` if the project is being opened; `false` otherwise.
	 */
	fun openKnownProject (
		knownAvailProject: KnownAvailProject,
		invokeLater: () -> Unit = {}
	): Boolean
	{
		val project = knownAvailProject.availProject() ?: return false
		val configPath = knownAvailProject.configFilePath
		return openProject(project, configPath, invokeLater)
	}

	/**
	 * Open the [GlobalAvailConfiguration.favoriteKnownProject] if one is
	 * selected.
	 *
	 * @return
	 *   `true` if one is set and is being opened; `false` otherwise.
	 */
	private fun openFavorite (): Boolean
	{
		globalConfig.favoriteKnownProject?.let {
			return openKnownProject(it)
		}
		return false
	}

	/**
	 * Launch a dialog to select an [AvailProject] config file from disk to open
	 * in an [AvailWorkbench].
	 */
	fun openProject ()
	{
		JFileChooser().apply chooser@ {
			dialogTitle = "Select Avail Project Configuration File to Open"
			fileSelectionMode = JFileChooser.FILES_ONLY
			fileFilter = FileNameExtensionFilter("*.json", "json")
			addChoosableFileFilter(
				object : FileFilter()
				{
					override fun getDescription(): String =
						"Avail Project Config (*.json)"

					override fun accept(f: File): Boolean =
						f.isFile
							&& f.canWrite()
							&& f.absolutePath.lowercase().endsWith(".json")
				})
			val result = showDialog(
				this@AvailProjectManager,
				"Select Project Config File")
			if (result == JFileChooser.APPROVE_OPTION)
			{
				val projectConfigFile = selectedFile
				val projectPath = projectConfigFile.absolutePath
					.removeSuffix(projectConfigFile.name)
					.removeSuffix(File.separator)
				val project = try
				{
					AvailProject.from(
						projectPath,
						jsonObject(projectConfigFile.readText(Charsets.UTF_8)))
				}
				catch (e: Throwable)
				{
					e.printStackTrace()
					null
				} ?: return@chooser

				globalConfig.add(project, projectConfigFile.absolutePath)
				AvailWorkbench.launchWorkbenchWithProject(
					project,
					globalConfig,
					projectConfigFile.absolutePath,
					projectManager = this@AvailProjectManager)
				SwingUtilities.invokeLater {
					hideProjectManager()
				}
			}
		}
	}

	init
	{
		if (System.getProperty("os.name").startsWith("Mac"))
		{
			// Set up desktop and taskbar features.
			Desktop.getDesktop().run {
				setDefaultMenuBar(jMenuBar)
//				setAboutHandler { aboutAction.showDialog() } // TODO
				setPreferencesHandler {
					SettingsView(
						globalConfig,
						this@AvailProjectManager,
						latestVersion)
				}
			}
			Taskbar.getTaskbar().run {
				iconImage = ImageIcon(
					AvailWorkbench::class.java.classLoader.getResource(
						"resources/workbench/AvailHammer.png")
				).image
				setIconBadge(AvailRuntimeConfiguration.activeVersionSummary)
			}
		}
	}
}
