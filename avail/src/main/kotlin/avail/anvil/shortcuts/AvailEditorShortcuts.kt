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
import avail.anvil.shortcuts.ModifierKey.Companion.menuShortcutKeyMaskEx
import avail.anvil.shortcuts.ModifierKey.SHIFT
import avail.anvil.text.BlockComment
import avail.anvil.text.LineComment
import avail.anvil.views.PhraseViewPanel
import avail.anvil.views.StructureViewPanel

/**
 * A [KeyboardShortcut] that is used in the [AvailEditor].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [AvailEditorShortcut].
 *
 * @param defaultKey
 *   The default [Key] when pressed triggers this shortcut.
 * @param key
 *   The [Key] used for this shortcut. Defaults to `defaultKey`.
 */
sealed class AvailEditorShortcut constructor(
	override val defaultKey: Key,
	override var key: Key = defaultKey
): KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.AVAIL_EDITOR
}

/**
 * The [AvailEditorShortcut] to open the [GoToDialog].
 *
 * @author Richard Arriaga
 */
object GoToDialogShortcut
	: AvailEditorShortcut(KeyCode.VK_L.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "go-to-dialog"
	override val description: String = "Open Go-To Line Dialog"
}

/**
 * The [AvailEditorShortcut] to open the [StructureViewPanel].
 *
 * @author Richard Arriaga
 */
object OpenStructureViewShortcut
	: AvailEditorShortcut(KeyCode.VK_M.with(menuShortcutKeyMaskEx, SHIFT))
{
	override val actionMapKey: String = "open-structure-view"
	override val description: String = "Open Structure View"
}

/**
 * The [AvailEditorShortcut] to open the [PhraseViewPanel].
 *
 * @author Mark van Gulik
 */
object OpenPhraseViewShortcut
	: AvailEditorShortcut(KeyCode.VK_P.with(menuShortcutKeyMaskEx, SHIFT))
{
	override val actionMapKey: String = "open-phrase-view"
	override val description: String = "Open Phrase View"
}

/**
 * The [AvailEditorShortcut] to rebuild the open editor's module and
 * refresh the screen style.
 *
 * @author Richard Arriaga
 */
object RefreshShortcut: AvailEditorShortcut(KeyCode.VK_F5.with())
{
	override val actionMapKey: String = "refresh"
	override val description: String = "Rebuild and Refresh Module"
}

/**
 * The [AvailEditorShortcut] to prefix each selected line with a [LineComment]
 * at the start of each line ([LineComment.commentAtLineStart]).
 *
 * @author Richard Arriaga
 */
object InsertLineCommentAtStartShortcut
	: AvailEditorShortcut(KeyCode.VK_SLASH.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "insert-line-comment-at-start"
	override val description: String = "Rebuild and Refresh Module"
}

/**
 * The [AvailEditorShortcut] to insert a [LineComment] after the tab position of
 * line with the least tabs before a non-tab character
 * ([LineComment.commentAtMinTab]).
 *
 * @author Richard Arriaga
 */
object InsertLineCommentAtTabShortcut
	: AvailEditorShortcut(KeyCode.VK_SLASH.with(menuShortcutKeyMaskEx, SHIFT))
{
	override val actionMapKey: String = "insert-line-comment-at-start"
	override val description: String = "Rebuild and Refresh Module"
}

/**
 * The [AvailEditorShortcut] to wrap the text selection in a [BlockComment].
 *
 * @author Richard Arriaga
 */
object WrapInBlockCommentShortcut
	: AvailEditorShortcut(KeyCode.VK_SLASH.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "insert-line-comment-at-start"
	override val description: String = "Rebuild and Refresh Module"
}
