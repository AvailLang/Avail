/*
 * TextPaneShortcuts.kt
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

import avail.anvil.actions.AbstractWorkbenchAction
import avail.anvil.actions.FindAction
import avail.anvil.debugger.AvailDebugger

/**
 * A [KeyboardShortcut] that is used to launch an
 * [AbstractWorkbenchAction] while using the [AvailDebugger].
 *
 * @constructor
 * Construct a [TextPaneShortcut].
 *
 * @param defaultKey
 *   The default [Key] when pressed triggers this shortcut.
 * @param key
 *   The [Key] used for this shortcut. Defaults to `defaultKey`.
 */
sealed class TextPaneShortcut constructor(
	override val defaultKey: Key,
	override var key: Key = defaultKey
): KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.WORKBENCH
}

/**
 * [TextPaneShortcut] for the [FindAction].
 *
 * @author Richard Arriaga
 */
object FindActionShortcut
	: TextPaneShortcut(KeyCode.VK_F.with(ModifierKey.menuShortcutKeyMaskEx))
{
	override val actionMapKey: String = "find-replace"
	override val description: String = "Open Find/Replace Dialog"
}
