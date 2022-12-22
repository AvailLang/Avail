/*
 * EditShortcutDialog.kt
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

package avail.anvil.shortcuts

import avail.anvil.AvailWorkbench
import avail.anvil.projects.KeyboardShortcutOverride
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * A dialog that permits the changing of the key mappings of a
 * [KeyboardShortcut].
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The active [AvailWorkbench].
 * @property shortcut
 *   The shortcut to update.
 */
class EditShortcutDialog constructor(
	private val workbench: AvailWorkbench,
	private val parent: ShortcutsPanel,
	private val shortcut: KeyboardShortcut
): JFrame(shortcut.descriptionDisplay)
{
	/**
	 * The set of [KeyboardShortcut]s that match the selected [Key].
	 */
	private var duplicateShortcuts = setOf<KeyboardShortcut>()

	/**
	 * The last [Key] chosen.
	 */
	private var key: Key? = shortcut.key

	/**
	 * The current [KeyboardShortcutOverride] or `null` if none selected.
	 */
	private var ksOverride: KeyboardShortcutOverride? = null

	/**
	 * The label that indicates if there are duplicates.
	 */
	val duplicates = JLabel(" ").apply {
		foreground = Color.RED
		font = font.deriveFont(font.style or Font.ITALIC)
	}

	/**
	 * The [JButton] that accepts the changes.
	 */
	private val okButton = JButton("OK").apply {
		isEnabled = ksOverride != null
		addActionListener {
			val kso = ksOverride!!
			if (shortcut.defaultKey == kso.key)
			{
				// There is no need to use the ksOverride as the override
				// is the same Key as the shortcut's default key.
				if (shortcut.actionMapKey in workbench
					.globalAvailConfiguration.keyboardShortcutOverrides)
				{
					workbench.globalAvailConfiguration
						.keyboardShortcutOverrides.remove(
							shortcut.actionMapKey)
					workbench.globalAvailConfiguration.saveToDisk()
					shortcut.resetToDefaults()
				}
			}
			else
			{
				shortcut.key = kso.key
				workbench.globalAvailConfiguration
					.keyboardShortcutOverrides[kso.actionMapKey] = kso
				workbench.globalAvailConfiguration.saveToDisk()
			}
			this@EditShortcutDialog.parent.redrawShortcuts()
			this@EditShortcutDialog.dispose()
		}
	}

	/** The displayable shortcut. */
	private val shortcutTextField = JTextField().apply {
		text = shortcut.key.keyAsString
		isEditable = false
		toolTipText = "Click in this text field then press the keys of the " +
			"desired new shortcut"
		addKeyListener(object: KeyListener
		{
			override fun keyTyped(e: KeyEvent)
			{
				// Do nothing
			}

			override fun keyPressed(e: KeyEvent)
			{
				val kc = KeyCode.lookupByCode(e.keyCode)
				val mks = ModifierKey.getModifiersFrom(e.modifiersEx)

				this@apply.text =
					mks.joinToString("")
					{ it.displayRepresentation } +
						(kc?.displayRepresentation ?: "")

				if (kc != null)
				{
					val overrideKs = shortcut.shortcutOverride
					val tempKey = Key(kc, mks)
					key = tempKey
					overrideKs.key = tempKey
					duplicateShortcuts = shortcut.category
						.checkShortcutsUniqueAgainst(overrideKs)
					if (duplicateShortcuts.isEmpty())
					{
						ksOverride = overrideKs
						duplicates.text = " "
						okButton.isEnabled = true
					}
					else
					{
						val errorText = "Duplicate Key Bindings: " +
							duplicateShortcuts.joinToString(", ")
							{ it.actionMapKey }
						duplicates.text = errorText
						okButton.isEnabled = false
					}
				}
				else
				{
					ksOverride = null
					duplicateShortcuts = emptySet()
					duplicates.text = ""
				}
			}

			override fun keyReleased(e: KeyEvent)
			{
				// Do nothing
			}
		})
	}

	init
	{
		val panel = JPanel(GridBagLayout()).apply {
			border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
		}
		JLabel("Shortcut:  ").apply {
			font = font.deriveFont(font.style or Font.BOLD)
			panel.add(
				this,
				GridBagConstraints().apply {
					gridx = 0
					gridy = 0
					gridwidth = 1
				})
		}
		panel.add(
			duplicates,
			GridBagConstraints().apply {
				gridx = 0
				gridy = 1
				gridwidth = 3
			})
		panel.add(
			okButton,
			GridBagConstraints().apply {
				gridx = 2
				gridy = 2
				gridwidth = 1
			}
		)
		JButton("Cancel").apply {
			panel.add(
				this,
				GridBagConstraints().apply {
					gridx = 1
					gridy = 2
					gridwidth = 1
				}
			)
			addActionListener { this@EditShortcutDialog.dispose() }
		}

		shortcutTextField.apply {
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
		add(panel)
		isResizable = false
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
		minimumSize = Dimension(290, size.height)
		maximumSize = Dimension(290, size.height)
		setLocationRelativeTo(parent)
		isVisible = true
		toFront()
	}
}
