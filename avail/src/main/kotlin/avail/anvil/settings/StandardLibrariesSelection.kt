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

import avail.anvil.environment.availStandardLibraries
import avail.anvil.icons.ProjectManagerIcons
import avail.anvil.versions.MavenCentralAPI
import org.availlang.artifact.environment.AvailEnvironment
import java.awt.Dimension
import java.awt.Font
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
 * The [SettingPanelSelection] used for showing other settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [StandardLibrariesSelection].
 *
 * @param settingsView
 *   The parent [SettingsView].
 */
class StandardLibrariesSelection constructor(
	settingsView: SettingsView
): SettingPanelSelection("Standard Library", settingsView)
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
		val libraries = availStandardLibraries
		val latest =
			this@StandardLibrariesSelection.settingsView.manager.latestVersion
		if (latest.isNotEmpty())
		{
			val latestJarName = "avail-stdlib-$latest.jar"
			val match = libraries.firstOrNull {
				it.name == latestJarName
			}
			if (match == null)
			{
				panel.add(NewLibAvailableRow(latest))
				panel.add(Box.createRigidArea(Dimension(0, 30)))
			}
		}
		libraries.forEach { panel.add(LibRow(it)) }
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
	 * A [JPanel] that displays an Avail standard library in
	 * [AvailEnvironment.availHomeLibs].
	 *
	 * @property latest
	 *   The latest available version
	 */
	inner class NewLibAvailableRow constructor(
		private val latest: String
	): JPanel(GridBagLayout())
	{
		/**
		 * The [GridBagConstraints] used for all components in this
		 * [NewLibAvailableRow].
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
				add(
					JLabel("New Avail Standard Library Available: $latest")
					.apply {
						font = font.deriveFont(font.style or Font.ITALIC)
					})
				this@NewLibAvailableRow.add(
					this,
					constraints.apply {
						weightx = 1.0
					})
			}

			JButton("Install").apply {
				addActionListener {
					val selection = JOptionPane.showConfirmDialog(
						this@NewLibAvailableRow,
						"Install 'avail-stdlib-$latest.jar'?",
						"Install Library",
						JOptionPane.YES_NO_OPTION)
					if (selection == 0)
					{
						SwingUtilities.invokeLater {
							val byteCount =
								MavenCentralAPI.downloadAvailStandardLib(latest)
							if (byteCount == 0L)
							{
								System.err.println(
									"Failed to download avail-stdlib: $latest")
							}
							this@StandardLibrariesSelection.updateSettingsPane()
						}
					}
				}
				this@NewLibAvailableRow.add(
					this,
					constraints.apply {
						weightx = 1.0
						anchor = GridBagConstraints.EAST
					})
			}
		}
	}

	/**
	 * A [JPanel] that displays an Avail standard library in
	 * [AvailEnvironment.availHomeLibs].
	 *
	 * @property lib
	 *   The library [File].
	 */
	inner class LibRow constructor(
		private val lib: File
	): JPanel(GridBagLayout())
	{
		/**
		 * The [GridBagConstraints] used for all components in this [LibRow].
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
				add(JLabel(lib.name))
				this@LibRow.add(
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
						this@LibRow,
						"Delete '${lib.name}'?",
						"Delete Library",
						JOptionPane.YES_NO_OPTION)
					if (selection == 0)
					{
						lib.delete()
						SwingUtilities.invokeLater {
							this@StandardLibrariesSelection.updateSettingsPane()
						}
					}
				}
				this@LibRow.add(
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
