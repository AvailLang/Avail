/*
 * ShortcutSettings.kt
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

package avail.anvil.settings

import avail.anvil.AnvilException
import avail.anvil.projects.KeyboardShortcutOverride
import avail.anvil.shortcuts.KeyboardShortcut
import org.availlang.json.JSONArray
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonReader
import java.io.File
import kotlin.reflect.full.companionObject

/**
 * The [Settings] for [KeyboardShortcut]s.
 *
 * @author Richard Arriaga
 *
 * @property keyboardShortcutOverrides
 *   The map of [KeyboardShortcutOverride.actionMapKey] to
 *   [KeyboardShortcutOverride] that override the default key combinations for
 *   the listed [KeyboardShortcut]s.
 */
data class ShortcutSettings constructor(
	val keyboardShortcutOverrides: Map<String, KeyboardShortcutOverride>
): Settings(
	ShortcutSettings::class.companionObject!!.objectInstance as SettingsType<*>)
{
	override val writeSettingsAction: JSONWriter.() -> Unit =
	{
		writeArray {
			keyboardShortcutOverrides.forEach {
				it.value.writeTo(this)
			}
		}
	}

	/**
	 * The [ShortcutSettings.Companion] is also a [SettingsType] parameterized
	 * on [ShortcutSettings].
	 */
	companion object: SettingsType<ShortcutSettings>("SHORTCUTS", false)
	{
		override fun extract (obj: JSONObject): ShortcutSettings
		{
			if (!obj.containsKey(SETTINGS))
			{
				throw IllegalStateException(
					"Settings JSON missing field: $SETTINGS")
			}
			return ShortcutSettings(obj.getArray(SETTINGS)
				.map { KeyboardShortcutOverride.from(it as JSONObject) }
				.associateBy { it.actionMapKey })
		}

		/**
		 * Extract the [ShortcutSettings] from the provided [Settings] [File].
		 *
		 * @param file
		 *   The [File] to extract the [ShortcutSettings] from.
		 *
		 */
		fun readFromFile (file: File): ShortcutSettings? =
			try
			{
				(jsonReader(file.readText()).read() as JSONArray)
					.map { it as JSONObject }
					.firstOrNull { key == it.getString(TYPE_KEY) }
					?.let { extract(it) }
			}
			catch (e: Throwable)
			{
				AnvilException(
					"Could not retrieve shortcut settings from ${file.path}",
					e).printStackTrace()
				null
			}
	}
}
