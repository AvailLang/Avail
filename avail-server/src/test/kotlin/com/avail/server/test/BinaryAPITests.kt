/*
 * APITests.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation
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

package com.avail.server.test

import com.avail.files.FileErrorCode
import com.avail.server.AvailServer
import com.avail.server.configuration.AvailServerConfiguration
import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.test.utility.AvailRuntimeTestHelper
import com.avail.server.test.utility.BinaryMessageBuilder
import com.avail.server.test.utility.TestAvailServerChannel
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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

/**
 * `BinaryAPITests` test the [AvailServer] [BinaryCommand] API.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BinaryAPITests
{
	/** The [AvailServer] used for these API tests. */
	private val server: AvailServer by lazy {
		val config = AvailServerConfiguration(
			AvailRuntimeTestHelper.helper.fileManager)
		AvailServer(
			config,
			AvailRuntimeTestHelper.helper.runtime,
			AvailRuntimeTestHelper.helper.fileManager)
	}

	/**
	 * Temp variable to store the sample test session-specific file cache id
	 */
	private var sampleTestFileId = -1

	/**
	 * Temp variable to store the sample test session-specific file cache id
	 */
	private var sampleOtherTestFileId = -1

	/**
	 * The [BinaryMessageBuilder] used to construct binary client messages.
	 */
	private val builder = BinaryMessageBuilder()

	/**
	 * The [TestAvailServerChannel] to act as the [AvailServerChannel].
	 */
	private val channel: TestAvailServerChannel by lazy {
		TestAvailServerChannel(server)
	}

	private fun open(path: String): Pair<Int, String>
	{
		val openMessage = builder.openFile(path)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			channel.reset()
			return Pair(fileId, String(raw, Charsets.UTF_16BE))
		}
		// Shouldn't get here
		throw RuntimeException("Received no messages!")
	}

	companion object
	{
		const val relativeTestModule = "/tests/Some Test.avail/Some Test.avail"
		const val relativeOtherTestModule = "/tests/Some Test.avail/Some Other Test.avail"
		const val randomTestModule = "/tests/Some Test.avail/Random Module.avail"
		const val someTestContent =
			"Module \"Some Test\"\n" +
				"Uses \"Avail\"\n" +
				"Extends \"Some Other Test\"\n" +
				"Body"

		const val someOtherTestContent =
			"Module \"Some Other Test\"\n" +
				"Uses \"Avail\"\n" +
				"Body"

		const val someTestContentReplace =
			"Module \"Some Test\"\n" +
				"Uses \"Avail\"\n" +
				"Extends \"Some Other Test\"\n" +
				"Names\n" +
				"\t\"_is not empty\"\n" +
				"Body\n" +
				"\n" +
				"Public method \"_is not empty\" is\n" +
				"[\n" +
				"\taTuple : tuple\n" +
				"|\n" +
				"\t|aTuple| > 0\n" +
				"] : boolean;"

		const val someEdit = "¬aTuple is empty"

		const val someReplaceEditContent =
			"Module \"Some Test\"\n" +
				"Uses \"Avail\"\n" +
				"Extends \"Some Other Test\"\n" +
				"Names\n" +
				"\t\"_is not empty\"\n" +
				"Body\n" +
				"\n" +
				"Public method \"_is not empty\" is\n" +
				"[\n" +
				"\taTuple : tuple\n" +
				"|\n" +
				"\t¬aTuple is empty\n" +
				"] : boolean;"
	}

	@BeforeAll
	@AfterAll
	fun reset ()
	{
		val sample = File(
			"${System.getProperty("user.dir")}/src/test/resources$relativeTestModule")
		if (sample.exists()) { sample.delete() }
		sample.createNewFile()
		sample.writeText(someTestContent)

		val randomModule = File(
			"${System.getProperty("user.dir")}/src/test/resources$randomTestModule")
		if (randomModule.exists()) { randomModule.delete() }
	}

	@Test
	@DisplayName("Open files API opens successfully")
	@Order(1)
	internal fun openFilesAPITest()
	{
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContent, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Fully replace the content of the file")
	@Order(2)
	internal fun replaceContentAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)
		channel.runContinuation = true
		val replaceMessage = builder.replaceFile(
			sampleTestFileId, someTestContentReplace)
		channel.receiveMessage(replaceMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}

		channel.reset()
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContentReplace, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Close the file, non-saved work will be lost")
	@Order(2)
	internal fun closeFileAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)
		channel.expectedMessageCount.set(1)
		val closeMessage = builder.closeFile(sampleTestFileId)
		channel.receiveMessage(closeMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}

		channel.reset()
		val openOtherMessage = builder.openFile(relativeOtherTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openOtherMessage)
		channel.semaphore.acquire()

		// Open other file to adjust the file id
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someOtherTestContent, fileContents)
			channel.sendQueue.clear()
			sampleOtherTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no other messages!")
		}

		channel.reset()
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			// No files opened in between so next free space id = 0
			assertNotEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContent, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt close file with bad file id")
	@Order(3)
	internal fun attemptCloseAlreadyClosedFileAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)
		val closeMessage = builder.closeFile(sampleTestFileId)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(closeMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}
		channel.reset()
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(closeMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			// No files opened in between so next free space id = 0
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContent, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Fully replace and save the content of the file")
	@Order(4)
	internal fun replaceSaveContentAPITest()
	{
		channel.runContinuation = true
		val closeOtherMessage = builder.closeFile(sampleOtherTestFileId)
		channel.receiveMessage(closeOtherMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}

		channel.reset()
		channel.runContinuation = true
		val replaceMessage = builder.replaceFile(
			sampleTestFileId, someTestContentReplace)
		channel.receiveMessage(replaceMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}

		channel.reset()
		channel.runContinuation = true
		val saveMessage = builder.saveFile(sampleTestFileId)
		channel.receiveMessage(saveMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		channel.reset()

		val closeMessage = builder.closeFile(sampleTestFileId)
		channel.receiveMessage(closeMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}

		channel.reset()
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContentReplace, fileContents)
			sampleTestFileId = fileId
			channel.expectedMessageCount.set(0)
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Create file API successfully")
	@Order(5)
	internal fun createFileAPITest()
	{
		val createFile = builder.createFile(randomTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(createFile)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assert(fileContents.isEmpty())
			val randomModule = File(
				"${System.getProperty("user.dir")}/src/test/resources$randomTestModule")
			assert(randomModule.exists())
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Edit a range in a file")
	@Order(6)
	internal fun editContentAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)
		channel.runContinuation = true
		val editFile = builder.editFile(
			sampleTestFileId, 141, 153, someEdit)
		channel.receiveMessage(editFile)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}

		channel.reset()
		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someReplaceEditContent, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Undo edit in a file")
	@Order(7)
	internal fun undoAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)

		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someReplaceEditContent, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}

		channel.reset()
		channel.runContinuation = true
		val undoEdit = builder.undoFile(sampleTestFileId)
		channel.receiveMessage(undoEdit)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}

		channel.reset()
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContentReplace, fileContents)
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Redo edit in a file")
	@Order(8)
	internal fun redoAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)

		val openMessage = builder.openFile(relativeTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(builder.transactionId.get() - 1, second.commandId)
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someTestContentReplace, fileContents)
			sampleTestFileId = fileId
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}

		channel.reset()
		channel.runContinuation = true
		val redoEdit = builder.redoFile(sampleTestFileId)
		channel.receiveMessage(redoEdit)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}

		channel.reset()
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			assertEquals(sampleTestFileId, fileId)
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assertEquals(someReplaceEditContent, fileContents)
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt create existing file API")
	@Order(9)
	internal fun attemptCreateExistingFileAPITest()
	{
		val createMessage = builder.createFile(randomTestModule)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(createMessage)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(FileErrorCode.FILE_ALREADY_EXISTS, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an Error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Delete file API successfully")
	@Order(10)
	internal fun deleteFileAPITest()
	{
		val openMessage = builder.openFile(randomTestModule)
		channel.expectedMessageCount.set(2)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.FILE_OPENED, first.binaryCommand)
			val fileId = first.buffer.int
			val fileSize = first.buffer.int
			val mimeSize = first.buffer.int
			val mimeBytes = ByteArray(mimeSize)
			first.buffer.get(mimeBytes)
			val mime = String(mimeBytes, StandardCharsets.UTF_8)
			assertEquals("text/avail", mime)
			assertEquals(2, channel.sendQueue.size)
			val second = channel.sendQueue[1]
			assertEquals(BinaryCommand.FILE_STREAM, second.binaryCommand)
			val secondFileId = second.buffer.int
			assertEquals(fileId, secondFileId)
			assertEquals(fileSize, second.buffer.remaining())
			val raw = ByteArray(fileSize)
			second.buffer.get(raw)
			val fileContents = String(raw, Charsets.UTF_16BE)
			assert(fileContents.isEmpty())
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages from open!")
		}

		channel.reset()
		val deleteFile = builder.deleteFile(randomTestModule)
		channel.receiveMessage(deleteFile)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}
		channel.reset()

		channel.expectedMessageCount.set(1)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(FileErrorCode.FILE_NOT_FOUND, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an Error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Open non-existent file API")
	@Order(11)
	internal fun openNonexistentFileAPITest()
	{
		val openMessage = builder.openFile("/tests/not here.avail")
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(openMessage)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(FileErrorCode.FILE_NOT_FOUND, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an Error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("delete non-existent file API")
	@Order(12)
	internal fun deleteNonexistentFileAPITest()
	{
		val deleteMessage = builder.deleteFile("/tests/not here.avail")
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(deleteMessage)
		channel.semaphore.acquire()

		if (channel.sendQueue.size > 0)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(FileErrorCode.FILE_NOT_FOUND, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an Error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt to edit a file with a bad file id")
	@Order(13)
	internal fun attemptEditFileBadFileIdAPITest()
	{
		val editFile = builder.editFile(
			155, 141, 153, someEdit)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(editFile)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt to replace a file's content with a bad file id")
	@Order(14)
	internal fun attemptReplaceContentFileBadFileIdAPITest()
	{
		val replace = builder.replaceFile(
			155, someTestContentReplace)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(replace)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt to save a file content with a bad file id")
	@Order(15)
	internal fun attemptSaveFileBadFileIdAPITest()
	{
		val saveMessage = builder.saveFile(155)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(saveMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt undo edit with a bad file id")
	@Order(16)
	internal fun attemptUndoBadFileIdAPITest()
	{
		val undoMessage = builder.undoFile(155)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(undoMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Attempt to redo edit with a bad file id")
	@Order(17)
	internal fun attemptRedoBadFileIdAPITest()
	{
		val redoMessage = builder.redoFile(155)
		channel.expectedMessageCount.set(1)
		channel.receiveMessage(redoMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(BinaryCommand.ERROR, first.binaryCommand)
			val errorCodeId = first.buffer.int
			val errorCode = ServerErrorCode.code(errorCodeId)
			assertEquals(ServerErrorCode.BAD_FILE_ID, errorCode)
		}
		else
		{
			throw RuntimeException("Expected an error message but got none")
		}
		channel.reset()
	}

	@Test
	@DisplayName("Complex undo/redo interactions")
	@Order(18)
	internal fun complexUndoRedoAPITest()
	{
		val (origId, old) = open(relativeTestModule)

		// Start w/ baseline expectation
		channel.runContinuation = true
		val replaceMessageInit = builder.replaceFile(
			origId, someTestContent)
		channel.receiveMessage(replaceMessageInit)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		channel.reset()

		val (_, check) = open(relativeTestModule)
		assertEquals(someTestContent, check)

		channel.runContinuation = true
		val saveMessage = builder.saveFile(origId)
		channel.receiveMessage(saveMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		channel.reset()

		// Close the file to ensure we start fresh w/ no traced actions
		channel.expectedMessageCount.set(1)
		val closeMessage = builder.closeFile(origId)
		channel.receiveMessage(closeMessage)
		channel.semaphore.acquire()
		if (channel.sendQueue.size == 1)
		{
			val first = channel.sendQueue[0]
			assertEquals(builder.transactionId.get() - 1, first.commandId)
			assertEquals(BinaryCommand.OK, first.binaryCommand)
		}
		else
		{
			throw RuntimeException("Expected an OK message but got none")
		}
		channel.reset()

		val (fileId, firstContent) = open(relativeTestModule)
		assertEquals(someTestContent, firstContent)

		// Start - Replace Message 1
		channel.runContinuation = true
		val replaceMessage1 = builder.replaceFile(
			fileId, someTestContentReplace)
		channel.receiveMessage(replaceMessage1)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, secondContent) = open(relativeTestModule)
		assertEquals(someTestContentReplace, secondContent)

		// Edit 2
		channel.runContinuation = true
		val editFile = builder.editFile(
			fileId, 141, 153, someEdit)
		channel.receiveMessage(editFile)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 2
		channel.reset()

		val (_, thirdContent) = open(relativeTestModule)
		assertEquals(someReplaceEditContent, thirdContent)

		// Undo 1
		channel.runContinuation = true
		val undoEdit = builder.undoFile(fileId)
		channel.receiveMessage(undoEdit)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, fourthContent) = open(relativeTestModule)
		assertEquals(secondContent, fourthContent)

		// Replace Message 2
		channel.runContinuation = true
		val replaceMessage2 = builder.replaceFile(
			origId, relativeOtherTestModule)
		channel.receiveMessage(replaceMessage2)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 2
		channel.reset()

		val (_, fifthContent) = open(relativeTestModule)
		assertEquals(relativeOtherTestModule, fifthContent)

		// Replace Message 3
		channel.runContinuation = true
		val replaceMessage3 = builder.replaceFile(
			origId, randomTestModule)
		channel.receiveMessage(replaceMessage3)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 3
		channel.reset()

		val (_, sixthContent) = open(relativeTestModule)
		assertEquals(randomTestModule, sixthContent)

		// Undo 2
		channel.runContinuation = true
		channel.receiveMessage(builder.undoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 2
		channel.reset()

		val (_, seventhContent) = open(relativeTestModule)
		assertEquals(fifthContent, seventhContent)

		// Replace Message 4
		channel.runContinuation = true
		val replaceMessage4 = builder.replaceFile(
			origId, relativeTestModule)
		channel.receiveMessage(replaceMessage4)
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 3
		channel.reset()

		val (_, eighthContent) = open(relativeTestModule)
		assertEquals(relativeTestModule, eighthContent)

		// Redo 1
		channel.runContinuation = true
		channel.receiveMessage(builder.redoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 3
		channel.reset()

		val (_, eighthContentB) = open(relativeTestModule)
		assertEquals(eighthContent, eighthContentB)

		// Undo 3
		channel.runContinuation = true
		channel.receiveMessage(builder.undoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 2
		channel.reset()

		val (_, ninth) = open(relativeTestModule)
		assertEquals(fifthContent, ninth)


		// Undo 4
		channel.runContinuation = true
		channel.receiveMessage(builder.undoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, tenth) = open(relativeTestModule)
		assertEquals(secondContent, tenth)

		// Undo 5
		channel.runContinuation = true
		channel.receiveMessage(builder.undoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 0
		channel.reset()

		val (_, eleventh) = open(relativeTestModule)
		assertEquals(firstContent, eleventh)

		// Undo 6
		channel.runContinuation = true
		channel.receiveMessage(builder.undoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 0
		channel.reset()

		val (_, twelfth) = open(relativeTestModule)
		assertEquals(firstContent, twelfth)

		// Redo 4
		channel.runContinuation = true
		channel.receiveMessage(builder.redoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, thirteenth) = open(relativeTestModule)
		assertEquals(tenth, thirteenth)

		// Redo 5
		channel.runContinuation = true
		channel.receiveMessage(builder.redoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, fourteenth) = open(relativeTestModule)
		assertEquals(ninth, fourteenth)

		// Redo 6
		channel.runContinuation = true
		channel.receiveMessage(builder.redoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, fifteenth) = open(relativeTestModule)
		assertEquals(eighthContent, fifteenth)

		// Redo 7
		channel.runContinuation = true
		channel.receiveMessage(builder.redoFile(fileId))
		channel.semaphore.acquire()
		if (channel.sendQueue.size > 0)
		{
			throw RuntimeException(
				"Expected no message but got ${channel.sendQueue[0]}")
		}
		// Traced Action Stack - 1
		channel.reset()

		val (_, sixteenth) = open(relativeTestModule)
		assertEquals(eighthContent, sixteenth)
	}
}