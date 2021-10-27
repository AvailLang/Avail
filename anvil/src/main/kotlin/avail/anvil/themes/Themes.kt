/*
 * Themes.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.anvil.themes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import avail.anvil.components.Tooltip
import avail.builder.ModuleRoot

////////////////////////////////////////////////////////////////////////////////
//                              Standard themes.                              //
////////////////////////////////////////////////////////////////////////////////

/** The default Anvil theme. */
@Composable
fun anvilTheme () =
	if (isSystemInDarkTheme()) anvilDarkTheme else anvilLightTheme

/** The default light theme for Anvil. */
val anvilLightTheme = lightColors()

/** The default dark theme for Anvil. */
val anvilDarkTheme = darkColors(
	primary = Color(0xFF3b3b3b),
	background = Color(0xFF3C3F41))

////////////////////////////////////////////////////////////////////////////////
//                             Theme management.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * The dynamically scoped theme for the enclosing view.
 */
val LocalTheme = staticCompositionLocalOf<Colors> {
	throw IllegalStateException("theme unavailable")
}

////////////////////////////////////////////////////////////////////////////////
//                              Version themes.                               //
////////////////////////////////////////////////////////////////////////////////

/** The color to use for version numbers. */
val versionNumberColor get () = Color.Magenta

////////////////////////////////////////////////////////////////////////////////
//                            Module root themes.                             //
////////////////////////////////////////////////////////////////////////////////

/** The color to use for [module root][ModuleRoot] [names][ModuleRoot.name]. */
val moduleRootColor get () = Color.Yellow

////////////////////////////////////////////////////////////////////////////////
//                              Tooltip themes.                               //
////////////////////////////////////////////////////////////////////////////////

/** The color to use for [tooltips][Tooltip]. */
@Composable
fun tooltipColor () =
	if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray

////////////////////////////////////////////////////////////////////////////////
//                            File Tree themes.                               //
////////////////////////////////////////////////////////////////////////////////

object LoadedStyle
{
	val fontStyle = FontStyle.Normal
	val fontWeight = FontWeight.Bold
	val color = Color(0xFFA6B3C2)
	val size = 12.sp
}

object AvailColors
{
	val GREY = Color(0xFFAFB1B3)
	val ROW1 = Color(0xFF414547)
	val ROW2 = Color(0xFF3C3F41)
	val BG = Color(0xFF3C3F41)
	val TITLE = Color(0xFF6D6F71)
}

/**
 *
 */
enum class AlternatingRowColor constructor(val color: Color)
{
	ROW1(Color(0xFF414547))
	{
		override val next: AlternatingRowColor
			get() = ROW2
	},
	ROW2(Color(0xFF3C3F41))
	{
		override val next: AlternatingRowColor
			get() = ROW1
	};

	abstract val next: AlternatingRowColor
}
