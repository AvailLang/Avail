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

import avail.anvil.debugger.AbstractDebuggerAction
import avail.anvil.debugger.AvailDebugger

/**
 * A [KeyboardShortcut] that is used to launch an [AbstractDebuggerAction]
 * while using the [AvailDebugger].
 */
sealed class AvailDebuggerShortCut: KeyboardShortcut()
{
	override val category: KeyboardShortcutCategory
		get() = KeyboardShortcutCategory.DEBUGGER
}

/**
 * [AvailDebuggerShortCut] for the single-step into action which allows the
 * selected fiber to execute one L1 nybblecode.
 *
 * @author Richard Arriaga
 */
object StepIntoShortcut: AvailDebuggerShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_F7
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "step-into"
}

/**
 * [AvailDebuggerShortCut] for the single-step over action.
 *
 * @author Richard Arriaga
 */
object StepOverShortcut: AvailDebuggerShortCut()
{
	override val defaultModifierKeys = emptySet<ModifierKey>()
	override val defaultKeyCode: KeyCode = KeyCode.VK_F8
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "step-over"
}

/**
 * [AvailDebuggerShortCut] for the stepping out of the current process.
 *
 * @author Richard Arriaga
 */
object StepOutShortcut: AvailDebuggerShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.SHIFT_DOWN_MASK)
	override val defaultKeyCode: KeyCode = KeyCode.VK_F8
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "step-out"
}

/**
 * [AvailDebuggerShortCut] for resuming current process.
 *
 * @author Richard Arriaga
 */
object ResumeActionShortcut: AvailDebuggerShortCut()
{
	override val defaultModifierKeys = setOf(ModifierKey.menuShortcutKeyMaskEx)
	override val defaultKeyCode: KeyCode = KeyCode.VK_R
	override var keyCode = defaultKeyCode
	override val actionMapKey: String = "resume"
}
