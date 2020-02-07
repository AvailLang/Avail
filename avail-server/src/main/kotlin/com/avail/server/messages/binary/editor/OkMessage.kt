/*
 * OkMessage.kt
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

import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Message
import java.nio.ByteBuffer

/**
 * `OkMessage` is a [BinaryMessage] used to affirm a request to the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an `OkMessage`.
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 */
internal class OkMessage constructor(
	override var commandId: Long): BinaryMessage()
{
	override val command = BinaryCommand.OK
	override val message: Message

	// TODO [RAA] get rid of this!
	override val stringStuff: String

	init
	{
		// Base size of payload is 12 bytes broken down as:
		//   BinaryCommand.id = 4
		//   commandId = 8
		val buffer = ByteBuffer.allocate(12)
		buffer.putInt(command.id)
		buffer.putLong(commandId)
		buffer.flip()
		val content = ByteArray(12)
		buffer.get(content)
		//TODO [RAA] this is the real message!
//		this.message = Message(content, AvailServerChannel.ProtocolState.BINARY)
		this.stringStuff = "$command $commandId"
		this.message = Message(stringStuff.toByteArray(), AvailServerChannel.ProtocolState.IO)
	}

	override fun processThen(channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.enqueueMessageThen(message, continuation)
	}
}