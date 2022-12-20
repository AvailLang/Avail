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
import avail.anvil.actions.OpenShortcutManagerAction

/**
 * A [KeyboardShortcut] that is used to launch an
 * [AbstractWorkbenchAction] while using the [AvailWorkbench].
 */
sealed class WorkbenchShortCut: KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.WORKBENCH
}

/**
 * [WorkbenchShortCut] for the [BuildAction].
 *
 * @author Richard Arriaga
 */
object WorkbenchBuildShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_ENTER
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "build"
	override val description: String = "Build the Selected Module"
}

/**
 * [WorkbenchShortCut] for the [RefreshAction].
 *
 * @author Richard Arriaga
 */
object WorkbenchRefreshShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_F5
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "refresh"
	override val description: String = "Update Module Tree from Filesystem"
}

/**
 * [WorkbenchShortCut]  for the [SearchOpenModuleDialogAction].
 *
 * @author Richard Arriaga
 */
object SearchOpenModuleDialogShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.menuShortcutKeyMaskEx, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_O
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "open-module-dialog"
	override val description: String = "Open Module Search Dialog"
}

/**
 * [WorkbenchShortCut]  for the [DebugAction].
 *
 * @author Richard Arriaga
 */
object DebugActionShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_D
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "open-debugger"
	override val description: String = "Launch Debugger"
}

/**
 * [WorkbenchShortCut]  for the [GenerateDocumentationAction].
 *
 * @author Richard Arriaga
 */
object GenerateDocumentationActionShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_G
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "generate-documentation"
	override val description: String = "Generate Documentation"
}

/**
 * [WorkbenchShortCut]  for the [OpenModuleAction].
 *
 * @author Richard Arriaga
 */
object OpenModuleShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_O
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "open-selected-module"
	override val description: String = "Open the Selected Module"
}

/**
 * [WorkbenchShortCut]  for the [NewModuleAction].
 *
 * @author Richard Arriaga
 */
object NewModuleActionShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_N
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "new-module"
	override val description: String = "Create New Module"
}

/**
 * [WorkbenchShortCut]  for the [CancelAction].
 *
 * @author Richard Arriaga
 */
object CancelBuildActionShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_ESCAPE
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "cancel-build"
	override val description: String = "Cancel the Build"
}

/**
 * [WorkbenchShortCut] for the [OpenShortcutManagerAction].
 *
 * @author Richard Arriaga
 */
object OpenShortcutManagerShortcut: WorkbenchShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_COMMA
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "shortcuts"
	override val description: String = "Open This Shortcuts Window"
}
