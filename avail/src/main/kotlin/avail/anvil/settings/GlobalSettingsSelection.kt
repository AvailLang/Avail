/*
 * GlobalSettingsSelection.kt
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
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.environmentConfigFile
import avail.anvil.environment.globalStylesFile
import avail.anvil.settings.editor.GlobalEnvironmentFileEditor
import avail.anvil.settings.editor.StylesFileEditor
import org.availlang.artifact.environment.location.AvailHome
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * The [SettingPanelSelection] used for accessing global settings from the
 * [AvailHome] directory.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [GlobalSettingsSelection].
 *
 * @param settingsView
 *   The parent [SettingsView].
 * @param workbench
 *   The active [AvailWorkbench].
 */
class GlobalSettingsSelection constructor(
	settingsView: SettingsView,
	private val workbench: AvailWorkbench
): SettingPanelSelection("Global", settingsView)
{

	/** The [GlobalEnvironmentSettings]. */
	internal val config get() = settingsView.globalSettings

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


		JButton("Open Environment Settings File").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 250, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 250, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 250, currentHeight + 40)
			toolTipText = "Opens the raw environment-settings.json file from " +
				"the Avail home directory in a file editor."
			addActionListener {
				workbench.openFileEditor(environmentConfigFile)
				{
					GlobalEnvironmentFileEditor(workbench)
				}
			}
			settingsPanel.add(this)
		}

		JButton("Open Global Styles File").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 250, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 250, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 250, currentHeight + 40)
			toolTipText = "Opens the raw global-styles.json file from the " +
				"Avail home directory in a file editor."
			addActionListener {
				workbench.openFileEditor(globalStylesFile)
				{
					StylesFileEditor(workbench, it, it)
				}
			}

			settingsPanel.add(this)
		}

		settingsView.rightPanel.minimumSize = Dimension(700, 750)
		settingsView.rightPanel.preferredSize = Dimension(700, 750)
		settingsView.rightPanel.maximumSize = Dimension(700, 750)
		settingsView.rightPanel.removeAll()
		settingsView.rightPanel.revalidate()
		settingsView.rightPanel.add(settingsPanel)
		settingsView.rightPanel.repaint()
	}

	init
	{
		init()
	}
}
