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

package avail.environment

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
	/** The palette's extreme color. */
	abstract val extreme: Color

	/** The background color for ordinary text. */
	abstract val textBackground: Color

	/** The background color for code. */
	abstract val codeBackground: Color

	/** The background color for an active console. */
	abstract val consoleBackground: Color

	/** The base color for code. */
	abstract val baseCode: Color

	/** The foreground color for an active console. */
	abstract val consoleText: Color

	/** A strong gray color. */
	abstract val strongGray: Color

	/** A weak gray color. */
	abstract val weakGray: Color

	/** A lilac color. */
	abstract val lilac: Color

	/** A solid magenta color. */
	abstract val magenta: Color

	/** A transparent magenta color. */
	abstract val transparentMagenta: Color

	/** A solid rose color. */
	abstract val rose: Color

	/** A transparent rose color. */
	abstract val transparentRose: Color

	/** A (faint) transparent rose color. */
	abstract val faintTransparentRose: Color

	/** A mango color. */
	abstract val mango: Color

	/** A solid mustard color. */
	abstract val mustard: Color

	/** A transparent mustard color. */
	abstract val transparentMustard: Color

	/** A solid green color. */
	abstract val green: Color

	/** A transparent green color. */
	abstract val transparentGreen: Color

	/** A (faint) transparent green color. */
	abstract val faintTransparentIndigo: Color

	/** A blue color. */
	abstract val blue: Color

	/** A color that almost hides something. */
	abstract val deemphasize: Color

	/**
	 * The guide color.
	 */
	abstract val guide: Color

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
	override val extreme = Color(0xFF, 0xFF, 0xFF, 0xFF)
	override val textBackground = Color(0xFF, 0xFF, 0xFF, 0x4A)
	override val codeBackground = Color(0xF7, 0xF7, 0xF7, 0xFF)
	override val consoleBackground = Color(0xE6, 0xE6, 0xE6, 0xFF)
	override val baseCode = Color(0x00, 0x00, 0x00, 0xEE)
	override val consoleText = Color(0x0, 0x0, 0x0, 0xFF)
	override val strongGray = Color(0x00, 0x00, 0x00, 0x77)
	override val weakGray = Color(0x00, 0x00, 0x00, 0x44)
	override val lilac = Color(0x81, 0x74, 0xFF, 0xFF)
	override val magenta = Color(0xD0, 0x3B, 0x83, 0xFF)
	override val transparentMagenta = Color(0xD0, 0x3B, 0x83, 0xAA)
	override val rose = Color(0xDC, 0x44, 0x44, 0xFF)
	override val transparentRose = Color(0xDC, 0x44, 0x44, 0x88)
	override val faintTransparentRose = Color(0xDC, 0x44, 0x44, 0x22)
	override val mango = Color(0xEE, 0x73, 0x1B, 0xFF)
	override val mustard = Color(0xE8, 0xB0, 0x00, 0xFF)
	override val transparentMustard = Color(0xE8, 0xB0, 0x00, 0x18)
	override val green = Color(0x4C, 0x85, 0x3E, 0xFF)
	override val transparentGreen = Color(0x4C, 0x85, 0x3E, 0x88)
	override val blue = Color(0x22, 0x94, 0xC6, 0xFF)
	override val faintTransparentIndigo = Color(0x4B, 0x35, 0xA9, 0x20)
	override val deemphasize = Color(0x80, 0x80, 0x80, 0x80)
	override val guide = strongGray
}

/**
 * The dark-themed palette of system colors.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object DarkColors: SystemColors()
{
	override val extreme = Color(0x00, 0x00, 0x00, 0xFF)
	override val textBackground = Color(0x21, 0x12, 0x19, 0x4D)
	override val codeBackground = Color(0x02, 0x23, 0x2E, 0xFF)
	override val consoleBackground = Color(0x05, 0x36, 0x46, 0xFF)
	override val baseCode = Color(0xFF, 0xFF, 0xFF, 0xEE)
	override val consoleText = Color(0xFF, 0xFF, 0xFF, 0xFF)
	override val strongGray = Color(0xFF, 0xFF, 0xFF, 0x77)
	override val weakGray = Color(0xFF, 0xFF, 0xFF, 0x44)
	override val lilac = Color(0x66, 0x59, 0xE2, 0xFF)
	override val magenta = Color(0xD2, 0x5B, 0x94, 0xFF)
	override val transparentMagenta = Color(0xD2, 0x5B, 0x94, 0xAA)
	override val rose = Color(0xDC, 0x44, 0x44, 0xFF)
	override val transparentRose = Color(0xDC, 0x44, 0x44, 0x88)
	override val faintTransparentRose = Color(0xDC, 0x44, 0x44, 0x22)
	override val mango = Color(0xFF, 0x88, 0x36, 0xFF)
	override val mustard = Color(0xFF, 0xD6, 0x59, 0xFF)
	override val transparentMustard = Color(0xFF, 0xD6, 0x59, 0x18)
	override val green = Color(0x78, 0xB6, 0x69, 0xFF)
	override val transparentGreen = Color(0x78, 0xB6, 0x69, 0x88)
	override val blue = Color(0x38, 0xB1, 0xE5, 0xFF)
	override val faintTransparentIndigo = Color(0x3B, 0x27, 0x95, 0x30)
	override val deemphasize = Color(0x60, 0x60, 0x60, 0x80)
	override val guide = strongGray
}
