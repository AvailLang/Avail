/*
 * GlobalAvailSettingsV1.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.environment

import avail.anvil.projects.KnownAvailProject
import avail.anvil.settings.ShortcutSettings
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.awt.Font

/**
 * Version 1 of [GlobalAvailSettings].
 *
 * @author Richard Arriaga
 */
class GlobalAvailSettingsV1: GlobalAvailSettings
{
	override val serializationVersion: Int = 1
	override val knownProjects = mutableSetOf<KnownAvailProject>()
	override var favorite: String? = null
	override var codePaneFontSize: Float = 13.0f
	override var font: String = Font.MONOSPACED
	override val editorGuideLines = mutableListOf<Int>()
	override val shortcutSettings = ShortcutSettings.readEnvOverrides()

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::serializationVersion.name) { write(serializationVersion) }

			at(::favorite.name)
			{
				favorite.let {
					if (it == null) writeNull()
					else write(it)
				}
			}
			at(::codePaneFontSize.name) { write(codePaneFontSize) }
			at(::font.name) { write(font) }
			at(::editorGuideLines.name)
			{
				writeArray {
					editorGuideLines.forEach { write(it) }
				}
			}
			at(::knownProjects.name)
			{
				writeArray(knownProjects.toMutableList().sorted())
			}
		}
	}

	companion object
	{
		/**
		 * Answer a [GlobalAvailSettingsV1] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [GlobalAvailSettingsV1] read from disk.
		 */
		fun from (obj: JSONObject): GlobalAvailSettingsV1
		{
			val config = GlobalAvailSettingsV1()

			obj.getStringOrNull(GlobalAvailSettingsV1::favorite.name)?.let {
				config.favorite = it
			}
			obj.getStringOrNull(GlobalAvailSettingsV1::font.name)?.let {
				config.font = it
			}
			obj.getFloatOrNull(
				GlobalAvailSettingsV1::codePaneFontSize.name)?.let {
					config.codePaneFontSize = it
			}
			obj.getArrayOrNull(
				GlobalAvailSettingsV1::editorGuideLines.name)?.let {
					config.editorGuideLines.addAll(it.ints)
			} ?: config.editorGuideLines.add(80)
			obj.getArrayOrNull(GlobalAvailSettingsV1::knownProjects.name)
				?.forEach { data ->
					if (data.isObject)
					{
						data as JSONObject
						KnownAvailProject.from(data)?.let {
							config.knownProjects.add(it)
						}
					}
				}
			return config
		}
	}
}
