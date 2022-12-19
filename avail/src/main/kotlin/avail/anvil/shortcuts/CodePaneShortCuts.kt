/*
 * CodePaneShortCuts.kt
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

import avail.anvil.text.CodePane
import javax.swing.InputMap
import javax.swing.JViewport
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent

/**
 * A [KeyboardShortcut] that is used in a generic [CodePane].
 *
 * @author Richard Arriaga
 */
sealed class CodePaneShortCut: KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.CODE_PANE
}

/**
 * [CodePaneShortCut] to replace the current selection with a space
 * (U+0020).
 *
 * @author Richard Arriaga
 */
object InsertSpaceShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_SPACE
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "insert-space"
}

/**
 * The [CodePaneShortCut] for break the current line by replacing the
 * current selection with a linefeed (U+000A) and as much horizontal tabulation
 * (U+0009) as began the line.
 *
 * @author Richard Arriaga
 */
object BreakLineShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_ENTER
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = DefaultEditorKit.insertBreakAction
}

/**
 * The [CodePaneShortCut] for outdenting selected lines enclosing the
 * selection. If no text is selected, then remove at most one horizontal
 * tabulation (U+0009) at the beginning of the line containing the caret.
 *
 * @author Richard Arriaga
 */
object OutdentShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_TAB
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "outdent"
}

/**
 * The [CodePaneShortCut] for centering the current line of the
 * [source&#32;component][JTextComponent] in its enclosing
 * [viewport][JViewport]. If no viewport encloses the receiver, then do not move
 * the caret.
 *
 * @author Richard Arriaga
 */
object CenterCurrentLineShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_M
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "outdent"
}

/**
 * The [CodePaneShortCut] that calls the undo action.
 *
 * @author Richard Arriaga
 */
object UndoShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_Z
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "undo"
}

/**
 * The [CodePaneShortCut] that calls the redo action.
 *
 * @author Richard Arriaga
 */
object RedoShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.menuShortcutKeyMaskEx, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_Z
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "redo"
}

/**
 * The [CodePaneShortCut] that expands a template selection.
 *
 * @author Richard Arriaga
 */
object ExpandTemplateShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_SPACE
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "expand-template"
}

/**
 * The [CodePaneShortCut] that cancels the template selection.
 *
 * @author Richard Arriaga
 */
object CancelTemplateSelectionShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_ESCAPE
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "cancel-template-selection"
}

/**
 * The [CodePaneShortCut] that moves the selected lines up.
 *
 * @author Richard Arriaga
 */
object MoveLineUpShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.ALT_DOWN_MASK, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_UP
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "move-line-up"
}

/**
 * The [CodePaneShortCut] that moves the selected lines down.
 *
 * @author Richard Arriaga
 */
object MoveLineDownShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.ALT_DOWN_MASK, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_DOWN
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "move-line-down"
}

/**
 * The [CodePaneShortCut] that transforms the selected text to uppercase.
 *
 * @author Richard Arriaga
 */
object UppercaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_U
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "uppercase"

	override fun addToInputMap(inputMap: InputMap)
	{
		super.addToInputMap(inputMap)
	}
}

/**
 * The [CodePaneShortCut] that transforms the selected text to lowercase.
 *
 * @author Richard Arriaga
 */
object LowercaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_L
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "lowercase"
}

/**
 * The [CodePaneShortCut] that transforms the selected text to camel case.
 *
 * @author Richard Arriaga
 */
object CamelCaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_C
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "camel-case"
}

/**
 * The [CodePaneShortCut] that transforms the selected text to pascal case.
 *
 * @author Richard Arriaga
 */
object PascalCaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_P
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "pascal-case"
}

/**
 * The [CodePaneShortCut] that transforms the selected text to snake case.
 *
 * @author Richard Arriaga
 */
object SnakeCaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_S
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "snake-case"
}

/**
 * The [CodePaneShortCut] that transforms the selected text to kebab case.
 *
 * @author Richard Arriaga
 */
object KebabCaseShortcut: CodePaneShortCut()
{
	override val defaultModifierKeys =
		setOf(ModifierKey.META_DOWN_MASK, ModifierKey.CTRL_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_K
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "kebab-case"
}
