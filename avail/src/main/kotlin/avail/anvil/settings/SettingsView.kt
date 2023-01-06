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

import avail.anvil.manager.AvailProjectManager
import java.awt.Color
import java.awt.Dialog.ModalityType.DOCUMENT_MODAL
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
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

