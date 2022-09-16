/*
 * CleanModuleAction.kt
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

import avail.anvil.AvailWorkbench
import avail.anvil.streams.StreamStyle.INFO
import org.availlang.persistence.IndexedFileException
import java.awt.event.ActionEvent
import java.lang.String.format
import javax.swing.Action

/**
 * A `CleanModuleAction` removes from the repository file all compiled versions
 * of the selected module.  If a package or root is selected, this is done for
 * all modules within it.
 *
 * @constructor
 * Construct a new `CleanModuleAction`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class CleanModuleAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Clean Module")
{
	override fun actionPerformed(event: ActionEvent)
	{
		assert(workbench.backgroundTask === null)

		val root = workbench.selectedModuleRoot()
		if (root !== null)
		{
			// Delete an entire repository.
			try
			{
				root.clearRepository()
			}
			catch (e: IndexedFileException)
			{
				// Ignore problem for now.
			}

			workbench.writeText(
				format("Repository %s has been cleaned.%n", root.name),
				INFO)
			return
		}

		// Delete a module or package (and everything inside it).
		val selectedModule =
			workbench.selectedModule()!!
		val rootRelative = selectedModule.rootRelativeName
		val repository = selectedModule.repository
		repository.cleanModulesUnder(rootRelative)
		repository.commit()
		workbench.writeText(
			format(
				"Module or package %s has been cleaned.%n",
				selectedModule.qualifiedName),
			INFO)
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Invalidate cached compilations for a module/package.")
	}
}
