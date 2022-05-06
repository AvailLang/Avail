/*
 * LineNumbering.kt
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

import avail.environment.AvailWorkbench.AdaptiveColor.Companion.blend
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JViewport

/**
 * A generic model interface which defines an underlying component with line
 * numbers.  This allows adaptation of more than just text areas.
 *
 * @author Greg Cope - public domain code, adapted by mark@availlang.org.
 */
interface LineNumberModel
{
	/**
	 * @return
	 *   The number of lines in this model.
	 */
	val numberOfLines: Int

	/**
	 * Return a [Rectangle] defining the location in the view of the parameter
	 * [line]. Only the y and height fields are required by callers.
	 *
	 * @param line
	 *   A line number in the model.
	 *
	 * @return
	 *   A Rectangle defining the view coordinates of the [line].
	 */
	fun lineToRectangle(line: Int): Rectangle

	/**
	 * Answer the [IntRange] of visible line numbers.
	 */
	fun visibleLines(): IntRange
}

/** Pixel padding on left and right. */
const val HORIZONTAL_PADDING = 1

/** Pixel padding below line number text.  TODO Fix to use baseline. */
const val VERTICAL_PADDING = 3

/** How to horizontally align line numbers. */
enum class Alignment
constructor(
	val placement: (String, Int, FontMetrics)->Int)
{
	LEFT_ALIGNMENT({ _, _, _ -> HORIZONTAL_PADDING }),
	RIGHT_ALIGNMENT({ text, totalWidth, metrics ->
		totalWidth - metrics.stringWidth(text) - HORIZONTAL_PADDING
	}),
	CENTER_ALIGNMENT({ text, totalWidth, metrics ->
		(totalWidth - metrics.stringWidth(text)) / 2
	});
}

/**
 * A JComponent that is used to draw line numbers. This JComponent should be
 * added as a row header view in a JScrollPane. Based upon the LineNumberModel
 * provided, this component will draw the line numbers as needed.
 *
 * @constructor
 * Construct a new instance for the given [LineNumberModel].
 *
 * @property lineNumberModel
 *   The [LineNumberModel] to query about line number geometry.
 * @property alignment
 *   The optional horizontal [Alignment] of the line numbers.
 *
 * @author Greg Cope - public domain code, adapted by mark@availlang.org.
 */
class LineNumberComponent
constructor(
	private val lineNumberModel: LineNumberModel,
	var alignment: Alignment = Alignment.LEFT_ALIGNMENT
) : JComponent()
{
	/**
	 * Check and adjust the width of this component based upon the line numbers.
	 */
	fun adjustWidth()
	{
		graphics ?: return
		val max = lineNumberModel.numberOfLines
		val width = graphics.fontMetrics.stringWidth(max.toString()) +
			2 * HORIZONTAL_PADDING
		val c = parent as JComponent? ?: return
		val parent = c.parent
		val dimension = when
		{
			c is JViewport && parent is JScrollPane ->
				parent.viewport.view.preferredSize
			else -> c.preferredSize
		}
		if (width != preferredSize.width)
		{
			preferredSize = Dimension(
				width + 2 * HORIZONTAL_PADDING, dimension.height)
			revalidate()
			repaint()
		}
	}

	public override fun paintComponent(g: Graphics)
	{
		super.paintComponent(g)
		val g2d = g as Graphics2D
		g2d.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON)
		g.setColor(background)
		g2d.fillRect(0, 0, width, height)
		g.setColor(blend(foreground, background, 0.3f))
		val metrics = g.fontMetrics
		val totalWidth = preferredSize.width
		for (i in lineNumberModel.visibleLines())
		{
			val rect: Rectangle = lineNumberModel.lineToRectangle(i)
			val text = (i + 1).toString()
			val yPosition = rect.y + rect.height - VERTICAL_PADDING
			val xPosition = alignment.placement(text, totalWidth, metrics)
			g2d.drawString((i + 1).toString(), xPosition, yPosition)
		}
	}
}
