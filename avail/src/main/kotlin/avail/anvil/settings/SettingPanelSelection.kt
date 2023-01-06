/*
 * SettingPanelSelection.kt
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

import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

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
): JPanel(FlowLayout(FlowLayout.LEFT))
{
	/**
	 * The [JLabel] that displays the [labelText].
	 */
	val label = JLabel(labelText)

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
