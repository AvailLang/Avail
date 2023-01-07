/*
 * TextFieldWithLabel.kt
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

package avail.anvil.components

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.Border

/**
 * A [JPanel] with a [GridBagLayout] that places a [JLabel] to the left of a
 * [JTextField] followed by a [JButton]
 * with a space for an optionally displayed validation message.
 *
 * @author Richard Arriaga
 *
 * @property validator
 *   Accepts the [input] and answers either `null` or String message to display.
 */
class TextFieldWithLabelButtonValidationText constructor(
	label: String,
	emptySpaceRight: Double? = null,
	emptySpaceLeft: Double? = null,
	panelBorder: Border = BorderFactory.createEmptyBorder(10,10,10,10),
	private val validator: (String) -> String?
): JPanel(GridBagLayout())
{
	/**
	 * The next column for the layout.
	 */
	private var nextColumn = 0

	/**
	 * The column for the validation text.
	 */
	private var validationColumn = 0

	init
	{
		emptySpaceLeft?.let {
			add(
				Box.createRigidArea(Dimension(1, 1)),
				GridBagConstraints().apply {
					gridx = nextColumn++
					gridy = 0
					gridheight = 2
					weightx = it
				})
			validationColumn = nextColumn
		}
	}

	/**
	 * The button to add at the end.
	 */
	val button: JButton = JButton("…")

	/**
	 * The [JLabel] to the left of the [JTextField].
	 */
	val label = JLabel(label).apply {
			this@TextFieldWithLabelButtonValidationText.add(
				this,
				GridBagConstraints().apply {
					gridx = nextColumn++
					gridy = 0
					gridwidth = 1
				})
		}

	/**
	 * The [JTextField] that accepts the text input.
	 */
	val textField: JTextField = JTextField().apply {
		this@TextFieldWithLabelButtonValidationText.add(
			this,
			GridBagConstraints().apply {
				weightx = 0.75
				weighty = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = nextColumn++
				gridy = 0
				gridwidth = 1
			})
	}

	/**
	 * The validation [JLabel] below the [label] that is displayed only if
	 * there is a problem.
	 */
	private val validationMessage = JLabel(label).apply {
		font = font.deriveFont(10.0f)
			.deriveFont(font.style or Font.ITALIC)
		foreground = Color.RED
		text = " "
		this@TextFieldWithLabelButtonValidationText.add(
			this,
			GridBagConstraints().apply {
				gridx = validationColumn
				gridy = 1
				gridwidth = 3
				this.anchor = GridBagConstraints.WEST
			})
	}

	/** The text in the [textField]. */
	val input: String get() = textField.text

	/**
	 * Run the [validator] on the [input] and apply any necessary changes to the
	 * [validationMessage].
	 */
	fun checkInput ()
	{
		validator(input).let {
			validationMessage.text = it ?: " "
			validationMessage.isVisible = it != null
		}
	}

	init
	{
		add(
			button,
			GridBagConstraints().apply {
				gridx = nextColumn++
				gridy = 0
				gridwidth = 1
			})
		emptySpaceRight?.let {
			add(
				Box.createRigidArea(Dimension(1, 1)),
				GridBagConstraints().apply {
					gridx = nextColumn
					gridy = 0
					gridheight = 2
					weightx = it
				})
		}
		textField.addFocusListener(object: FocusListener
		{
			// Do nothing
			override fun focusGained(e: FocusEvent?) = Unit

			override fun focusLost(e: FocusEvent?)
			{
				validator(input).let {
					validationMessage.text = it ?: " "
					validationMessage.isVisible = it != null
				}
			}
		})
		border = panelBorder
	}
}
