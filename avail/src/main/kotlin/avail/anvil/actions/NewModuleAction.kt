/*
 * NewModuleAction.kt
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

import avail.builder.ModuleRoot
import avail.builder.ResolvedModuleName
import avail.anvil.AvailWorkbench
import avail.anvil.dialogs.NewModuleDialog
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.Action
import javax.swing.KeyStroke

/**
 * Create a new module and open an editor on it.
 *
 * @constructor
 * Construct a new [NewModuleAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class NewModuleAction
constructor (
	workbench: AvailWorkbench,
) : AbstractWorkbenchAction(
	workbench,
	"New module",
	KeyStroke.getKeyStroke(KeyEvent.VK_N, AvailWorkbench.menuShortcutMask))
{
	override fun actionPerformed(event: ActionEvent)
	{
		workbench.selectedModule()?.let {
			relativeToModule(it)
		} ?: run {
			workbench.selectedModuleRoot()?.let {
				relativeToModuleRoot(it)
			}
		}
	}

	/**
	 * The new module to create is relative to a
	 * [AvailWorkbench.selectedModule].
	 *
	 * @param relativeModule
	 *   The [AvailWorkbench.selectedModule] to create the new module relative
	 *   to. If the [ResolvedModuleName] is a
	 *   [module package][ResolvedModuleName.isPackage] then the new module will
	 *   be created inside of it. If the [ResolvedModuleName] is a module file,
	 *   the new module will be created as its sibling.
	 */
	private fun relativeToModule (relativeModule: ResolvedModuleName)
	{
		val path = relativeModule.resolverReference.uri.path
		val targetDir = path.removeSuffix(path.split(File.separator).last())
		val parentQualifiedName =
			if (relativeModule.isPackage)
			{
				relativeModule.resolverReference.qualifiedName
			}
			else
			{
				relativeModule.resolverReference.parentName
			}
		val projectRoot =
			workbench.availProject.roots.values.find {
				it.name == relativeModule.rootName
			}!!
		NewModuleDialog(
			targetDir,
			parentQualifiedName,
			projectRoot,
			relativeModule.moduleRoot,
			workbench)
	}

	/**
	 * Create a new module at the top of the provided [ModuleRoot].
	 *
	 * @param relativeRoot
	 *   The [ModuleRoot] to create the module in.
	 */
	private fun relativeToModuleRoot (relativeRoot: ModuleRoot)
	{

		NewModuleDialog(
			"${relativeRoot.resolver.uri.path}${File.separator}",
			"${File.separator}${relativeRoot.name}",
			workbench.availProject.roots.values.find {
				it.name == relativeRoot.name
			}!!,
			relativeRoot,
			workbench)
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Create a new module.")
	}
}
