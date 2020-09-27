/*
 * GraphTask.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.environment.tasks

import com.avail.builder.ResolvedModuleName
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.environment.AvailWorkbench
import com.avail.environment.AvailWorkbench.AbstractWorkbenchTask
import com.avail.environment.streams.StreamStyle.INFO
import java.awt.Cursor
import java.io.File

/**
 * A `GraphTask` generates a .dot file describing a visual graph of
 * module dependencies for the target [module][ModuleDescriptor].
 *
 * @constructor
 * Construct a new `GraphTask`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param targetModuleName
 *   The resolved name of the target [module][ModuleDescriptor].
 */
class GraphTask constructor(
		workbench: AvailWorkbench, targetModuleName: ResolvedModuleName?)
	: AbstractWorkbenchTask(workbench, targetModuleName)
{
	@Throws(Exception::class)
	override fun executeTask()
	{

		try
		{
			val file = File("modules.dot")
			workbench.availBuilder.generateGraph(
				targetModuleName(),
				file,
				workbench.availBuilder.buildProblemHandler)
			workbench.writeText(
				String.format(
					"Module graph '%s' was created.\n",
					file.absolutePath),
				INFO)
		}
		catch (e: Exception)
		{
			// Put a breakpoint here to debug graph exceptions.
			throw e
		}

	}

	override fun done()
	{
		workbench.backgroundTask = null
		reportDone()
		workbench.availBuilder.checkStableInvariants()
		workbench.setEnablements()
		workbench.cursor = Cursor.getDefaultCursor()
	}
}
