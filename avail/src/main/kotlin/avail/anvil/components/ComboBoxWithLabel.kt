/*
 * ComboBoxWithLabel.kt
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

import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.border.Border

/**
 * A [JPanel] with a [GridBagLayout] that places a [JLabel] to the left of a
 * [JComboBox].
 *
 * @param label
 *   The text for the [JLabel] to the left of the [JComboBox].
 * @param items
 *   The items to be displayed in the [JComboBox].
 * @param additionalComponents
 *   Additional components to be added to the bottom of the [JComboBox].
 * @param emptySpaceRight
 *   Optional space to the right of the [JComboBox].
 * @param emptySpaceLeft
 *   Optional space to the left of the [JLabel].
 * @param panelBorder
 *   The border surrounding the [JPanel].
 */
class ComboBoxWithLabel<T>(
	label: String,
	items: Array<T>,
	additionalComponents: Array<JComponent> = emptyArray(),
	emptySpaceRight: Double? = null,
	emptySpaceLeft: Double? = null,
	panelBorder: Border = BorderFactory.createEmptyBorder(10,10,10,10)
): JPanel(GridBagLayout())
{

	/**
	 * The next column for the layout.
	 */
	private var nextColumn = 0

	init {
		emptySpaceLeft?.let {
			add(
				Box.createRigidArea(Dimension(1, 1)),
				GridBagConstraints().apply {
					gridx = nextColumn++
					gridy = 0
					gridheight = 2
					weightx = it
				})
		}
	}

	/**
	 * The [JLabel] to the left of the [JComboBox].
	 */
	val label = JLabel(label).apply {
		this@ComboBoxWithLabel.add(
			this,
			GridBagConstraints().apply {
				gridx = nextColumn++
				gridy = 0
				gridwidth = 1
			})
	}

	/**
	 * The [JComboBox] that displays the selectable items.
	 */
	val comboBox: JComboBox<T> = JComboBox(items).apply {
		this@ComboBoxWithLabel.add(
			this,
			GridBagConstraints().apply {
				weightx = 0.75
				weighty = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = nextColumn++
				gridy = 0
				gridwidth = 1
			})
		additionalComponents.forEachIndexed { index, component ->
			this@ComboBoxWithLabel.add(
				component,
				GridBagConstraints().apply {
					weightx = 0.75
					weighty = 0.0
					fill = GridBagConstraints.HORIZONTAL
					gridx = 0  // Align with the start of the combo box
					gridy = index + 1  // Position under the combo box
					gridwidth = 2  // Span across both label and combo box
					// Add some top margin for better spacing
					insets = Insets(10, 0, 0, 0)
				})
		}
	}


	init {
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
		border = panelBorder
	}
}
