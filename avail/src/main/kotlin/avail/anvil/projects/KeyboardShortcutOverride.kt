/*
 * KeyboardShortcutOverride.kt
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

package avail.anvil.projects

import avail.anvil.shortcuts.BaseKeyboardShortcut
import avail.anvil.shortcuts.KeyCode
import avail.anvil.shortcuts.KeyboardShortcut
import avail.anvil.shortcuts.KeyboardShortcutCategory
import avail.anvil.shortcuts.ModifierKey
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import kotlin.IllegalArgumentException

/**
 * A user-defined override for a [BaseKeyboardShortcut].
 *
 * @author Richard Arriaga
 */
class KeyboardShortcutOverride constructor(
	override val category: KeyboardShortcutCategory,
	override var keyCode: KeyCode,
	override val actionMapKey: String
): BaseKeyboardShortcut, JSONFriendly
{
	override val modifierKeys = mutableSetOf<ModifierKey>()

	/**
	 * The associated [KeyboardShortcut] or `null` if [actionMapKey] is not a
	 * valid [KeyboardShortcut.actionMapKey]
	 */
	val associatedShortcut: KeyboardShortcut? get() =
		category.keyboardShortCut(actionMapKey)

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::category.name)
			{
				write(category.name)
			}
			at(::actionMapKey.name)
			{
				write(actionMapKey)
			}
			at(::keyCode.name)
			{
				write(keyCode.lookupKey)
			}
			at(::modifierKeys.name)
			{
				writeArray {
					modifierKeys.forEach {
						write(it.lookupKey)
					}
				}
			}
		}
	}

	companion object
	{
		/**
		 * Answer a [KeyboardShortcutOverride] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [KeyboardShortcutOverride] read from the JSON object.
		 */
		fun from(obj: JSONObject): KeyboardShortcutOverride
		{
			val categoryName =
				obj[KeyboardShortcutOverride::category.name].string
			val category =
				try
				{
					KeyboardShortcutCategory.valueOf(categoryName)
				}
				catch (e: IllegalArgumentException)
				{
					throw IllegalArgumentException(
						"$categoryName is not a valid " +
							"KeyboardShortcutCategory. The category must be " +
							"one of:\n ${KeyboardShortcutCategory.validNames}",
						e)
				}
			val actionMapKey =
				obj[KeyboardShortcutOverride::actionMapKey.name].string

			val keyCodeLookup =
				obj[KeyboardShortcutOverride::keyCode.name].string
			val keyCode = KeyCode.lookup(keyCodeLookup) ?:
				throw IllegalArgumentException(
					"$keyCodeLookup is not a valid KeyCode lookup. " +
						"The KeyCode lookup must be one of:\n " +
						KeyCode.validLookups)

			val shortcut =
				KeyboardShortcutOverride(category, keyCode, actionMapKey)

			if (shortcut.associatedShortcut == null)
			{
				throw IllegalArgumentException(
					"$actionMapKey is not a valid " +
						"KeyboardShortcut.actionMapKey. The actionMapKey " +
						"must be one of:\n" +
						KeyboardShortcutCategory.validActionMapKeys)
			}

			obj.getArray(KeyboardShortcutOverride::modifierKeys.name)
				.strings.forEach {
					val key = ModifierKey.lookup(it) ?:
						throw IllegalArgumentException(
							"$it is not a valid ModifierKey lookup. " +
								"The ModifierKey lookup must be one of:\n " +
								ModifierKey.validLookups)
					shortcut.modifierKeys.add(key)
				}

			return shortcut
		}
	}
}
