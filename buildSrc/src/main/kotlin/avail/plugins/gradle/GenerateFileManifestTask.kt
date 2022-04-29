/*
 * GenerateFileManifestTask.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.tasks.*
import java.io.File
import java.security.MessageDigest

/**
 * Construct a single file that names all files matching a given pattern inside
 * a directory structure.  Each entry is the file name relative to the basePath,
 * followed by a linefeed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class GenerateFileManifestTask: DefaultTask()
{
	/**
	 * The base path String containing files to scan. This path is stripped from
	 * each file name prefix prior to invoking the [fileNameTransformer].
	 */
	@Input
	lateinit var basePath: String

	/**
	 * A transformer from the basePath-relative file name to a string to write
	 * to the output file.
	 */
	@Input
	var fileNameTransformer: String.() -> String = { this }

	/**
	 * The path to the output file, relative to outputs.files.singleFile.
	 */
	@Input
	var outputFilePath = ""

	@TaskAction
	fun createFileManifest() {
		val primitivesSourceTree = inputs.files
		val baseSourcePath = basePath + "/"
		val allFileNames = primitivesSourceTree.map {
			it.absolutePath
				.replace("\\\\", "/")
				.replaceFirst(baseSourcePath, "")
				.fileNameTransformer()
		}.sorted().joinToString("\n")
		val outBase = outputs.files.singleFile
		val outFile = outBase.resolve(outputFilePath)
		if (!outFile.exists() || outFile.readText() != allFileNames)
		{
			outFile.parentFile.mkdirs()
			outFile.writeText(allFileNames)
		}
	}
}
