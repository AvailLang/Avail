/*
 * AddLoadOnStartAction.kt
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
import avail.builder.ResolvedModuleName
import org.availlang.artifact.environment.project.LocalSettings
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * An [AbstractWorkbenchAction] that removes the [ResolvedModuleName] from the
 * [LocalSettings.loadModulesOnStartup].
 *
 * @constructor
 * Construct a new [RemoveLoadOnStartAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class RemoveLoadOnStartAction constructor (
	workbench: AvailWorkbench
) : AbstractWorkbenchAction(workbench, "Remove Load on Anvil Start")
{
	/**
	 * @return `true` if this [AddLoadOnStartAction] meets all the internal
	 *   requirements to be enabled; `false` otherwise.
	 */
	private fun mayEnable (): Boolean {
		val qualifiedName =
			workbench.selectedModuleQualifiedName() ?: return false
		val settings =
			workbench.selectedModuleLocalSettings() ?: return false

		return settings.loadModulesOnStartup.contains(qualifiedName)
	}
	override fun updateIsEnabled (busy: Boolean)
	{
		isEnabled = !busy && mayEnable()
	}

	override fun actionPerformed(event: ActionEvent)
	{
		val selected = workbench.selectedModule() ?: return
		val qualifiedName = selected.qualifiedName
		val projRoot = workbench.availProject.roots[selected.rootName] ?: return
		projRoot.localSettings.loadModulesOnStartup.remove(qualifiedName)
		projRoot.saveLocalSettingsToDisk()
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Remove this module from local settings load on startup.")
	}
}
