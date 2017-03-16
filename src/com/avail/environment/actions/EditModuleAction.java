/**
 * BuildAction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchAction;
import com.avail.environment.tasks.ViewModuleTask;
import com.avail.environment.viewer.ModuleViewer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * A {@code EditModuleAction} launches a {@linkplain ModuleViewer module viewer}
 * in a Swing worker thread.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
@SuppressWarnings("serial")
public final class EditModuleAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;

		final ResolvedModuleName selectedModule = workbench.selectedModule();
		assert selectedModule != null;

		final JFrame frame = workbench.openedSourceModules.get(selectedModule);
		if (frame != null)
		{
			frame.setVisible(true);
			frame.toFront();
			frame.requestFocus();
		}
		else
		{
			// Build the target module in a Swing worker thread.
			final ViewModuleTask task =
				new ViewModuleTask(workbench, selectedModule);
			workbench.backgroundTask = task;
			workbench.availBuilder.checkStableInvariants();
			workbench.setEnablements();
			task.execute();
		}
	}

	/**
	 * Construct a new {@link EditModuleAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public EditModuleAction (final AvailWorkbench workbench)
	{
		super(workbench, "Edit");
		putValue(
			SHORT_DESCRIPTION,
			"Edit the selected module");
		putValue(
			ACCELERATOR_KEY,
			KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
	}
}
