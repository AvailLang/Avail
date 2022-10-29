/*
 * RemoveRootAction.kt
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
import avail.utility.notNullAnd
import org.availlang.json.JSONWriter
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.Action
import javax.swing.JOptionPane
import javax.swing.JOptionPane.CANCEL_OPTION
import javax.swing.JOptionPane.NO_OPTION
import javax.swing.JOptionPane.WARNING_MESSAGE
import javax.swing.JOptionPane.YES_NO_CANCEL_OPTION
import javax.swing.JOptionPane.YES_OPTION
import kotlin.io.path.toPath

/**
 * Remove a root from the current project configuration.
 *
 * @constructor
 * Construct a new [RemoveRootAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class RemoveRootAction
constructor (
	workbench: AvailWorkbench,
) : AbstractWorkbenchAction(
	workbench,
	"Remove root")
{
	override fun actionPerformed(event: ActionEvent)
	{
		val root = workbench.selectedModuleRoot()!!
		var deleteDirectory = false
		if (root.resolver.canSave &&
			workbench.getProjectRoot(root.name).notNullAnd { editable })
		{
			val response = JOptionPane.showOptionDialog(
				workbench,
				"Also delete the entire directory?",
				"Confirm directory deletion",
				YES_NO_CANCEL_OPTION,
				WARNING_MESSAGE,
				null,
				null,
				NO_OPTION)
			when (response)
			{
				CANCEL_OPTION -> return
				YES_OPTION -> deleteDirectory = true
			}
		}
		val project = workbench.availProject
		val projectRoot = workbench.getProjectRoot(root.name)!!
		val removedRoot = project.removeRoot(projectRoot.id)
		if (removedRoot == null)
		{
			JOptionPane.showMessageDialog(
				workbench,
				"Internal error - could not remove root \"${root.name}\".",
				"Warning",
				WARNING_MESSAGE)
			return
		}
		// Update the runtime as well.
		workbench.runtime.moduleRoots().removeRoot(root.name)
		val projectFilePath = workbench.availProjectFilePath
		// We're allowed to edit the roots even if there's no backing project
		// file.  We have nowhere to write back the new configuration, but it's
		// still useful while the project is open.
		if (projectFilePath.isNotEmpty())
		{
			// Update the backing project file.
			val writer = JSONWriter.newPrettyPrinterWriter()
			workbench.availProject.writeTo(writer)
			File(projectFilePath).writeText(writer.contents())
		}
		if (deleteDirectory)
		{
			val success =
				root.resolver.uri.toPath().toFile().deleteRecursively()
			if (!success)
			{
				JOptionPane.showMessageDialog(
					workbench,
					"Unable to delete some of the files and directories.",
					"Warning",
					WARNING_MESSAGE)
			}
		}
		// Refresh it visually to eliminate the root from the workbench.
		workbench.refreshAction.runAction()
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Remove this module root from the project.")
	}
}
