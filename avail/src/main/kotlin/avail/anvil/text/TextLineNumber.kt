/*
 * TextLineNumber.kt
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
package avail.anvil.text

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import kotlin.math.max
import kotlin.math.min

/**
 * This class will display line numbers for a related text component. The text
 * component must use the same line height for each line. TextLineNumber
 * supports wrapped lines and will highlight the line number of the current
 * line(s) in the text component.
 *
 * This class was designed to be used as a component added to the row header
 * of a JScrollPane.
 *
 * @constructor
 * Create new [TextLineNumber] to attach to the given [JTextComponent]
 *
 * @property component
 *   The [JTextComponent] to which to attach this instance.
 *
 * Adapted and extended from code by Rod Camick,
 *   https://tips4java.wordpress.com/2009/05/23/text-component-line-number/
 */
class TextLineNumber
constructor(
	private val component: JTextComponent
) : JPanel(), CaretListener, DocumentListener, PropertyChangeListener
{
	/**
	 * The Color used to render the current line digits. Default is [Color.RED].
	 * If set to null, it uses the foreground color.
	 */
	private var currentLineForeground: Color? = Color.RED

	//  Keep history information to reduce the number of times the component
	//  needs to be repainted
	private var lastDigits = 0

	private var lastHeight = 0

	private var lastLines = -1..-1

	init
	{
		font = component.font
		border = CompoundBorder(
			MatteBorder(0, 0, 0, 2, Color.GRAY), EmptyBorder(0, 5, 0, 5))
		component.document.addDocumentListener(this)
		component.addCaretListener(this)
		component.addPropertyChangeListener("font", this)
		lastDigits = 0
		setPreferredWidth()
	}

	/**
	 * Calculate the width needed to display the maximum line number
	 */
	private fun setPreferredWidth()
	{
		val root = component.document.defaultRootElement
		val lines = root.elementCount
		val digits = lines.toString().length

		//  Update sizes when number of digits in the line number changes
		if (lastDigits != digits)
		{
			lastDigits = digits
			val fontMetrics = getFontMetrics(font)
			val width = fontMetrics.charWidth('0') * digits
			val insets = insets
			val preferredWidth = insets.left + insets.right + width
			preferredSize = Dimension(preferredWidth, Int.MAX_VALUE - 1000000)
			size = preferredSize
		}
	}

	/**
	 * Draw the line numbers
	 */
	public override fun paintComponent(g: Graphics)
	{
		super.paintComponent(g)

		// Determine the width of the space available to draw the line number
		val leftInset = insets.left

		// Determine the rows to draw within the clipped bounds.
		val root = component.document.defaultRootElement
		val clip = g.clipBounds
		val firstLine: Int = root.getElementIndex(
			component.viewToModel2D(Point(0, clip.minY.toInt())))
		val lastLine = root.getElementIndex(
			component.viewToModel2D(Point(0, clip.maxY.toInt())))

		// Determine which lines should be highlighted.
		val selectedLines = computeSelectionLines()

		for (line in firstLine..lastLine)
		{
			try
			{
				g.color = when (line)
				{
					in selectedLines -> currentLineForeground ?: foreground
					else -> foreground
				}
				val element = root.getElement(line)
				val topY =
					component.modelToView2D(element.startOffset).y.toInt()
				val offsetY = g.fontMetrics.ascent
				g.drawString((line + 1).toString(), leftInset, topY + offsetY)
			}
			catch (e: Exception)
			{
				break
			}
		}
	}

	override fun caretUpdate(e: CaretEvent)
	{
		//  Get the line the caret is positioned on
		val currentLines = computeSelectionLines()
		if (lastLines != currentLines)
		{
			lastLines = currentLines
			parent.repaint()
		}
	}

	/**
	 * Answer an [IntRange] of zero-based line numbers to highlight.
	 */
	private fun computeSelectionLines(): IntRange
	{
		val root = component.document.defaultRootElement
		val dot = component.caret.dot
		val mark = component.caret.mark
		val startLine = root.getElementIndex(min(dot, mark))
		val endLine = root.getElementIndex(max(dot, mark))
		return startLine .. endLine
	}

	override fun changedUpdate(e: DocumentEvent)
	{
		documentChanged()
	}

	override fun insertUpdate(e: DocumentEvent)
	{
		documentChanged()
	}

	override fun removeUpdate(e: DocumentEvent)
	{
		documentChanged()
	}

	private fun documentChanged()
	{
		//  View of the component has not been updated at the time
		//  the DocumentEvent is fired
		SwingUtilities.invokeLater {
			try
			{
				val endPos = component.document.length
				val rect = component.modelToView2D(endPos)
				if (rect.y.toInt() != lastHeight)
				{
					setPreferredWidth()
					parent.repaint()
					lastHeight = rect.y.toInt()
				}
			}
			catch (ex: BadLocationException)
			{
				// Ignore.
			}
		}
	}

	override fun propertyChange(evt: PropertyChangeEvent)
	{
		when (val value = evt.newValue)
		{
			is Font ->
			{
				font = value
				// Force calculation of the new width.
				lastDigits = 0
				setPreferredWidth()
			}
		}
	}
}
