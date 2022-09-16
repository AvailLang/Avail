/*
 * ShowCCReportAction.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.anvil.actions

import avail.AvailRuntime
import avail.builder.AvailBuilder
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.codeCoverageReportsThen
import avail.anvil.AvailWorkbench
import avail.anvil.streams.StreamStyle.INFO
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * A `DisplayCodeCoverageReportAction` instructs the
 * [Avail&#32;builder][AvailBuilder] to display the code coverage report.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @property runtime
 *   The current [AvailRuntime].
 * @constructor
 * Construct a new [ShowCCReportAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param runtime
 *   The current [AvailRuntime].
 */
class ShowCCReportAction constructor(
	workbench: AvailWorkbench, private val runtime: AvailRuntime)
	: AbstractWorkbenchAction(workbench, "Generate Code Coverage Report")
{
	override fun actionPerformed(event: ActionEvent)
	{
		runtime.execute(FiberDescriptor.commandPriority)
		{
			codeCoverageReportsThen { reports ->
				// Announce the beginning of the report dump.
				val builder = StringBuilder()
					.append("Code Coverage Report - all functions\n\n")
					.append("KEY\n")
					.append("r: Function has run\n")
					.append("t: Function has been translated\n")
					.append("m: Module name\n")
					.append("l: Starting line number\n")
					.append("f: Function name and sub-function ordinals\n\n")

				// Iterate over each report. Order the report items using the
				// natural sort defined in the object.
				for (r in reports.sorted())
				{
					builder.append(r)
					builder.append('\n')
				}
				workbench.writeText(builder.toString(), INFO)
			}
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Generate and display the code coverage report.")
	}
}
