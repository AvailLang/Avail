/*
 * AbstractTransportChannel.kt
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

import com.avail.server.AvailServer
import com.avail.server.AvailServer.Companion.receiveMessageThen
import com.avail.server.messages.Message
import com.avail.utility.Pair
import com.avail.utility.evaluation.Combinator.recurse
import java.util.*

/**
 * An `AbstractTransportChannel` represents an abstract connection between an
 * [AvailServer] and a [TransportAdapter]. It encapsulates the
 * implementation-specific channel required by the `TransportAdapter`, and
 * provides mechanisms for sending and receiving messages.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @param T
 *   The type of the enclosed channel.
 *
 * @constructor
 * Construct an [AbstractTransportChannel].
 *
 * @param closeAction
 *   The custom action that is to be called when the this channel is closed in
 *   order to support implementation-specific requirements after the closing of
 *   this channel.
 */
abstract class AbstractTransportChannel<T> constructor(
		closeAction: (DisconnectReason, AvailServerChannel) -> Unit)
	: AvailServerChannel(closeAction)
{
	/**
	 * The [Heartbeat] used by this [AbstractTransportChannel] to track
	 * connectivity.
	 */
	open val heartbeat: Heartbeat = NoHeartbeat

	/**
	 * A [queue][Deque] of [messages][Message] awaiting transmission by the
	 * [adapter][TransportAdapter].
	 */
	protected val sendQueue: Deque<Message> = LinkedList()

	/**
	 * Should the [channel][AbstractTransportChannel] close after emptying the
	 * [message queue][sendQueue]?
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected var shouldCloseAfterEmptyingSendQueue = false

	/**
	 * A [queue][Deque] of [pairs][Pair] of [messages][Message] and
	 * continuations, as supplied to calls of
	 * [enqueueMessageThen][enqueueMessageThen]. The maximum depth of this queue
	 * is proportional to the size of the I/O thread pool.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected val senders: Deque<Pair<Message, ()->Unit>> = LinkedList()

	/**
	 * A [queue][Deque] of [messages][Message] awaiting processing by the
	 * [server][AvailServer].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected val receiveQueue: Deque<Message> = LinkedList()

	/**
	 * The [TransportAdapter] that created this
	 * [channel][AbstractTransportChannel].
	 */
	internal abstract val adapter: TransportAdapter<T>

	/** The underlying channel. */
	abstract val transport: T

	/**
	 * Close the underlying [transport].
	 *
	 * Implementations should *only* attempt to close the `transport` only if it
	 * has not been closed already. Implementations should also cancel the
	 * [heartbeat].
	 */
	internal abstract fun closeTransport ()

	/**
	 * The [server][AvailServer] that created this
	 * [channel][AbstractTransportChannel].
	 */
	override val server get() = adapter.server

	/** The maximum send queue depth for the message queue. */
	protected abstract val maximumSendQueueDepth: Int

	override fun closeImmediately(reason: DisconnectReason)
	{
		channelCloseHandler.reason = reason
		closeTransport()
	}

	/**
	 * Begin transmission of the enqueued messages, starting with the specified
	 * [message][Message]. The argument must be the first message in the message
	 * queue.
	 *
	 * @param message
	 *   The first message in the message queue (still enqueued).
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected fun beginTransmission(message: Message)
	{
		val adapter = adapter
		adapter.sendUserData(
			this,
			message,
			{
				recurse { sendMore ->
					val nextMessage: Message?
					val pair: Pair<Message, ()->Unit>?
					synchronized(sendQueue) {
						// The message remains on the queue during transmission
						// (in order to simplify the execution model). Remove it
						// *after* transmission completes.
						val sentMessage = sendQueue.removeFirst()
						if (sentMessage.closeAfterSending)
						{
							adapter.sendClose(this, ServerMessageDisconnect)
							return@recurse
						}
						// Remove the oldest sender, but release the monitor
						// before evaluating it.
						pair = senders.pollFirst()
						if (pair != null)
						{
							sendQueue.addLast(pair.first())
						}
						nextMessage = sendQueue.peekFirst()
						assert(sendQueue.size <= maximumSendQueueDepth)
					}
					// Begin transmission of the next message.
					if (nextMessage != null)
					{
						adapter.sendUserData(this, nextMessage, sendMore, null)
					}
					else
					{
						synchronized(sendQueue) {
							// If a close is in progress, but awaiting the queue
							// to empty, then finish the close.
							if (shouldCloseAfterEmptyingSendQueue)
							{
								adapter.sendClose(this)
								return@recurse
							}
						}
					}
					// Proceed the paused client.
					pair?.second()?.invoke()
				}
			},
			null)
	}

	override fun enqueueMessageThen(
		message: Message,
		enqueueSucceeded: ()->Unit)
	{
		val beginTransmitting: Boolean
		val invokeContinuationNow: Boolean
		val maxQueueDepth = maximumSendQueueDepth
		synchronized(sendQueue) {
			val size = sendQueue.size
			// On the transition from empty to nonempty, begin consuming the
			// message queue.
			when
			{
				size == 0 ->
				{
					// If there is no room available on the message queue, then
					// pause the client until room becomes available.
					sendQueue.addLast(message)
					beginTransmitting = true
					invokeContinuationNow = true
				}
				size < maxQueueDepth ->
				{
					// If the queue is nonempty and there is room available on
					// the message queue, then simply enqueue the message.
					sendQueue.addLast(message)
					beginTransmitting = false
					invokeContinuationNow = true
				}
				else ->
				{
					assert(size == maxQueueDepth)
					senders.addLast(Pair(
						message, enqueueSucceeded))
					beginTransmitting = false
					invokeContinuationNow = false
				}
			}
		}
		// Initiate the asynchronous transmission "loop".
		if (beginTransmitting)
		{
			beginTransmission(message)
		}
		// Run the supplied continuation to proceed the execution of the client.
		if (invokeContinuationNow)
		{
			enqueueSucceeded()
		}
	}

	/** The maximum receive queue depth for the message queue. */
	protected abstract val maximumReceiveQueueDepth: Int

	/**
	 * Begin receipt of the enqueued messages, starting with the specified
	 * [message][Message]. The argument must be the first message in the message
	 * queue.
	 *
	 * @param message
	 *   The first message in the message queue (still enqueued).
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected fun beginReceiving(message: Message)
	{
		val maxQueueDepth = maximumReceiveQueueDepth
		receiveMessageThen(message, this) {
			recurse { receiveMore ->
				val nextMessage: Message?
				val resumeReading: Boolean
				synchronized(receiveQueue) {
					// The message remains on the queue during reception (in
					// order to simplify the execution model). Remove it *after*
					// reception completes.
					receiveQueue.removeFirst()
					nextMessage = receiveQueue.peekFirst()
					assert(receiveQueue.size < maximumReceiveQueueDepth)
					// If the queue transitioned from full to non-full, then
					// resume reading from the transport.
					resumeReading = receiveQueue.size == maxQueueDepth - 1
				}
				// Begin receipt of the next message.
				if (resumeReading)
				{
					adapter.readMessage(this)
				}
				// Process the next message.
				if (nextMessage != null)
				{
					receiveMessageThen(nextMessage, this, receiveMore)
				}
			}
		}
	}

	override fun receiveMessage(message: Message)
	{
		val beginReceiving: Boolean
		val resumeReading: Boolean
		val maxQueueDepth = maximumReceiveQueueDepth
		synchronized(receiveQueue) {
			val size = receiveQueue.size
			// On the transition from empty to nonempty, begin consuming the
			// message queue.
			when
			{
				size == 0 ->
				{
					// If there is no room available on the message queue, then
					// pause the transport until room becomes available.
					receiveQueue.addLast(message)
					beginReceiving = true
					resumeReading = true
				}
				size < maxQueueDepth ->
				{
					// If the queue is nonempty and there is room available on
					// the message queue, then simply enqueue the message.
					receiveQueue.addLast(message)
					beginReceiving = false
					resumeReading = true
				}
				else ->
				{
					assert(size == maxQueueDepth)
					beginReceiving = false
					resumeReading = false
				}
			}
		}
		// Resume reading messages from the transport.
		if (resumeReading)
		{
			adapter.readMessage(this)
		}
		// Initiate the asynchronous reception "loop".
		if (beginReceiving)
		{
			beginReceiving(message)
		}
	}
}
