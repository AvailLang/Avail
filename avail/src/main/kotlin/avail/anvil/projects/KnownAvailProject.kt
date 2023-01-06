/*
 * KnownAvailProject.kt
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

import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A reference to an [AvailProject] on this machine.
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The [AvailProject.name].
 * @property id
 *   The [AvailProject.id].
 * @property projectConfigFile
 *   The path to the [AvailProject] configuration file.
 * @property lastOpened
 *   The time in milliseconds since the Unix Epoch UTC when the associated
 *   project was last opened.
 */
data class KnownAvailProject constructor (
	var name: String,
	val id: String,
	var projectConfigFile: String,
	var lastOpened: Long
): JSONFriendly, Comparable<KnownAvailProject>
{
	/**
	 * The [absolute path][File.getAbsolutePath] of the [projectConfigFile].
	 */
	val configFilePath: String get() = File(projectConfigFile).absolutePath

	/**
	 * [lastOpened] as the local timezone string `MM/dd/yyyy HH:mm`.
	 */
	val lastOpenedTimestamp: String get()
	{
		val zoneId = ZonedDateTime.now().zone
		val instant = Instant.ofEpochMilli(lastOpened)
		val zdt =  ZonedDateTime
			.of(
				instant.atZone(zoneId).toLocalDate(),
				instant.atZone(zoneId).toLocalTime(),
				zoneId)
		val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
		return zdt.format(formatter)
	}

	/**
	 * @return
	 *   The [AvailProject] read from the [projectConfigFile] or `null` if not
	 *   found or if there is a problem with the configuration file.
	 */
	fun availProject (): AvailProject?
	{
		val configFile = File(projectConfigFile)
		if (!configFile.exists() || !configFile.isFile)
		{
			return null
		}
		val projectPath = configFile.absolutePath.removeSuffix(configFile.name)
			.removeSuffix(File.separator)
		return try
		{
			AvailProject.from(
				projectPath,
				jsonObject(configFile.readText(Charsets.UTF_8)))
		}
		catch (e: Throwable)
		{
			e.printStackTrace()
			null
		}
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(KnownAvailProject::id.name) { write(id) }
			at(KnownAvailProject::name.name) { write(name) }
			at(KnownAvailProject::projectConfigFile.name) {
				write(projectConfigFile)
			}
			at(KnownAvailProject::lastOpened.name) { write(lastOpened) }
		}
	}

	override fun compareTo(other: KnownAvailProject): Int =
		when
		{
			lastOpened > other.lastOpened -> 1
			lastOpened < other.lastOpened -> -1
			else -> 0
		}

	override fun toString(): String = "$name: $projectConfigFile"

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is KnownAvailProject) return false

		if (id != other.id) return false

		return true
	}

	override fun hashCode(): Int
	{
		return id.hashCode()
	}

	companion object
	{
		/**
		 * Answer a [KnownAvailProject] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [KnownAvailProject] or `null` if malformed.
		 */
		fun from (obj: JSONObject): KnownAvailProject?
		{
			if (!obj.containsKey(KnownAvailProject::projectConfigFile.name)
				|| !obj.containsKey(KnownAvailProject::name.name)
				|| !obj.containsKey(KnownAvailProject::id.name)
				|| !obj.containsKey(KnownAvailProject::lastOpened.name))
			{
				return null
			}
			return try
			{
				KnownAvailProject(
					obj.getString(KnownAvailProject::name.name),
					obj.getString(KnownAvailProject::id.name),
					obj.getString(KnownAvailProject::projectConfigFile.name),
					obj.getNumber(KnownAvailProject::lastOpened.name).long)
			}
			catch (e: Throwable)
			{
				e.printStackTrace()
				null
			}
		}
	}
}
