/*
 * WorkbenchScreenState.kt
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

package avail.anvil.window

import avail.builder.ModuleName
import avail.anvil.AvailEditor
import avail.anvil.AvailWorkbench
import avail.anvil.views.PhraseViewPanel
import avail.anvil.views.StructureViewPanel
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import org.availlang.json.jsonWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The local [AvailProject] screen state that tracks the state of the open
 * windows on the local machine. This is used to save open window positioning
 * between project startups.
 *
 * @author Richard Arriaga
 *
 * @property workbenchLayoutConfig
 *   The [AvailWorkbenchLayoutConfiguration.stringToStore]
 * @property structureViewLayoutConfig
 *   The [LayoutConfiguration.stringToStore] for the [StructureViewPanel] or
 *   an empty string if not open.
 * @property phraseViewLayoutConfig
 *   The [LayoutConfiguration.stringToStore] for the [PhraseViewPanel] or
 *   an empty string if not open.
 */
class WorkbenchScreenState constructor(
	var workbenchLayoutConfig: String = "",
	var structureViewLayoutConfig: String = "",
	var phraseViewLayoutConfig: String = ""
): JSONFriendly
{
	// TODO use JTextComponent.setCaretFrom to position caret
	/**
	 * The map from [ModuleName] to [AvailEditorLayoutConfiguration] for the
	 * open [AvailEditor]s.
	 */
	val openEditors: MutableMap<ModuleName, AvailEditorLayoutConfiguration> =
		ConcurrentHashMap()

	/**
	 * Refresh [openEditors] from the given [AvailWorkbench].
	 *
	 * @param workbench
	 *   The source of the [AvailWorkbench.openEditors].
	 */
	fun refreshOpenEditors (workbench: AvailWorkbench)
	{
		openEditors.clear()
		workbench.openEditors.forEach { (k, v) ->
			v.saveWindowPosition()
			openEditors[k] =
				v.layoutConfiguration as AvailEditorLayoutConfiguration
		}
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(WorkbenchScreenState::workbenchLayoutConfig.name) {
				write(workbenchLayoutConfig)
			}
			at(WorkbenchScreenState::structureViewLayoutConfig.name) {
				write(structureViewLayoutConfig)
			}
			at(WorkbenchScreenState::phraseViewLayoutConfig.name) {
				write(phraseViewLayoutConfig)
			}
			at(WorkbenchScreenState::openEditors.name) {
				writeArray {
					openEditors.forEach { (_, v) ->
						v.writeTo(this)
					}
				}
			}
		}
	}

	/**
	 * The file contents to write to the
	 */
	val fileContent: String get() =
		jsonWriter {
			this@WorkbenchScreenState.writeTo(this)
		}.toString()

	companion object
	{
		/**
		 * Extract a [WorkbenchScreenState] from the provided file.
		 *
		 * @param file
		 *   The file to extract data from.
		 * @return
		 *   A [WorkbenchScreenState] populated with data from the file or an empty
		 *   [WorkbenchScreenState] if any occur
		 *   1. The file does not exist
		 *   2. The file is a directory
		 *   3. An exception occurs during data extraction.
		 */
		fun from (file: File): WorkbenchScreenState =
			try
			{
				if (!file.exists() || !file.isFile)
				{
					WorkbenchScreenState()
				}
				else
				{
					from(jsonObject(file.readText()))
				}
			}
			catch (e: Throwable)
			{
				WorkbenchScreenState()
			}

		/**
		 * Answer a [WorkbenchScreenState] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [WorkbenchScreenState].
		 */
		fun from (obj: JSONObject): WorkbenchScreenState
		{
			val wb = obj.getString(
				WorkbenchScreenState::workbenchLayoutConfig.name
			) { "" }
			val sv = obj.getString(
				WorkbenchScreenState::structureViewLayoutConfig.name
			) { "" }
			val pv = obj.getString(
				WorkbenchScreenState::phraseViewLayoutConfig.name
			) { "" }
			return WorkbenchScreenState(wb, sv, pv).apply {
				if (obj.containsKey(WorkbenchScreenState::openEditors.name))
				{
					obj.getArray(WorkbenchScreenState::openEditors.name).forEach {
						if (it.isObject)
						{
							val alc = AvailEditorLayoutConfiguration.from(
								it as JSONObject)
							if (alc != null)
							{
								val mn = ModuleName(alc.qualifiedName, false)
								openEditors[mn] = alc
							}
						}
					}
				}
			}
		}
	}
}
