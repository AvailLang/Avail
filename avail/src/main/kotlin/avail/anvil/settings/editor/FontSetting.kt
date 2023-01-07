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

import avail.anvil.components.ComboWithLabel
import avail.anvil.environment.GlobalAvailSettings
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import javax.swing.BorderFactory
import javax.swing.JComponent

/**
 * The [EditorSetting] for changing the
 * [GlobalAvailSettings.codePaneFontSize].
 *
 * @author Richard Arriaga
 */
internal class FontSetting constructor(
	override val editorSettings: EditorSettingsSelection,
	override val parent: JComponent
): EditorSetting
{
	/** The current value in [GlobalAvailSettings.editorGuideLines]. */
	private var original: String = editorSettings.config.font

	private val monospaceFonts =
		FontRenderContext(
			null,
			RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT,
			RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT
		).let { frc ->
			GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
				.filter { it.family.lowercase().contains("mono") }
				.filter {
					val iBounds = it.getStringBounds("i", frc)
					val mBounds = it.getStringBounds("m", frc)
					iBounds.width == mBounds.width
				}
				.toMutableSet().apply { Font(Font.MONOSPACED, Font.PLAIN, 13) }
				.toList().apply {
					sortedBy { it.name }
				}
		}

	private val combo =
		ComboWithLabel<String>(
			"Font: ",
			monospaceFonts.map { it.name }.toTypedArray(),
			emptySpaceRight = 7.0
		).apply {
			toolTipText = "Enter comma separated list of positive integers"
			border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
			val currentHeight = height
			minimumSize = Dimension(690, currentHeight + 55)
			preferredSize = Dimension(690, currentHeight + 55)
			maximumSize = Dimension(690, currentHeight + 55)
			combo.selectedItem = original
		}

	private val selection get() = combo.combo.selectedItem as? String

	override val component: JComponent get() = combo

	override fun reset()
	{
		combo.combo.selectedItem = original
	}

	override fun update()
	{
		val selected = selection ?: return
		editorSettings.config.font = selected
		original = combo.combo.selectedItem as String
	}

	override fun changeReady(): Boolean =
		selection?.let {
			it.isNotBlank()
				&& it != original
		} ?: false

	override fun hasValidContent(): Boolean = selection != null
}
