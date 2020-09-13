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
		/**
		 * The statically available
		 */
		const val relativeTestModule = "/tests/Some Test.avail/Some Test.avail"
		const val someTestContent =
			"Module \"Some Test\"\n" +
			"Uses \"Avail\"\n" +
			"Body"
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
		}
		else
		{
			// Shouldn't get here
			throw RuntimeException("Received no messages!")
		}

	}
}