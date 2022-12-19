/*
 * AvailEditorShortcuts.kt
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

import avail.anvil.AvailEditor
import avail.anvil.editor.GoToDialog
import avail.anvil.views.StructureViewPanel

/**
 * A [KeyboardShortcut] that is used in the [AvailEditor].
 *
 * @author Richard Arriaga
 */
sealed class AvailEditorShortCut: KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.AVAIL_EDITOR
}

/**
 * The [AvailEditorShortCut] to open the [GoToDialog].
 *
 * @author Richard Arriaga
 */
object GoToDialogShortcut: AvailEditorShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_L
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "go-to-dialog"
}

/**
 * The [AvailEditorShortCut] to open the [StructureViewPanel].
 *
 * @author Richard Arriaga
 */
object OpenStructureViewShortcut: AvailEditorShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.menuShortcutKeyMaskEx, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_M
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "open-structure-view"
}

/**
 * The [AvailEditorShortCut] to rebuild the open editor's module and
 * refresh the screen style.
 *
 * @author Richard Arriaga
 */
object RefreshShortcut: AvailEditorShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_F5
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "refresh"
}
