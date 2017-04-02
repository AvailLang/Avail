/**
 * BuildTask.java
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

package com.avail.environment.tasks;

import java.awt.*;
import com.avail.builder.*;
import com.avail.compiler.AvailCompiler.*;
import com.avail.descriptor.*;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchTask;
import com.avail.utility.evaluation.*;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code BuildTask} launches the actual build of the target {@linkplain
 * ModuleDescriptor module}.
 */
public final class BuildTask
extends AbstractWorkbenchTask
{
	/**
	 * Construct a new {@link BuildTask}.
	 *
	 * @param workbench The owning {@link AvailWorkbench}.
	 * @param targetModuleName
	 *        The resolved name of the target {@linkplain ModuleDescriptor
	 *        module}.
	 */
	public BuildTask (
		final AvailWorkbench workbench,
		final ResolvedModuleName targetModuleName)
	{
		super(workbench, targetModuleName);
	}

	/**
	 * Answer a suitable {@linkplain CompilerProgressReporter compiler
	 * progress reporter}.
	 *
	 * @return A compiler progress reporter.
	 */
	private CompilerProgressReporter compilerProgressReporter ()
	{
		return new CompilerProgressReporter()
		{
			@Override
			public void value (
				final @Nullable ModuleName moduleName,
				final @Nullable Long moduleSize,
				final @Nullable Long position)
			{
				assert moduleSize != null;
				assert position != null;
				workbench.eventuallyUpdatePerModuleProgress(
					moduleName, moduleSize, position);
			}
		};
	}

	/**
	 * Answer a suitable {@linkplain Continuation3 global tracker}.
	 *
	 * @return A global tracker.
	 */
	private Continuation2<Long, Long> globalTracker ()
	{
		return new Continuation2<Long, Long>()
		{
			@Override
			public void value (
				final @Nullable Long position,
				final @Nullable Long globalCodeSize)
			{
				assert position != null;
				assert globalCodeSize != null;
				workbench.eventuallyUpdateBuildProgress(
					position, globalCodeSize);
			}
		};
	}

	@Override
	protected void executeTask () throws Exception
	{
		assert targetModuleName != null;
		workbench.availBuilder.buildTarget(
			targetModuleName(),
			compilerProgressReporter(),
			globalTracker());
	}

	@Override
	protected void done ()
	{
		workbench.backgroundTask = null;
		reportDone();
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();
		workbench.setCursor(Cursor.getDefaultCursor());
	}
}
