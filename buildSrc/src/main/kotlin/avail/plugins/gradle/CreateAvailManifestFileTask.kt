/*
 * CreateDigestsFileTask.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.plugins.gradle

import org.availlang.artifact.AvailArtifactType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File
import java.security.MessageDigest
import org.availlang.artifact.DigestUtility
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailManifestRoot
import org.availlang.artifact.AvailArtifact

/**
 * Construct the [AvailArtifactManifest] file.
 *
 * This manifest file is stored inside all [AvailArtifact]s.
 *
 * @author Richard Arriaga
 */
abstract class CreateAvailManifestFileTask : DefaultTask()
{
	/**
	 * Create the manifest file and write it to the
	 */
	@TaskAction
	fun createManifest()
	{
		val manifestRoot = AvailManifestRoot(
			"avail",
			listOf(".avail"),
			listOf("!_"))
		AvailArtifactManifest.writeManifestFile(
			AvailArtifactType.LIBRARY,
			outputs.files.singleFile,
			mapOf("avail" to manifestRoot))
	}
}
