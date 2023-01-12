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
import javax.swing.plaf.LayerUI
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.JTextComponent
import javax.swing.text.Position.Bias
import javax.swing.text.StyleConstants
import javax.swing.text.View
import kotlin.math.max

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
	parent?.parent?.parent?.let { return it.cast() }
	val scrollPane = JScrollPane(this)
	val codeGuide = CodeGuide(workbench, this, guideLines)
	val guidePane = JLayer(scrollPane, codeGuide)
	putClientProperty(CodeGuide::class.java.name, codeGuide)
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
class CodeGuide constructor(
	private val workbench: AvailWorkbench,
	private val jTextPane: JTextPane,
	private val guideLines: List<Int> = listOf(80)
): LayerUI<JScrollPane>()
{
	/** The X-offset for the guide. */
	var x: Int? = null

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
			if (x === null)
			{
				// The font is monospaced, so we can use any character we like to
				// measure the width.
				val fontMetrics = view.getFontMetrics(jTextPane.font)
				// The Euro, €, is a slightly wider character which appears to
				// place the guideline in the correct spot based on empirical
				// evidence.
				val stringWidth = fontMetrics.stringWidth("€".repeat(it))
				x = bounds.x + stringWidth
			}
			val x = x!!
			val deltaX = view.viewport.viewPosition.x
			g.color = guideColor
			g.drawLine(x - deltaX, bounds.y, x - deltaX, bounds.height)
			this.x = null
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

class GlowHighlightPainter
constructor (
	color: Color
): DefaultHighlightPainter(color)
{
	override fun paintLayer(
		g: Graphics,
		offs0: Int,
		offs1: Int,
		bounds: Shape,
		c: JTextComponent,
		view: View
	): Shape?
	{
		g.color = color ?: c.selectionColor
		val shape = try
		{
			view.modelToView(offs0, Bias.Backward, offs1, Bias.Forward, bounds)
		}
		catch (e: BadLocationException)
		{
			return null
		}
		val r = shape as? Rectangle ?: shape.bounds
		// If we are asked to highlight, we should draw something even
		// if the model-to-view projection is of zero width.
		r.width = max(r.width, 1)
		g.drawRect(r.x, r.y, r.width, r.height - 1)
		return Rectangle(r.x - 1, r.y - 1, r.width + 3, r.height + 2)
	}
}

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

class WindowAction
constructor(
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
