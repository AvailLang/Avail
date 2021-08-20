/*
 * ErrorBinaryMessage.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.messages.binary.editor

import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Message
import com.avail.server.AvailServer.Companion.logger
import java.util.logging.Level

/**
 * `InvalidBinaryMessage` is a [BinaryMessage] that wraps a received [Message]
 * with a bad [BinaryCommand.id].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The received invalid [BinaryCommand.id].
 * @property content
 *   The received [Message.content].
 *
 * @constructor
 * Construct an [InvalidBinaryMessage].
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 * @param id
 *   The received invalid [BinaryCommand.id].
 * @param content
 *   The received [Message.content].
 */
internal class InvalidBinaryMessage constructor(
		override var commandId: Long, val id: Int, val content: ByteArray)
	: BinaryMessage()
{
	init
	{
		val prefix =
			content.copyOfRange(0, minOf(content.size, PREFIX_SIZE))
		logger.log(
			Level.WARNING,
			"InvalidBinaryMessage ($commandId)")
	}

	override val command: BinaryCommand get() = BinaryCommand.INVALID

	override val message =
		Message(content, AvailServerChannel.ProtocolState.BINARY)

	override fun processThen(
		channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.enqueueMessageThen(
			ErrorBinaryMessage(
				commandId,
				ServerErrorCode.INVALID_REQUEST,
				true,
				"$id is not a valid command").message) {}
	}
}
