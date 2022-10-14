/*
 * GlobalAvailConfigurationV1.kt
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

import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * Version 1 of [GlobalAvailConfiguration].
 *
 * @author Richard Arriaga
 */
class GlobalAvailConfigurationV1
	: GlobalAvailConfiguration
{
	override val serializationVersion: Int = 1
	override val knownProjects = mutableSetOf<KnownAvailProject>()
	override var defaultStandardLibrary: String? = null
	override var favorite: String? = null
	override val globalTemplates = mutableMapOf<String, String>()

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(GlobalAvailConfigurationV1::serializationVersion.name) {
				write(serializationVersion)
			}
			at(GlobalAvailConfigurationV1::defaultStandardLibrary.name) {
				defaultStandardLibrary.let {
					if (it == null) writeNull()
					else write(it)
				}
			}
			at(GlobalAvailConfigurationV1::favorite.name) {
				favorite.let {
					if (it == null) writeNull()
					else write(it)
				}
			}
			at(GlobalAvailConfigurationV1::knownProjects.name) {
				writeArray(knownProjects.toMutableList().sorted())
			}
			if (globalTemplates.isNotEmpty())
			{
				at(GlobalAvailConfigurationV1::globalTemplates.name) {
					writeObject {
						globalTemplates.forEach { (name, expansion) ->
							at(name) { write(expansion) }
						}
					}
				}
			}
		}
	}

	companion object
	{
		/**
		 * Answer a [GlobalAvailConfigurationV1] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [GlobalAvailConfigurationV1] read from disk.
		 */
		fun from (obj: JSONObject): GlobalAvailConfigurationV1
		{
			val config = GlobalAvailConfigurationV1()
			if (obj.containsKey(
				GlobalAvailConfigurationV1::defaultStandardLibrary.name))
			{
				val lib =
					obj[GlobalAvailConfigurationV1::defaultStandardLibrary.name]
				if (lib.isString)
				{
					config.defaultStandardLibrary =	lib.string
				}
			}
			if (obj.containsKey(GlobalAvailConfigurationV1::favorite.name))
			{
				val favorite = obj[GlobalAvailConfigurationV1::favorite.name]
				if (favorite.isString)
				{
					config.favorite = favorite.string
				}
			}
			if (obj.containsKey(
					GlobalAvailConfigurationV1::knownProjects.name))
			{
				obj.getArray(GlobalAvailConfigurationV1::knownProjects.name)
					.forEach { data ->
						if (data.isObject)
						{
							data as JSONObject
							KnownAvailProject.from(data)?.let {
								config.knownProjects.add(it)
							}
						}
					}
			}
			if (obj.containsKey(GlobalAvailConfigurationV1::globalTemplates.name))
			{
				val map = obj.getObject(
					GlobalAvailConfigurationV1::globalTemplates.name)
				map.forEach { (name, expansion) ->
					config.globalTemplates[name] = expansion.string
				}
			}
			return config
		}
	}
}