/*
 * AvailStdLibVersion.kt
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

package avail.anvil.projects

/**
 * A version for an Avail Standard Library artifact.
 *
 * @author Richard Arriaga
 *
 * @property sdkVersion
 *   The [AvailVersion] of the Avail SDK (the org.availlang:avail artifact).
 * @property libVersion
 *   The [AvailVersion] of the Avail Library.
 */
data class AvailStdLibVersion constructor(
	val sdkVersion: AvailVersion,
	val libVersion: AvailVersion
): Comparable<AvailStdLibVersion>
{
	/**
	 * The stringified version.
	 */
	val version: String = "${sdkVersion.version}-${libVersion.version}"

	override fun toString (): String = version

	override fun compareTo(other: AvailStdLibVersion): Int
	{
		val sdkCompare = sdkVersion.compareTo(other.sdkVersion)
		if (sdkCompare != 0) return sdkCompare
		return libVersion.compareTo(other.libVersion)
	}

	companion object
	{
		/**
		 * Given a version String, such as `2.0.0-1.6.1` or
		 * `2.0.0.alpha01-1.6.2.beta01` or
		 * `2.0.0.rc1-1.6.2.rc01` to parse it into an [AvailStdLibVersion].
		 *
		 * @param version
		 *   The raw version string to transform in an [AvailStdLibVersion].
		 * @return
		 *   The [AvailStdLibVersion] or `null` if the `version` string is
		 *   malformed.
		 */
		fun versionOrNull (version: String): AvailStdLibVersion?
		{
			val split = version.split("-")
			if (split.size != 2)
			{
				return null
			}
			val sdkVersion = AvailVersion.versionOrNull(split[0]) ?: return null
			val libVersion = AvailVersion.versionOrNull(split[1]) ?: return null

			return AvailStdLibVersion(sdkVersion, libVersion)
		}
	}
}
