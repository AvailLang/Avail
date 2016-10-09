/**
 * ParserIntegrityCheckAction.java
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

import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.compiler.AvailCompiler;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.FiberDescriptor;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchAction;
import com.avail.utility.evaluation.Continuation0;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;

import static com.avail.environment.AvailWorkbench.StreamStyle.INFO;

/**
 * A {@code ParserIntegrityCheckAction} checks critical data structures used by
 * the {@link AvailCompiler}.
 */
@SuppressWarnings("serial")
public final class ParserIntegrityCheckAction
extends AbstractWorkbenchAction
{
	/** The current runtime. */
	final AvailRuntime runtime;

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		workbench.clearTranscript();
		runtime.execute(new AvailTask(FiberDescriptor.commandPriority)
		{
			@Override
			public void value ()
			{
				runtime.integrityCheck();
			}
		});
	}

	/**
	 * Construct a new {@link ParserIntegrityCheckAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param runtime
	 *        The active {@link AvailRuntime}.
	 */
	public ParserIntegrityCheckAction (
		final AvailWorkbench workbench,
		final AvailRuntime runtime)
	{
		super(workbench, "Integrity check");
		this.runtime = runtime;
		putValue(
			SHORT_DESCRIPTION,
			"Perform an integrity check on key parser structures.");
	}
}
