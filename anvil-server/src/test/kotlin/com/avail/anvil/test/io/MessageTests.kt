/*
 * MessageTests.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package avail.anvil.test.io

import avail.anvil.AcceptedVersionMessage
import avail.anvil.AcknowledgedMessage
import avail.anvil.AcknowledgmentCode.OK
import avail.anvil.DisconnectMessage
import avail.anvil.Message
import avail.anvil.MessageOrigin.CLIENT
import avail.anvil.MessageTag
import avail.anvil.NegotiateVersionMessage
import avail.anvil.RebuttedVersionsMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer
import java.util.stream.Stream

/**
 * Test features of [messages][Message].
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
class MessageTests
{
	/**
	 * Test [encode][Message.encode] and [decode][MessageTag.decode] for various
	 * [messages][Message].
	 */
	@ParameterizedTest(name = "round-trip: {0}")
	@MethodSource("messages")
	fun testMessageCodec (message: Message)
	{
		val buffer = ByteBuffer.allocate(BUFFER_SIZE)
		message.encode(buffer, writeMore = { _, _ -> fail() }) {
			// Do nothing.
		}
		buffer.flip()
		MessageTag.decode(
			buffer,
			readMore = { fail() },
			failed = { _, _ -> fail() }
		) { decoded, _ ->
			assertEquals(decoded, message)
		}
	}

	companion object
	{
		/**
		 * Make the buffers big enough to accommodate the tests.
		 */
		private const val BUFFER_SIZE: Int = 4096

		/**
		 * Produce [messages][Message] to test.
		 */
		@Suppress("unused")
		@JvmStatic
		fun messages (): Stream<Message> =
			mutableListOf<Message>().apply {
				add(DisconnectMessage(CLIENT, 0))
				add(NegotiateVersionMessage(CLIENT, 0, setOf(1, 2, 6)))
				add(AcceptedVersionMessage(CLIENT, 0, 1))
				add(RebuttedVersionsMessage(CLIENT, 0, setOf(4, 5)))
				add(AcknowledgedMessage(CLIENT, 0, OK))
			}.stream()
	}
}
