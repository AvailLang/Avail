/*
 * SwingHelper.kt
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

import avail.anvil.SystemStyleClassifier.CODE_GUIDE
import avail.anvil.text.TextLineNumber
import avail.utility.cast
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayer
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI
import javax.swing.text.BadLocationException
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import javax.swing.text.LayeredHighlighter.LayerPainter
import javax.swing.text.Position.Bias
import javax.swing.text.StyleConstants
import javax.swing.text.View

/**
 * Either places the receiver JTextArea inside a JScrollPane with line numbers
 * presented as row headers, or answers the JScrollPane that it's already
 * inside.
 *
 * @param workbench
 *   The owning [workbench][AvailWorkbench].
 * @param guideLines
 *   The list of after how many (character) columns to display a guide line.
 *   Defaults to a single guideline at `80`.
 * @return
 *   The requested [JLayer].
 */
fun JTextPane.scrollTextWithLineNumbers(
	workbench: AvailWorkbench,
	guideLines: List<Int> = listOf(80)
): JLayer<JScrollPane>
{
	getClientProperty(CodeOverlay::class.java.name)?.let { return it.cast() }
	val scrollPane = JScrollPane(this)
	val overlay = CodeOverlay(workbench, this, guideLines)
	val guidePane = JLayer(scrollPane, overlay)
	putClientProperty(CodeOverlay::class.java.name, overlay)
	val lines = TextLineNumber(this)
	scrollPane.setRowHeaderView(lines)
	// Make sure that the font is available in several places, for convenience.
	scrollPane.font = font
	guidePane.font = font
	return guidePane
}

/**
 * Draws code guides on the decorated [JScrollPane] after the appropriate
 * columns.
 *
 * @property workbench
 *   The owning [workbench][AvailWorkbench].
 * @property jTextPane
 *   The owning [text&#32;pane][JTextPane].
 * @property guideLines
 *   The list of after how many (character) columns to display a guide line.
 *   Defaults to a single guideline at `80`.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga
 */
class CodeOverlay constructor(
	private val workbench: AvailWorkbench,
	private val jTextPane: JTextPane,
	private val guideLines: List<Int> = listOf(80)
): LayerUI<JScrollPane>()
{
	/** The color of the code guide. */
	var guideColor = computeColor()

	/**
	 * Compute the color of the guide lines from the [workbench]'s
	 * [stylesheet][Stylesheet].
	 *
	 * @return
	 *   The color. Defaults to [SystemColors.codeGuide] if the
	 *   [stylesheet][Stylesheet] does not contain a rule that matches
	 *   [CODE_GUIDE].
	 */
	fun computeColor() = workbench.stylesheet[CODE_GUIDE.classifier]
		.documentAttributes.getAttribute(StyleConstants.Foreground) as Color

	override fun paint(g: Graphics, c: JComponent)
	{
		super.paint(g, c)
		val layer: JLayer<JScrollPane> = c.cast()
		val view = layer.view
		val bounds = view.viewportBorderBounds
		guideLines.forEach {
			// The font is monospaced, so we can use any character we like
			// to measure the width.
			val fontMetrics = view.getFontMetrics(jTextPane.font)
			// The Euro, €, is a slightly wider character which appears to
			// place the guideline in the correct spot based on empirical
			// evidence.
			val stringWidth = fontMetrics.stringWidth("€".repeat(it))
			val x = bounds.x + stringWidth
			val deltaX = view.viewport.viewPosition.x
			g.color = guideColor
			g.drawLine(x - deltaX, bounds.y, x - deltaX, bounds.height)
		}
	}
}

/**
 * Either places the given component inside a JScrollPane or answers the
 * JScrollPane that it's already inside.
 */
fun Component.scroll(): JScrollPane
{
	parent?.parent?.let { return it as JScrollPane }
	return JScrollPane(this)
}

/**
 * Answer the pane wrapped in a JScrollPane.
 *
 * @param innerComponent
 *   The [Component] to be wrapped with scrolling capability.
 * @param minWidth
 *   The [JComponent.minimumSize]'s minimum width,
 * @param minHeight
 *   The [JComponent.minimumSize]'s minimum height,
 * @return
 *   The new [JScrollPane].
 */
internal fun createScrollPane(
	innerComponent: Component,
	minWidth: Int = 100,
	minHeight: Int = 50
): JScrollPane =
	JScrollPane(
		innerComponent,
		VERTICAL_SCROLLBAR_ALWAYS,
		HORIZONTAL_SCROLLBAR_ALWAYS
	).apply {
		minimumSize = Dimension(minWidth, minHeight)
	}

/**
 * Scroll the given [JTextPane] to ensure the given text range is visible, and
 * preferably not jammed against the top or bottom border.
 */
