/*
 * KeyboardShortcut.kt
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

import avail.anvil.settings.KeyboardShortcutOverride
import java.awt.event.KeyEvent
import javax.swing.InputMap
import javax.swing.KeyStroke

/**
 * A combination of keyboard keys.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @property code
 *   The [KeyCode] of the [Key] combination.
 * @property modifiers
 *   The [ModifierKey]s of this [Key] combination.
 */
data class Key(val code: KeyCode, val modifiers: Set<ModifierKey>)
{
	/**
	 * The [KeyStroke] used to trigger this [Key].
	 */
	val keyStroke : KeyStroke get() =
		KeyStroke.getKeyStroke(
			code.code,
			modifiers.fold(0)
			{
				modifier, mk -> modifier.or(mk.modifier)
			})

	/**
	 * The [ModifierKey.displayRepresentation]s and the
	 * [KeyCode.displayRepresentation] in a single String.
	 */
	val keyAsString: String get() =
		modifiers.sorted().joinToString("") { it.displayRepresentation } +
			code.displayRepresentation

	companion object
	{
		/**
		 * Answer the [Key] capture in the provided [KeyEvent].
		 *
		 * @param keyEvent
		 *   The [KeyEvent] to extract the [Key] from.
		 * @return
		 *   The extracted [Key] or `null` if [KeyEvent.keyCode] does not map
		 *   to a known [KeyCode].
		 */
		fun from (keyEvent: KeyEvent): Key?
		{
			val kc = KeyCode.lookupByCode(keyEvent.keyCode)
				?: KeyCode.lookup(keyEvent.keyChar)
				?: return null
			return Key(kc, ModifierKey.getModifiersFrom(keyEvent.modifiersEx))
		}
	}
}

/**
 * The base-level information required to create a keyboard shortcut.
 *
 * @author Richard Arriaga
 */
interface BaseKeyboardShortcut
{
	/**
	 * The [KeyboardShortcutCategory] this shortcut belongs to.
	 */
	val category: KeyboardShortcutCategory

	/**
	 * The key of this [BaseKeyboardShortcut].
	 */
	var key: Key

	/**
	 * The key that identifies this [BaseKeyboardShortcut].
	 */
	val actionMapKey: String

	/**
	 * The [KeyStroke] used to trigger this [KeyboardShortcut].
	 */
	val keyStroke : KeyStroke get() = key.keyStroke

	/**
	 * Check to see if the provided [BaseKeyboardShortcut] matches the
	 * key bindings of this [BaseKeyboardShortcut] while having different
	 * [actionMapKey]s.
	 *
	 * @param bks
	 *   The [BaseKeyboardShortcut] to compare.
	 * @return
	 *   `true` indicates the shortcuts match and the [actionMapKey]s are
	 *   different; `false` otherwise.
	 */
	fun matchesOther(bks: BaseKeyboardShortcut): Boolean =
		bks.key == key && bks.actionMapKey != actionMapKey
}

/**
 * An abstract keyboard shortcut used to perform some action in Anvil.
 *
 * **NOTE** Keyboard shortcuts must adhere to the following implementation
 * rules:
 * 1. All non-abstract subclass implementations must be a child type of a
 *   sealed class that corresponds to a [KeyboardShortcutCategory].
 * 2. All non-abstract subclass implementations must be an object.
 * 3. All abstract subclass implementations must be a sealed class that
 *   corresponds to a single [KeyboardShortcutCategory].
 *
 * @author Richard Arriaga
 */
abstract class KeyboardShortcut: BaseKeyboardShortcut
{
	/**
	 * This [KeyboardShortcut] is permitted to customized for an environment.
	 */
	open val customizable: Boolean = true

	/**
	 * The description of the action the shortcut performs.
	 */
	abstract val description: String

	/**
	 * The default [Key] when pressed triggers this shortcut.
	 */
	abstract val defaultKey: Key

	/**
	 * The [description] that is displayed that describes the shortcut or the
	 * [actionMapKey] if the [description] is empty.
	 */
	val descriptionDisplay: String get () =
		description.ifBlank { actionMapKey }

	/**
	 * A corresponding [KeyboardShortcutOverride] sourced from this
	 * [KeyboardShortcut].
	 */
	val shortcutOverride: KeyboardShortcutOverride
		get() =
		KeyboardShortcutOverride(category, key, actionMapKey)

	/**
	 * Reset the [key] to the [defaultKey].
	 */
	fun resetToDefaults()
	{
		key = defaultKey
	}

	/**
	 * Add this [KeyboardShortcut] to the provided [InputMap].
	 *
	 * @param inputMap
	 *   The inputMap to add this shortcut to.
	 */
	open fun addToInputMap (inputMap: InputMap)
	{
		inputMap.put(keyStroke, actionMapKey)
	}

	/**
	 * Update this [KeyboardShortcut] to match the [ModifierKey]s and [KeyCode]
	 * used in the provided [KeyStroke].
	 *
	 * @param keyStroke
	 *   The [KeyStroke] to use to update this [KeyboardShortcut].
	 * @return
	 *   `true` if the update was successfully completed; `false` if the
	 *   [KeyStroke.keyCode] is not a recognized [KeyCode].
	 */
	fun updateFrom (keyStroke: KeyStroke): Boolean
	{
		val newKeyCode = KeyCode.lookupByCode(keyStroke.keyCode) ?: return false
		val mks = ModifierKey.getModifiersFrom(keyStroke)
		key = Key(newKeyCode, mks)
		return true
	}
}
