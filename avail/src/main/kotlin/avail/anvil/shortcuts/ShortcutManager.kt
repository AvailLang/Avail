/*
 * ShortcutManager.kt
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
import avail.anvil.icons.structure.EditIcons
import avail.anvil.projects.manager.KnownProjectRow
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.EAST
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

/**
 * The [JFrame] that presents the different [KeyboardShortcut]s.
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The active [AvailWorkbench].
 */
class ShortcutManager internal constructor(
	val workbench: AvailWorkbench
): JFrame("Shortcuts")
{
	val tabs: JTabbedPane =
		JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT)

	init
	{
		KeyboardShortcutCategory.values().forEach {
			 tabs.addTab(
				 it.display, ShortcutsPanel(it, workbench).redrawShortcuts())
		}
		contentPane.add(tabs)
		minimumSize = Dimension(700, 800)
		preferredSize = Dimension(700, 800)
		maximumSize = Dimension(700, 1200)
		pack()
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent)
			{
				workbench.shortcutManager = null
			}
		})
		setLocationRelativeTo(workbench)
		isVisible = true
	}
}

/**
 * A [JPanel] that displays all the shortcuts.
 *
 * @author Richard Arriaga
 */
class ShortcutsPanel constructor(
	val category: KeyboardShortcutCategory,
	val workbench: AvailWorkbench
) : JPanel()
{
	/**
	 * The panel that contains all the shortcuts.
	 */
	private val shortcutsPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
	}

	/**
	 * Redraw the [shortcutsPanel].
	 */
	fun redrawShortcuts (): ShortcutsPanel
	{
		SwingUtilities.invokeLater {
			shortcutsPanel.removeAll()
			category.shortcutsByDescription.forEach {
				shortcutsPanel.add(ShortcutRow(it, workbench, this))
			}
			shortcutsPanel.revalidate()
			shortcutsPanel.repaint()
		}
		return this
	}

	init
	{
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		val overlaps = category.overlapCategories
		if (overlaps.isNotEmpty())
		{
			val overlapCategories = overlaps.toList()
				.sortedBy { it.display }
				.joinToString(", ") { it.display }
			add(JPanel().apply {
				layout = (FlowLayout(FlowLayout.LEFT))
				minimumSize = Dimension(720, 30)
				preferredSize = Dimension(750, 30)
				maximumSize = Dimension(750, 30)
				add(JLabel("In-Scope with $overlapCategories").apply {
					font = font.deriveFont(font.style or Font.BOLD)
				})
			})
		}
		add(JScrollPane(shortcutsPanel))

		val reset = JButton("Reset to Defaults").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 150, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 150, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 150, currentHeight + 40)
			toolTipText = "Resets all shortcuts across all categories to " +
				"default key mappings"
			addActionListener {
				KeyboardShortcutCategory.resetAllToDefaults(workbench)
				redrawShortcuts()
			}
		}

		add(JPanel().apply {
			layout = (FlowLayout(FlowLayout.RIGHT))
			minimumSize = Dimension(600, 50)
			preferredSize = Dimension(700, 50)
			maximumSize = Dimension(700, 50)
			background = Color(0x3C, 0x3F, 0x41)
			add(reset)
		})
	}
}

/**
 * The [JPanel] that displays a [KeyboardShortcut].
 *
 * @author Richard Arriaga
 *
 * @property shortcut
 *   The [KeyboardShortcut] to show.
 * @property workbench
 *   The associated [AvailWorkbench]
 */
internal class ShortcutRow constructor(
	private val shortcut: KeyboardShortcut,
	val workbench: AvailWorkbench,
	val parent: ShortcutsPanel
): JPanel(GridBagLayout())
{
	/**
	 * The [GridBagConstraints] used for all components in [KnownProjectRow].
	 */
	private val constraints = GridBagConstraints().apply {
		anchor = GridBagConstraints.WEST
	}
	init
	{
		border = BorderFactory.createEmptyBorder()
		minimumSize = Dimension(650, 50)
		preferredSize = Dimension(650, 50)
		maximumSize = Dimension(650, 50)
		addMouseListener(
			object : MouseAdapter()
			{
				override fun mouseClicked(e: MouseEvent)
				{
					if (e.clickCount == 2
						&& e.button == MouseEvent.BUTTON1)
					{
						e.consume()
						openEditDialog()
					}
				}
			})
	}

	/**
	 * Open dialog uses to edit the associated [shortcut].
	 */
	private fun openEditDialog ()
	{
		EditShortcutDialog(workbench, parent, shortcut)
	}

	/**
	 * The [JPanel] that displays the [shortcut]
	 * [description][KeyboardShortcut.descriptionDisplay] and the shortcut
	 * [action key][KeyboardShortcut.actionMapKey].
	 */
	private val descriptionPanel: JPanel = JPanel().apply {
		border = BorderFactory.createEmptyBorder()
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
	}

	/**
	 * The [shortcut] [description][KeyboardShortcut.descriptionDisplay].
	 */
	@Suppress("unused")
	private val shortcutDescription: JLabel =
		JLabel(shortcut.descriptionDisplay).apply {
			font = font.deriveFont(font.style or Font.BOLD)
			descriptionPanel.add(this)
		}

	/**
	 * The [shortcut] [action key][KeyboardShortcut.actionMapKey].
	 */
	@Suppress("unused")
	private val actionKey: JLabel = JLabel(shortcut.actionMapKey).apply {
		font = font.deriveFont(
			font.style or Font.ITALIC,
			(font.size - 3).toFloat())
		descriptionPanel.add(this)
	}

	init
	{
		add(
			descriptionPanel,
			constraints.apply {
				weightx = 1.0
			})
	}

	/**
	 * The [JPanel] that displays the [shortcut]
	 * [description][KeyboardShortcut.descriptionDisplay] and the shortcut
	 * [action key][KeyboardShortcut.actionMapKey].
	 */
	private val shortcutPanel: JPanel = JPanel().apply {
		border = BorderFactory.createEmptyBorder()
		layout = BoxLayout(this, BoxLayout.X_AXIS)
	}

	/**
	 * The [shortcut] [description][KeyboardShortcut.descriptionDisplay].
	 */
	@Suppress("unused")
	private val shortcutKeys: JLabel =
		JLabel(shortcut.shortcutAsString).apply { shortcutPanel.add(this) }

	init
	{
		shortcutPanel.add(Box.createRigidArea(Dimension(10, 0)))
	}
	/**
	 * The button to remove the project from the view.
	 */
	@Suppress("unused")
	private val editShortcut: JButton =
		JButton(editIcon).apply {
			isContentAreaFilled = false
			isBorderPainted = false
			toolTipText = EditIcons.PENCIL_GREY.toolTip
			addActionListener {
				openEditDialog()
			}
			shortcutPanel.add(this)
		}

	init
	{
		add(
			shortcutPanel,
			constraints.apply {
				weightx = 1.0
				anchor = EAST
			})
	}

	companion object
	{
		/**
		 * The height to use for the favorite icons.
		 */
		private const val scaledIconHeight = 20

		/**
		 * The edit icon.
		 */
		private val editIcon get() = EditIcons.PENCIL_GREY.icon(scaledIconHeight)
	}
}
