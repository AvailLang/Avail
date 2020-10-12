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

package com.avail.server.test

import com.avail.server.error.ServerErrorCode
import com.avail.files.EditRange
import com.avail.files.FileManager
import com.avail.files.ReplaceContents
import com.avail.files.SaveAction
import com.avail.server.test.utility.FileManagerTestHelper
import com.avail.server.test.utility.FileStateHolder
import com.avail.utility.Mutable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.util.UUID
import java.util.concurrent.Semaphore

/**
 * A `FileManagerTest` tests the [FileManager] with interleaved ordered tests
 * on two different files.
 *
 * // TODO fix the tests!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FileManagerTest
{
	/** The [FileManagerTestHelper] used in these tests. */
	private val helper: FileManagerTestHelper by lazy {
		FileManagerTestHelper("fmt")
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

		/** Initial contents of [sampleFilePath]. */
		const val sampleContents = "This is\n" +
			"some\n" +
			"\tsample\n" +
			"text!"

		private val sessionId = UUID.nameUUIDFromBytes("TEST".toByteArray())
	}

	/**
	 * Initialize the files for this test.
	 */
	@BeforeAll
	fun initialize ()
	{
		val created = File(createdFilePath)
		if (created.exists()) { created.delete() }
		val sampleFile = File(sampleFilePath)
		if (sampleFile.exists()) { sampleFile.delete() }
		sampleFile.createNewFile()
		sampleFile.writeText(sampleContents)
		helper.helper.availModuleRootResolver
		helper.helper.testModuleRootResolver
	}

	/**
	 * Clean up the files from this test.
	 */
	@AfterAll
	fun cleanUp ()
	{
		val created = File(createdFilePath)
		if (created.exists()) { created.delete() }
		val sampleFile = File(sampleFilePath)
		if (sampleFile.exists()) { sampleFile.delete() }
	}

	@Test
	@DisplayName("Open files successfully")
	@Order(1)
	internal fun openFilesTest()
	{
		val semaphore = Semaphore(0)
		val state = FileStateHolder()
		fileManager.readFile(
			helper.sampleFileName,
			helper.helper.testModuleRootResolver,
			{ id, mime, file ->
				state.updateFile(id, mime, file.rawContent)
				semaphore.release()
			})
			{ code, e ->
				state.updateError(
					code,
					RuntimeException(
						"ReadFile1: Error Code: $code (${e?.message})", e))
				semaphore.release()
			}
		semaphore.acquire()
		state.conditionallyThrowError()
		val firstId = state.fileId
		assertEquals("text/plain", state.fileMime)
		assertEquals(sampleContents, state.fileContents)
		state.reset()
		fileManager.readFile(
			helper.sampleFileName,
			helper.helper.testModuleRootResolver,
			{id, mime, file ->
				state.updateFile(id, mime, file.rawContent)
				semaphore.release()
			})
			{ code, e ->
				state.updateError(
					code,
					RuntimeException(
						"ReadFile2: Error Code: $code (${e?.message})", e))
				semaphore.release()
			}
		semaphore.acquire()
		assertEquals("text/plain", state.fileMime)
		assertEquals(sampleContents, state.fileContents)
		assertEquals(firstId, state.fileId)
	}

	@Test
	@DisplayName("Open file missing file")
	@Order(2)
	internal fun openFileTestMissingFile()
	{
//		val semaphore = Semaphore(0)
//		val target = "$resourcesDir/no_such_file.txt"
//		val fileFound = Mutable(false)
//		val errorCode : Mutable<ServerErrorCode?> = Mutable(null)
//		val error : Mutable<Throwable?> = Mutable(null)
//		fileManager.readFile(
//			target,
//			{_, _, _ ->
//				fileFound.value = true
//				semaphore.release()
//			})
//			{ code, e ->
//				errorCode.value = code
//				error.value = e
//				semaphore.release()
//			}
//		semaphore.acquire()
//		assertFalse(fileFound.value)
//		assertEquals(ServerErrorCode.FILE_NOT_FOUND, errorCode.value)
	}

