/*
 * AbstractServerOutputChannel.kt
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

package com.avail.server.io

import com.avail.io.TextOutputChannel
import com.avail.server.messages.Message
import com.avail.utility.json.JSONWriter
import java.nio.CharBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler

/**
 * A `AbstractServerOutputChannel` adapts an [channel][AvailServerChannel] for
 * ordinary output.
 *
 * @property channel
 *   The underlying [server channel][AvailServerChannel].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [AbstractServerOutputChannel].
 *
 * @param channel
 *   The [server channel][AvailServerChannel] to adapt for general output.
 */
abstract class AbstractServerOutputChannel constructor(
	private val channel: AvailServerChannel) : TextOutputChannel
{
	override fun isOpen() = channel.isOpen

	/**
	 * The prefix for [message][Message] [content][Message.content] written to
	 * this output channel.
	 */
	protected abstract val channelTag: String

	/**
	 * Answer a [message][Message] that suitably represents this
	 * [channel][AbstractServerOutputChannel] and the specified
	 * [content][String].
	 *
	 * @param channel
	 *   The [AvailServerChannel] the message is for.
	 * @param data
	 *   The content of the message.
	 * @return
	 *   A message.
	 */
	private fun newMessage(channel: AvailServerChannel, data: String): Message
	{
		val writer = JSONWriter()
		writer.writeObject {
			writer.write("tag")
			writer.write(channelTag)
			writer.write("content")
			writer.write(data)
		}
		return Message(writer.toString().toByteArray(), channel.state)
	}

	override fun <A> write(
		buffer: CharBuffer,
		attachment: A?,
		handler: CompletionHandler<Int, A>)
	{
		if (!isOpen)
		{
			try
			{
				throw ClosedChannelException()
			}
			catch (e: ClosedChannelException)
			{
				handler.failed(e, attachment)
				return
			}

		}
		val limit = buffer.limit()
		val message = newMessage(channel, buffer.toString())
		channel.enqueueMessageThen(message) {
			handler.completed(limit, attachment)
		}
	}

	override fun <A> write(
		data: String,
		attachment: A?,
		handler: CompletionHandler<Int, A>)
	{
		if (!isOpen)
		{
			try
			{
				throw ClosedChannelException()
			}
			catch (e: ClosedChannelException)
			{
				handler.failed(e, attachment)
				return
			}

		}
		val message = newMessage(channel, data)
		channel.enqueueMessageThen(message) {
			handler.completed(data.length, attachment)
		}
	}

	override fun close()
	{
		// The AvailServerChannel should be closed, not this.
		assert(false) { "This should not be closed directly!" }
	}
}
