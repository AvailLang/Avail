/*
 * PreferencesAction.kt
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

package avail.anvil.actions

import avail.anvil.AvailWorkbench
import org.availlang.persistence.IndexedFileException
import avail.persistence.cache.Repositories
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.concurrent.Semaphore
import javax.swing.Action
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities.invokeLater
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import kotlin.math.min

/**
 * A [PreferencesAction] presents the preferences dialog.
 * TODO Implement user preferences not related to roots/renames or other
 *  project-specific data stored in JSON config file.
 *
 * @constructor
 * Construct a new [PreferencesAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
@Deprecated("Placeholder until we have preferences that aren't in JSON file.")
class PreferencesAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Preferences…")
{
	private var preferencesDialog: JDialog? = null

	private val rootsTableModel =
		SimpleTableModel("root", "source")

	private val renamesTableModel =
		SimpleTableModel("module", "replacement path")

	inner class SimpleTableModel internal constructor(
		vararg columnNames: String) : AbstractTableModel()
	{
		private val columnNames: Array<String> = arrayOf(*columnNames)
		val rows = mutableListOf<MutableList<String>>()

		override fun getColumnName(column: Int): String = columnNames[column]

		override fun getRowCount(): Int = rows.size

		override fun getColumnCount(): Int = columnNames.size

		override fun getValueAt(row: Int, column: Int): Any = rows[row][column]

		override fun isCellEditable(row: Int, column: Int): Boolean = true

		override fun setValueAt(value: Any, row: Int, column: Int)
		{
			rows[row][column] = value as String
			fireTableCellUpdated(row, column)
		}
	}

	override fun actionPerformed(event: ActionEvent?)
	{
		// Do nothing for now.
		if (true)
		{
			JOptionPane.showMessageDialog(
				workbench,
				"Preferences dialog is currently under construction.",
				"Warning",
				JOptionPane.WARNING_MESSAGE)
			return
		}

		if (preferencesDialog === null)
		{
			createDialog()
			preferencesDialog!!.isVisible = true
			preferencesDialog = null
		}
		else
		{
			preferencesDialog!!.toFront()
		}
	}

	fun savePreferences()
	{
		// Stubbed until it has non-roots/renames functionality.
		if (true) return

		// Rebuild the ModuleRoots from the rootsTableModel.
		val roots = workbench.resolver.moduleRoots
		Repositories.closeAllRepositories()
		roots.clearRoots()
		for (pair in rootsTableModel.rows)
		{
			assert(pair.size == 2)
			try
			{
				val (name, uri) = pair
				val semaphore = Semaphore(0)
				roots.addRoot(name, uri) { failures ->
					failures.forEach { failure ->
						System.err.println(failure)
					}
					semaphore.release()
				}
				semaphore.acquire()
			}
			catch (e: IndexedFileException)
			{
				// Just ignore this malformed entry for now.
			}

			for (root in roots)
			{
				root.repository.reopenIfNecessary()
			}
		}

		// Rebuild the current rename rules from the renamesTableModel.
		workbench.resolver.clearRenameRules()
		for (pair in renamesTableModel.rows)
		{
			assert(pair.size == 2)
			workbench.resolver.addRenameRule(pair[0], pair[1])
		}

//		workbench.saveModuleConfiguration()
	}

	/**
	 * Actually show the Preferences dialog.  This is provided separately from
	 * the usual [ActionListener.actionPerformed] mechanism so that we can
	 * invoke it directly whenever we want, without having to synthesize an
	 * [ActionEvent].
	 */
	private fun createDialog()
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)

		preferencesDialog = JDialog(workbench, "Preferences")

		// Add the module roots area.
		val rootsLabel = JLabel("Avail module roots")
		panel.add(rootsLabel)
		rootsTableModel.rows.clear()
		for (root in workbench.resolver.moduleRoots.roots)
		{
			rootsTableModel.rows.add(
				mutableListOf(root.name, root.resolver.uri.toString()))
		}
		val rootsTable = JTable(rootsTableModel)
		rootsTable.putClientProperty("terminateEditOnFocusLost", true)
		val rootsColumns = rootsTable.columnModel
		rootsColumns.getColumn(0).run { minWidth = 30; preferredWidth = 60 }
		rootsColumns.getColumn(1).run { minWidth = 50;  preferredWidth = 500 }
		val rootsScrollPane = JScrollPane(rootsTable)
		panel.add(rootsScrollPane)
		val (addRootAction, removeRootAction) = actionsFor(rootsTable)
		val addRootButton = JButton(addRootAction)
		panel.add(addRootButton)
		val removeRootButton = JButton(removeRootAction)
		panel.add(removeRootButton)

		// Add the renames area.
		val renamesLabel = JLabel("Renames")
		panel.add(renamesLabel)
		renamesTableModel.rows.clear()
		for ((key, value) in workbench.resolver.renameRules)
		{
			renamesTableModel.rows.add(mutableListOf(key, value))
		}
		val renamesTable = JTable(renamesTableModel)
		renamesTable.putClientProperty("terminateEditOnFocusLost", true)
		val renamesColumns = renamesTable.columnModel
		renamesColumns.getColumn(0).run { minWidth = 50; preferredWidth = 400 }
		renamesColumns.getColumn(1).run { minWidth = 50; preferredWidth = 400 }
		val renamesScrollPane = JScrollPane(renamesTable)
		panel.add(renamesScrollPane)
		val (addRenameAction, removeRenameAction) = actionsFor(renamesTable)
		val addRenameButton = JButton(addRenameAction)
		panel.add(addRenameButton)
		val removeRenameButton = JButton(removeRenameAction)
		panel.add(removeRenameButton)

		// Add the ok/cancel buttons.
		val okAction = object : AbstractWorkbenchAction(
			workbench,
			UIManager.getString("OptionPane.okButtonText"))
		{
			override fun actionPerformed(e: ActionEvent)
			{
				savePreferences()
				workbench.calculateRefreshedTreesThen { modules, entryPoints ->
					invokeLater {
						workbench.refreshFor(modules, entryPoints)
						preferencesDialog!!.isVisible = false
					}
				}
			}
		}
		val okButton = JButton(okAction)
		panel.add(okButton)
		val cancelAction = object : AbstractWorkbenchAction(
			workbench,
			UIManager.getString("OptionPane.cancelButtonText"))
		{
			override fun actionPerformed(e: ActionEvent)
			{
				preferencesDialog!!.isVisible = false
			}
		}
		val cancelButton = JButton(cancelAction)
		panel.add(cancelButton)

		val layout = GroupLayout(panel)
		panel.layout = layout
		layout.autoCreateGaps = true
		layout.setHorizontalGroup(
			layout.createParallelGroup()
				.addComponent(rootsLabel)
				.addComponent(rootsScrollPane)
				.addGroup(layout.createSequentialGroup()
					.addComponent(addRootButton)
					.addComponent(removeRootButton))
				.addComponent(renamesLabel)
				.addComponent(renamesScrollPane)
				.addGroup(layout.createSequentialGroup()
					.addComponent(addRenameButton)
					.addComponent(removeRenameButton))
				.addGroup(Alignment.TRAILING, layout.createSequentialGroup()
					.addComponent(okButton)
					.addComponent(cancelButton)))
		layout.setVerticalGroup(
			layout.createSequentialGroup()
				.addComponent(rootsLabel)
				.addComponent(rootsScrollPane)
				.addGroup(layout.createParallelGroup()
					.addComponent(addRootButton)
					.addComponent(removeRootButton))
				.addComponent(renamesLabel)
				.addComponent(renamesScrollPane)
				.addGroup(layout.createParallelGroup()
					.addComponent(addRenameButton)
					.addComponent(removeRenameButton))
				.addGroup(layout.createParallelGroup()
					.addComponent(okButton)
					.addComponent(cancelButton)))
		layout.linkSize(SwingConstants.HORIZONTAL, okButton, cancelButton)
		preferencesDialog!!.run {
			minimumSize = Dimension(300, 250)
			preferredSize = Dimension(900, 500)
			modalityType = ModalityType.APPLICATION_MODAL
			contentPane.add(panel)
			isResizable = true
			pack()
			location = workbench.location.apply { translate(22, 22) }
		}
	}

	/**
	 * Answer the add and remove actions for this table.
	 *
	 * @param table
	 *   The [JTable] for which to create add and remove actions.  The add
	 *   action will create a row containing two empty strings.
	 * @return
	 *   A [Pair] containing the add action and the remove action.
	 */
	private fun actionsFor(
		table: JTable
	): Pair<AbstractWorkbenchAction, AbstractWorkbenchAction>
	{
		table.gridColor = Color.gray
		table.fillsViewportHeight = true
		val addAction = object : AbstractWorkbenchAction(workbench, "+")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				val model = table.model as SimpleTableModel
				var insertionIndex = table.selectedRow
				if (insertionIndex == -1)
				{
					insertionIndex = model.rowCount
				}
				model.rows.add(insertionIndex, mutableListOf("", ""))
				model.fireTableDataChanged()
				table.changeSelection(insertionIndex, 0, false, false)
			}
		}
		val removeAction = object : AbstractWorkbenchAction(workbench, "-")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				val deletionIndex = table.selectedRow
				if (deletionIndex != -1)
				{
					val model = table.model as SimpleTableModel
					model.rows.removeAt(deletionIndex)
					model.fireTableDataChanged()
					table.changeSelection(
						if (model.rows.isEmpty()) -1
						else min(deletionIndex, model.rowCount - 1),
						0,
						false,
						false)
				}
			}
		}
		return addAction to removeAction
	}

	init
	{
		putValue(Action.SHORT_DESCRIPTION, "Preferences…")
	}
}
