/*
 * UnloadAllAction.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.builder.AvailBuilder;
import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.tasks.DocumentationTask;
import com.avail.stacks.StacksGenerator;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static com.avail.utility.Nulls.stripNull;
import static java.awt.Cursor.WAIT_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;

/**
 * A {@code GenerateDocumentationAction} instructs the {@linkplain
 * AvailBuilder Avail builder} to recursively {@linkplain StacksGenerator
 * generate Stacks documentation}.
 */
@SuppressWarnings("serial")
public final class GenerateDocumentationAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;
		final ResolvedModuleName selectedModule =
			stripNull(workbench.selectedModule());

		// Update the UI.
		workbench.setCursor(getPredefinedCursor(WAIT_CURSOR));
		workbench.setEnablements();
		workbench.buildProgress.setValue(0);
		workbench.clearTranscript();

		// Generate documentation for the target module in a Swing worker
		// thread.
		final DocumentationTask task =
			new DocumentationTask(workbench, selectedModule);
		workbench.backgroundTask = task;
		workbench.setEnablements();
		task.execute();
	}

	/**
	 * Construct a new {@link GenerateDocumentationAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public GenerateDocumentationAction (final AvailWorkbench workbench)
	{
		super(workbench, "Generate Documentation");
		putValue(
			SHORT_DESCRIPTION,
			"Generate API documentation for the selected module and "
			+ "its ancestors.");
		putValue(
			ACCELERATOR_KEY,
			KeyStroke.getKeyStroke(
				KeyEvent.VK_G,
				AvailWorkbench.menuShortcutMask));
	}
}
