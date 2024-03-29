/*
 * StandardLibrariesSelection.kt
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

package avail.anvil.settings

import avail.anvil.AvailWorkbench
import avail.anvil.icons.ProjectManagerIcons
import org.availlang.artifact.environment.project.LocalSettings
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * The [SettingPanelSelection] used for showing all
 * [LocalSettings.loadModulesOnStartup].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [LoadOnStartSelection].
 *
 * @param settingsView
 *   The parent [SettingsView].
 * @param workbench
 *   The active [AvailWorkbench].
 */
class LoadOnStartSelection constructor(
	settingsView: SettingsView,
	private val workbench: AvailWorkbench
): SettingPanelSelection("Load on Anvil Start", settingsView)
{
	override fun updateSettingsPane()
	{
		val panel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			minimumSize = Dimension(700, 800)
			preferredSize = Dimension(700, 800)
			maximumSize = Dimension(700, 800)
			border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
		}
		val locals = workbench.availProject.availProjectRoots
			.map {
				it.localSettings
			}
		locals.forEach { localSettings ->
			localSettings.loadModulesOnStartup.toList().sorted().forEach {
				panel.add(ModuleRow(localSettings, it))
			}

		}
		settingsView.rightPanel.removeAll()
		settingsView.rightPanel.revalidate()
		settingsView.rightPanel.add(panel)
		settingsView.rightPanel.add(JScrollPane().apply {
			setViewportView(panel)
			horizontalScrollBarPolicy =
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
			verticalScrollBarPolicy =
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
		})
		settingsView.rightPanel.repaint()
	}

	init
	{
		init()
	}

	/**
	 * A [JPanel] that displays the qualified name of an Avail module that is
	 * loaded on Anvil start.
	 *
	 * @property localSettings
	 *   The [LocalSettings] the module is listed in.
	 * @property qualifiedName
	 *   The qualified name of the module that is loaded on Anvil start.
	 */
	inner class ModuleRow constructor(
		private val localSettings: LocalSettings,
		private val qualifiedName: String
	): JPanel(GridBagLayout())
	{
		/**
		 * The [GridBagConstraints] used for all components in this [ModuleRow].
		 */
		private val constraints = GridBagConstraints().apply {
			anchor = GridBagConstraints.WEST
		}

		init
		{
			border = BorderFactory.createEmptyBorder()
			minimumSize = Dimension(675, 35)
			preferredSize = Dimension(675, 35)
			maximumSize = Dimension(675, 35)

			JPanel().apply {
				border = BorderFactory.createEmptyBorder()
				layout = BoxLayout(this, BoxLayout.X_AXIS)
				add(Box.createRigidArea(Dimension(10, 0)))
				add(JLabel(qualifiedName.substring(1)))
				this@ModuleRow.add(
					this,
					constraints.apply {
						weightx = 1.0
					})
			}

			JButton(deleteIcon).apply {
				isContentAreaFilled = false
				isBorderPainted = false
				addActionListener {
					val selection = JOptionPane.showConfirmDialog(
						this@ModuleRow,
						"Remove '$qualifiedName'?",
						"Remove from Load on Start",
						JOptionPane.YES_NO_OPTION)
					if (selection == 0)
					{
						localSettings.loadModulesOnStartup.remove(qualifiedName)
						localSettings.save()
						val path = File(
							File(localSettings.configDir),
							LocalSettings.LOCAL_SETTINGS_FILE).absolutePath
						workbench.openFileEditors[path]?.reloadFileFromDisk()
						SwingUtilities.invokeLater {
							this@LoadOnStartSelection.updateSettingsPane()
						}
					}
				}
				this@ModuleRow.add(
					this,
					constraints.apply {
						weightx = 1.0
						anchor = GridBagConstraints.EAST
					})
			}
		}
	}

	companion object
	{
		/**
		 * The height to use for the favorite icons.
		 */
		private const val scaledIconHeight = 20

		/**
		 * The delete icon.
		 */
		private val deleteIcon get() =
			ProjectManagerIcons.cancelFilled(scaledIconHeight)
	}
}