fun JTextComponent.showTextRange(rangeStart: Int, rangeEnd: Int)
{
	try
	{
		val start2D = modelToView2D(rangeStart)
		val end2D = modelToView2D(rangeEnd)
		val union2D = start2D.createUnion(end2D)
		val union = Rectangle(
			union2D.x.toInt(),
			union2D.y.toInt(),
			union2D.width.toInt(),
			union2D.height.toInt())
		// First make sure the actual text rectangle is visible.
		scrollRectToVisible(union)
		// Now try to make an expanded rectangle visible.
		union.grow(0, 50)
		scrollRectToVisible(union)
	}
	catch (ble: BadLocationException)
	{
		// Ignore text range problems.
	}
}

/**
 * A mechanism for highlighting explicit spans of text.  It's composed of
 * [GlowHighlightRangePainter]s that can paint the initial and/or final
 * characters of the span, and the interior portions of the span.  Those
 * subranges (at most 3) are added to a [JTextPane]'s highlighter's highlighted
 * regions, using the specific [GlowHighlightRangePainter] for each range.
 *
 * At rendering time, the captured [GlowHighlightRangePainter] for a range is
 * told to paint itself for each potentially smaller run of text that has the
 * same styling information throughout that smaller run.
 *
 * @param colors
 *   The array of [Color]s to draw in successive rings around the text.
 */
class Glow constructor(vararg colors: Color)
{
	/**
	 * Create the array of four [GlowHighlightRangePainter]s, in an order that
	 * [painterFor] can decode.
	 */
	private val painters = Array(4) {
		GlowHighlightRangePainter(colors, (it and 2) != 0, (it and 1) != 0)
	}

	/**
	 * Select the appropriate [GlowHighlightRangePainter] for highlighting text.
	 *
	 * @param isStart
	 *   If the highlighter is for the first character of the range.
	 * @param isEnd
	 *   If the highlighter is for the last character of the range.
	 */
	fun painterFor(
		isStart: Boolean,
		isEnd: Boolean
	): GlowHighlightRangePainter =
		painters[
			(if (isStart) 2 else 0)
				+ (if (isEnd) 1 else 0)
		]
}

/**
 * A [LayerPainter] that is capable of highlighting text with a glowing border.
 * The glow is accomplished through an array of colors to draw successive layers
 * around the text in a rounded rectangle.
 */
class GlowHighlightRangePainter(
	private val colors: Array<out Color>,
	private val isStart: Boolean,
	private val isEnd: Boolean
): LayerPainter()
{
	override fun paint(
		g: Graphics?,
		p0: Int,
		p1: Int,
		bounds: Shape?,
		c: JTextComponent?)
	{
		throw UnsupportedOperationException()
	}

	override fun paintLayer(
		g: Graphics,
		offs0: Int,
		offs1: Int,
		bounds: Shape,
		c: JTextComponent,
		view: View
	): Shape?
	{
		val shape = try
		{
			view.modelToView(offs0, Bias.Backward, offs1, Bias.Forward, bounds)
		}
		catch (e: BadLocationException)
		{
			return null
		}
		val rect = Rectangle(shape.bounds)
		// If we are asked to highlight, we should draw something even
		// if the model-to-view projection is of zero width.
		if (rect.width == 0) rect.width = 1
		repeat(colors.size) { zeroOut ->
			val out = zeroOut + 1
			val out2 = out * 2
			val safeOut = out + 3  // Out plus a safety margin for arcs.
			g.color = colors[zeroOut]

			// This version uses rounded rectangles, but it leaves visible holes
			// in the corners.
			//
			//g.create().run {
			//	val r = rect.run {
			//		Rectangle(x - out, y - out, width + out2, height + out2)
			//	}
			//	val clip = clipBounds
			//	if (!isStart)
			//	{
			//		clip.width += clip.x - rect.x
			//		clip.x = rect.x
			//	}
			//	if (!isEnd)
			//	{
			//		clip.width = rect.x + rect.width - clip.x
			//	}
			//	clipRect(clip.x, clip.y, clip.width, clip.height)
			//	drawRoundRect(
			//		rect.x - out,
			//		rect.y - out,
			//		rect.width + out2,
			//		rect.height + out2,
			//		out,  //zeroOut?
			//		out)
			//}

			with(rect) {
				// Draw top of box
				g.fillRect(x, y - out, width, 1)
				// Draw bottom of box
				g.fillRect(x, y + height + out - 1, width, 1)
				if (isStart)
				{
					// Draw left side.
					g.fillRect(x - out, y, 1, height)
					// Top left arc.
					g.create().run {
						clipRect(x - safeOut, y - safeOut, safeOut, safeOut)
						drawOval(x - out, y - out, out2, out2)
					}
					// Bottom left arc.
					g.create().run {
						clipRect(x - safeOut, y + height, safeOut, safeOut)
						drawOval(x - out, y + height - out - 1, out2, out2)
					}

				}
				if (isEnd)
				{
					// Draw right side.
					g.fillRect(x + width - 1 + out, y, 1, height)
					// Top right arc.
					g.create().run {
						clipRect(x + width, y - out, safeOut, out)
						drawOval(x + width - out - 1, y - out, out2, out2)
					}
					// Bottom right arc.
					g.create().run {
						clipRect(x + width, y + height, out, out)
						drawOval(
							x + width - out - 1, y + height - out, out2, out2)
					}
				}
			}
		}
		val maxOutset = colors.size
		return Rectangle(
			rect.x - maxOutset,
			rect.y - maxOutset,
			rect.width + maxOutset * 2 + 1,
			rect.height + maxOutset * 2 + 1)
	}
}


