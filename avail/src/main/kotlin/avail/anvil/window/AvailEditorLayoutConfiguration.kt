/*
 * AvailEditorLayoutConfiguration.kt
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

import avail.anvil.AvailEditor
import avail.anvil.AvailWorkbench
import avail.anvil.text.DotPosition
import avail.anvil.text.MarkPosition
import avail.anvil.text.MarkToDotRange
import avail.anvil.text.centerCurrentLine
import avail.anvil.text.setCaretFrom
import avail.builder.ModuleName
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * The [LayoutConfiguration] for an [AvailEditor]
 *
 * @author Richard Arriaga
 */
class AvailEditorLayoutConfiguration internal constructor (
	internal val qualifiedName: String
) : LayoutConfiguration(), JSONFriendly
{
	/**
	 * The associated [AvailEditor.range].
	 */
	var range = MarkToDotRange(
		MarkPosition(0, 0, 0),
		DotPosition(0, 0, 0))

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(AvailEditorLayoutConfiguration::qualifiedName.name) {
				write(qualifiedName)
			}
			at(layoutKey) { write(stringToStore()) }
			at(AvailEditorLayoutConfiguration::range.name) {
				range.writeTo(this)
			}
		}
	}

	/**
	 * Open an [AvailEditor] based on the information in this
	 * [AvailEditorLayoutConfiguration].
	 *
	 * @param workbench
	 *   The owning [AvailWorkbench].
	 */
	fun open (workbench: AvailWorkbench)
	{
		AvailEditor(workbench, ModuleName(qualifiedName, false))
		{
			it.sourcePane.setCaretFrom(range)
			it.sourcePane.centerCurrentLine()
		}.apply {
			this@AvailEditorLayoutConfiguration.placement?.let {
				bounds = it
			}
			extendedState = this@AvailEditorLayoutConfiguration.extendedState
			workbench.openEditors[this.resolverReference.moduleName] = this
		}
	}

	companion object
	{
		/**
		 * The JSON field that contains the [stringToStore].
		 */
		private const val layoutKey = "layout"

		/**
		 * Extract the [AvailWorkbenchLayoutConfiguration] from the provided
		 * [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   An [AvailWorkbenchLayoutConfiguration] or `null` if malformed..
		 */
		fun from (obj: JSONObject): AvailEditorLayoutConfiguration?
		{
			if (!obj.containsKey(
					AvailEditorLayoutConfiguration::qualifiedName.name))
			{
				return null
			}
			val qn = obj.getString(
				AvailEditorLayoutConfiguration::qualifiedName.name)
			val layout =
				if (!obj.containsKey(layoutKey)) ""
				else obj.getString(layoutKey)
			val extractedRange =
				if (obj.containsKey(AvailEditorLayoutConfiguration::range.name))
				{
					MarkToDotRange.from(
						obj.getObject(
							AvailEditorLayoutConfiguration::range.name))
				}
				else null
			return AvailEditorLayoutConfiguration(qn).apply {
				if (extractedRange != null)
				{
					this.range = extractedRange
				}
				parseInput(layout)
			}
		}
	}
}
