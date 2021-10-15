/*
 * Message.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.server.messages

import avail.server.AvailServer
import avail.server.io.AvailServerChannel
import org.availlang.json.JSONWriter
import java.nio.charset.StandardCharsets

/**
 * An [AvailServer] sends and receives `Message`s. A `Message` received by the
 * server represents a command from the client, whereas a `Message` sent by the
 * server represents a response to a command.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property content
 *   The [content][ByteArray] of the [message][Message].
 * @property state
 *   The [AvailServerChannel.ProtocolState] the source/target
 *   [AvailServerChannel] was at when this [Message] was received/created.
 * @property closeAfterSending
 *   Should the [channel][AvailServerChannel] be
 *   [closed][AvailServerChannel.scheduleClose] after transmitting this
 *   [message][Message]?
 *
 * @constructor
 * Construct a new [Message].
 *
 * @param content
 *   The [content][ByteArray].
 * @param state
 *   The [AvailServerChannel.ProtocolState] the source/target
 *   [AvailServerChannel] was at when this `Message` was received/created.
 * @param closeAfterSending
 *   `true` if the [channel][AvailServerChannel] should be
 *   [closed][AvailServerChannel.scheduleClose] after transmitting this message.
 */
class Message constructor(
	val content: ByteArray,
	private val state: AvailServerChannel.ProtocolState,
	val closeAfterSending: Boolean = false)
{
	// TODO Process all message data as bytes and transform it just before
	//  actually using the data when being read.
	val stringContent: String get()
	{
		require(!state.generalBinary)
		{
			"Message.stringContent was called but message was received as " +
			"$state."
		}
		return String(content, StandardCharsets.UTF_8)
	}

	/**
	 * Construct a new [Message].
	 *
	 * @param writer
	 *   The [JSONWriter] that contains the message.
	 * @param state
	 *   The [AvailServerChannel.ProtocolState] the source/target
	 *   [AvailServerChannel] was at when this `Message` was received/created.
	 * @param closeAfterSending
	 *   `true` if the [channel][AvailServerChannel] should be
	 *   [closed][AvailServerChannel.scheduleClose] after transmitting this
	 *   message.
	 */
	constructor(
		writer: JSONWriter,
		state: AvailServerChannel.ProtocolState,
		closeAfterSending: Boolean = false
	): this(writer.toString().toByteArray(), state, closeAfterSending)

	companion object
	{
		/** The maximum allowed size of a frame. */
		const val MAX_SIZE = 1024000
	}
}
