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
import com.avail.server.configuration.AvailServerConfiguration
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
		val config = AvailServerConfiguration()
		AvailServer(config, AvailRuntimeTestHelper.helper.runtime)
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
			channel.expectedMessageCount.set(0)
			channel.sendQueue.clear()
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
			channel.expectedMessageCount.set(0)
			channel.sendQueue.clear()
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
			channel.expectedMessageCount.set(0)
			channel.sendQueue.clear()
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
	@Order(3)
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
			channel.sendQueue.clear()
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
	@Order(4)
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
			channel.expectedMessageCount.set(0)
			channel.sendQueue.clear()
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
	@Order(5)
	internal fun editContentAPITest()
	{
		// Make sure we have a valid file id
		assertNotEquals(-1, sampleTestFileId)
		channel.sendQueue.clear()
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
			channel.expectedMessageCount.set(0)
			channel.sendQueue.clear()
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}
	}
}