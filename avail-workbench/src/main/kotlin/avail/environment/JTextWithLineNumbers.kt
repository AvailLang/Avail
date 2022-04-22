/*
 * JTextWithLineNumbers.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import java.awt.Color
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Element
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * TODO Document
 */
class JTextWithLineNumbers
constructor(
	val textArea: JTextArea)
{
	val lineNumberArea = JTextArea().apply {
		background = Color.DARK_GRAY
		isEditable = false
		foreground = Color.LIGHT_GRAY
		text = "1"
		val attribs = SimpleAttributeSet()
		StyleConstants.setAlignment(attribs, StyleConstants.ALIGN_RIGHT)
		//        setParagraphAttributes(attribs, true)
	}

	init {
		textArea.apply {
			document.addDocumentListener(object : DocumentListener
			{
				fun calculateGutter(): String
				{
					val caretPosition = document.length
					val root: Element = document.defaultRootElement
					return buildString {
						append("1")
						append(System.getProperty("line.separator"))
						for (i in 2 until
							root.getElementIndex(caretPosition) + 2)
						{
							append(i)
							append(System.getProperty("line.separator"))
						}
					}
				}

				override fun insertUpdate(e: DocumentEvent?)
				{
					lineNumberArea.text = calculateGutter()
				}

				override fun removeUpdate(e: DocumentEvent?)
				{
					lineNumberArea.text = calculateGutter()
				}

				override fun changedUpdate(e: DocumentEvent?)
				{
					lineNumberArea.text = calculateGutter()
				}
			})
		}
	}

	fun addTo (scroll: JScrollPane)
	{
		scroll.viewport.add(textArea)
		scroll.setRowHeaderView(lineNumberArea)
	}
}