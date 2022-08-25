/*
 * NewModuleDialog.kt
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

package avail.environment.editor

import avail.environment.AvailEditor
import avail.environment.text.goTo
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

/**
 * Move the caret of the specified [editor] to a line and optional position.
 *
 * @property editor
 *   The target [AvailEditor].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 */
class GoToDialog constructor(
	private val editor: AvailEditor
): JFrame("Go To Line:Column")
{
	/**
	 * How to input the desired position.
	 */
	private val positionTextField = JTextField()

	init
	{
		val panel = JPanel(GridBagLayout()).apply {
			border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
		}
		JLabel("[Line] [:Column]:").apply {
			panel.add(
				this,
				GridBagConstraints().apply {
					gridx = 0
					gridy = 0
					gridwidth = 1
				})
		}
		val cancelButton = JButton("Cancel").apply {
			panel.add(
				this,
				GridBagConstraints().apply {
					gridx = 1
					gridy = 1
					gridwidth = 1
				}
			)
			addActionListener { this@GoToDialog.dispose() }
		}
		val okButton = JButton("OK").apply {
			panel.add(
				this,
				GridBagConstraints().apply {
					gridx = 2
					gridy = 1
					gridwidth = 1
				}
			)
			addActionListener {
				val text = positionTextField.text
				val lineAndPosition = text.split(":", limit = 2)
				val line = lineAndPosition.getOrNull(0)?.toIntOrNull()
				if (line !== null)
				{
					val positionText = lineAndPosition.getOrNull(1)
					if (positionText === null || positionText.isEmpty())
					{
						// Adjust the line for a 0 basis.
						editor.sourcePane.goTo(line - 1)
						this@GoToDialog.dispose()
					}
					else
					{
						positionText.toIntOrNull()?.let { position ->
							// Adjust the line and position for 0 bases.
							editor.sourcePane.goTo(
								line - 1,
								position - 1
							)
							this@GoToDialog.dispose()
						}
					}
				}
			}
		}
		positionTextField.apply {
			panel.add(
				this,
				GridBagConstraints().apply {
					weightx = 1.0
					weighty = 1.0
					fill = GridBagConstraints.HORIZONTAL
					gridx = 1
					gridy = 0
					gridwidth = 2
				})
			requestFocusInWindow()
		}
		rootPane.registerKeyboardAction(
			{ cancelButton.doClick() },
			KeyStroke.getKeyStroke(VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW
		)
		rootPane.registerKeyboardAction(
			{ okButton.doClick() },
			KeyStroke.getKeyStroke(VK_ENTER, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW
		)
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent)
			{
				editor.openDialogs.remove(this@GoToDialog)
			}
		})
		add(panel)
		isResizable = false
		editor.openDialogs.add(this)
		displayWindow()
	}

	override fun getPreferredSize(): Dimension
	{
		val currentWidth = size.width
		return super.getPreferredSize().apply {
			width = currentWidth
		}
	}

	/**
	 * Perform the necessary final operations to display this view.
	 */
	private fun displayWindow()
	{
		pack()
		minimumSize = Dimension(280, size.height)
		maximumSize = Dimension(280, size.height)
		setLocationRelativeTo(editor)
		isVisible = true
		toFront()
	}
}
