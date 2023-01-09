/*
 * WorkbenchShortcuts.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.anvil.shortcuts

import avail.anvil.AvailWorkbench
import avail.anvil.actions.AbstractWorkbenchAction
import avail.anvil.actions.BuildAction
import avail.anvil.actions.CancelAction
import avail.anvil.actions.DebugAction
import avail.anvil.actions.GenerateDocumentationAction
import avail.anvil.actions.NewModuleAction
import avail.anvil.actions.OpenModuleAction
import avail.anvil.actions.RefreshAction
import avail.anvil.actions.SearchOpenModuleDialogAction
import avail.anvil.actions.OpenSettingsViewAction
import avail.anvil.actions.RefreshStylesheetAction
import avail.anvil.shortcuts.ModifierKey.*
import avail.anvil.shortcuts.ModifierKey.Companion.menuShortcutKeyMaskEx

/**
 * A [KeyboardShortcut] that is used to launch an
 * [AbstractWorkbenchAction] while using the [AvailWorkbench].
 *
 * @constructor
 * Construct a [WorkbenchShortcut].
 *
 * @param defaultKey
 *   The default [Key] when pressed triggers this shortcut.
 * @param key
 *   The [Key] used for this shortcut. Defaults to `defaultKey`.
 */
sealed class WorkbenchShortcut constructor(
	override val defaultKey: Key,
	override var key: Key = defaultKey
): KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.WORKBENCH
}

/**
 * [WorkbenchShortcut] for the [BuildAction].
 *
 * @author Richard Arriaga
 */
object WorkbenchBuildShortcut
	: WorkbenchShortcut(KeyCode.VK_ENTER.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "build"
	override val description: String = "Build the Selected Module"
}

/**
 * [WorkbenchShortcut] for the [RefreshAction].
 *
 * @author Richard Arriaga
 */
object WorkbenchRefreshShortcut: WorkbenchShortcut(KeyCode.VK_F5.with())
{
	override val actionMapKey: String = "refresh"
	override val description: String = "Update Module Tree from Filesystem"
}

/**
 * [WorkbenchShortcut] for the [RefreshStylesheetAction].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object RefreshStylesheetShortcut
	: WorkbenchShortcut(KeyCode.VK_F5.with(SHIFT))
{
	override val actionMapKey = "refresh-stylesheet"
	override val description = "Refresh Stylesheet"
}

/**
 * [WorkbenchShortcut]  for the [SearchOpenModuleDialogAction].
 *
 * @author Richard Arriaga
 */
object SearchOpenModuleDialogShortcut
	: WorkbenchShortcut(KeyCode.VK_O.with(menuShortcutKeyMaskEx, SHIFT))
{
	override val actionMapKey: String = "open-module-dialog"
	override val description: String = "Open Module Search Dialog"
}

/**
 * [WorkbenchShortcut]  for the [DebugAction].
 *
 * @author Richard Arriaga
 */
object DebugActionShortcut
	: WorkbenchShortcut(KeyCode.VK_D.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "open-debugger"
	override val description: String = "Launch Debugger"
}

/**
 * [WorkbenchShortcut]  for the [GenerateDocumentationAction].
 *
 * @author Richard Arriaga
 */
object GenerateDocumentationActionShortcut
	: WorkbenchShortcut(KeyCode.VK_G.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "generate-documentation"
	override val description: String = "Generate Documentation"
}

/**
 * [WorkbenchShortcut]  for the [OpenModuleAction].
 *
 * @author Richard Arriaga
 */
object OpenModuleShortcut
	: WorkbenchShortcut(KeyCode.VK_O.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "open-selected-module"
	override val description: String = "Open the Selected Module"
}

/**
 * [WorkbenchShortcut]  for the [NewModuleAction].
 *
 * @author Richard Arriaga
 */
object NewModuleActionShortcut
	: WorkbenchShortcut(KeyCode.VK_N.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "new-module"
	override val description: String = "Create New Module"
}

/**
 * [WorkbenchShortcut]  for the [CancelAction].
 *
 * @author Richard Arriaga
 */
object CancelBuildActionShortcut
	: WorkbenchShortcut(KeyCode.VK_ESCAPE.with(CTRL))
{
	override val actionMapKey: String = "cancel-build"
	override val description: String = "Cancel the Build"
}

/**
 * [WorkbenchShortcut] for the [OpenSettingsViewAction].
 *
 * @author Richard Arriaga
 */
object OpenSettingsViewShortcut
	: WorkbenchShortcut(KeyCode.VK_COMMA.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "settings"
	override val description: String = "Open Settings Window"
}
