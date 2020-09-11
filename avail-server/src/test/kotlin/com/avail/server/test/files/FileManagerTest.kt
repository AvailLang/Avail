/*
 * FileManagerTest.kt
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

package com.avail.server.test.files

import com.avail.server.error.ServerErrorCode
import com.avail.server.io.files.FileManager
import com.avail.server.io.files.LocalFileManager
import com.avail.server.test.AvailRuntimeTestHelper
import com.avail.utility.Mutable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.RuntimeException
import java.util.UUID
import java.util.concurrent.Semaphore

/**
 * A `FileManagerTest` is tests the [FileManager].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileManagerTest
{
	/** Setup for the test.  */
	private val helper: AvailRuntimeTestHelper by lazy {
		AvailRuntimeTestHelper()
	}

	private val fileManager: FileManager by lazy {
		LocalFileManager(helper.runtime)
	}

	val fileManagerInMemory: FileManager by lazy {
		TestInMemoryFileManager(helper)
	}

	private val resourcesDir: String by lazy {
		"${System.getProperty("user.dir")}/src/test/resources"
	}

	companion object
	{
		const val sampleContents = "This is\n" +
			"some\n" +
			"\tsample\n" +
			"text!"
	}

	@Test
	@DisplayName("Open files successfully")
	internal fun openFilesTest()
	{
		val semaphore = Semaphore(0)
		val target = "$resourcesDir/sample.txt"
		val error : Mutable<RuntimeException?> = Mutable(null)
		val fileId : Mutable<UUID?> = Mutable(null)
		try
		{
			fileManager.readFile(target, { id, mime, raw ->
				fileId.value = id
				assertEquals("text/plain", mime)
				val contents = String(raw, Charsets.UTF_16BE)
				assertEquals(sampleContents, contents)
				semaphore.release()
			})
			{ code, e ->
				error.value =
					RuntimeException("Error Code: $code (${e?.message})", e)
				semaphore.release()
			}
		}
		catch (e: Throwable)
		{
			val q = 5;
		}
		semaphore.acquire()
		val e = error.value
		if (e != null) { throw e }
		val firstId = fileId.value
		assertNotNull(firstId)
		firstId!!
		fileId.value = null
		fileManager.readFile(target, {id, mime, raw ->
			fileId.value = id
			assertEquals("text/plain", mime)
			val contents = String(raw, Charsets.UTF_16BE)
			assertEquals(sampleContents, contents)
			semaphore.release()
		})
		{ code, e ->
			error.value =
				RuntimeException("Error Code: $code (${e?.message})", e)
			semaphore.release()
		}
		semaphore.acquire()
		assertEquals(fileId.value, firstId)
	}

	@Test
	@DisplayName("Open file missing file")
	internal fun openFileTestMissingFile()
	{
		val semaphore = Semaphore(0)
		val target = "$resourcesDir/no_such_file.txt"
		val fileId : Mutable<UUID?> = Mutable(null)
		fileManager.readFile(target, {id, mime, raw ->
			fileId.value = id
			assertEquals("text/plain", mime)
			val contents = String(raw, Charsets.UTF_16BE)
			assertEquals(sampleContents, contents)
			semaphore.release()
		})
		{ code, e ->
			assertEquals(ServerErrorCode.FILE_NOT_FOUND, code)
			assert(e is java.nio.file.NoSuchFileException)
			semaphore.release()
		}
		semaphore.acquire()
	}
}