/**
 * CleanAction.java
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

import java.awt.event.*;
import java.io.IOException;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import com.avail.annotations.*;
import com.avail.builder.ModuleRoot;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchAction;
import com.avail.persistence.IndexedFileException;

/**
 * A {@code CleanAction} empties all compiled module repositories.
 */
@SuppressWarnings("serial")
public final class CleanAction
extends AbstractWorkbenchAction
{
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		workbench.availBuilder.unloadTarget(null);
		assert workbench.backgroundTask == null;
		try
		{
			// Clear all repositories.
			for (final ModuleRoot root :
				workbench.resolver.moduleRoots().roots())
			{
				root.repository().clear();
			}
		}
		catch (final IOException e)
		{
			throw new IndexedFileException(e);
		}
		final StyledDocument doc = workbench.transcript.getStyledDocument();
		try
		{
			doc.insertString(
				doc.getLength(),
				String.format("Repository has been cleared.%n"),
				doc.getStyle(AvailWorkbench.infoStyleName));
		}
		catch (final BadLocationException e)
		{
			assert false : "This never happens.";
		}
	}

	/**
	 * Construct a new {@link CleanAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public CleanAction (final AvailWorkbench workbench)
	{
		super(workbench, "Clean All");
		putValue(
			SHORT_DESCRIPTION,
			"Unload all code and wipe the compiled module cache.");
	}
}
