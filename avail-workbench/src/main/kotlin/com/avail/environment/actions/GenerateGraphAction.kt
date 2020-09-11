/*
 * GenerateGraphAction.java
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

package com.avail.environment.actions

import com.avail.builder.AvailBuilder
import com.avail.environment.AvailWorkbench
import com.avail.environment.tasks.GraphTask
import java.awt.Cursor.WAIT_CURSOR
import java.awt.Cursor.getPredefinedCursor
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * A `GenerateGraphAction` instructs the [Avail&#32;builder][AvailBuilder] to
 * generate a graphical representation of the selected module's dependencies.
 *
 * @constructor
 * Construct a new [GenerateGraphAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class GenerateGraphAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Generate graph")
{
	override fun actionPerformed(event: ActionEvent?)
	{
		assert(workbench.backgroundTask === null)
		val selectedModule = workbench.selectedModule()!!

		// Update the UI.
		workbench.cursor = getPredefinedCursor(WAIT_CURSOR)
		workbench.setEnablements()
		workbench.buildProgress.value = 0
		workbench.clearTranscript()

		// Generate dependency graph for the target module in a Swing worker
		// thread.
		val task = GraphTask(workbench, selectedModule)
		workbench.backgroundTask = task
		workbench.setEnablements()
		task.execute()
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Generate dependency graph for the selected module and " + "its ancestors.")
	}
}
