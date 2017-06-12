/**
 * InsertEntryPointAction.java
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

import java.awt.*;
import java.awt.event.*;
import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.tasks.BuildTask;
import org.jetbrains.annotations.Nullable;

/**
 * An {@code InsertEntryPointAction} inserts a string based on the currently
 * selected entry point into the user input field.
 */
@SuppressWarnings("serial")
public final class InsertEntryPointAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;

		final String selectedEntryPoint = workbench.selectedEntryPoint();
		if (selectedEntryPoint == null)
		{
			return;
		}
		// Strip back-ticks as a nicety.  Also put spaces around underscores
		// that are adjacent to words (Note that underscore is itself
		// considered a word character, so we want \B to see if the
		// character adjacent to the underscore is also a word character.
		// We could do more, but this should be sufficient for now.
		final String entryPointText = selectedEntryPoint
			.replaceAll("`", "")
			.replaceAll("\\B_", " _")
			.replaceAll("_\\B", "_ ");
		assert entryPointText != null;
		workbench.inputField.setText(entryPointText);
		final int offsetToUnderscore = entryPointText.indexOf("_");
		final int offset;
		if (offsetToUnderscore == -1)
		{
			offset = entryPointText.length();
			workbench.inputField.select(offset, offset);
		}
		else
		{
			// Select the underscore.
			offset = offsetToUnderscore;
			workbench.inputField.select(offset, offset + 1);
		}
		workbench.inputField.requestFocusInWindow();

		final ResolvedModuleName moduleName =
			workbench.selectedEntryPointModule();
		if (moduleName != null)
		{
			if (workbench.availBuilder.getLoadedModule(moduleName) == null)
			{
				// Start loading the module as a convenience.
				workbench.setCursor(
					Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				workbench.buildProgress.setValue(0);
				workbench.inputField.requestFocusInWindow();
				workbench.clearTranscript();

				// Clear the build input stream.
				workbench.inputStream().clear();

				// Build the target module in a Swing worker thread.
				final BuildTask task = new BuildTask(workbench, moduleName);
				workbench.backgroundTask = task;
				workbench.setEnablements();
				task.execute();
			}
		}
	}

	/**
	 * Construct a new {@link InsertEntryPointAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public InsertEntryPointAction (final AvailWorkbench workbench)
	{
		super(workbench, "Insert Entry Point");
		putValue(
			SHORT_DESCRIPTION,
			"Insert this entry point's name in the input area.");
	}
}
