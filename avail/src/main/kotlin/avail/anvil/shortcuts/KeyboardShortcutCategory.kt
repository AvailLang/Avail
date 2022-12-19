/*
 * KeyboardShortcutCategory.kt
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
import avail.anvil.AvailWorkbench
import avail.anvil.debugger.AvailDebugger
import avail.anvil.text.CodePane

/**
 * An enumeration of the different categorization of [KeyboardShortcut]s.
 *
 * @author Richard Arriaga
 */
enum class KeyboardShortcutCategory constructor(val display: String)
{
	/** [KeyboardShortcut]s for [AvailEditor]s. */
	AVAIL_EDITOR("Avail Editor")
	{
		override val shortcuts: List<KeyboardShortcut>
			get() = AvailEditorShortCut::class.sealedSubclasses.map {
				it.objectInstance!!
			}
	},

	/** [KeyboardShortcut]s across all [CodePane]s. */
	CODE_PANE("Code Pane")
	{
		override val shortcuts: List<KeyboardShortcut>
			get() = CodePaneShortCut::class.sealedSubclasses.map {
				it.objectInstance!!
			}
	},

	/** [KeyboardShortcut]s for the [AvailDebugger]. */
	DEBUGGER("Avail Debugger")
	{
		override val shortcuts: List<KeyboardShortcut>
			get() = AvailDebuggerShortCut::class.sealedSubclasses.map {
				it.objectInstance!!
			}
	},

	/** [KeyboardShortcut]s for the [AvailWorkbench]. */
	WORKBENCH("Workbench")
	{
		override val shortcuts: List<KeyboardShortcut>
			get() = WorkbenchShortCut::class.sealedSubclasses.map {
				it.objectInstance!!
			}
	};

	/**
	 * The list of [KeyboardShortcut]s in this [KeyboardShortcutCategory].
	 */
	abstract val shortcuts: List<KeyboardShortcut>

	/**
	 * Answer the [KeyboardShortcut] associated with the provided
	 * [KeyboardShortcut.actionMapKey].
	 *
	 * @param actionMapKey
	 *   The proposed [KeyboardShortcut.actionMapKey] to lookup.
	 * @return
	 *   The associated [KeyboardShortcut] or `null` if not a valid key.
	 */
	fun keyboardShortCut (actionMapKey: String): KeyboardShortcut? =
		shortcuts.firstOrNull { it.actionMapKey == actionMapKey }

	companion object
	{
		/**
		 * The names of all the valid [KeyboardShortcutCategory]s.
		 */
		val validNames: String get() =
			values().joinToString(", ") { it.name }

		val validActionMapKeys: String get() =
			values().joinToString(", ") {
				it.shortcuts
					.filter {sc -> sc.customizable }
					.joinToString(", ") { sc ->
						"${sc.actionMapKey} (${it.name})"
					}
			}
	}
}
