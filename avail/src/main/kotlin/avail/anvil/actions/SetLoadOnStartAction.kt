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
 * An [AbstractWorkbenchAction] that either adds to or removes from
 * [LocalSettings.loadModulesOnStartup] the selected [ResolvedModuleName].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a new [SetLoadOnStartAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class SetLoadOnStartAction constructor (
	workbench: AvailWorkbench
) : AbstractWorkbenchAction(workbench, ChangeAction.ADD.menuLabel)
{
	/**
	 * The different possible  [LocalSettings.loadModulesOnStartup] change
	 * actions.
	 *
	 * @property menuLabel
	 *   The context menu label that displays the currently available
	 *   [ChangeAction].
	 */
	private enum class ChangeAction (val menuLabel: String)
	{
		/**
		 * Add the selected [ResolvedModuleName] to the
		 * [LocalSettings.loadModulesOnStartup].
		 */
		ADD("Add Load on Anvil Start")
		{
			override fun performAction(
				localSettings: LocalSettings,
				qualifiedName: String)
			{
				localSettings.loadModulesOnStartup.add(qualifiedName)
			}
		},

		/**
		 * Remove the selected [ResolvedModuleName] to the
		 * [LocalSettings.loadModulesOnStartup].
		 */
		REMOVE("Remove Load on Anvil Start")
		{
			override fun performAction(
				localSettings: LocalSettings,
				qualifiedName: String)
			{
				localSettings.loadModulesOnStartup.remove(qualifiedName)
			}
		};

		/**
		 * Perform this action on the [LocalSettings.loadModulesOnStartup].
		 *
		 * @param localSettings
		 *   The [LocalSettings] to update.
		 * @param qualifiedName
		 *   The qualified name of the target module used in the update of the
		 *   [LocalSettings].
		 */
		abstract fun performAction(
			localSettings: LocalSettings,
			qualifiedName: String)

		/**
		 * Update the context menu label of the provided [SetLoadOnStartAction].
		 *
		 * @param setLoadOnStartAction
		 *   The [SetLoadOnStartAction] to update.
		 */
		fun updateName (setLoadOnStartAction: SetLoadOnStartAction)
		{
			setLoadOnStartAction.putValue(NAME, menuLabel)
		}
	}

	/**
	 * The currently available [ChangeAction] that can be performed on the
	 * selected module.
	 */
	private var nextChangeAction: ChangeAction = ChangeAction.ADD

	override fun actionPerformed(event: ActionEvent)
	{
		val selected = workbench.selectedModule() ?: return
		val projRoot = workbench.availProject.roots[selected.rootName] ?: return
		val qualifiedName = selected.qualifiedName
		nextChangeAction.performAction(projRoot.localSettings, qualifiedName)
		projRoot.saveLocalSettingsToDisk()
		workbench.setEnablements()
	}

	override fun updateIsEnabled (busy: Boolean)
	{
		isEnabled = !busy
		val selected = workbench.selectedModule() ?: return
		val settings =
			workbench.availProject.roots[selected.rootName]?.localSettings
				?: return
		val qualifiedName = selected.qualifiedName
		nextChangeAction =
			if (settings.loadModulesOnStartup.contains(qualifiedName))
			{
				ChangeAction.REMOVE
			}
			else
			{
				ChangeAction.ADD
			}.apply { updateName(this@SetLoadOnStartAction) }

	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Add/Remove this module from local settings load on startup.")
	}
}
