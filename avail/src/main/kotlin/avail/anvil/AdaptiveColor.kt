/*
 * AdaptiveColor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.anvil

import java.awt.Color

/**
 * An abstraction for a color that's dependent on light versus dark mode.
 *
 * @property light
 *   The color to use in light mode.
 * @property light
 *   The color to use in dark mode.
 * @constructor
 * Construct a new `AdaptiveColor`.
 */
data class AdaptiveColor constructor(
	val light: Color,
	val dark: Color)
{
	val color: Color get() = if (AvailWorkbench.darkMode) dark else light

	val hex: String get() = color.hex

	fun blend(
		otherColor: Color,
		selfWeight: Float = 0.5f
	) : AdaptiveColor
	{
		assert(selfWeight in 0.0 .. 1.0)
		return AdaptiveColor(
			blend(
				light,
				otherColor,
				selfWeight),
			blend(
				dark,
				otherColor,
				selfWeight))
	}

	companion object
	{
		val Color.hex: String
			get() = String.format("#%02x%02x%02x", red, green, blue)

		fun blend(
			selfColor: Color,
			otherColor: Color,
			selfWeight: Float
		) : Color
		{
			assert(selfWeight in 0.0 .. 1.0)
			val otherWeight = 1.0f - selfWeight
			val vector = selfColor.getRGBComponents(null)
				.zip(otherColor.getRGBComponents(null))
				.map { (a, b) -> a * selfWeight + b * otherWeight }
				.toFloatArray()
			return Color(vector[0], vector[1], vector[2], vector[3])
		}
	}
}
