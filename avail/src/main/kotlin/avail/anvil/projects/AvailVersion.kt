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

package avail.anvil.projects

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
 *   The [Suffix] of the version.
 * @property suffixVersion
 *   The numeric version of the suffix; e.g. `alpha01`.
 * @property isValid
 *   `true` indicates it is a valid version; `false` otherwise. Only [INVALID]
 *   should be invalid.
 */
data class AvailVersion constructor (
	val major: Int,
	val minor: Int,
	val revision: Int,
	val suffix: Suffix,
	val suffixVersion: Int = 0,
	val isValid: Boolean = true
): Comparable<AvailVersion>
{
	/**
	 * Answer the integer version component as a string.
	 *
	 * @param ver
	 *   The integer version component to stringify.
	 * @return
	 *   The stringified version component.
	 */
	private fun verAsString (ver: Int): String =
		if (ver < 10) { "0$ver"} else ver.toString()

	/**
	 * The stringified version.
	 */
	val version: String =
		if (suffix == Suffix.PROD)
		{
			"$major.${verAsString(minor)}.${verAsString(revision)}"
		}
		else
		{
			"$major.${verAsString(minor)}.${verAsString(revision)}" +
				".$suffix${verAsString(suffixVersion)}"
		}


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
							else ->
							when
							{
								suffix > other.suffix -> 1
								suffix < other.suffix -> -1
								else ->
									when
									{
										suffixVersion > other.suffixVersion -> 1
										suffixVersion < other.suffixVersion -> -1
										else -> 0
									}
							}
						}
				}
		}

	/**
	 * The acceptable suffixes for an [AvailVersion]. The enum `name` is what
	 * should be used as the actual suffix.
	 */
	enum class Suffix constructor(val suffix: String)
	{
		/**
		 * A development version. This is used to represent a version that is
		 * undergoing active development. A `dev` [AvailVersion] should never
		 * be published.
		 */
		DEV("dev"),

		/** An Alpha release [AvailVersion]. */
		ALPHA("alpha"),

		/** A Beta release [AvailVersion]. */
		BETA("beta"),

		/** A production release candidate [AvailVersion]. */
		RC("rc"),

		/** A production release [AvailVersion] has no suffix. */
		PROD("");

		companion object
		{
			/**
			 * Answer the [Suffix] with the associated provided [Suffix.suffix].
			 *
			 * @param suffix
			 *   The [Suffix.suffix] to lookup.
			 * @return
			 *   The associated [Suffix] or `null` if invalid.
			 */
			operator fun get (suffix: String): Pair<Suffix, Int>? =
				values().firstOrNull { suffix.lowercase().startsWith(it.suffix) }
					?.let {
						val v = suffix.lowercase().split(it.suffix).last()
						Pair(it, v.toInt())
					}
		}
	}

	companion object
	{
		/**
		 * Given a version String, such as `2.0.0` or `2.0.0.alpha01` or
		 * `2.0.0.rc1` to parse it into an [AvailVersion].
		 *
		 * @param version
		 *   The raw version string to transform in an [AvailVersion].
		 * @return
		 *   The [AvailVersion] or `null` if the `version` string is malformed.
		 */
		fun versionOrNull (version: String): AvailVersion?
		{
			val split = version.lowercase().split(".")
			if (split.size !in 3..4)
			{
				return null
			}
			val suffix = if (split.size == 4)
			{
				Suffix[split[3]] ?: return null
			}
			else
			{
				Pair(Suffix.PROD, 0)
			}

			return AvailVersion(
				split[0].toInt(),
				split[1].toInt(),
				split[2].toInt(),
				suffix.first,
				suffix.second)
		}

		/**
		 * The canonical single not valid ([AvailVersion.isValid]) instance.
		 */
		val INVALID = AvailVersion(
			-1, -1, -1, Suffix.DEV, -1,false)
	}
}

