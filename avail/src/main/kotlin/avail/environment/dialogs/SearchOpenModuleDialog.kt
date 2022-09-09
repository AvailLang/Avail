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
import avail.utility.ifZero
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ActionMap
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.TextAction

/**
 * Opens the dialog for searching for an opening a module in an editor.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
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
	private val abbreviationField: JTextField = JTextField()

	/**
	 * The variable that holds onto the proposed module name to open.
	 */
	private val abbreviation get() = abbreviationField.text

	/**
	 * The active [ModuleRootResolver]s.
	 */
	private val resolvers =
		workbench.resolver.moduleRoots.map { it.resolver }

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
	private val resultsPanel = JPanel(BorderLayout()).apply {
		border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
	}

	/**
	 * The [list][JList] of [results][ResolverReference] from the most recent
	 * search.
	 */
	private var resultsList: JList<ResolverReference>? = null

	/**
	 * Populate the center panel with the list of located [ResolverReference]s.
	 *
	 * @param found
	 *   The list of [ResolverReference]s to display.
	 */
	private fun populateCenterPanel(found: List<ResolverReference>)
	{
		resultsPanel.removeAll()
		if (found.isEmpty())
		{
			resultsList = null
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
						if (e.keyCode == VK_ENTER && selectedIndex >= 0)
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
				list.selectedIndex = 0
				resultsList = list
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
	private fun openModule(reference: ResolverReference)
	{
		SwingUtilities.invokeLater {
			var isOpen = false
			val editor =
				workbench.openEditors.computeIfAbsent(reference.moduleName) {
					isOpen = true
					AvailEditor(
						workbench,
						reference.moduleName
					).apply(AvailEditor::open)
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

		abbreviationField.apply {
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
			document.addDocumentListener(object : DocumentListener
			{
				override fun insertUpdate(e: DocumentEvent) =
					abbreviationUpdated()

				override fun removeUpdate(e: DocumentEvent) =
					abbreviationUpdated()

				override fun changedUpdate(e: DocumentEvent) =
					abbreviationUpdated()
			})
			val actions = ActionMap().apply {
				parent = actionMap
				put(openModule, object : TextAction(openModule)
				{
					override fun actionPerformed(e: ActionEvent)
					{
						val list = resultsList
						if (list !== null && list.selectedIndex >= 0)
						{
							openModule(list.selectedValue)
						}
					}
				})
				put(previousReference, object : TextAction(previousReference)
				{
					override fun actionPerformed(e: ActionEvent)
					{
						val list = resultsList
						if (list !== null)
						{
							var index = list.selectedIndex - 1
							if (index < 0)
							{
								index = list.model.size
							}
							list.selectedIndex = index
						}
					}
				})
				put(nextReference, object : TextAction(nextReference)
				{
					override fun actionPerformed(e: ActionEvent)
					{
						val list = resultsList
						if (list !== null)
						{
							var index = list.selectedIndex + 1
							if (index >= list.model.size)
							{
								index = 0
							}
							list.selectedIndex = index
						}
					}
				})
			}
			actionMap = actions
			inputMap.put(getKeyStroke(VK_ENTER, 0), openModule)
			inputMap.put(getKeyStroke(VK_UP, 0), previousReference)
			inputMap.put(getKeyStroke(VK_DOWN, 0), nextReference)
		}

		populateCenterPanel(listOf())
		panel.add(resultsPanel, BorderLayout.CENTER)
		rootPane.registerKeyboardAction(
			{ this@SearchOpenModuleDialog.dispose() },
			getKeyStroke(VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW)
		add(panel)
		displayWindow()
	}

	/**
	 * The [abbreviation][abbreviationField] was updated, so refresh the
	 * results accordingly.
	 */
	private fun abbreviationUpdated()
	{
		populateCenterPanel(
			if (abbreviation.isNotEmpty()) resolvers.results
			else listOf())
		pack()
		resultsPanel.validate()
		repaint()
	}

	/**
	 * The sublist of the receiver whose elements match the abbreviation, sorted
	 * sensibly for likely user interest.
	 */
	private val List<ModuleRootResolver>.results get() =
		flatMap { it.referencesMatchingAbbreviation(abbreviation, true) }
			.sortedWith { a, b ->
				val aqn = a.qualifiedName
				val bqn = b.qualifiedName
				// Exact matches have the highest priority.
				(bqn == abbreviation).compareTo(aqn == abbreviation)
					// Component prefix matches have the next priority.
					.ifZero(
						{
							bqn
								.substringAfterLast('/')
								.startsWith(abbreviation, true)
						},
						{
							aqn
								.substringAfterLast('/')
								.startsWith(abbreviation, true)
						}
					)
					// Containment matches have the next priority.
					.ifZero(
						{ bqn.contains(abbreviation, true) },
						{ aqn.contains(abbreviation, true) }
					)
					// Length has the next priority.
					.ifZero(
						{ aqn.length },
						{ bqn.length }
					)
					// Finally, fall back on lexicographic order.
					.ifZero { aqn.compareTo(bqn) }
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
		maximumSize = Dimension(1000, 600)
		minimumSize = Dimension(500, size.height)
		setLocationRelativeTo(workbench)
		isVisible = true
		toFront()
	}

	companion object
	{
		/** The name of the anonymous open-module action. */
		const val openModule = "open-module"

		/** The name of the anonymous previous-reference action. */
		const val previousReference = "previous-reference"

		/** The name of the anonymous next-reference action. */
		const val nextReference = "next-reference"
	}
}
