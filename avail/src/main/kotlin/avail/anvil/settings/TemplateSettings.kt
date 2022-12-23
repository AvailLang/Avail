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
import avail.anvil.AvailEditor
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.json.JSONArray
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonReader
import java.io.File
import kotlin.reflect.full.companionObject

/**
 * The [Settings] for [AvailEditor] templates.
 *
 * @author Richard Arriaga
 *
 * @property templates
 *   The map of template key to template expansion.
 */
data class TemplateSettings constructor(
	val templates: Map<String, String>
): Settings(
	TemplateSettings::class.companionObject!!.objectInstance as SettingsType<*>)
{
	override val writeSettingsAction: JSONWriter.() -> Unit =
	{
		writeObject {
			templates.forEach {
				at(it.key) { write(it.value) }
			}
		}
	}

	/**
	 * Combine this [TemplateSettings] with another [TemplateSettings] to create
	 * a new [TemplateSettings] containing all the templates from the source
	 * templates.
	 *
	 * @param other
	 *   The [TemplateSettings] to combine with this [TemplateSettings].
	 * @return
	 *   The new [TemplateSettings].
	 */
	fun combine (other: TemplateSettings): TemplateSettings =
		TemplateSettings(templates + other.templates)

	/**
	 * The [TemplateSettings.Companion] is also a [SettingsType] parameterized
	 * on [TemplateSettings].
	 */
	companion object: SettingsType<TemplateSettings>("TEMPLATES", true)
	{
		/**
		 * Extract the [TemplateSettings] from the provided [Settings] [File].
		 *
		 * @param file
		 *   The [File] to extract the [TemplateSettings] from.
		 *
		 */
		fun readFromFile (file: File): TemplateSettings? =
			try
			{
				getCombinedTemplates(
					jsonReader(file.readText()).read() as JSONArray)
			}
			catch (e: Throwable)
			{
				AnvilException(
					"Could not retrieve shortcut settings from ${file.path}",
					e).printStackTrace()
				null
			}

		/**
		 * The system's default [TemplateSettings] or `null` if there is a
		 * problem retrieving the default [TemplateSettings].
		 */
		val systemDefaultTemplates: TemplateSettings?
			get() =
				try
				{
					readFromFile(File(
						TemplateSettings::class.java.getResource(
							"/defaultTemplates.json")!!.path))
				}
				catch (e: Throwable)
				{
					AnvilException(
						"Could not retrieve system default templates",
						e).printStackTrace()
					null
				}

		override fun extract(obj: JSONObject): TemplateSettings
		{
			if (!obj.containsKey(SETTINGS))
			{
				throw IllegalStateException(
					"Settings JSON missing field: $SETTINGS")
			}
			return TemplateSettings(obj.getObject(SETTINGS)
				.associateTo(mutableMapOf()) { (name, expansion) ->
					name to expansion.string
				})
		}

		/**
		 * Combine the [TemplateSettings] [Collection] into a single
		 * [TemplateSettings]. Note any duplicates that exist across the
		 * different [TemplateSettings] will result in the last to
		 * [TemplateSettings.templates] as the final expansion for that template
		 * key.
		 *
		 * @param settings
		 *   The [TemplateSettings] [Collection] to combine.
		 * @return
		 *   A single [TemplateSettings] containing all the template expansions
		 *   across all the provided settings.
		 */
		fun combine(settings: Collection<TemplateSettings>): TemplateSettings =
			TemplateSettings(
				settings.flatMap { it.templates.entries }
					.associate { it.key to it.value })

		/**
		 * Get the all the [TemplateSettings] from the provided [JSONArray] and
		 * [combine] them into a single [TemplateSettings].
		 *
		 * @param arr
		 *   The [JSONArray] to extract the [TemplateSettings]s from.
		 * @return
		 *   The single [combined][combine] [TemplateSettings].
		 */
		fun getCombinedTemplates(arr: JSONArray): TemplateSettings =
			combine(arr
				.map { it as JSONObject }
				.filter { key == it.getString(TYPE_KEY) }
				.map { extract(it) })
	}
}

/**
 * The [AvailProjectRoot.templateSettings] as [TemplateSettings].
 */
val AvailProjectRoot.templateSettings: TemplateSettings get() =
	TemplateSettings(this.templates.toMap())

/**
 * Import the [TemplateSettings.templates] into this
 * [AvailProjectRoot.templates].
 *
 * @param templateSettings
 *   The [TemplateSettings] to import.
 */
fun AvailProjectRoot.import(templateSettings: TemplateSettings)
{
	this.templates.putAll(templateSettings.templates)
}

/**
 * The [AvailProject.templateSettings] as [TemplateSettings].
 */
val AvailProject.templateSettings: TemplateSettings get() =
	TemplateSettings(this.templates.toMap())

/**
 * Import the [TemplateSettings.templates] into this [AvailProject.templates].
 *
 * @param templateSettings
 *   The [TemplateSettings] to import.
 */
fun AvailProject.import(templateSettings: TemplateSettings)
{
	this.templates.putAll(templateSettings.templates)
}
