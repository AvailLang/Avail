/*
 * APITests.kt
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

package com.avail.server.test

import com.avail.server.AvailServer
import com.avail.server.io.files.FileManager
import com.avail.server.test.utility.FileManagerTestHelper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * `APITests` test the [AvailServer] API.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class APITests
{
	/** The [FileManagerTestHelper] used in these tests. */
	private val helper: FileManagerTestHelper by lazy {
		FileManagerTestHelper("api")
	}

	/** The [FileManager] used in this test. */
	private val fileManager get() = helper.fileManager

	/** Directory where all files will go/be. */
	private val resourcesDir get() = helper.resourcesDir

	/** File path for `created.txt` file. */
	private val createdFilePath get() = helper.createdFilePath

	/** Path for `sample.txt file.` */
	private val sampleFilePath get() = helper.sampleFilePath

	companion object
	{
		/** Edit text used in tests w/ [sampleFilePath]. */
		const val sampleReplace = " pi"

		/** Edited contents of [sampleFilePath]. */
		const val sampleContentsEdited = "This is pi" +
			"\tsample\n" +
			"text!"

		/** Contents added to [createdFilePath]. */
		const val createdFileContent = "Some more\n\ttext"
	}

	@BeforeAll
	fun initialize () = helper.initialize()

	@AfterAll
	fun cleanUp () = helper.cleanUp()

	// TODO create necessary mocks to run tests
}