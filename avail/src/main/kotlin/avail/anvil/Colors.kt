/*
 * Colors.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.anvil

import avail.anvil.streams.StreamStyle
import java.awt.Color

/**
 * The abstract palette of system colors. Supports styling Avail source text and
 * UI components.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class SystemColors
{
	/** The background color for the input field of an active console. */
	abstract val inputBackground: Color

	/** The foreground color for the input field of an active console. */
	abstract val inputText: Color

	/** The background color for source text. */
	abstract val codeBackground: Color

	/** The foreground color for source text. */
	abstract val codeText: Color


	/** The color of a [code&#32;guide][CodeGuide]. */
	abstract val codeGuide: Color

	/** The default [color][Color] for [StreamStyle.IN_ECHO]. */
	abstract val streamInput: Color

	/** The default [color][Color] for [StreamStyle.OUT]. */
	abstract val streamOutput: Color

	/** The default [color][Color] for [StreamStyle.ERR]. */
	abstract val streamError: Color

	/** The default [color][Color] for [StreamStyle.INFO]. */
	abstract val streamInfo: Color

	/** The default [color][Color] for [StreamStyle.COMMAND]. */
	abstract val streamCommand: Color

	/** The default [color][Color] for [StreamStyle.BUILD_PROGRESS]. */
	abstract val streamBuildProgress: Color

	/** The default [color][Color] of [StreamStyle.REPORT]. */
	abstract val streamReport: Color

	companion object
	{
		/** The active palette. */
		val active get() =
			if (AvailWorkbench.darkMode) DarkColors else LightColors
	}
}

/**
 * The light-themed palette of system colors.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object LightColors: SystemColors()
{
	override val inputBackground = Color(0xE6, 0xE6, 0xE6, 0xFF)
	override val inputText = Color(0x00, 0x00, 0x00, 0xFF)
	override val codeBackground = Color(0xF7, 0xF7, 0xF7, 0xFF)
	override val codeText = Color(0x00, 0x00, 0x00, 0xEE)
	override val codeGuide = Color(0x00, 0x00, 0x00, 0x77)
	override val streamInput = Color(0x20, 0x90, 0x20, 0xFF)
	override val streamOutput = Color(0x00, 0x00, 0x00, 0xFF)
	override val streamError = Color(0xFF, 0x00, 0x00, 0xFF)
	override val streamInfo = Color(0x00, 0x00, 0xFF, 0xFF)
	override val streamCommand = Color(0xFF, 0x00, 0xFF, 0xFF)
	override val streamBuildProgress = Color(0x80, 0x60, 0x00, 0xFF)
	override val streamReport = Color(0xAA, 0x00, 0x70, 0xFF)
}

/**
 * The dark-themed palette of system colors.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object DarkColors: SystemColors()
{
	override val inputBackground = Color(0x05, 0x36, 0x46, 0xFF)
	override val inputText = Color(0xFF, 0xFF, 0xFF, 0xFF)
	override val codeBackground = Color(0x02, 0x23, 0x2E, 0xFF)
	override val codeText = Color(0xFF, 0xFF, 0xFF, 0xEE)
	override val codeGuide = Color(0xFF, 0xFF, 0xFF, 0x77)
	override val streamInput = Color(0x37, 0x9C, 0x1A, 0xFF)
	override val streamOutput = Color(0xEE, 0xEE, 0xEE, 0xFF)
	override val streamError = Color(0xFF, 0x64, 0x58, 0xFF)
	override val streamInfo = Color(0x9E, 0xC4, 0xF1, 0xFF)
	override val streamCommand = Color(0xAE, 0x8A, 0xBE, 0xFF)
	override val streamBuildProgress = Color(0xDC, 0xC4, 0x57, 0xFF)
	override val streamReport = Color(0xFB, 0x53, 0xAC, 0xFF)
}
