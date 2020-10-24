/*
 * BinaryMessage.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.messages.binary.editor

import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Message
import java.nio.ByteBuffer

/**
 * A `BinaryMessage` represents a fully parsed [BinaryCommand].
 *
 * The structure of all `BinaryMessage`s is as follows:
 *
 *  1. [BinaryCommand.id] (4 bytes): Indicates which command is being used.
 *  2. Transaction Id (4 bytes): The id of the transaction between the client
 *     and the server.
 *  3. Payload (variable bytes): Command-specific data.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal abstract class BinaryMessage
{
	/**
	 * The identifier of the [message][BinaryMessage]. This identifier should
	 * appear in any responses to this message.
	 */
	open var commandId: Long = 0

	/** The encoded [BinaryCommand]. */
	abstract val command: BinaryCommand

	/** The [Message] that represents this [BinaryMessage].  */
	abstract val message: Message

	/**
	 * Answer a [ByteBuffer] of a pre-determined size that contains the properly
	 * ordered prefix content: [BinaryCommand.id], [commandId].
	 *
	 * @param payloadSize
	 *   The number of bytes, in addition to the prefix size, to
	 *   [allocate][ByteBuffer.allocate] for the answered `ByteBuffer`. This
	 *   represents the custom payload byte count.
	 * @return
	 *   The pre-populated `ByteBuffer`.
	 */
	protected fun buffer (payloadSize: Int): ByteBuffer =
		ByteBuffer.allocate(PREFIX_SIZE + payloadSize).apply {
			this.putInt(command.id).putLong(commandId)
		}

	/**
	 * Process this [binary message][BinaryMessage] on behalf of the specified
	 * [channel][AvailServerChannel].
	 *
	 * @param channel
	 *   The channel that is associated with this message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred.
	 */
	abstract fun processThen (
		channel: AvailServerChannel, continuation: () -> Unit = {})

	companion object
	{
		/**
		 * The minimum number of bytes in every [BinaryMessage] that represents
		 * the [command][BinaryCommand.id] and the [commandId].
		 */
		const val PREFIX_SIZE = 12
	}
}