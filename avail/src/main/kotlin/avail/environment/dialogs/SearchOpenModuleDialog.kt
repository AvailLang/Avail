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

package avail.environment.dialogs

import avail.environment.AvailEditor
import avail.environment.AvailWorkbench
import avail.resolver.ModuleRootResolver
import avail.resolver.ResolverReference
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * Opens the dialog for searching for an opening a module in an editor.
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The running [AvailWorkbench].
 */
class SearchOpenModuleDialog constructor(
	private val workbench: AvailWorkbench
): JFrame("Open Module")
{
	/**
	 * The [JTextField] field to use wto set the module name.
	 */
	private val moduleNameTextField: JTextField = JTextField()

	/**
	 * The active [ModuleRootResolver]s.
	 */
	private val resolvers =
		workbench.resolver.moduleRoots.map { it.resolver }

	/**
	 * The variable that holds onto the proposed module name to open.
	 */
	private val partialName get() = moduleNameTextField.text

	/**
	 * The label to be displayed if there no results found.
	 */
	private val noResultsMessageLabel = JLabel().apply {
		font = Font(font.name, Font.ITALIC, 10)
		text = "No Results"
	}

	/**
	 * The panel to add the search results to.
	 */
	val resultsPanel = JPanel(BorderLayout()).apply {
		border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
	}

	/**
	 * Populate the center panel with the list of located [ResolverReference]s.
	 *
	 * @param found
	 *   The list of [ResolverReference]s to display.
	 */
	private fun populateCenterPanel (found: List<ResolverReference>)
	{
		resultsPanel.removeAll()
		if (found.isEmpty())
		{
			resultsPanel.add(noResultsMessageLabel, BorderLayout.CENTER)
		}
		else
		{
			val list = JList(found.toTypedArray()).apply {
				selectionMode = ListSelectionModel.SINGLE_INTERVAL_SELECTION
				visibleRowCount = 10
				layoutOrientation = JList.VERTICAL

				addKeyListener(object: KeyAdapter() {
					override fun keyReleased(e: KeyEvent)
					{
						if (e.keyCode == KeyEvent.VK_ENTER &&
							selectedIndex > -1)
						{
							openModule(selectedValue)
						}
					}
				})
				addMouseListener(object: MouseAdapter()
				{
					override fun mouseClicked(e: MouseEvent)
					{
						if (e.clickCount == 1)
						{
							val index = this@apply.locationToIndex(e.point)
							if (index >= 0)
							{
								val o = this@apply.model.getElementAt(index)
								openModule(o)
							}
						}
					}
				})
			}
			JScrollPane().apply {
				setViewportView(list)
				resultsPanel.add(this)
			}
		}
	}

	/**
	 * Open the selected module.
	 *
	 * @param reference
	 *   The [ResolverReference] of the module to open.
	 */
	private fun openModule (reference: ResolverReference)
	{
		SwingUtilities.invokeLater {
			var isOpen = false
			val editor =
				workbench.openEditors.computeIfAbsent(reference.moduleName)
				{
					isOpen = true
					AvailEditor(workbench, reference.moduleName).apply { open() }
				}
			if (!isOpen) editor.toFront()
			dispose()
		}
	}

	init
	{
		val panel = JPanel(BorderLayout()).apply {
			border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
		}
		val moduleNamePanel = JPanel(GridBagLayout()).apply {
			panel.add(this, BorderLayout.PAGE_START)
		}
		// The label for the text field to enter the module name
		JLabel("Module Name:").apply {
			moduleNamePanel.add(
				this,
				GridBagConstraints().apply {
					gridx = 0
					gridy = 0
					gridwidth = 1
				})
		}

		moduleNameTextField.apply {
			moduleNamePanel.add(
				this,
				GridBagConstraints().apply {
					weightx = 1.0
					weighty = 1.0
					fill = GridBagConstraints.HORIZONTAL
					gridx = 1
					gridy = 0
					gridwidth = 1
				})
				addKeyListener(object: KeyAdapter() {
					override fun keyReleased(e: KeyEvent)
					{
						super.keyReleased(e)
						if (partialName.length > 1)
						{
							populateCenterPanel(
								resolvers
									.flatMap { it.matches(partialName) }
									.sortedBy { it.qualifiedName })
							pack()
							resultsPanel.validate()
							repaint()
						}
						else
						{
							populateCenterPanel(listOf())
							pack()
							resultsPanel.validate()
							repaint()
						}
					}
				})
		}

		populateCenterPanel(listOf())
		panel.add(resultsPanel, BorderLayout.CENTER)
		rootPane.registerKeyboardAction(
			{
				this@SearchOpenModuleDialog.dispose()
			},
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW)

		add(panel)
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
	private fun displayWindow ()
	{
		setLocationRelativeTo(workbench)
		pack()
		maximumSize = Dimension(1000, 600)
		minimumSize = Dimension(500, size.height)
		isVisible = true
		toFront()
	}
}
