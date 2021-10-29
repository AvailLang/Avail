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

package org.availlang.ide.anvil.streams

import java.awt.Color

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
	val light: Color,
	val dark: Color)
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
}
