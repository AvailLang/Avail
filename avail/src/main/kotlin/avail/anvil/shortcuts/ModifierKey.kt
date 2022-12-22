/*
 * ModifierKey.kt
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

import java.awt.Toolkit
import java.awt.event.InputEvent
import javax.swing.KeyStroke

/**
 * Enumerates the [InputEvent] modifier keys.
 *
 * @author Richard Arriaga
 *
 * @property modifier
 *   The modifier constant.
 * @property lookupKey
 *   The key used to look up this modifier.
 */
enum class ModifierKey constructor(
	val modifier: Int,
	val lookupKey: String)
{
	/** The [Shift Key][InputEvent.SHIFT_DOWN_MASK]. */
	SHIFT(InputEvent.SHIFT_DOWN_MASK, "SHIFT")
	{
		override val displayRepresentation: String = "⇧"
	},

	/** The [Control Key][InputEvent.CTRL_DOWN_MASK]. */
	CTRL(InputEvent.CTRL_DOWN_MASK, "CTRL")
	{
		override val displayRepresentation: String = "⌃"
	},

	/**
	 * The [Meta Key][InputEvent.META_DOWN_MASK]. On Windows this is the Winodws
	 * key; on Mac, the Command key.
	 */
	META(InputEvent.META_DOWN_MASK, "META")
	{
		override val displayRepresentation: String get() =
			if (System.getProperty("os.name").startsWith("Mac"))
			{
				"⌘"
			}
			else
			{
				"Windows"
			}
	},

	/** The [Alt Key][InputEvent.ALT_DOWN_MASK]. */
	ALT(InputEvent.ALT_DOWN_MASK, "ALT")
	{
		override val displayRepresentation: String = "⌥"
	},

	/** The [Mouse Button 1][InputEvent.BUTTON1_DOWN_MASK]. */
	BUTTON1(InputEvent.BUTTON1_DOWN_MASK, "LEFT-CLICK"),

	/** The [Mouse Button 2][InputEvent.BUTTON2_DOWN_MASK]. */
	BUTTON2(InputEvent.BUTTON2_DOWN_MASK, "RIGHT-CLICK"),

	/** The [Mouse Button 3][InputEvent.BUTTON3_DOWN_MASK]. */
	BUTTON3(InputEvent.BUTTON3_DOWN_MASK, "MIDDLE-MOUSE-BUTTON");

	/**
	 * The platform-specific String that represents this key.
	 */
	open val displayRepresentation: String get () = lookupKey

	companion object
	{
		/**
		 * Answer the [ModifierKey] that matches the provided
		 * [ModifierKey.lookupKey].
		 *
		 * @param lookupKey
		 *   The String lookup key to retrieve.
		 * @return
		 *   The matching [ModifierKey] of `null` if not found.
		 */
		fun lookup (lookupKey: String): ModifierKey? =
			values().firstOrNull { lookupKey == it.lookupKey }

		/**
		 * Answer the [ModifierKey] that matches the provided [modifier].
		 *
		 * @param code
		 *   The key code to retrieve.
		 * @return
		 *   The matching [ModifierKey] of `null` if not found.
		 */
		fun lookupByCode (code: Int): ModifierKey? =
			values().firstOrNull { code == it.modifier }

		/**
		 * Answer all the [ModifierKey]s used in the provided [KeyStroke].
		 *
		 * @param keyStroke
		 *   The [KeyStroke] to extract the [ModifierKey]s from.
		 * @return
		 *   The set of [ModifierKey]s used; an empty set if none used.
		 */
		fun getModifiersFrom (keyStroke: KeyStroke): Set<ModifierKey>
		{
			val modifiers = mutableSetOf<ModifierKey>()
			values().forEach {
				if (it.modifier.and(keyStroke.modifiers) == it.modifier)
				{
					modifiers.add(it)
				}
			}
			return modifiers
		}

		/**
		 * Answer all the [ModifierKey]s used in the provided modifier value.
		 *
		 * @param modifier
		 *   The modifier value to extract the [ModifierKey]s from.
		 * @return
		 *   The set of [ModifierKey]s used; an empty set if none used.
		 */
		fun getModifiersFrom (modifier: Int): Set<ModifierKey>
		{
			val modifiers = mutableSetOf<ModifierKey>()
			values().forEach {
				if (it.modifier.and(modifier) == it.modifier)
				{
					modifiers.add(it)
				}
			}
			return modifiers
		}

		/**
		 * The [ModifierKey] that is the appropriate accelerator key for menu
		 * shortcuts.
		 */
		val menuShortcutKeyMaskEx: ModifierKey get()
		{
			val key = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
			return lookupByCode(key)!!
		}

		/**
		 * The [ModifierKey.lookupKey]s of all the valid [ModifierKey]s.
		 */
		val validLookups: String get() =
			values().joinToString(", ") { it.lookupKey }
	}
}
