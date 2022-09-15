/*
 * AvailBinary.kt
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

package avail.environment.projects

import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * The location of the Avail jar to use for the Avail runtime.
 *
 * @author Richard Arriaga
 *
 * @property version
 *   The [AvailVersion] of Avail to use.
 * @property path
 *   The path to the Avail jar file.
 */
data class AvailJar constructor(
	val version: AvailVersion,
	val path: String
): JSONFriendly, Comparable<AvailJar>
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(AvailJar::version.name) { write(version.version) }
			at(AvailJar::path.name) { write(path) }
		}
	}

	override fun compareTo(other: AvailJar): Int =
		version.compareTo(other.version)

	companion object
	{
		/**
		 * Answer a [AvailJar] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract data from.
		 * @return
		 *   The [AvailJar] or `null` if malformed.
		 */
		fun from (obj: JSONObject): AvailJar?
		{
			if (!obj.containsKey(AvailJar::version.name)
				|| !obj.containsKey(AvailJar::path.name))
			{
				return null
			}
			return try
			{
				val version = AvailVersion.versionOrNull(
					obj.getString(AvailJar::version.name)) ?: return null
				AvailJar(
					version,
					obj.getString(AvailJar::path.name))
			}
			catch (e: Throwable)
			{
				e.printStackTrace()
				null
			}
		}
	}
}
