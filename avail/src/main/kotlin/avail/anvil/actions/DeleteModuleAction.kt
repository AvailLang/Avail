/*
 * DeleteModuleAction.kt
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
import avail.anvil.streams.StreamStyle
import avail.utility.isNullOr
import avail.utility.notNullAnd
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JOptionPane
import javax.swing.JOptionPane.ERROR_MESSAGE
import javax.swing.JOptionPane.OK_CANCEL_OPTION
import javax.swing.JOptionPane.OK_OPTION
import javax.swing.JOptionPane.WARNING_MESSAGE
import kotlin.io.path.toPath

/**
 * Delete a module file or package directory.
 *
 * @constructor
 * Construct a new [DeleteModuleAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class DeleteModuleAction
constructor (
	workbench: AvailWorkbench,
) : AbstractWorkbenchAction(
	workbench,
	"Delete module")
{
	override fun updateIsEnabled(busy: Boolean)
	{
		isEnabled = !busy && workbench.selectedModule().notNullAnd {
			moduleRoot.resolver.canSave &&
				workbench.getProjectRoot(moduleRoot.name)
					.notNullAnd { editable }
		}
	}

	override fun actionPerformed(event: ActionEvent)
	{
		val module = workbench.selectedModule()!!
		val resolverReference = module.resolverReference
		val root = module.moduleRoot
		val message: String
		val title: String
		val pathToDelete: Path
		if (!root.resolver.canSave ||
			workbench.getProjectRoot(root.name).isNullOr {!editable })
		{
			JOptionPane.showMessageDialog(
				workbench,
				"Modules within this root (${root.name}) cannot "
					+ "be modified.",
				"Cannot modify",
				ERROR_MESSAGE)
			return
		}
		when
		{
			resolverReference.isPackageRepresentative ->
			{
				message = "Delete this package " +
					"(${resolverReference.parentName}) " +
					"and all modules within it?"
				title = "Confirm package deletion"
				pathToDelete = resolverReference.uri.toPath().parent
			}
			else ->
			{
				message = "Delete this module (${module.qualifiedName})?"
				title = "Confirm module deletion"
				pathToDelete = resolverReference.uri.toPath()
			}
		}
		val ret = JOptionPane.showOptionDialog(
			workbench,
			message,
			title,
			OK_CANCEL_OPTION,
			WARNING_MESSAGE,
			null,
			null,
			OK_OPTION)
		if (ret != OK_OPTION) return
		workbench.writeText("Deleting: $pathToDelete...\n", StreamStyle.INFO)
		pathToDelete.toFile().deleteRecursively()
		workbench.writeText("Done.\n", StreamStyle.INFO)
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Create a new module.")
	}
}
