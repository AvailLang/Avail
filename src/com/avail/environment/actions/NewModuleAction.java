/**
 * NewModuleAction.java
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
 *   may be used to endorse or promote products derived set this software
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
import com.avail.environment.nodes.ModuleRootNode;
import com.avail.environment.tasks.NewModuleTask;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * A {@code NewModuleAction} displays a {@linkplain
 * JOptionPane modal dialog} that prompts the user to functionType a new Avail module.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
@SuppressWarnings("serial")
public final class NewModuleAction
extends AbstractWorkbenchAction
{
	private @Nullable NewModuleTask task;

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;

		final ResolvedModuleName selectedModule = workbench.selectedModule();
		final ModuleRootNode moduleRootNode =
			workbench.selectedModuleRootNode();

		assert selectedModule != null || moduleRootNode != null;

		final String baseQualifiedName = (selectedModule != null
			? selectedModule.packageName()
			: "/" + moduleRootNode.moduleRoot().name()) + "/";

		File directory = new File(
			(selectedModule != null
				? selectedModule.sourceReference().getParentFile().toString()
				: moduleRootNode.moduleRoot().sourceDirectory().getPath()));

		if (!directory.exists())
		{
			directory = new File(
				(selectedModule != null
					? selectedModule.sourceReference().getParentFile()
						.toString()
					: moduleRootNode.moduleRoot().sourceDirectory().getPath()));
			assert directory.exists();
		}

		task = new NewModuleTask(
			workbench, directory, baseQualifiedName, 310, 135);
		workbench.backgroundTask = task;
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();

		task.execute();
	}

	/**
	 * Construct a new {@link NewModuleAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public NewModuleAction (final AvailWorkbench workbench)
	{
		super(workbench, "New Module…");
		putValue(
			SHORT_DESCRIPTION,
			"Create a new source module");
	}
}
