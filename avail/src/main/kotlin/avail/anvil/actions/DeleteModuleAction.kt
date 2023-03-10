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
import avail.anvil.nodes.AbstractWorkbenchTreeNode
import avail.anvil.nodes.ModuleOrPackageNode
import avail.anvil.nodes.ModuleRootNode
import avail.anvil.nodes.ResourceDirNode
import avail.anvil.nodes.ResourceNode
import avail.anvil.shortcuts.DeleteFileShortcut
import avail.anvil.streams.StreamStyle
import avail.builder.ModuleRoot
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
	"Delete",
	DeleteFileShortcut)
{
	override fun updateIsEnabled(busy: Boolean)
	{
		isEnabled = !busy && workbench.selectedModuleTreeNode.notNullAnd {
			val root = when (this)
			{
				is ModuleOrPackageNode ->
					this.resolvedModuleName.moduleRoot
				is ResourceDirNode ->
					this.reference.resolver.moduleRoot
				is ResourceNode ->
					this.reference.resolver.moduleRoot
				else -> return@notNullAnd false
			}
			root.resolver.canSave &&
				workbench.getProjectRoot(root.name)
					.notNullAnd { editable }
		}
	}

	override fun actionPerformed(event: ActionEvent)
	{
		val seleceted = workbench.selectedModuleTreeNode ?: return
		val root: ModuleRoot
		val parent: AbstractWorkbenchTreeNode
		val reference =
			when (seleceted)
			{
				is ModuleOrPackageNode ->
				{
					parent = seleceted.parent as AbstractWorkbenchTreeNode
					root = seleceted.resolvedModuleName.moduleRoot
					seleceted.resolvedModuleName.resolverReference
				}
				is ResourceDirNode ->
				{
					parent = seleceted.parent as AbstractWorkbenchTreeNode
					root = seleceted.reference.resolver.moduleRoot
					seleceted.reference
				}
				is ResourceNode ->
				{
					parent = seleceted.parent as AbstractWorkbenchTreeNode
					root = seleceted.reference.resolver.moduleRoot
					seleceted.reference
				}
				else -> return
			}
		val message: String
		val title: String
		val pathToDelete: Path
		if (!root.resolver.canSave ||
			workbench.getProjectRoot(root.name).isNullOr {!editable })
		{
			JOptionPane.showMessageDialog(
				workbench,
				"Content within this root (${root.name}) cannot "
					+ "be modified.",
				"Cannot modify",
				ERROR_MESSAGE)
			return
		}
		when
		{
			reference.isPackageRepresentative ->
			{
				message = "Delete this package " +
					"(${reference.parentName}) " +
					"and all modules within it?"
				title = "Confirm package deletion"
				pathToDelete = reference.uri.toPath().parent
			}
			reference.isResource && reference.hasChildren ->
			{
				message = "Delete this resource directory " +
					"(${reference.qualifiedName}) " +
					"and all content within it?"
				title = "Confirm directory deletion"
				pathToDelete = reference.uri.toPath()
			}
			else ->
			{
				message = "Delete ${reference.qualifiedName}?"
				title = "Confirm file deletion"
				pathToDelete = reference.uri.toPath()
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
		val ref = when (parent)
		{
			is ModuleRootNode ->
				parent.moduleRoot.resolver.moduleRootTree ?: return
			is ModuleOrPackageNode ->
				parent.resolvedModuleName.resolverReference
			is ResourceDirNode ->
				parent.reference
			else -> return
		}
		if (seleceted is ModuleOrPackageNode)
		{
			if (reference.isPackageRepresentative)
			{
				val pr = ref.modules.firstOrNull {
					it.qualifiedName == reference.parentName
				} ?: return
				ref.modules.remove(pr)
			}
			else
			{
				ref.modules.remove(reference)
			}
		}
		else
		{
			ref.resources.remove(reference)
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Delete a module or resource.")
	}
}
