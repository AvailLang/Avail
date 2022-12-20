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

import javax.swing.InputMap
import javax.swing.KeyStroke

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
	 * The overrideable [Set] of [ModifierKey]s to trigger this shortcut. This
	 * is the modifier set use to trigger the short cut.
	 */
	val modifierKeys: MutableSet<ModifierKey>

	/**
	 * The [KeyCode] that represents the key when pressed in combination with
	 * the [modifierKeys] triggers the shortcut. This is the overrideable
	 * [KeyCode] used to trigger this shortcut
	 */
	var keyCode: KeyCode

	/**
	 * The key that identifies this [BaseKeyboardShortcut].
	 */
	val actionMapKey: String

	/**
	 * The [KeyStroke] used to trigger this [KeyboardShortcut].
	 */
	val keyStroke : KeyStroke get() =
		KeyStroke.getKeyStroke(
			keyCode.code,
			modifierKeys.fold(0)
			{
					modifier, mk -> modifier.or(mk.modifier)
			})
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
	 * The description of the action the shortcut performs.
	 */
	open val description: String = ""

	/**
	 * This [KeyboardShortcut] is permitted to customized for an environment.
	 */
	open val customizable: Boolean = true

	/**
	 * The default [Set] of [ModifierKey]s to trigger this shortcut.
	 */
	abstract val defaultModifierKeys: Set<ModifierKey>

	/**
	 * The overrideable [Set] of [ModifierKey]s to trigger this shortcut. This
	 * is the modifier set use to trigger the short cut.
	 */
	override val modifierKeys: MutableSet<ModifierKey> by lazy {
		defaultModifierKeys.toMutableSet()
	}

	/**
	 * The default [KeyCode] that represents the key when pressed in combination
	 * with the [modifierKeys] triggers the shortcut.
	 */
	abstract val defaultKeyCode: KeyCode

	/**
	 * The [description] that is displayed that describes the shortcut or the
	 * [actionMapKey] if the [description] is empty.
	 */
	val descriptionDisplay: String get () =
		description.ifBlank { actionMapKey }

	/**
	 * Reset the [modifierKeys] to the [defaultModifierKeys] and reset the
	 * [keyCode] to the [defaultKeyCode].
	 */
	fun resetToDefaults()
	{
		modifierKeys.clear()
		modifierKeys.addAll(defaultModifierKeys)
		keyCode = defaultKeyCode
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
}
