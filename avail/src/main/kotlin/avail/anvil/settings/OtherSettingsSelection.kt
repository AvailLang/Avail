/*
 * OtherSettingsSelection.kt
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

import avail.anvil.components.TextFieldWithLabel
import avail.anvil.environment.GlobalAvailConfiguration
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane

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

	/** The [GlobalAvailConfiguration]. */
	private val config get() = settingsView.manager.globalConfig

	override fun updateSettingsPane()
	{
		val settingsPanel = JPanel().apply {
			alignmentY = TOP_ALIGNMENT
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			minimumSize = Dimension(700, 690)
			preferredSize = Dimension(700, 690)
			maximumSize = Dimension(700, 690)
			add(Box.createRigidArea(Dimension(0, 20)))
		}
		val guidelines = TextFieldWithLabel("Editor Guide Lines: ").apply {
			toolTipText = "Enter comma separated list of positive integers"
			border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
			val currentHeight = height
			minimumSize = Dimension(690, currentHeight + 40)
			preferredSize = Dimension(690, currentHeight + 40)
			maximumSize = Dimension(690, currentHeight + 40)
			textField.text =
				config.editorGuideLines.joinToString { it.toString() }
			settingsPanel.add(this)
		}
		val apply = JButton("Apply").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 150, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 150, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 150, currentHeight + 40)
			toolTipText = "Save Changes"
			addActionListener {
				// TODO update global config
				val lines = guidelines.textField.text.split(",")
					.map {
						try
						{
							it.trim().toInt()
						}
						catch (e: Throwable)
						{
							return@addActionListener
						}
					}

				config.editorGuideLines.clear()
				config.editorGuideLines.addAll(lines)
				settingsView.manager.globalConfig.saveToDisk()
			}
		}
		val buttonPanel = JPanel().apply {
			layout = (FlowLayout(FlowLayout.RIGHT))
			minimumSize = Dimension(600, 50)
			preferredSize = Dimension(700, 50)
			maximumSize = Dimension(700, 50)
			background = Color(0x3C, 0x3F, 0x41)
			add(apply)
		}
		settingsView.rightPanel.minimumSize = Dimension(700, 750)
		settingsView.rightPanel.preferredSize = Dimension(700, 750)
		settingsView.rightPanel.maximumSize = Dimension(700, 750)
		settingsView.rightPanel.removeAll()
		settingsView.rightPanel.revalidate()
		settingsView.rightPanel.add(settingsPanel)
		settingsView.rightPanel.add(
			JScrollPane(buttonPanel))
		settingsView.rightPanel.repaint()
	}

	init
	{
		init()
	}
}