//	@Test
//	@DisplayName("Edit file action test")
//	@Order(3)
//	fun executeEditAction ()
//	{
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.readFile(
//			sampleFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException("ReadFile1: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(sampleContents, state.fileContents)
//		state.reset()
//		val editMade = Mutable(false)
//		val edit = EditRange(
//			sampleReplace.toByteArray(StandardCharsets.UTF_16BE), 7, 13)
//		fileManager.executeAction(
//			firstId,
//			edit,
//			sessionId,
//			{
//				editMade.value = true
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"EditRange: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assert(editMade.value)
//		state.reset()
//
//		fileManager.readFile(sampleFilePath, {id, mime, raw ->
//			state.updateFile(id, mime, raw)
//			semaphore.release()
//		})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile2: Error Code: $code (${e?.message})", e))
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals(firstId, state.fileId)
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(sampleContentsEdited, state.fileContents)
//	}
//
//	@Test
//	@DisplayName("Replace file contents action test")
//	@Order(4)
//	fun replaceContentsActionTest ()
//	{
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.readFile(
//			sampleFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile1: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(sampleContentsEdited, state.fileContents)
//		assertEquals(firstId, state.fileId)
//		val editMade = Mutable(false)
//		val replace = ReplaceContents(
//			sampleReplace.toByteArray(StandardCharsets.UTF_16BE))
//		fileManager.executeAction(
//			firstId,
//			replace,
//			sessionId,
//			{
//				editMade.value = true
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReplaceAction: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assert(editMade.value)
//		state.reset()
//
//		fileManager.readFile(sampleFilePath, {id, mime, raw ->
//			state.updateFile(id, mime, raw)
//			semaphore.release()
//		})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile2: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals(firstId, state.fileId)
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(sampleReplace, state.fileContents)
//	}
//
//	@Test
//	@DisplayName("Create file success")
//	@Order(1)
//	internal fun createFileTest()
//	{
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.createFile(
//			createdFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//			{ code, e ->
//				state.updateError(
//					code,
//					RuntimeException(
//						"CreateFile: Error Code: $code (${e?.message})", e))
//				semaphore.release()
//			}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals(firstId, state.fileId)
//		assertEquals("text/plain", state.fileMime)
//		assert(state.fileContents!!.isEmpty())
//		state.reset()
//
//		val secondId = fileManager.readFile(createdFilePath, { id, mime, raw ->
//			state.updateFile(id, mime, raw)
//			semaphore.release()
//		})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile1: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals(firstId, state.fileId)
//		assertEquals(firstId, secondId)
//		assertEquals("text/plain", state.fileMime)
//		assert(state.fileContents!!.isEmpty())
//	}
//
//	@Test
//	@DisplayName("Ensure updates not saved without save test")
//	@Order(5)
//	fun notSavedChangesNotInFile ()
//	{
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.readFile(
//			sampleFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile1: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(sampleReplace, state.fileContents)
//		assertEquals(firstId, state.fileId)
//
//		fileManager.remove(firstId)
//		state.reset()
//
//		fileManager.readFile(sampleFilePath, {id, mime, raw ->
//			state.updateFile(id, mime, raw)
//			semaphore.release()
//		})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"ReadFile2: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertNotEquals(firstId, state.fileId)
//		assertEquals("text/plain",state.fileMime)
//		assertEquals(sampleContents, state.fileContents)
//	}
//
//	@Test
//	@DisplayName("Create file already exists")
//	@Order(2)
//	internal fun createFileTestExists()
//	{
//		assert(File(createdFilePath).exists())
//		val semaphore = Semaphore(0)
//		val fileCreated = Mutable(false)
//		val state = FileStateHolder()
//		val firstId = fileManager.createFile(
//			createdFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(code, e)
//			semaphore.release()
//		}
//		semaphore.acquire()
//		assertNull(firstId)
//		assertFalse(fileCreated.value)
//		assertEquals(ServerErrorCode.FILE_ALREADY_EXISTS, state.errorCode)
//		assert(state.error is FileAlreadyExistsException)
//	}
//
//	@Test
//	@DisplayName("Add text to new file test")
//	@Order(3)
//	fun addTextToNewFileTest ()
//	{
//		assert(File(createdFilePath).exists())
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.readFile(
//			createdFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//			{ code, e ->
//				state.updateError(
//					code,
//					RuntimeException(
//						"ReadFile1: Error Code: $code (${e?.message})", e))
//				semaphore.release()
//			}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals("text/plain", state.fileMime)
//		assert(state.fileContents!!.isEmpty())
//		assertEquals(firstId, state.fileId)
//		state.reset()
//		val editMade = Mutable(false)
//		val edit = EditRange(
//			createdFileContent.toByteArray(StandardCharsets.UTF_16BE), 0, 0)
//		fileManager.executeAction(
//			firstId,
//			edit,
//			sessionId,
//			{
//				editMade.value = true
//				semaphore.release()
//			})
//			{ code, e ->
//				state.updateError(
//					code,
//					RuntimeException(
//						"EditRange: Error Code: $code (${e?.message})", e))
//			}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assert(editMade.value)
//	}
//
//	@Test
//	@DisplayName("Save file test")
//	@Order(4)
//	internal fun saveFileTest()
//	{
//		assert(File(createdFilePath).exists())
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val firstId = fileManager.readFile(
//			createdFilePath,
//			{ id, mime, raw ->
//				state.updateFile(id, mime, raw)
//				semaphore.release()
//			})
//			{ code, e ->
//				state.updateError(
//					code,
//					RuntimeException(
//						"ReadFile1: Error Code: $code (${e?.message})", e))
//			}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assertEquals("text/plain", state.fileMime)
//		assertEquals(createdFileContent, state.fileContents)
//		assertEquals(firstId, state.fileId)
//		val saved = Mutable(false)
//		val saveAction = SaveAction(fileManager)
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"SaveAction1: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		fileManager.executeAction(
//			firstId,
//			saveAction,
//			sessionId,
//			{
//				saved.value = true
//				semaphore.release()
//			})
//		{ code, e ->
//			state.updateError(
//				code,
//				RuntimeException(
//					"SaveAction2: Error Code: $code (${e?.message})", e))
//			semaphore.release()
//		}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assert(saved.value)
//	}
//
//	@Test
//	@DisplayName("Delete file test")
//	@Order(5)
//	internal fun deleteFileTest()
//	{
//		assert(File(createdFilePath).exists())
//		val semaphore = Semaphore(0)
//		val state = FileStateHolder()
//		val deleted = Mutable(false)
//		fileManager.delete(
//			createdFilePath,
//			{
//				deleted.value = true
//				semaphore.release()
//			})
//			{ code, e ->
//				state.updateError(
//					code,
//					RuntimeException(
//						"DeleteAction: Error Code: $code (${e?.message})", e))
//				semaphore.release()
//			}
//		semaphore.acquire()
//		state.conditionallyThrowError()
//		assert(deleted.value)
//	}
}