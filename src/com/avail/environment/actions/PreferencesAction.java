/**
 * PreferencesAction.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.environment.actions;

import com.avail.annotations.InnerAccess;
import com.avail.builder.ModuleRoot;
import com.avail.builder.ModuleRoots;
import com.avail.environment.AvailWorkbench;
import com.avail.persistence.IndexedFileException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.lang.Math.min;

/**
 * An {@code AboutAction} presents the "About Avail" dialog.
 */
@SuppressWarnings("serial")
public final class PreferencesAction
extends AbstractWorkbenchAction
{
	public final class SimpleTableModel extends AbstractTableModel
	{
		private final String[] columnNames;
		private final List<List<String>> rows = new ArrayList<>();

		SimpleTableModel (final String... columnNames)
		{
			this.columnNames = columnNames;
		}

		@Override
		public String getColumnName (final int column) {
			return columnNames[column];
		}

		@Override
		public int getRowCount ()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount ()
		{
			return columnNames.length;
		}

		@Override
		public Object getValueAt (final int row, final int column) {
			return rows.get(row).get(column);
		}

		@Override
		public boolean isCellEditable (final int row, final int column)
		{
			return true;
		}

		@Override
		public void setValueAt (final Object value, final int row, final int column) {
			rows.get(row).set(column, (String)value);
			fireTableCellUpdated(row, column);
		}

		public List<List<String>> rows ()
		{
			return rows;
		}
	}

	@InnerAccess @Nullable JDialog preferencesDialog;

	@InnerAccess final SimpleTableModel rootsTableModel =
		new SimpleTableModel("root", "repository", "source");

	@InnerAccess final SimpleTableModel renamesTableModel =
		new SimpleTableModel("module", "replacement path");

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		if (preferencesDialog == null)
		{
			createDialog();
			preferencesDialog.setVisible(true);
			preferencesDialog = null;
		}
		else
		{
			preferencesDialog.toFront();
		}
	}

	public void savePreferences ()
	{
		// Rebuild the ModuleRoots from the rootsTableModel.
		final ModuleRoots roots = workbench.resolver.moduleRoots();
		for (final ModuleRoot root : roots.roots())
		{
			root.repository().close();
		}
		roots.clearRoots();
		for (final List<String> triple : rootsTableModel.rows())
		{
			assert triple.size() == 3;
			try
			{
				final ModuleRoot root = new ModuleRoot(
					triple.get(0),
					new File(triple.get(1)),
					triple.get(2).isEmpty()
						? null
						: new File(triple.get(2)));
				roots.addRoot(root);
			}
			catch (final IndexedFileException e)
			{
				// Just ignore this malformed entry for now.
			}
			for (final ModuleRoot root : roots)
			{
				root.repository().reopenIfNecessary();
			}
		}

		// Rebuild the current rename rules from the renamesTableModel.
		workbench.resolver.clearRenameRules();
		for (final List<String> pair : renamesTableModel.rows())
		{
			assert pair.size() == 2;
			workbench.resolver.addRenameRule(pair.get(0), pair.get(1));
		}

		workbench.saveModuleConfiguration();
	}

	/**
	 * Actually show the Preferences dialog.  This is provided separately from
	 * the usual {@link ActionListener#actionPerformed(ActionEvent)} mechanism
	 * so that we can invoke it directly whenever we want, without having to
	 * synthesize an {@link ActionEvent}.
	 */
	public void createDialog ()
	{
		final JPanel panel = new JPanel(new BorderLayout(20, 20));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		preferencesDialog = new JDialog(workbench, "Preferences");


		// Add the module roots area.
		final JLabel rootsLabel = new JLabel("Avail module roots");
		panel.add(rootsLabel);

		rootsTableModel.rows().clear();
		for (final ModuleRoot root : workbench.resolver.moduleRoots().roots())
		{
			final List<String> triple = new ArrayList<>(3);
			triple.add(root.name());
			triple.add(root.repository().fileName().getPath());
			final @Nullable File source = root.sourceDirectory();
			triple.add(source == null ? "" : source.getPath());
			rootsTableModel.rows().add(triple);
		}
		final JTable rootsTable = new JTable(rootsTableModel);
		rootsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		final TableColumnModel rootsColumns = rootsTable.getColumnModel();
		rootsColumns.getColumn(0).setMinWidth(30);
		rootsColumns.getColumn(0).setPreferredWidth(60);
		rootsColumns.getColumn(1).setMinWidth(50);
		rootsColumns.getColumn(1).setPreferredWidth(400);
		rootsColumns.getColumn(2).setMinWidth(50);
		rootsColumns.getColumn(2).setPreferredWidth(400);
		rootsTable.setGridColor(Color.gray);
		rootsTable.setFillsViewportHeight(true);
		final JScrollPane rootsScrollPane = new JScrollPane(rootsTable);
		panel.add(rootsScrollPane);

		final AbstractWorkbenchAction addRootAction =
			new AbstractWorkbenchAction(workbench, "+")
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					int insertionIndex = rootsTable.getSelectedRow();
					if (insertionIndex == -1)
					{
						insertionIndex = rootsTableModel.getRowCount();
					}
					rootsTableModel.rows().add(
						insertionIndex, Arrays.asList("", "", ""));
					rootsTableModel.fireTableDataChanged();
					rootsTable.changeSelection(insertionIndex, 0, false, false);
				}
			};
		final JButton addRootButton = new JButton(addRootAction);
		panel.add(addRootButton);

		final AbstractWorkbenchAction removeRootAction =
			new AbstractWorkbenchAction(workbench, "-")
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					final int deletionIndex = rootsTable.getSelectedRow();
					if (deletionIndex != -1)
					{
						rootsTableModel.rows().remove(deletionIndex);
						rootsTableModel.fireTableDataChanged();
						rootsTable.changeSelection(
							rootsTableModel.rows().isEmpty()
								? -1
								: min(
									deletionIndex,
									rootsTableModel.getRowCount() - 1),
							0,
							false,
							false);
					}
				}
			};
		final JButton removeRootButton = new JButton(removeRootAction);
		panel.add(removeRootButton);


		// Add the renames area.
		final JLabel renamesLabel = new JLabel("Renames");
		panel.add(renamesLabel);

		renamesTableModel.rows().clear();
		for (final Entry<String, String> rename
			: workbench.resolver.renameRules().entrySet())
		{
			final List<String> pair = new ArrayList<>(2);
			pair.add(rename.getKey());
			pair.add(rename.getValue());
			renamesTableModel.rows().add(pair);
		}

		final JTable renamesTable = new JTable(renamesTableModel);
		renamesTable.putClientProperty(
			"terminateEditOnFocusLost", Boolean.TRUE);
		final TableColumnModel renamesColumns = renamesTable.getColumnModel();
		renamesColumns.getColumn(0).setMinWidth(50);
		renamesColumns.getColumn(0).setPreferredWidth(400);
		renamesColumns.getColumn(1).setMinWidth(50);
		renamesColumns.getColumn(1).setPreferredWidth(400);
		renamesTable.setGridColor(Color.gray);
		renamesTable.setFillsViewportHeight(true);
		final JScrollPane renamesScrollPane = new JScrollPane(renamesTable);
		panel.add(renamesScrollPane);

		final AbstractWorkbenchAction addRenameAction =
			new AbstractWorkbenchAction(workbench, "+")
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					int insertionIndex = renamesTable.getSelectedRow();
					if (insertionIndex == -1)
					{
						insertionIndex = renamesTableModel.getRowCount();
					}
					renamesTableModel.rows().add(
						insertionIndex, Arrays.asList("", ""));
					renamesTableModel.fireTableDataChanged();
					renamesTable.changeSelection(
						insertionIndex, 0, false, false);
				}
			};
		final JButton addRenameButton = new JButton(addRenameAction);
		panel.add(addRenameButton);

		final AbstractWorkbenchAction removeRenameAction =
			new AbstractWorkbenchAction(workbench, "-")
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					final int deletionIndex = renamesTable.getSelectedRow();
					if (deletionIndex != -1)
					{
						renamesTableModel.rows().remove(deletionIndex);
						renamesTableModel.fireTableDataChanged();
						renamesTable.changeSelection(
							renamesTableModel.rows().isEmpty()
								? -1
								: min(
									deletionIndex,
									renamesTableModel.getRowCount() - 1),
							0,
							false,
							false);
					}
				}
			};
		final JButton removeRenameButton = new JButton(removeRenameAction);
		panel.add(removeRenameButton);


		// Add the ok/cancel buttons.
		final AbstractWorkbenchAction okAction =
			new AbstractWorkbenchAction(
				workbench,
				UIManager.getString("OptionPane.okButtonText"))
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					savePreferences();
					workbench.refresh();
					preferencesDialog.setVisible(false);
				}
			};
		final JButton okButton = new JButton(okAction);
		panel.add(okButton);
		final AbstractWorkbenchAction cancelAction =
			new AbstractWorkbenchAction(
				workbench,
				UIManager.getString("OptionPane.cancelButtonText"))
			{
				@Override
				public void actionPerformed (final ActionEvent e)
				{
					preferencesDialog.setVisible(false);
				}
			};
		final JButton cancelButton = new JButton(cancelAction);
		panel.add(cancelButton);

		final GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
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
					.addComponent(cancelButton)));
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
					.addComponent(cancelButton)));
		layout.linkSize(SwingConstants.HORIZONTAL, okButton, cancelButton);
		preferencesDialog.setMinimumSize(new Dimension(300, 250));
		preferencesDialog.setPreferredSize(new Dimension(900, 500));
		preferencesDialog.setModalityType(ModalityType.APPLICATION_MODAL);
		preferencesDialog.getContentPane().add(panel);
		preferencesDialog.setResizable(true);
		preferencesDialog.pack();
		final Point topLeft = workbench.getLocation();
		preferencesDialog.setLocation(
			(int)topLeft.getX() + 22, (int)topLeft.getY() + 22);
	}

	/**
	 * Construct a new {@link PreferencesAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public PreferencesAction (final AvailWorkbench workbench)
	{
		super(workbench, "Preferences…");
		putValue(
			SHORT_DESCRIPTION,
			"Preferences…");
	}
}
