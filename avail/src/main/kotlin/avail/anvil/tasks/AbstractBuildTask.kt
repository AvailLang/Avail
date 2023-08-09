/*
 * BuildTask.kt
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

package avail.anvil.tasks

import avail.anvil.AvailWorkbench
import avail.anvil.text.centerCurrentLine
import avail.anvil.text.setCaretFrom
import avail.builder.ModuleRoot
import avail.builder.ModuleRoots
import avail.descriptor.module.ModuleDescriptor
import avail.persistence.cache.Repository
import java.awt.Cursor
import javax.swing.SwingUtilities

/**
 * An [AbstractWorkbenchModuleTask] that launches the actual build of
 * [module(s)][ModuleDescriptor].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a new [AbstractWorkbenchModuleTask].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
abstract class AbstractBuildTask constructor (
	workbench: AvailWorkbench
) : AbstractWorkbenchTask(workbench)
{
	/**
	 * [Reopen][Repository.reopenIfNecessary] the [ModuleRoot.repository] for
	 * each [ModuleRoot] in the [ModuleRoots].
	 */
	protected fun reopenRootsRepositories ()
	{
		workbench.resolver.moduleRoots.roots.forEach { root ->
			root.repository.reopenIfNecessary()
		}
	}

	/**
	 * The [SwingUtilities.invokeLater] action to perform after this
	 * [AbstractBuildTask] is complete.
	 *
	 * @param afterExecute
	 *   The lambda to run after the [task][executeTaskThen] completes.
	 */
	protected fun swingAction(afterExecute: ()->Unit)
	{
		SwingUtilities.invokeLater {
			workbench.backgroundTask = null
			reportDone()
			workbench.availBuilder.checkStableInvariants()
			workbench.setEnablements()
			workbench.cursor = Cursor.getDefaultCursor()
			workbench.refresh()
			workbench.openEditors.values.forEach { editor ->
				val r = editor.range
				editor.populateSourcePane {
					it.sourcePane.setCaretFrom(r)
					it.sourcePane.centerCurrentLine()
					if (workbench.structureViewPanel.editor == editor)
					{
						editor.openStructureView(false)
					}
					if (workbench.phraseViewPanel.editor == editor)
					{
						editor.updatePhraseStructure()
					}
				}
			}
			afterExecute()
		}
	}
}
