/*
 * ExamineCompilationAction.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.AvailRuntime;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.FiberDescriptor;
import com.avail.environment.AvailWorkbench;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilation;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.persistence.IndexedRepositoryManagerDescriber;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import static com.avail.utility.Casts.nullableCast;
import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code ExamineCompilationAction} presents information about a specific
 * compilation of the selected module.
 */
@SuppressWarnings("serial")
public final class ExamineCompilationAction
extends AbstractWorkbenchAction
{
	/** The current runtime. */
	private final AvailRuntime runtime;

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		workbench.clearTranscript();
		runtime.execute(
			FiberDescriptor.commandPriority,
			() ->
			{
				final ResolvedModuleName moduleName =
					stripNull(workbench.selectedModule());
				try (final IndexedRepositoryManager repository =
					moduleName.repository())
				{
					repository.reopenIfNecessary();
					final List<ModuleCompilation> compilations =
						new ArrayList<>();
					final ModuleArchive archive =
						repository.getArchive(moduleName.rootRelativeName());
					for (final Entry<ModuleVersionKey, ModuleVersion> entry :
						archive.getAllKnownVersions().entrySet())
					{
						// final ModuleVersionKey versionKey = entry.getKey();
						final ModuleVersion version = entry.getValue();
						compilations.addAll(version.getAllCompilations());
					}
					final Object[] compilationsArray = compilations.toArray();
					final @Nullable ModuleCompilation selectedCompilation =
						nullableCast(
							JOptionPane.showInputDialog(
								workbench,
								"Select module compilation to examine",
								"Examine compilation",
								JOptionPane.PLAIN_MESSAGE,
								null,
								compilationsArray,
								compilationsArray.length > 0
									? compilationsArray[0]
									: null));
					if (selectedCompilation == null)
					{
						// Nothing was selected, so abort the command silently.
						return;
					}

					final IndexedRepositoryManagerDescriber describer =
						new IndexedRepositoryManagerDescriber(repository);
					final String description = describer.describeCompilation(
						selectedCompilation.getRecordNumber());
					workbench.outputStream().println(description);
				}
			});
	}

	/**
	 * Construct a new {@code ExamineRepositoryAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param runtime
	 *        The active {@link AvailRuntime}.
	 */
	public ExamineCompilationAction (
		final AvailWorkbench workbench,
		final AvailRuntime runtime)
	{
		super(workbench, "Examine compilation");
		this.runtime = runtime;
		putValue(
			SHORT_DESCRIPTION,
			"Disassemble an existing module compilation");
	}
}
