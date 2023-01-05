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

package avail.anvil.projects.manager

import avail.anvil.settings.ShortcutsPanel
import avail.anvil.shortcuts.KeyboardShortcutCategory
import java.awt.Color
import java.awt.Dialog.ModalityType.DOCUMENT_MODAL
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.ScrollPaneConstants

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
	 * The currently selected [SettingPanelLabel].
	 */
	var selected: SettingPanelLabel

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
		minimumSize = Dimension(100, 750)
		preferredSize = Dimension(100, 750)
		maximumSize = Dimension(100, 750)
		border = BorderFactory.createLineBorder(Color(0x616365))
		val shortcutsLabel = ShortcutsLabel(this@SettingsView).apply {
			label.font = label.font.deriveFont(font.style or Font.BOLD)
			background = Color(0x55, 0x58, 0x5A)
		}
		selected = shortcutsLabel
		add(shortcutsLabel)
		add(OtherSettingsLabel(this@SettingsView))
	}

	init
	{
		minimumSize = Dimension(800, 800)
		preferredSize = Dimension(800, 800)
		maximumSize = Dimension(800, 800)
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
sealed class SettingPanelLabel constructor(
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
		minimumSize = Dimension(100, 35)
		preferredSize = Dimension(100, 35)
		maximumSize = Dimension(100, 35)
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
	 * configuration screen associated with this [SettingPanelLabel].
	 */
	abstract fun updateSettingsPane ()

	/**
	 * Select this [SettingPanelLabel].
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
	 * Deselect this [SettingPanelLabel].
	 */
	private fun deselect ()
	{
		label.font = label.font.deriveFont(font.style or Font.PLAIN)
		background = settingsView.background
	}
}

/**
 * The [SettingPanelLabel] used for showing the shortcut settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [ShortcutsLabel].
 *
 * @param settingsView
 *   The parent [SettingsView].
 */
class ShortcutsLabel constructor(
	settingsView: SettingsView
): SettingPanelLabel("Shortcuts", settingsView)
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
 * The [SettingPanelLabel] used for showing other settings.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [OtherSettingsLabel].
 *
 * @param settingsView
 *   The parent [SettingsView].
 */
class OtherSettingsLabel constructor(
	settingsView: SettingsView
): SettingPanelLabel("Other", settingsView)
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
