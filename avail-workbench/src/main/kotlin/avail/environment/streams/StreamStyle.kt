/*
 * StreamStyle.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.environment.streams

import avail.environment.AvailWorkbench
import java.awt.Color
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument

/**
 * An abstraction of the styles of streams used by the workbench.
 *
 * @property styleName
 *   The name of this style.
 * @constructor
 * Construct a new `StreamStyle`.
 *
 * @param light
 *   The color of foreground text in this style for light mode.
 * @param dark
 *   The color of foreground text in this style for dark mode.
 */
enum class StreamStyle constructor(
	private val styleName: String,
	light: Color,
	dark: Color)
{
	/** The stream style used to echo user input. */
	IN_ECHO(
		"input",
		light = Color(32, 144, 32),
		dark = Color(55, 156, 26)),

	/** The stream style used to display normal output. */
	OUT(
		"output",
		light = Color.BLACK,
		dark = Color(238, 238, 238)),

	/** The stream style used to display error output. */
	ERR(
		"error",
		light = Color.RED,
		dark = Color(231, 70, 68)),

	/** The stream style used to display informational text. */
	INFO(
		"info",
		light = Color.BLUE,
		dark = Color(83, 148, 236)),

	/** The stream style used to echo commands. */
	COMMAND(
		"command",
		light = Color.MAGENTA,
		dark = Color(174, 138, 190)),

	/** Progress updates produced by a build. */
	BUILD_PROGRESS(
		"build",
		light = Color(128, 96, 0),
		dark = Color(220, 196, 87));

	/** Combine the light and dark into an AdaptiveColor. */
	private val adaptiveColor = AvailWorkbench.AdaptiveColor(light, dark)

	/**
	 * Create my corresponding [Style] in the [StyledDocument].
	 *
	 * @param doc
	 * The document in which to define this style.
	 */
	internal fun defineStyleIn(doc: StyledDocument)
	{
		val defaultStyle =
			StyleContext.getDefaultStyleContext().getStyle(
				StyleContext.DEFAULT_STYLE)
		val style = doc.addStyle(styleName, defaultStyle)
		StyleConstants.setForeground(style, adaptiveColor.color)
	}

	/**
	 * Extract this style from the given [document][StyledDocument].
	 * Look up my [styleName].
	 *
	 * @param doc
	 *   The document.
	 * @return The [Style].
	 */
	fun styleIn(doc: StyledDocument): Style
	{
		return doc.getStyle(styleName)
	}
}
