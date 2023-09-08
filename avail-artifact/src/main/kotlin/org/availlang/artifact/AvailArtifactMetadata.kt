/*
 * AvailArtifactMetadata.kt
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

package org.availlang.artifact

import org.availlang.artifact.environment.location.Absolute
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.manifest.AvailArtifactManifest
import java.net.URI

/**
 * Provides information about the location and contents of an [AvailArtifact].
 *
 * @author Richard Arriaga
 *
 * @property location
 *   The [AvailLocation] of the [AvailArtifact].
 * @property manifest
 *   The [AvailArtifactManifest] that describes the content of the manifest.
 */
class AvailArtifactMetadata private constructor(
	val location: AvailLocation,
	val manifest: AvailArtifactManifest)
{
	/**
	 * The set of module root names inside the associated [AvailArtifact].
	 */
	val rootNames: Set<String> get() = manifest.roots.keys

	companion object
	{
		/**
		 * Answer an [AvailArtifactMetadata].
		 *
		 * @param artifact
		 *   The [AvailArtifact] to get the metadata for.
		 * @param
		 *   The [AvailLocation] of the [artifact].
		 * @return
		 *   The [AvailArtifactMetadata].
		 */
		fun from(
			artifact: AvailArtifact,
			location: AvailLocation
		): AvailArtifactMetadata =
			AvailArtifactMetadata(location, artifact.manifest)

		/**
		 * Answer an [AvailArtifactMetadata] for an [AvailArtifactJar].
		 *
		 * @param artifactJarUri
		 *   The [URI] pointing to the jar to get the metadata for.
		 * @param
		 *   The [AvailLocation] of the [AvailArtifactJar] or `null` if to be
		 *   derived from [artifactJarUri].
		 * @return
		 *   The [AvailArtifactMetadata].
		 */
		fun fromJar(
			artifactJarUri: URI,
			location: AvailLocation?
		): AvailArtifactMetadata =
			from(
				AvailArtifactJar(artifactJarUri),
				location ?: Absolute(artifactJarUri.path, Scheme.JAR, null))
	}
}
