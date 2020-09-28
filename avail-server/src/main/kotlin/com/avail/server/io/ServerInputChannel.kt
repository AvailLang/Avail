/*
 * ServerInputChannel.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.io.TextInputChannel
import com.avail.server.messages.Message
import com.avail.utility.cast
import java.io.IOException
import java.nio.BufferOverflowException
import java.nio.CharBuffer
import java.nio.channels.CompletionHandler
import java.util.ArrayDeque
import java.util.Deque
import javax.annotation.concurrent.GuardedBy
import kotlin.math.min

/**
 * A `ServerInputChannel` adapts an [AvailServerChannel] for use as a standard
 * input channel.
 *
 * @property channel
 *   The underlying [server&#32;channel][AvailServerChannel].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [ServerInputChannel].
 *
 * @param channel
 *   The [server&#32;channel][ServerInputChannel] to adapt as a standard input
 *   channel.
 */
class ServerInputChannel constructor(
	private val channel: AvailServerChannel) : TextInputChannel
{
	/**
	 * The [Throwable] that, when not `null` the execution of [read] can no
	 * longer proceed, and thus should fail.
	 */
	@GuardedBy("this")
	private var error: Throwable? = null
		private set(value)
		{
			if (field === null && value !== null)
			{
				field = value
				while (waiters.isNotEmpty())
				{
					val waiter = waiters.removeFirst()
					waiter.failed(value)
				}
			}
		}

	/**
	 * The [queue][Deque] of [messages][Message] awaiting delivery. It is
	 * invariant that there are either pending messages or pending [waiters].
	 * Whenever there are no messages, the [position] must be `0`.
	 */
	private val messages = ArrayDeque<Message>()

	/**
	 * The position of the next unread character within the
	 * [head][Deque.peekFirst] [message][Message] of the [queue][messages].
	 */
	private var position = 0

	/**
	 * The [queue][Deque] of [waiters][Waiter]. It is invariant that there are
	 * either pending waiters or pending [messages].
	 */
	private val waiters = ArrayDeque<Waiter>()

	/**
	 * The [buffer][CharBuffer] of data read since the last call to
	 * [mark][mark].
	 */
	private var markBuffer: CharBuffer? = null

	override fun isOpen() = channel.isOpen

	/**
	 * A `Waiter` represents a client that is awaiting I/O completion.
	 *
	 * @property buffer
	 *   The [buffer][CharBuffer] provided for receipt of inbound data.
	 * @property attachment
	 *   The attachment, if any.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [Waiter].
	 *
	 * @param buffer
	 *   The [buffer][CharBuffer] provided for receipt of inbound data.
	 * @param attachment
	 *   The attachment, of `null` if none.
	 * @param handler
	 *   The [completion&#32;handler][CompletionHandler] provided for
	 *   notification of data availability.
	 */
	private class Waiter constructor(
		val buffer: CharBuffer,
		val attachment: Any?,
		handler: CompletionHandler<Int, *>)
	{
		/**
		 * The [completion&#32;handler][CompletionHandler] provided for
		 * notification of data availability.
		 */
		val handler: CompletionHandler<Int, Any> = handler.cast()

		/** The number of bytes read. */
		var bytesRead = 0

		/**
		 * Invoke the [handler][CompletionHandler]'s
		 * [success][CompletionHandler.completed] entry point.
		 */
		fun completed()
		{
			handler.completed(bytesRead, attachment)
		}

		/**
		 * Invoke the [handler][CompletionHandler]'s
		 * [failed][CompletionHandler.failed] entry point.
		 *
		 * @param exception
		 *   The cause of the failure.
		 */
		fun failed(exception: Throwable)
		{
			handler.failed(exception, attachment)
		}
	}

	@Synchronized
	override fun mark(readAhead: Int)
	{
		markBuffer = CharBuffer.allocate(readAhead)
	}

	@Throws(IOException::class)
	override fun reset()
	{
		val ready: MutableList<Waiter>
		synchronized(this) {
			val buffer = markBuffer ?: throw IOException()
			// Discard the mark.
			markBuffer = null
			if (waiters.isEmpty())
			{
				val message = Message(
					buffer.toString().toByteArray(), channel.state)
				messages.addFirst(message)
				position = 0
				return
			}
			assert(messages.isEmpty())
			assert(position == 0)
			ready = mutableListOf()
			val content = buffer.toString()
			val contentLength = content.length
			while (position != contentLength && !waiters.isEmpty())
			{
				val waiter = waiters.removeFirst()
				val size = min(
					waiter.buffer.remaining(), contentLength - position)
				waiter.buffer.append(content, position, position + size)
				waiter.bytesRead = size
				ready.add(waiter)
				position += size
			}
			// If the message still contains data, then enqueue it. Preserve the
			// position.
			if (position != contentLength)
			{
				// assert waiters.isEmpty();
				val message = Message(content.toByteArray(), channel.state)
				messages.addFirst(message)
			}
			// Otherwise, reset the position (since the whole message was
			// consumed).
			else
			{
				position = 0
			}
		}
		for (waiter in ready)
		{
			waiter.completed()
		}
	}

	override fun <A> read(
		buffer: CharBuffer,
		attachment: A?,
		handler: CompletionHandler<Int, A>)
	{
		var totalSize = 0
		synchronized(this) {
			assert(waiters.isEmpty())
			if (error !== null)
			{
				handler.failed(error!!, attachment)
				return
			}
			if (messages.isEmpty())
			{
				val waiter = Waiter(buffer, attachment, handler)
				waiters.addLast(waiter)
				return
			}
			assert(error !== null)
			assert(waiters.isEmpty())
			// Otherwise, attempt to fill the buffer.
			while (buffer.hasRemaining() && !messages.isEmpty())
			{
				// TODO check the error queue
				val message = messages.peekFirst()
				val content = message.stringContent
				val contentLength = content.length
				val size = min(
					buffer.remaining(), contentLength - position)
				buffer.append(content, position, position + size)
				// If the channel has been marked, then duplicate message data
				// into the mark buffer.
				val mark = markBuffer
				if (mark !== null)
				{
					try
					{
						mark.append(content, position, position + size)
					}
					catch (e: BufferOverflowException)
					{
						// Invalidate the mark.
						markBuffer = null
					}

				}
				position += size
				if (position == contentLength)
				{
					messages.removeFirst()
					position = 0
				}
				totalSize += size
			}
			// If data has been marked, then truncate the current message if
			// necessary in order to keep positioning simple (by eliminating
			// redundant data).
			val mark = markBuffer
			if (mark !== null && position != 0 && !messages.isEmpty())
			{
				val message = messages.removeFirst()
				val newMessage = Message(
					message.stringContent.substring(position).toByteArray(),
					channel.state)
				messages.addFirst(newMessage)
				position = 0
			}
		}
		handler.completed(totalSize, attachment)
	}

	/**
	 * Receive a [message][Message] for delivery to a reader.
	 *
	 * @param message
	 *   A message.
	 * @param receiveNext
	 *   How to receive the next message from the underlying
	 *   [channel][AvailServerChannel].
	 */
	fun receiveMessageThen(message: Message, receiveNext: ()->Unit)
	{
		val ready: MutableList<Waiter>
		synchronized(this) {
			assert(error === null)
			if (waiters.isEmpty())
			{
				messages.addLast(message)
				return
			}
			// Otherwise, attempt to feed the message into any waiters.
			assert(messages.isEmpty())
			assert(position == 0)
			ready = mutableListOf()
			val content = message.stringContent
			val contentLength = content.length
			while (position != contentLength && !waiters.isEmpty())
			{
				val waiter = waiters.removeFirst()
				val size = min(
					waiter.buffer.remaining(), contentLength - position)
				waiter.buffer.append(content, position, position + size)
				waiter.bytesRead = size
				ready.add(waiter)
				// If the channel has been marked, then duplicate message data
				// into the mark buffer.
				val mark = markBuffer
				if (mark !== null)
				{
					try
					{
						mark.append(content, position, position + size)
					}
					catch (e: BufferOverflowException)
					{
						// Invalidate the mark.
						markBuffer = null
					}

				}
				position += size
			}
			if (position != contentLength)
			{
				val mark = markBuffer
				// If data has been marked, then truncate the current message if
				// necessary in order to keep positioning simple (by eliminating
				// redundant data). Reset the position.
				if (mark !== null)
				{
					val newMessage = Message(
						message.stringContent.substring(position).toByteArray(),
						channel.state)
					messages.addLast(newMessage)
					position = 0
				}
				// Otherwise, maintain the position and just add the message.
				else
				{
					messages.addLast(message)
				}
			}
			// Otherwise, reset the position.
			else
			{
				position = 0
			}
		}
		for (waiter in ready)
		{
			waiter.completed()
		}
		receiveNext()
	}

	/**
	 * Receive an [error][Throwable] indicating this [ServerInputChannel] is
	 * no longer viable.
	 *
	 * @param cause
	 *   The [Throwable] cause for killing this [ServerInputChannel].
	 */
	fun receiveError(cause: Throwable)
	{
		synchronized(this) {
			error = cause
		}
	}

	override fun close()
	{
		// The AvailServerChannel should be closed, not this.
		assert(false) { "This should not be closed directly!" }
	}
}
