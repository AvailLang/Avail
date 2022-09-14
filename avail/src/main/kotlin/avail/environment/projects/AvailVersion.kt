/*
 * AvailVersion.kt
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

/**
 * A version for Avail.
 *
 * @author Richard Arriaga
 *
 * @property major
 *   The numeric major revision of the version.
 * @property minor
 *   The numeric minor revision of the version.
 * @property revision
 *   The numeric revision of the version.
 * @property suffix
 *   The suffix of the version string, e.g. "-SNAPSHOT". All suffixes must start
 *   with "-"
 * @property isValid
 *   `true` indicates it is a valid version; `false` otherwise. Only [INVALID]
 *   should be invalid.
 */
data class AvailVersion constructor (
	val major: Int,
	val minor: Int,
	val revision: Int,
	val suffix: String = "",
	val isValid: Boolean = true
) : Comparable<AvailVersion>
{
	private fun verAsString (ver: Int): String =
		if (ver < 10) { "0$ver"} else ver.toString()

	val version: String =
		"$major.${verAsString(minor)}.${verAsString(revision)}$suffix"

	override fun toString (): String = version
	override fun compareTo(other: AvailVersion): Int =
		when
		{
			major > other.major -> 1
			major < other.major -> -1
			else -> // major versions are equal
				when
				{

					minor > other.minor -> 1
					minor < other.minor -> -1
					else -> // minor versions are equal
						when
						{
							revision > other.revision -> 1
							revision < other.revision -> -1
							else ->  suffix.compareTo(other.suffix)
						}
				}
		}

	companion object
	{
		/**
		 * Given a version String, such as `2.0.0` or `2.0.0-SNAPSHOT` or
		 * `2.0.0-SNAPSHOT.rc1` to parse it into an [AvailVersion].
		 *
		 * @param version
		 *   The raw version string to transform in an [AvailVersion].
		 * @return
		 *   The [AvailVersion] or `null` if the `version` string is malformed.
		 */
		fun versionOrNull (version: String): AvailVersion?
		{
			val split = version.uppercase().split("-")
			val value = split[0]
			val suffix = if (split.size > 1) "-${split[1]}" else ""
			val parts = value.split(".").map { it.toInt() }

			if (parts.size != 3)
			{
				return null
			}
			return AvailVersion(parts[0], parts[1], parts[2], suffix)
		}

		/**
		 * The canonical single not valid ([AvailVersion.isValid]) instance.
		 */
		val INVALID = AvailVersion(
			-1, -1, -1, isValid = false)
	}
}