/**
 * Given a range and a [Glow], apply that glow to the characters in that range.
 * Add a highlight for the first character, the middle region if any, and the
 * last character of that range.  A size-one range acts as both a first and last
 * character, with no middle part.  This simplifies rendering of a box
 * highlight, since the highlight mechanism is executed for each styled run
 * separately, with no easy way to tell if the left or right walls of the box
 * should be drawn.
 *
 * Answer the list of objects that Swing produced to represent the highlight
 * areas.
 */
fun Highlighter.addGlow(
	range: IntRange,
	glow: Glow
): List<Any>
{
	val tags = mutableListOf<Any>()
	val size = range.last + 1 - range.first
	if (size == 1)
	{
		// Size one region.
		tags.add(
			addGlowHighlight(range.first, range.last + 1, glow, true, true))
	}
	else if (size >= 2)
	{
		// Left character, middle region if any, right character.
		tags.add(
			addGlowHighlight(range.first, range.first + 1, glow, true, false))
		if (size >= 3)
		{
			tags.add(
				addGlowHighlight(
					range.first + 1, range.last, glow, false, false))
		}
		tags.add(
			addGlowHighlight(range.last, range.last + 1, glow, false, true))
	}
	return tags
}

/**
 * Add the [GlowHighlightRangePainter] to the range of text, answering the
 * resulting highlight tag (crazily untyped by Swing).
 */
private fun Highlighter.addGlowHighlight(
	start: Int,
	end: Int,
	glow: Glow,
	isStart: Boolean,
	isEnd: Boolean
): Any = addHighlight(start, end, glow.painterFor(isStart, isEnd))

/**
 * Create a Window menu that appears suitable for the platform.  Which is
 * only Mac at the moment, although these commands should work anywhere.
 */
fun MenuBarBuilder.addWindowMenu(frame: JFrame)
{
	menu("Window")
	{
		item(
			WindowAction(
				"Minimize",
				frame,
				KeyStroke.getKeyStroke(
					KeyEvent.VK_M, AvailWorkbench.menuShortcutMask)
			) {
				val mask = JFrame.ICONIFIED
				val state = frame.extendedState
				when (state and mask)
				{
					0 -> frame.extendedState = state or mask
					else -> frame.extendedState = state and (mask.inv())
				}
			})
		item(
			WindowAction(
				"Zoom",
				frame,
				KeyStroke.getKeyStroke(
					KeyEvent.VK_F,
					AvailWorkbench.menuShortcutMask
						or InputEvent.CTRL_DOWN_MASK)
			) {
				val mask = JFrame.MAXIMIZED_BOTH
				val state = frame.extendedState
				when (state and mask)
				{
					0 -> frame.extendedState = state or mask
					else -> frame.extendedState = state and (mask.inv())
				}
			})
		item(
			WindowAction(
				"Close",
				frame,
				KeyStroke.getKeyStroke(
					KeyEvent.VK_W, AvailWorkbench.menuShortcutMask)
			) {
				frame.dispatchEvent(
					WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
			})
	}
}

class WindowAction constructor(
	name: String,
	frame: JFrame,
	keyStroke: KeyStroke? = null,
	val action: ()->Unit
): AbstractAction(name)
{
	init
	{
		val rootPane = frame.rootPane
		rootPane.actionMap.put(this, this)
		keyStroke?.let {
			putValue(Action.ACCELERATOR_KEY, it)
			rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				it, this)
		}
	}

	override fun actionPerformed(e: ActionEvent?)
	{
		action()
	}
}

/**
 * Perform the specified [action] on the
 * [event&#32;dispatch&#32;thread][SwingUtilities.isEventDispatchThread] and
 * wait for it to complete. If this _is_ the event dispatch thread, then
 * just perform the [action] synchronously.
 *
 * @param action
 *   The action to perform on the
 *   [event&#32;dispatch&#32;thread][SwingUtilities.isEventDispatchThread].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun invokeAndWaitIfNecessary(
	noinline action: ()->Unit
)
{
	if (SwingUtilities.isEventDispatchThread()) action()
	else SwingUtilities.invokeAndWait(action)
}
