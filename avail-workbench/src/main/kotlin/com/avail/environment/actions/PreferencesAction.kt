/*
 * PreferencesAction.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.environment.actions

import com.avail.builder.ModuleRoot
import com.avail.environment.AvailWorkbench
import com.avail.persistence.IndexedFileException
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.util.*
import javax.swing.Action
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
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
 * An `AboutAction` presents the "About Avail" dialog.
 *
 * @constructor
 * Construct a new [PreferencesAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class PreferencesAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Preferences…")
{
	internal var preferencesDialog: JDialog? = null

	internal val rootsTableModel =
		SimpleTableModel("root", "repository", "source")

	internal val renamesTableModel =
		SimpleTableModel("module", "replacement path")

	inner class SimpleTableModel internal constructor(
		vararg columnNames: String) : AbstractTableModel()
	{
		private val columnNames: Array<String> = arrayOf(*columnNames)
		val rows = mutableListOf<MutableList<String>>()

		override fun getColumnName(column: Int): String =  columnNames[column]

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
		if (preferencesDialog == null)
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
		// Rebuild the ModuleRoots from the rootsTableModel.
		val roots = workbench.resolver.moduleRoots
		for (root in roots.roots)
		{
			root.repository.close()
		}
		roots.clearRoots()
		for (triple in rootsTableModel.rows)
		{
			assert(triple.size == 3)
			try
			{
				val root = ModuleRoot(
					triple[0],
					File(triple[1]),
					if (triple[2].isEmpty())
						null
					else
						File(triple[2]))
				roots.addRoot(root)
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

		workbench.saveModuleConfiguration()
	}

	/**
	 * Actually show the Preferences dialog.  This is provided separately from
	 * the usual [ActionListener.actionPerformed] mechanism so that we can
	 * invoke it directly whenever we want, without having to synthesize an
	 * [ActionEvent].
	 */
	fun createDialog()
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
			val triple = ArrayList<String>(3)
			triple.add(root.name)
			triple.add(root.repository.fileName.path)
			val source = root.sourceDirectory
			triple.add(if (source == null) "" else source.path)
			rootsTableModel.rows.add(triple)
		}
		val rootsTable = JTable(rootsTableModel)
		rootsTable.putClientProperty("terminateEditOnFocusLost", java.lang.Boolean.TRUE)
		val rootsColumns = rootsTable.columnModel
		rootsColumns.getColumn(0).minWidth = 30
		rootsColumns.getColumn(0).preferredWidth = 60
		rootsColumns.getColumn(1).minWidth = 50
		rootsColumns.getColumn(1).preferredWidth = 400
		rootsColumns.getColumn(2).minWidth = 50
		rootsColumns.getColumn(2).preferredWidth = 400
		rootsTable.gridColor = Color.gray
		rootsTable.fillsViewportHeight = true
		val rootsScrollPane = JScrollPane(rootsTable)
		panel.add(rootsScrollPane)

		val addRootAction = object : AbstractWorkbenchAction(workbench, "+")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				var insertionIndex = rootsTable.selectedRow
				if (insertionIndex == -1)
				{
					insertionIndex = rootsTableModel.rowCount
				}
				rootsTableModel.rows.add(
					insertionIndex, mutableListOf("", "", ""))
				rootsTableModel.fireTableDataChanged()
				rootsTable.changeSelection(
					insertionIndex, 0, false, false)
			}
		}
		val addRootButton = JButton(addRootAction)
		panel.add(addRootButton)

		val removeRootAction = object : AbstractWorkbenchAction(workbench, "-")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				val deletionIndex = rootsTable.selectedRow
				if (deletionIndex != -1)
				{
					rootsTableModel.rows.removeAt(deletionIndex)
					rootsTableModel.fireTableDataChanged()
					rootsTable.changeSelection(
						if (rootsTableModel.rows.isEmpty())
							-1
						else
							min(
								deletionIndex,
								rootsTableModel.rowCount - 1),
						0,
						false,
						false)
				}
			}
		}
		val removeRootButton = JButton(removeRootAction)
		panel.add(removeRootButton)


		// Add the renames area.
		val renamesLabel = JLabel("Renames")
		panel.add(renamesLabel)

		renamesTableModel.rows.clear()
		for ((key, value) in workbench.resolver.renameRules)
		{
			val pair = ArrayList<String>(2)
			pair.add(key)
			pair.add(value)
			renamesTableModel.rows.add(pair)
		}

		val renamesTable = JTable(renamesTableModel)
		renamesTable.putClientProperty(
			"terminateEditOnFocusLost", java.lang.Boolean.TRUE)
		val renamesColumns = renamesTable.columnModel
		renamesColumns.getColumn(0).minWidth = 50
		renamesColumns.getColumn(0).preferredWidth = 400
		renamesColumns.getColumn(1).minWidth = 50
		renamesColumns.getColumn(1).preferredWidth = 400
		renamesTable.gridColor = Color.gray
		renamesTable.fillsViewportHeight = true
		val renamesScrollPane = JScrollPane(renamesTable)
		panel.add(renamesScrollPane)

		val addRenameAction = object : AbstractWorkbenchAction(workbench, "+")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				var insertionIndex = renamesTable.selectedRow
				if (insertionIndex == -1)
				{
					insertionIndex = renamesTableModel.rowCount
				}
				renamesTableModel.rows.add(
					insertionIndex, mutableListOf("", ""))
				renamesTableModel.fireTableDataChanged()
				renamesTable.changeSelection(
					insertionIndex, 0, false, false)
			}
		}
		val addRenameButton = JButton(addRenameAction)
		panel.add(addRenameButton)

		val removeRenameAction = object : AbstractWorkbenchAction(workbench, "-")
		{
			override fun actionPerformed(e: ActionEvent)
			{
				val deletionIndex = renamesTable.selectedRow
				if (deletionIndex != -1)
				{
					renamesTableModel.rows.removeAt(deletionIndex)
					renamesTableModel.fireTableDataChanged()
					renamesTable.changeSelection(
						if (renamesTableModel.rows.isEmpty())
							-1
						else
							min(
								deletionIndex,
								renamesTableModel.rowCount - 1),
						0,
						false,
						false)
				}
			}
		}
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
				val trees = workbench.calculateRefreshedTrees()
				invokeLater {
					workbench.refreshFor(trees.first(), trees.second())
					preferencesDialog!!.isVisible = false
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
		preferencesDialog!!.minimumSize = Dimension(300, 250)
		preferencesDialog!!.preferredSize = Dimension(900, 500)
		preferencesDialog!!.modalityType = ModalityType.APPLICATION_MODAL
		preferencesDialog!!.contentPane.add(panel)
		preferencesDialog!!.isResizable = true
		preferencesDialog!!.pack()
		val topLeft = workbench.location
		preferencesDialog!!.setLocation(
			topLeft.getX().toInt() + 22, topLeft.getY().toInt() + 22)
	}

	init
	{
		putValue(Action.SHORT_DESCRIPTION, "Preferences…")
	}
}
