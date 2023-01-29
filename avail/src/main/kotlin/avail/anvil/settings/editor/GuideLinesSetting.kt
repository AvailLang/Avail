/*
 * GuideLinesSetting.kt
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

package avail.anvil.settings.editor

import avail.anvil.components.TextFieldWithLabelButtonValidationText
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.icons.ProjectManagerIcons
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent

/**
 * The [EditorSetting] for changing the
 * [GlobalEnvironmentSettings.editorGuideLines].
 *
 * @author Richard Arriaga
 */
internal class GuideLinesSetting constructor(
	override val editorSettings: EditorSettingsSelection,
	override val parent: JComponent
): EditorSetting
{
	/** The current value in [GlobalEnvironmentSettings.editorGuideLines]. */
	private var original: String =
		editorSettings.config.editorGuideLines.joinToString { it.toString() }

	private val textField =
		TextFieldWithLabelButtonValidationText("Editor Guide Lines: ", 5.0)
		{
			if (hasValidContent()) null
			else "Invalid Entry"
		}.apply {
			toolTipText = "Enter comma separated list of positive integers"
			border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
			val currentHeight = height
			minimumSize = Dimension(690, currentHeight + 55)
			preferredSize = Dimension(690, currentHeight + 55)
			maximumSize = Dimension(690, currentHeight + 55)
			textField.text = original
			button.apply {
				isContentAreaFilled = false
				isBorderPainted = false
				text = ""
				icon = ProjectManagerIcons.refresh(23)
				addActionListener {
					reset()
				}
			}
		}

	override val component: JComponent get() = textField

	/** The parsed guide lines */
	internal val lines: List<Int>? get() =
		textField.input.split(",").map {
			try
			{
				it.trim().toInt()
			}
			catch (e: Throwable)
			{
				return null
			}
		}

	override fun reset()
	{
		textField.textField.text = original
		textField.checkInput()
	}

	override fun update()
	{
		val l = lines ?: return
		editorSettings.config.editorGuideLines.apply {
			clear()
			addAll(l)
		}
		original = textField.input
	}

	override fun changeReady(): Boolean =
		textField.input.isNotBlank()
			&& textField.input != original
			&& lines != null

	override fun hasValidContent(): Boolean = lines != null

	init
	{
		textField.checkInput()
	}
}
