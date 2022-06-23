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

package avail.plugin

import org.availlang.artifact.DigestUtility
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

/**
 * Construct a single file that summarizes message digests of all files of a
 * directory structure.  Each input file is read, digested with the specified
 * [digestAlgorithm] (default is SHA-256), and written to the digests file.
 * The entries are the file name relative to the basePath, a colon, the hex
 * representation of the digest of that file, and a linefeed.
 *
 * This digests file is used within an avail artifact, such as avail-stdlib.jar.
 *
 * @author Richard Arriaga
 */
abstract class CreateRootDigestsFileTask : DefaultTask()
{
	/**
	 * The path to the root to create digests for.
	 */
	@Input
	var rootBasePath: String = ""

	/**
	 * The digest algorithm to use to create the digests for each file.
	 */
	@Input
	var digestAlgorithm: String = "SHA-256"

	@TaskAction
	fun createDigestsFile()
	{
		outputs.files.singleFile.writeText(
			DigestUtility.createDigest(rootBasePath, digestAlgorithm))
	}
}
