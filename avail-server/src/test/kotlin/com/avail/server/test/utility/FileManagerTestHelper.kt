/*
 * FileManagerTestHelper.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.server.test.utility

import com.avail.server.io.files.FileManager
import com.avail.server.io.files.LocalFileManager
import org.junit.jupiter.api.AfterAll
import java.io.File

/**
 * `FileManagerTestHelper` provides reusable test utilities for interacting
 * w/ a [FileManager] during tests.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class FileManagerTestHelper constructor(val testName: String)
{
	/**
	 * The [AvailRuntimeTestHelper] that provides Avail state needed to conduct
	 * tests.
	 */
	val helper: AvailRuntimeTestHelper by lazy {
		AvailRuntimeTestHelper()
	}

	/** The [FileManager] used in this test. */
	val fileManager: FileManager by lazy {
		LocalFileManager(helper.runtime)
	}

	/** Directory where all files will go/be. */
	val resourcesDir: String by lazy {
		"${System.getProperty("user.dir")}/src/test/resources"
	}

	/** File path for `created.txt` file. */
	val createdFilePath: String by lazy {
		"$resourcesDir/$testName-created.txt"
	}

	/** Path for `sample.txt file.` */
	val sampleFilePath: String by lazy {
		"$resourcesDir/$testName-sample.txt"
	}

	/**
	 * Initialize the files for this test.
	 */
	fun initialize ()
	{
		val created = File(createdFilePath)
		if (created.exists()) { created.delete() }
		val sampleFile = File(sampleFilePath)
		if (sampleFile.exists()) { sampleFile.delete() }
		sampleFile.createNewFile()
		sampleFile.writeText(sampleContents)
	}

	/**
	 * Clean up the files from this test.
	 */
	fun cleanUp ()
	{
		val created = File(createdFilePath)
		if (created.exists()) { created.delete() }
		val sampleFile = File(sampleFilePath)
		if (sampleFile.exists()) { sampleFile.delete() }
	}

	companion object
	{
		/** Initial contents of [sampleFilePath]. */
		const val sampleContents = "This is\n" +
			"some\n" +
			"\tsample\n" +
			"text!"

		/** Contents added to [createdFilePath]. */
		const val createdFileContent = "Some more\n\ttext"
	}
}