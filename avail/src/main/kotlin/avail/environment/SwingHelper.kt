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

package avail.environment

import avail.environment.BoundStyle.Companion.defaultStyle
import avail.environment.text.AvailEditorKit
import avail.environment.text.TextLineNumber
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.JTextComponent
import javax.swing.text.Position.Bias
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import javax.swing.text.TabSet
import javax.swing.text.TabStop
import javax.swing.text.View
import kotlin.math.max

/**
 * Either places the receiver JTextArea inside a JScrollPane with line numbers
 * presented as row headers, or answers the JScrollPane that it's already
 * inside.
 */
fun JTextPane.scrollTextWithLineNumbers(): JScrollPane
{
	parent?.parent?.let { return it as JScrollPane }
	val scrollPane = JScrollPane(this)
	val lines = TextLineNumber(this)
	scrollPane.setRowHeaderView(lines)
	return scrollPane
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
 * Create a JTextPane, initializing it and its [StyledDocument] in a way that
 * makes it suitable for displaying or editing Avail code.  By default, make it
 * editable.
 *
 * @param workbench
 *   The owning [AvailWorkbench], even if the pane is for a different [Frame].
 * @return a new [JTextPane]
 */
// Set up styles for the transcript.
fun codeSuitableTextPane(
	workbench: AvailWorkbench,
	frame: JFrame
): JTextPane = JTextPane().apply {
	border = BorderFactory.createEtchedBorder()
	isEditable = true
	isEnabled = true
	isFocusable = true
	preferredSize = Dimension(0, 500)
	editorKit = AvailEditorKit(workbench, frame)
	foreground = SystemColors.active.baseCode
	background = SystemColors.active.codeBackground
	val attributes = SimpleAttributeSet()
	StyleConstants.setTabSet(
		attributes, TabSet(Array(500) { TabStop(32.0f * (it + 1)) }))
	StyleConstants.setFontFamily(attributes, "Monospaced")
	styledDocument.run {
		setParagraphAttributes(0, length, attributes, false)
		val defaultStyle = defaultStyle
		defaultStyle.addAttributes(attributes)
		StyleRegistry.addAllStyles(this)
	}
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
		val r = if (false) //(offs0 == view.startOffset && offs1 == view.endOffset)
		{
			// Contained in view, can just use bounds.
			bounds as? Rectangle ?: bounds.bounds
		}
		else
		{
			// Should only render part of View.
			try
			{
				// --- determine locations ---
				val shape = view.modelToView(
					offs0, Bias.Forward, offs1, Bias.Backward, bounds)
				shape as? Rectangle ?: shape.bounds
			}
			catch (e: BadLocationException)
			{
				// can't render
				return null
			}
		}
		// If we are asked to highlight, we should draw something even
		// if the model-to-view projection is of zero width (6340106).
		r.width = max(r.width, 1)
		g.drawRect(r.x, r.y, r.width, r.height - 1)
		return Rectangle(r.x - 1, r.y - 1, r.width + 3, r.height + 2)
	}
}
