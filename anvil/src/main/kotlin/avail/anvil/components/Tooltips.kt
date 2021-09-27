/*
 * Tooltips.kt
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

package avail.anvil.components

import androidx.compose.foundation.BoxWithTooltip
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import avail.anvil.themes.tooltipColor
import java.time.Duration

////////////////////////////////////////////////////////////////////////////////
//                             Standard tooltips.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * Decorator that provides a highly configurable tooltip.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param modifier
 *   The [Modifier] for the tooltip itself.
 * @param shape
 *   The [Shape] of the tooltip itself.
 * @param color
 *   The [Color] of the tooltip itself.
 * @param delay
 *   The delay before the tooltip appears.
 * @param placement
 *   The preferred relative location of the tooltip.
 * @param tooltipContent
 *   The content of the tooltip.
 * @param content
 *   The content that is decorated with the tooltip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Tooltip (
	modifier: Modifier = Modifier.shadow(4.dp),
	shape: Shape = RoundedCornerShape(4.dp),
	color: Color = tooltipColor(),
	delay: Duration = Duration.ofMillis(300),
	placement: TooltipPlacement = StandardTooltipPlacement.TopEnd,
	tooltipContent: @Composable () -> Unit,
	content: @Composable () -> Unit
) {
	BoxWithTooltip(
		tooltip = {
			Surface(
				modifier = modifier,
				shape = shape,
				color = color
			) {
				tooltipContent()
			}
		},
		delay = delay.toMillis().toInt(),
		tooltipPlacement = placement
	) {
		content()
	}
}

/**
 * Decorator that provides a simple textual tooltip.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param text
 *   The body of the tooltip.
 * @param modifier
 *   The [Modifier] for the tooltip itself.
 * @param shape
 *   The [Shape] of the tooltip itself.
 * @param color
 *   The [Color] of the tooltip itself.
 * @param textModifier
 *   The [Modifier] for the body of the tooltip.
 * @param textSize
 *   The [TextUnit] for the body of the tooltip.
 * @param delay
 *   The delay before the tooltip appears.
 * @param placement
 *   The preferred relative location of the tooltip.
 * @param content
 *   The content that is decorated with the tooltip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Tooltip (
	text: AnnotatedString,
	modifier: Modifier = Modifier.shadow(4.dp),
	shape: Shape = RoundedCornerShape(4.dp),
	color: Color = tooltipColor(),
	textModifier: Modifier = Modifier.padding(10.dp),
	textSize: TextUnit = 10.sp,
	delay: Duration = Duration.ofMillis(300),
	placement: TooltipPlacement = StandardTooltipPlacement.TopEnd,
	content: @Composable () -> Unit
) {
	Tooltip(
		modifier = modifier,
		shape = shape,
		color = color,
		delay = delay,
		placement = placement,
		tooltipContent = {
			Text(text = text, modifier = textModifier, fontSize = textSize)
		}
	) {
		content()
	}
}

/**
 * Decorator that provides a simple textual tooltip.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param text
 *   The body of the tooltip.
 * @param modifier
 *   The [Modifier] for the tooltip itself.
 * @param shape
 *   The [Shape] of the tooltip itself.
 * @param color
 *   The [Color] of the tooltip itself.
 * @param textModifier
 *   The [Modifier] for the body of the tooltip.
 * @param textSize
 *   The [TextUnit] for the body of the tooltip.
 * @param delay
 *   The delay before the tooltip appears.
 * @param placement
 *   The preferred relative location of the tooltip.
 * @param content
 *   The content that is decorated with the tooltip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Tooltip (
	text: String,
	modifier: Modifier = Modifier.shadow(4.dp),
	shape: Shape = RoundedCornerShape(4.dp),
	color: Color = tooltipColor(),
	textModifier: Modifier = Modifier.padding(10.dp),
	textSize: TextUnit = 10.sp,
	delay: Duration = Duration.ofMillis(300),
	placement: TooltipPlacement = StandardTooltipPlacement.TopEnd,
	content: @Composable () -> Unit
) {
	Tooltip(
		modifier = modifier,
		shape = shape,
		color = color,
		delay = delay,
		placement = placement,
		tooltipContent = {
			Text(text = text, modifier = textModifier, fontSize = textSize)
		}
	) {
		content()
	}
}

////////////////////////////////////////////////////////////////////////////////
//                        Standard tooltip positions.                         //
////////////////////////////////////////////////////////////////////////////////

/**
 * The standard [tooltip&#32;placements][TooltipPlacement].
 *
 * @author &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
@OptIn(ExperimentalComposeUiApi::class)
object StandardTooltipPlacement
{
	/** The top-start position. */
	val TopStart: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.TopStart,
		offset = DpOffset((-4).dp, (-4).dp)
	)

	/** The top-center position. */
	val TopCenter: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.TopCenter,
		offset = DpOffset(0.dp, (-4).dp)
	)

	/** The top-end position. */
	val TopEnd: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.TopEnd,
		offset = DpOffset(4.dp, (-4).dp)
	)

	/** The center-start position. */
	val CenterStart: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.CenterStart,
		offset = DpOffset((-4).dp, 0.dp)
	)

	/** The center position. */
	val Center: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.Center,
		offset = DpOffset(0.dp, 0.dp)
	)

	/** The center-end position. */
	val CenterEnd: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.CenterEnd,
		offset = DpOffset(4.dp, 0.dp)
	)

	/** The bottom-start position. */
	val BottomStart: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.BottomStart,
		offset = DpOffset((-4).dp, 4.dp)
	)

	/** The bottom-center position. */
	val BottomCenter: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.BottomCenter,
		offset = DpOffset(0.dp, 4.dp)
	)

	/** The bottom-end position. */
	val BottomEnd: TooltipPlacement = TooltipPlacement.CursorPoint(
		alignment = Alignment.BottomEnd,
		offset = DpOffset(4.dp, 4.dp)
	)
}
