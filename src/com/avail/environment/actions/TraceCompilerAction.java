/*
 * TraceCompilerAction.java
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

import com.avail.AvailRuntimeConfiguration;
import com.avail.environment.AvailWorkbench;

import javax.annotation.Nullable;
import java.awt.event.ActionEvent;

/**
 * A {@code TraceCompilerAction} toggles the flag that indicates whether to show
 * detailed compiler traces.
 */
@SuppressWarnings("serial")
public final class TraceCompilerAction
extends AbstractWorkbenchAction
{
	@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		AvailRuntimeConfiguration.debugCompilerSteps ^= true;
	}

	/**
	 * Construct a new {@code TraceCompilerAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 */
	public TraceCompilerAction (final AvailWorkbench workbench)
	{
		super(workbench, "Trace compiler");
		putValue(
			SHORT_DESCRIPTION,
			"Show detailed compiler steps during compilation.");
		putValue(
			SELECTED_KEY, AvailRuntimeConfiguration.debugCompilerSteps);
	}
}
