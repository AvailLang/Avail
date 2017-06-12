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

import com.avail.environment.AvailWorkbench;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@code AboutAction} presents the "About Avail" dialog.
 */
@SuppressWarnings("serial")
public final class PreferencesAction
extends AbstractWorkbenchAction
{
	public final class SimpleTableModel extends AbstractTableModel
	{
		final String[] columnNames;
		final List<List<String>> rows = new ArrayList<>();

		SimpleTableModel (final String... columnNames)
		{
			this.columnNames = columnNames;
		}

		public String getColumnName (int column) {
			return columnNames[column];
		}

		public int getRowCount()
		{
			return rows.size();
		}

		public int getColumnCount()
		{
			return columnNames.length;
		}

		public Object getValueAt(int row, int column) {
			return rows.get(row).get(column);
		}

		public boolean isCellEditable(int row, int column)
		{
			return true;
		}

		public void setValueAt(Object value, int row, int column) {
			rows.get(row).set(column, (String)value);
			fireTableCellUpdated(row, column);
		}
	}


	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		showDialog();
	}

	/**
	 * Actually show the Preferences dialog.  This is provided separately from
	 * the usual {@link ActionListener#actionPerformed(ActionEvent)} mechanism
	 * so that we can invoke it directly whenever we want, without having to
	 * synthesize an {@link ActionEvent}.
	 */
	public void showDialog ()
	{
		final JPanel panel = new JPanel(new BorderLayout(20, 20));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel rootsLabel = new JLabel("Avail module roots");
		panel.add(rootsLabel);

		SimpleTableModel rootsTableModel = new SimpleTableModel(
			"root", "source", "repository");
		final JTable rootsTable = new JTable(rootsTableModel);
		rootsTable.setFillsViewportHeight(true);
		JScrollPane rootsScrollPane = new JScrollPane(rootsTable);
		panel.add(rootsScrollPane);

		//TODO MvG - Add the renames table.

		final JDialog preferencesDialog = new JDialog(workbench, "Preferences");
		preferencesDialog.setModalityType(ModalityType.APPLICATION_MODAL);
		preferencesDialog.getContentPane().add(panel);
		preferencesDialog.setResizable(true);
		preferencesDialog.pack();
		final Point topLeft = workbench.getLocation();
		preferencesDialog.setLocation(
			(int)topLeft.getX() + 22, (int)topLeft.getY() + 22);
		preferencesDialog.setVisible(true);
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
