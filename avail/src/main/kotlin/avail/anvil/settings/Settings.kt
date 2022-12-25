/*
 * Settings.kt
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

import org.availlang.json.JSONArray
import org.availlang.json.JSONData
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonWriter
import java.io.File

/**
 * Describes the type of a [Settings].
 *
 * **NOTE** The companion object of the associated [Settings] is expected to
 * extend [SettingsType] for the implementation of [Settings].
 *
 * @author Richard Arriaga
 *
 * @property key
 *   The String key that uniquely identifies the associated [Settings].
 * @property allowsDuplicates
 *   `true` if this [SettingsType] allows multiple [Settings] of this type to be
 *   exported to the same settings file; `false` otherwise.
 */
sealed class SettingsType<Type: Settings> constructor(
	val key: String,
	val allowsDuplicates: Boolean)
{
	/**
	 * Extract the [Type] from the provided [JSONObject].
	 *
	 * @param obj
	 *   The [JSONObject] to extract the [Type] from.
	 * @return
	 *   The extracted [Type].
	 * @throws IllegalStateException
	 *   If the "settings" [JSONArray] field is missing.
	 * @throws ClassCastException
	 *   If the "settings" field is not a [JSONArray].
	 */
	abstract fun extract (obj: JSONObject): Type

	/**
	 * Extract the [Type] from the provided [JSONObject] and pass it to the
	 * provided lambda.
	 *
	 * @param obj
	 *   The [JSONObject] to extract the [Type] from.
	 * @param then
	 *   The lambda that accepts two nullable types: 1. The extracted [Type] and
	 *   2. a [Throwable] in the event the [Type] extraction fails.
	 */
	fun extractAndProcess (
		obj: JSONObject,
		then: (Type?, Throwable?) -> Unit)
	{
		try
		{
			then(extract(obj), null)
		}
		catch (e: Throwable)
		{
			then(null, e)
		}
	}

	companion object
	{
		/**
		 * Get the [SettingsType] that matches the provided [SettingsType.key].
		 *
		 * @param key
		 *   The [SettingsType.key] to lookup.
		 * @return
		 *   The associated [SettingsType] or `null` if not recognized.
		 */
		fun typeFor (key: String): SettingsType<*>? =
			SettingsType::class.sealedSubclasses.firstOrNull {
				it.objectInstance?.key == key
			}?.objectInstance
	}
}

/**
 * The abstract parent type of all user-editable Anvil settings.
 *
 * **NOTE** All implementations of [Settings] are expected to use their
 * companion objects act as its associated [SettingsType] by having the
 * companion object extend [SettingsType].
 *
 * @author Richard Arriaga
 *
 * @property type
 *   The associated [SettingsType]. This is expected to be the companion object
 *   of the implemented subtype.
 */
sealed class Settings constructor(val type: SettingsType<*>): JSONFriendly
{
	/**
	 * The [JSONWriter] action that writes the data contained in these
	 * [Settings] to the [JSONWriter].
	 */
	abstract val writeSettingsAction: JSONWriter.() -> Unit

	final override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(TYPE_KEY) { write(type.key) }
			at(SETTINGS, writeSettingsAction)
		}
	}

	companion object
	{
		/**
		 * The JSON field name for the [SettingsType] in the associated JSON
		 * payload.
		 */
		const val TYPE_KEY = "type"

		/**
		 * The JSON field name for the actual settings [JSONData] in the
		 * associated JSON payload.
		 */
		const val SETTINGS = "settings"

		/**
		 * Export the provided [Settings] to the provided [File]. The [Settings]
		 * are written as a [JSONArray] of [JSONObject]s, with each [JSONObject]
		 * taking the form:
		 *
		 * ```json
		 * {
		 *   "type": //associated SettingsType.key
		 *   "settings": //however the specific type is written to JSON
		 * }
		 * ```
		 *
		 * @param file
		 *   The [File] to write the settings to.
		 * @param settings
		 *   The vararg [Settings] to write.
		 */
		fun exportSettings (file: File, vararg settings: Settings)
		{
			file.writeText(jsonWriter {
				writeArray {
					settings.forEach { it.writeTo(this) }
				}
			}.toString())
		}

		/**
		 * Export the provided [Settings] to the provided [File]. The [Settings]
		 * are written as a [JSONArray] of [JSONObject]s, with each [JSONObject]
		 * taking the form:
		 *
		 * ```json
		 * {
		 *   "type": //associated SettingsType.key
		 *   "settings": //however the specific type is written to JSON
		 * }
		 * ```
		 *
		 * @param file
		 *   The [File] to write the settings to.
		 * @param settings
		 *   The collection of [Settings] to write.
		 */
		fun exportSettings (file: File, settings: Collection<Settings>)
		{
			file.writeText(jsonWriter {
				writeArray {
					settings.forEach { it.writeTo(this) }
				}
			}.toString())
		}
	}
}

