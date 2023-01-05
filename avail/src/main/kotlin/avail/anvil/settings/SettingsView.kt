/*
 * SettingsView.kt
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
import avail.anvil.manager.AvailProjectManager
import avail.anvil.shortcuts.KeyboardShortcutCategory
import org.availlang.artifact.environment.AvailEnvironment
import java.awt.Color
import java.awt.Dialog.ModalityType.DOCUMENT_MODAL
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * A [JFrame] that displays configurable global settings.
 *
 * @author Richard Arriaga
 *
 * @property manager
 *   The parent [AvailProjectManager] window.
 */
class SettingsView constructor (
	internal val manager: AvailProjectManager,
): JDialog(manager, "Settings", DOCUMENT_MODAL)
{
	/**
	 * The currently selected [SettingPanelSelection].
	 */
	var selected: SettingPanelSelection

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	internal val rightPanel = JPanel().apply {
		minimumSize = Dimension(700, 800)
		preferredSize = Dimension(700, 800)
		maximumSize = Dimension(700, 800)
	}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val leftPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		minimumSize = Dimension(150, 750)
		preferredSize = Dimension(150, 750)
		maximumSize = Dimension(150, 750)
		border = BorderFactory.createLineBorder(Color(0x616365))
		val shortcuts = ShortcutsSelection(this@SettingsView).apply {
			label.font = label.font.deriveFont(font.style or Font.BOLD)
			background = Color(0x55, 0x58, 0x5A)
		}
		selected = shortcuts
		add(shortcuts)
		add(StandardLibrariesSelection(this@SettingsView))
		add(OtherSettingsSelection(this@SettingsView))
	}

	init
	{
		minimumSize = Dimension(850, 800)
		preferredSize = Dimension(850, 800)
		maximumSize = Dimension(850, 800)
		add(JPanel().apply {
			layout = BoxLayout(this, BoxLayout.X_AXIS)
			add(JScrollPane(leftPanel).apply {
				horizontalScrollBarPolicy =
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
				verticalScrollBarPolicy =
					ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
			})
			add(rightPanel)
		})
		selected.updateSettingsPane()
		setLocationRelativeTo(manager)
		isVisible = true
	}
}

/**
 * A [JPanel] that represents a settings category.
 *
 * @author Richard Arriaga
 *
 * @property labelText
 *   The text to show in the panel label.
 * @param settingsView
 *   The parent [SettingsView].
 */
sealed class SettingPanelSelection constructor(
	private val labelText: String,
	protected val settingsView: SettingsView
):JPanel(FlowLayout(FlowLayout.LEFT))
{
	/**
	 * The [JLabel] that displays the [labelText].
	 */
	val label =  JLabel(labelText)

	fun init()
	{
		minimumSize = Dimension(150, 35)
		preferredSize = Dimension(150, 35)
		maximumSize = Dimension(150, 35)
		addMouseListener(object: MouseAdapter()
		{
			override fun mouseClicked(e: MouseEvent)
			{
				if (e.clickCount == 1)
				{
					select()
				}
			}
		})
		add(label)
	}

	/**
	 * Update the [settingsView] right panel with the appropriate settings
	 * configuration screen associated with this [SettingPanelSelection].
	 */
	abstract fun updateSettingsPane ()

	/**
	 * Select this [SettingPanelSelection].
	 */
	fun select ()
	{
		if (this == settingsView.selected) return
		label.font = label.font.deriveFont(font.style or Font.BOLD)
		background = Color(0x55, 0x58, 0x5A)
		settingsView.selected.deselect()
		settingsView.selected = this
		updateSettingsPane()
	}

	/**
	 * Deselect this [SettingPanelSelection].
	 */
	private fun deselect ()
	{
		label.font = label.font.deriveFont(font.style or Font.PLAIN)
		background = settingsView.background
	}
}

/**
 * The [SettingPanelSelection] used for showing the shortcut settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [ShortcutsSelection].
 *
 * @param settingsView
 *   The parent [SettingsView].
 */
class ShortcutsSelection constructor(
	settingsView: SettingsView
): SettingPanelSelection("Shortcuts", settingsView)
{
	override fun updateSettingsPane()
	{
		val tabs = JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT)

		KeyboardShortcutCategory.values().forEach {
			tabs.addTab(
				it.display,
				ShortcutsPanel(it, settingsView.manager.globalConfig)
					.redrawShortcuts())
		}
		tabs.minimumSize = Dimension(700, 750)
		tabs.preferredSize = Dimension(700, 750)
		tabs.maximumSize = Dimension(700, 750)
		settingsView.rightPanel.removeAll()
		settingsView.rightPanel.revalidate()
		settingsView.rightPanel.add(tabs)
		settingsView.rightPanel.repaint()
	}

	init
	{
		init()
	}
}

/**
 * The [SettingPanelSelection] used for showing other settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [OtherSettingsSelection].
 *
 * @param settingsView
 *   The parent [SettingsView].
 */
class OtherSettingsSelection constructor(
	settingsView: SettingsView
): SettingPanelSelection("Other", settingsView)
{
	override fun updateSettingsPane()
	{
		// TODO build screen
		settingsView.rightPanel.removeAll()
		settingsView.rightPanel.revalidate()
		settingsView.rightPanel.add(Box.createRigidArea(Dimension(0, 70)))
		settingsView.rightPanel.add(JLabel("WIP…"))
		settingsView.rightPanel.repaint()
	}

	init
	{
		init()
	}
}

/**
 * The [SettingPanelSelection] used for showing other settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [OtherSettingsSelection].
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
	 * @property lib
	 *   The library [File].
	 */
	inner class LibRow constructor(
		private val lib: File
	): JPanel(GridBagLayout())
	{
		/**
		 * The [GridBagConstraints] used for all components in [TemplateRow].
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
