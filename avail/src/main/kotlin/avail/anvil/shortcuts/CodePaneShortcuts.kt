/*
 * CodePaneShortcuts.kt
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

import avail.anvil.shortcuts.ModifierKey.*
import avail.anvil.shortcuts.ModifierKey.Companion.menuShortcutKeyMaskEx
import avail.anvil.text.CodePane
import javax.swing.JViewport
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent

/**
 * A [KeyboardShortcut] that is used in a generic [CodePane].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [CodePaneShortcut].
 *
 * @param defaultKey
 *   The default [Key] when pressed triggers this shortcut.
 * @param key
 *   The [Key] used for this shortcut. Defaults to `defaultKey`.
 */
sealed class CodePaneShortcut constructor(
	override val defaultKey: Key,
	override var key: Key = defaultKey
): KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.CODE_PANE
}

/**
 * [CodePaneShortcut] to replace the current selection with a space
 * (U+0020).
 *
 * @author Richard Arriaga
 */
object InsertSpaceShortcut: CodePaneShortcut(KeyCode.VK_SPACE.with())
{
	override val actionMapKey: String = "insert-space"
	override val description: String = "Insert Space"
}

/**
 * The [CodePaneShortcut] for break the current line by replacing the
 * current selection with a linefeed (U+000A) and as much horizontal tabulation
 * (U+0009) as began the line.
 *
 * @author Richard Arriaga
 */
object BreakLineShortcut: CodePaneShortcut(KeyCode.VK_ENTER.with())
{
	override val actionMapKey: String = DefaultEditorKit.insertBreakAction
	override val description: String = "Insert Line Break with Indentation"
}

/**
 * The [CodePaneShortcut] for outdenting selected lines enclosing the
 * selection. If no text is selected, then remove at most one horizontal
 * tabulation (U+0009) at the beginning of the line containing the caret.
 *
 * @author Richard Arriaga
 */
object OutdentShortcut: CodePaneShortcut(KeyCode.VK_TAB.with(SHIFT))
{
	override val actionMapKey: String = "outdent"
	override val description: String = "Outdent Text"
}

/**
 * The [CodePaneShortcut] for centering the current line of the
 * [source&#32;component][JTextComponent] in its enclosing
 * [viewport][JViewport]. If no viewport encloses the receiver, then do not move
 * the caret.
 *
 * @author Richard Arriaga
 */
object CenterCurrentLineShortcut
	: CodePaneShortcut(KeyCode.VK_M.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "center-line"
	override val description: String = "Center Line in View"
}

/**
 * The [CodePaneShortcut] that calls the undo action.
 *
 * @author Richard Arriaga
 */
object UndoShortcut: CodePaneShortcut(KeyCode.VK_Z.with(menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "undo"
	override val description: String = "Undo Last Action"
}

/**
 * The [CodePaneShortcut] that calls the redo action.
 *
 * @author Richard Arriaga
 */
object RedoShortcut
	: CodePaneShortcut(KeyCode.VK_Z.with(menuShortcutKeyMaskEx, SHIFT))
{
	override val actionMapKey: String = "redo"
	override val description: String = "Redo Last Action"
}

/**
 * The [CodePaneShortcut] that expands a template selection.
 *
 * @author Richard Arriaga
 */
object ExpandTemplateShortcut: CodePaneShortcut(KeyCode.VK_SPACE.with(CTRL))
{
	override val actionMapKey: String = "expand-template"
	override val description: String = "Expand the Selected Template"
}

/**
 * The [CodePaneShortcut] that cancels the template selection.
 *
 * @author Richard Arriaga
 */
object CancelTemplateSelectionShortcut
	: CodePaneShortcut(KeyCode.VK_ESCAPE.with(CTRL))
{
	override val actionMapKey: String = "cancel-template-selection"
	override val description: String = "Cancel Template Selection Expansion"
}

/**
 * The [CodePaneShortcut] that moves the selected lines up.
 *
 * @author Richard Arriaga
 */
object MoveLineUpShortcut: CodePaneShortcut(KeyCode.VK_UP.with(ALT, SHIFT))
{
	override val actionMapKey: String = "move-line-up"
	override val description: String = "Move the Selected Text Up One Line"
}

/**
 * The [CodePaneShortcut] that moves the selected lines down.
 *
 * @author Richard Arriaga
 */
object MoveLineDownShortcut: CodePaneShortcut( KeyCode.VK_DOWN.with())
{
	override val actionMapKey: String = "move-line-down"
	override val description: String = "Move the Selected Text Down One Line"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to uppercase.
 *
 * @author Richard Arriaga
 */
object UppercaseShortcut: CodePaneShortcut(KeyCode.VK_U.with(META, SHIFT))
{
	override val actionMapKey: String = "uppercase"
	override val description: String = "Uppercase Selected Text"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to lowercase.
 *
 * @author Richard Arriaga
 */
object LowercaseShortcut: CodePaneShortcut(KeyCode.VK_L.with(META, SHIFT))
{
	override val actionMapKey: String = "lowercase"
	override val description: String = "Lowercase Selected Text"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to camel case.
 *
 * @author Richard Arriaga
 */
object CamelCaseShortcut: CodePaneShortcut(KeyCode.VK_C.with(META, CTRL))
{
	override val actionMapKey: String = "camel-case"
	override val description: String = "Transform Selected Text to camelCase"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to pascal case.
 *
 * @author Richard Arriaga
 */
object PascalCaseShortcut: CodePaneShortcut(KeyCode.VK_P.with(META, CTRL))
{
	override val actionMapKey: String = "pascal-case"
	override val description: String = "Transform Selected Text to PascalCase"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to snake case.
 *
 * @author Richard Arriaga
 */
object SnakeCaseShortcut: CodePaneShortcut(KeyCode.VK_S.with(META, CTRL))
{
	override val actionMapKey: String = "snake-case"
	override val description: String = "Transform Selected Text to snake_case"
}

/**
 * The [CodePaneShortcut] that transforms the selected text to kebab case.
 *
 * @author Richard Arriaga
 */
object KebabCaseShortcut: CodePaneShortcut(KeyCode.VK_K.with(META, CTRL))
{
	override val actionMapKey: String = "kebab-case"
	override val description: String = "Transform Selected Text to kebab-case"
}

/**
 * The [CodePaneShortcut] that increases the size of the font by one point size.
 *
 * @author Richard Arriaga
 */
object IncreaseFontSizeShortcut: CodePaneShortcut(KeyCode.VK_EQUALS.with(META))
{
	override val actionMapKey: String = "increase-font-size"
	override val description: String =
		"Increase the font size by one point size"
}

/**
 * The [CodePaneShortcut] that decreases the size of the font by one point size.
 *
 * @author Richard Arriaga
 */
object DecreaseFontSizeShortcut: CodePaneShortcut(KeyCode.VK_MINUS.with(META))
{
	override val actionMapKey: String = "decrease-font-size"
	override val description: String =
		"Decrease the font size by one point size"
}
