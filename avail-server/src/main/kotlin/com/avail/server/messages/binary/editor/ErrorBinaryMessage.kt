/*
 * ErrorBinaryMessage.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.messages.binary.editor

import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Message
import java.nio.charset.StandardCharsets
import com.avail.server.AvailServer.Companion.logger
import java.util.logging.Level

/**
 * `ErrorBinaryMessage` is a [BinaryMessage] indicating that an error has
 * occurred. This message may be sent by either the Avail server or the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property error
 *   The [ServerErrorCode] that identifies the type of error.
 * @property description
 *   Optional String that describes the error or null if not described.
 *
 * @constructor
 * Construct an [ErrorBinaryMessage].
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 * @param error
 *   The [ServerErrorCode] that identifies the type of error.
 * @param closeAfterSending
 *   `true` if the [channel][AvailServerChannel] should be
 *   [closed][AvailServerChannel.scheduleClose] after transmitting this message.
 * @param description
 *   Optional String that describes the error or null if not described.
 */
internal class ErrorBinaryMessage constructor(
	override var commandId: Long,
	val error: ServerErrorCode,
	private val closeAfterSending: Boolean = false,
	private val description: String? = null): BinaryMessage()
{
	init
	{
		logger.log(Level.WARNING, "$error ($commandId): $description")
	}

	override val command: BinaryCommand get() = BinaryCommand.ERROR

	override val message: Message get()
	{
		val descBytes =
			description?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
		// Allocate a buffer that can accommodate the payload; 4 bytes for the
		// error and n bytes for the description.
		val buffer = buffer(4 + descBytes.size)
		buffer.putInt(error.code).put(descBytes)
		buffer.flip()
		val content = ByteArray(buffer.limit())
		buffer.get(content)
		return Message(
			content, AvailServerChannel.ProtocolState.BINARY, closeAfterSending)
	}

	override fun processThen(
		channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.enqueueMessageThen(message, continuation)
	}
}