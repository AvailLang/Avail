/*
 * FileManagerTestHelper.kt
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

package com.avail.server.test.utility

import com.avail.files.FileManager
import com.avail.test.AvailRuntimeTestHelper

/**
 * `FileManagerTestHelper` provides reusable test utilities for interacting
 * w/ a [FileManager] during tests.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class FileManagerTestHelper constructor(val testName: String)
{
	val testHelper = AvailRuntimeTestHelper(true)

	/** The [FileManager] used in this test. */
	val fileManager: FileManager by lazy {
		testHelper.fileManager.apply {
			associateRuntime(testHelper.runtime)
		}
	}

	/** Directory where all files will go/be. */
	val resourcesDir: String by lazy {
		AvailRuntimeTestHelper.testDirectory
			.resolve("test/resources")
			.toString()
	}

	/** File path for `created.txt` file. */
	val createdFilePath: String by lazy {
		"$resourcesDir/$testName/$createdFileName"
	}

	val createdFileName = "$testName-created.txt"

	/** Path for `sample.txt file.` */
	val sampleFilePath: String by lazy {
		"$resourcesDir/$testName/$sampleFileName"
	}

	val sampleFileName = "$testName-sample.txt"
}
