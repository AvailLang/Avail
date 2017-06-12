/**
 * UnloadAction.java
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
import com.avail.builder.*;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.tasks.UnloadTask;
import org.jetbrains.annotations.Nullable;

/**
 * An {@code UnloadAction} launches an {@linkplain UnloadTask unload task}
 * in a Swing worker thread.
 */
@SuppressWarnings("serial")
public final class UnloadAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;
		final ResolvedModuleName selectedModule = workbench.selectedModule();
		assert selectedModule != null;

		// Update the UI.
		workbench.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		workbench.buildProgress.setValue(0);
		workbench.clearTranscript();

		// Clear the build input stream.
		workbench.inputStream().clear();

		// Unload the target module in a Swing worker thread.
		final UnloadTask task = new UnloadTask(workbench, selectedModule);
		workbench.backgroundTask = task;
		workbench.setEnablements();
		task.execute();
	}

	/**
	 * Construct a new {@link UnloadAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public UnloadAction (final AvailWorkbench workbench)
	{
		super(workbench, "Unload");
		putValue(
			SHORT_DESCRIPTION,
			"Unload the target module.");
	}
}
