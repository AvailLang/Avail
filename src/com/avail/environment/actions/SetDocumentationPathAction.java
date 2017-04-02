/**
 * SetDocumentationPathAction.java
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

import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchAction;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code SetDocumentationPathAction} displays a {@linkplain
 * JOptionPane modal dialog} that prompts the user for the Stacks
 * documentation path.
 */
@SuppressWarnings("serial")
public final class SetDocumentationPathAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		final JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setSelectedFile(workbench.documentationPath.toFile());
		chooser.ensureFileIsVisible(workbench.documentationPath.toFile());
		chooser.addChoosableFileFilter(new FileFilter()
		{
			@Override
			public String getDescription ()
			{
				return "Directories";
			}

			@Override
			public boolean accept (final @Nullable File f)
			{
				assert f != null;
				return f.isDirectory() && f.canWrite();
			}
		});
		final int result = chooser.showDialog(
			workbench, "Set Documentation Path");
		if (result == JFileChooser.APPROVE_OPTION)
		{
			workbench.documentationPath = chooser.getSelectedFile().toPath();
		}
	}

	/**
	 * Construct a new {@link SetDocumentationPathAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public SetDocumentationPathAction (final AvailWorkbench workbench)
	{
		super(workbench, "Set Documentation Path…");
		putValue(
			SHORT_DESCRIPTION,
			"Set the Stacks documentation path.");
	}
}
