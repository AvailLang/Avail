/*
 * CleanModuleAction.java
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

import com.avail.builder.ModuleRoot;
import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.persistence.IndexedFileException;
import com.avail.persistence.IndexedRepositoryManager;

import javax.annotation.Nullable;
import java.awt.event.ActionEvent;

import static com.avail.environment.AvailWorkbench.StreamStyle.INFO;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;

/**
 * A {@code CleanModuleAction} removes from the repository file all compiled
 * versions of the selected module.  If a package or root is selected, this is
 * done for all modules within it.
 */
@SuppressWarnings("serial")
public final class CleanModuleAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;

		final @Nullable ModuleRoot root = workbench.selectedModuleRoot();
		if (root != null)
		{
			// Delete an entire repository.
			try
			{
				root.clearRepository();
			}
			catch (final IndexedFileException e)
			{
				// Ignore problem for now.
			}
			workbench.writeText(
				format("Repository %s has been cleaned.%n", root.name()),
				INFO);
			return;
		}

		// Delete a module or package (and everything inside it).
		final ResolvedModuleName selectedModule =
			stripNull(workbench.selectedModule());
		final String rootRelative = selectedModule.rootRelativeName();
		final IndexedRepositoryManager repository = selectedModule.repository();
		repository.cleanModulesUnder(rootRelative);
		repository.commit();
		workbench.writeText(
			format(
				"Module or package %s has been cleaned.%n",
				selectedModule.qualifiedName()),
			INFO);
	}

	/**
	 * Construct a new {@code CleanModuleAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public CleanModuleAction (final AvailWorkbench workbench)
	{
		super(workbench, "Clean Module");
		putValue(
			SHORT_DESCRIPTION,
			"Invalidate cached compilations for a module/package.");
	}
}
