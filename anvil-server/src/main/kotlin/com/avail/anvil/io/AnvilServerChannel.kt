/*
 * AnvilServerChannel.kt
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

package com.avail.anvil.io

import com.avail.anvil.AnvilServer
import com.avail.anvil.BadAcknowledgmentCodeException
import com.avail.anvil.BadMessageException
import com.avail.anvil.Conversation
import com.avail.anvil.Message
import com.avail.anvil.MessageOrigin
import com.avail.anvil.MessageOrigin.SERVER
import com.avail.anvil.MessageTag
import com.avail.anvil.NegotiateVersionMessage
import com.avail.anvil.io.AnvilServerChannel.ProtocolState.TERMINATED
import com.avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_NEGOTIATION
import com.avail.io.SimpleCompletionHandler
import com.avail.io.SimpleCompletionHandler.Dummy.Companion.dummy
import com.avail.utility.IO
import com.avail.utility.evaluation.Combinator.recurse
import org.jetbrains.annotations.Contract
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.Deque
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.concurrent.GuardedBy

/**
 * An `AnvilServerChannel` represents a connection between an [AnvilServer] and
 * a client. It provides mechanisms for sending and receiving
 * [messages][Message]. Though many messages may be queued for delivery or
 * receipt, the channel separately serializes the transmission and reception of
 * messages, such that only one of each may experience I/O at any given time.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property adapter
 *   The [SocketAdapter] that created this [channel][AnvilServerChannel].
 * @property transport
 *   The [transport][AsynchronousSocketChannel].
 * @property onClose
 *   The custom action that is to be called when the this channel is closed in
 *   order to support implementation-specific requirements after the closing of
 *   this channel.
 *
 * @constructor
 * Construct an [AnvilServerChannel].
 *
 * @param adapter
 *   The [SocketAdapter] that created this [channel][AnvilServerChannel].
 * @param transport
 *   The [transport][AsynchronousSocketChannel].
 * @param onClose
 *   The custom action that is to be called when the this channel is closed in
 *   order to support implementation-specific requirements after the closing of
 *   this channel.
 */
class AnvilServerChannel constructor (
	private val adapter: SocketAdapter,
	private val transport: AsynchronousSocketChannel,
	private var onClose: ((CloseReason, AnvilServerChannel)->Unit)? = null)
{
	/** `true` if the channel is open, `false` otherwise. */
	private val isOpen get () = transport.isOpen

	/** The channel identifier. */
	val id = UUID.randomUUID()!!

	init
	{
		logger.log(Level.INFO, "channel created ${transport.remoteAddress}")
	}

	/** The [AnvilServer]. */
	private val server get () = adapter.server

	/** The [logger][Logger]. */
	private val logger get () = server.logger

	/** The next conversation identifier to issue. */
	private val conversationId = AtomicLong(-1)

	/**
	 * The next conversation identifier, for [server][AnvilServer]
	 * [originated][MessageOrigin] [conversations][Conversation].
	 */
	val nextConversationId get () = conversationId.getAndDecrement()

	/**
	 * `ProtocolState` represents the communication state of a
	 * [server&#32;channel][AnvilServerChannel].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class ProtocolState
	{
		/** Protocol version must be negotiated.  */
		VERSION_NEGOTIATION
		{
			override val allowedSuccessorStates
				get () = setOf(VERSION_REBUTTED, READY, TERMINATED)
		},
		
		/** Protocol version has been rebutted. */
		VERSION_REBUTTED
		{
			override val allowedSuccessorStates
				get () = setOf(READY, TERMINATED)
		},

		/** Ready for general use. */
		READY
		{
			override val allowedSuccessorStates
				get () = setOf(TERMINATED)
		},

		/** Terminated. */
		TERMINATED
		{
			override val allowedSuccessorStates
				get () = emptySet<ProtocolState>()
		};

		/** The allowed successor [states][ProtocolState] of the receiver. */
		abstract val allowedSuccessorStates: Set<ProtocolState>
	}

	/** The current [protocol&#32;state][ProtocolState].  */
	@Volatile
	internal var state: ProtocolState = VERSION_NEGOTIATION
		set (nextState)
		{
			assert(field.allowedSuccessorStates.contains(nextState))
			field = nextState
		}

	/**
	 * The negotiated protocol version. Though it is initialized to an invalid
	 * version, [NegotiateVersionMessage] must specially treat this invalid
	 * version as though it were permitted.
	 */
	@Volatile
	internal var negotiatedVersion = AnvilServer.invalidProtocolVersion

	/** The ongoing conversations. */
	@GuardedBy("itself")
	private val conversations = mutableMapOf<Long, Conversation>()

	/**
	 * Look up a conversation by the specified identifier.
	 *
	 * @param id
	 *   The conversation identifier.
	 * @param conversation
	 *   How to install a conversation for a start, if no ongoing conversation
	 *   is found, or `null` if no conversation should be installed.
	 * @return
	 *   The requested conversation, or `null` if the conversation wasn't found.
	 */
	@Contract("_, !null -> !null")
	internal fun lookupConversation (
			id: Long,
			conversation: (()->Conversation)? = null) =
		synchronized(conversations) {
			conversation?.let { conversations.getOrPut(id, it) }
				?: conversations[id]
		}

	/**
	 * Update the specified conversation. May be used to start a new
	 * conversation also.
	 *
	 * @param conversation
	 *   The conversation to start or continue.
	 */
	private fun updateConversation (conversation: Conversation) =
		synchronized(conversations) {
			conversations[conversation.id] = conversation
		}

	/**
	 * End the specified conversation. Behaves idempotently.
	 *
	 * @param conversation
	 *   The conversation to terminate.
 	 */
	internal fun endConversation (conversation: Conversation) =
		synchronized(conversations) {
			conversations.remove(conversation.id)
		}

	/**
	 * Verify the appropriateness of a [message][Message]. If the message is
	 * deemed inappropriate, then [close] the receiver.
	 *
	 * @param message
	 *   The message to check.
	 * @param conversation
	 *   The associated [conversation][Conversation], or `null` if either
	 *   (1) the message ends a conversation or (2) the message has just
	 *   arrived on the socket.
	 * @param buildCloseReason
	 *   How to build a [CloseReason] if necessary.
	 * @return `true` iff the message is deemed appropriate.
	 */
	@Throws(IllegalArgumentException::class)
	private fun checkMessage (
		message: Message,
		conversation: Conversation? = null,
		buildCloseReason: (Throwable) -> CloseReason
	): Boolean
	{
		try
		{
			if (!message.availableInVersion(negotiatedVersion))
			{
				// The originator attempted send a message not supported by the
				// negotiated protocol version.
				throw IllegalArgumentException()
			}
			if (!message.allowedOrigins.contains(message.origin))
			{
				// The originator attempted to send a message only appropriate
				// to the other side.
				throw IllegalArgumentException()
			}
			if (!message.allowedStates.contains(state))
			{
				// The originator attempted to send a message during a
				// disallowed protocol state.
				throw IllegalArgumentException()
			}
			if (message.origin == SERVER
				&& message.endsConversation != (conversation === null))
			{
				// The server attempted to continue a defunct conversation.
				throw IllegalArgumentException()
			}
			if (conversation !== null && message.id != conversation.id)
			{
				// The originator attempted to continue the wrong
				// conversation.
				throw IllegalArgumentException()
			}
			val predecessor = lookupConversation(message.id)
			if (message.startsConversation != (predecessor === null))
			{
				// The originator attempted to initiate or continue a
				// conversation with an inappropriate message.
				throw IllegalArgumentException()
			}
			if (predecessor?.allowedSuccessors?.contains(message.tag) == false)
			{
				// The originator attempted to continue a conversation
				// with an inappropriate message.
				throw IllegalArgumentException()
			}
			return true
		}
		catch (e: IllegalArgumentException)
		{
			// The stack trace is filled in, so close the channel and fail the
			// check. It's up to the caller to throw another exception if so
			// desired.
			close(buildCloseReason(e))
			return false
		}
	}

	/**
	 * A [queue][Deque] of [messages][Message] awaiting transmission by the
	 * [adapter][SocketAdapter].
	 */
	@GuardedBy("itself")
	private val sendQueue: Deque<Message> = LinkedList()

	/**
	 * A [queue][Deque] of [pairs][Pair] of [messages][Message] and
	 * continuations, as supplied to calls of
	 * [enqueueMessageThen][enqueueMessage]. The maximum depth of this queue
	 * is proportional to the size of the I/O thread pool.
	 */
	@GuardedBy("sendQueue")
	private val senders: Deque<Pair<Message, ()->Unit>> = LinkedList()

	/**
	 * Enqueue the given [message][Message]. When the message has been enqueued,
	 * then execute the action.
	 *
	 * @param message
	 *   A message.
	 * @param conversation
	 *   How to continue the conversation.
	 * @param enqueueSucceeded
	 *   What to do when the message has been successfully enqueued.
	 */
	fun enqueueMessage (
		message: Message,
		conversation: Conversation? = null,
		enqueueSucceeded: ()->Unit)
	{
		// Only check the server-side message machinery if assertions are
		// enabled.
		assert(
			checkMessage(message, conversation) {
				InternalErrorCloseReason(it)
			})
		val beginTransmitting: Boolean
		val invokeContinuationNow: Boolean
		if (conversation !== null)
		{
			// A conversation has been provided (and validated if checking is
			// enabled), so fearlessly install it into the conversation table.
			updateConversation(conversation)
		}
		synchronized(sendQueue) {
			if (!isOpen)
			{
				// The channel has closed, which is unrecoverable, so don't
				// bother enqueuing the message.
				return
			}
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
				size < MAX_SEND_QUEUE_DEPTH ->
				{
					// If the queue is nonempty and there is room available on
					// the message queue, then simply enqueue the message.
					sendQueue.addLast(message)
					beginTransmitting = false
					invokeContinuationNow = true
				}
				else ->
				{
					assert(size == MAX_SEND_QUEUE_DEPTH)
					senders.addLast(message to enqueueSucceeded)
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

	/**
	 * Begin transmission of the enqueued messages, starting with the specified
	 * [message][Message]. The argument must be the first message in the message
	 * queue.
	 *
	 * @param message
	 *   The first message in the message queue (still enqueued).
	 */
	private fun beginTransmission (message: Message) =
		writeMessage(
			message,
			success = {
				recurse { sendMore ->
					val nextMessage: Message?
					val pair: Pair<Message, ()->Unit>?
					synchronized(sendQueue) {
						if (!isOpen)
						{
							// The channel has closed, which is unrecoverable,
							// so abandon transmission.
							return@recurse
						}
						// The message remains on the queue during transmission
						// (in order to simplify the execution model). Remove it
						// now that transmission has completed.
						sendQueue.removeFirst()
						// Remove the oldest sender, but release the monitor
						// before evaluating it.
						pair = senders.pollFirst()
						if (pair !== null)
						{
							sendQueue.addLast(pair.first)
						}
						nextMessage = sendQueue.peekFirst()
						assert(sendQueue.size <= MAX_SEND_QUEUE_DEPTH)
					}
					// Begin transmission of the next message.
					if (nextMessage !== null)
					{
						@Suppress("MoveLambdaOutsideParentheses")
						writeMessage(nextMessage, sendMore, {})
					}
					// Proceed the paused client.
					pair?.second?.invoke()
				}
			},
			failure = {})

	/**
	 * The write buffer. Every write goes through this buffer, using an
	 * asynchronous buffer chaining strategy.
	 *
	 * Concurrent access to the write buffer is forbidden by construction, by
	 * careful management of flow control. A channel can only transmit one
	 * [message][Message] at a time, and only the transmitter is permitted to
	 * access the buffer. The write buffer is never accessed inside a lock, but
	 * concurrent access is effectively ensured by the monitor on the
	 * [sendQueue], and flow control around its usage.
	 */
	@GuardedBy("sendQueue")
	private val writeBuffer = ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE)

	/**
	 * Send a [message][Message] over the
	 * [transport][AsynchronousSocketChannel].
	 *
	 * @param message
	 *   A message.
	 * @param success
	 *   What to do after sending the message.
	 * @param failure
	 *   What to do if sending the message fails.
	 */
	private fun writeMessage (
		message: Message,
		success: ()->Unit,
		failure: (Throwable)->Unit)
	{
		var afterWriting: ((ByteBuffer)->Unit)? = null
		SimpleCompletionHandler<Int>(
			{
				if (writeBuffer.hasRemaining())
				{
					// Transmission did not completely exhaust the buffer, so we
					// need to continue transmission before writing new content
					// into the buffer.
					transport.write(writeBuffer, dummy, handler)
					return@SimpleCompletionHandler
				}
				// This will continue the encoding process if it's still
				// incomplete, otherwise it will invoke the success handler.
				writeBuffer.clear()
				afterWriting!!(writeBuffer)
			},
			{
				close(SocketIOErrorReason(throwable))
				failure(throwable)
			}
		).guardedDo {
			// The write buffer should always begin empty, since the last
			// complete write should have consumed it.
			assert(writeBuffer.limit() == writeBuffer.capacity())
			message.encode(
				writeBuffer,
				writeMore = { _, proceed ->
					// We've filled the buffer, so write it to the transport.
					// Save the argument continuation, so that we can resume
					// encoding when the completion handler finishes.
					afterWriting = proceed
					writeBuffer.clear()
					transport.write(writeBuffer, dummy, handler)
				}
			) {
				// We completed encoding, but need to transmit the remainder of
				// the buffer. Arrange to signal success when transmission
				// completes.
				assert(writeBuffer.hasRemaining())
				afterWriting = {
					if (message.closeAfterSending)
					{
						// The message implies a disconnection, so close the
						// channel once it has been transmitted.
						close(OrderlyServerCloseReason)
					}
					success()
				}
				writeBuffer.flip()
				transport.write(writeBuffer, dummy, handler)
			}
		}
	}

	/**
	 * The read buffer. Every read goes through this buffer, using an
	 * asynchronous buffer chaining strategy.
	 *
	 * Concurrent access to the read buffer is forbidden by construction, by
	 * careful management of flow control. A channel can only receive one
	 * [message][Message] at a time, and only the receiver is permitted to
	 * access the buffer. The read buffer is never accessed inside a lock, but
	 * concurrent access is effectively ensured by the monitor on the
	 * [receiveQueue], and flow control around its usage.
	 */
	@GuardedBy("receiveQueue")
	private val readBuffer = run {
		val buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
		// Force the buffer to appear empty, so that the first attempt to read
		// will force a fetch from the socket.
		buffer.flip()
		buffer
	}

	/**
	 * Read a complete [message][Message] from the
	 * [transport][AsynchronousSocketChannel].
	 */
	internal fun readMessage ()
	{
		var continueDecoding: ((ByteBuffer)->Unit)? = null
		SimpleCompletionHandler<Int>(
			{
				if (remoteEndClosed(value))
				{
					// The remote end closed in a disorderly fashion.
					close(DisorderlyClientCloseReason)
					return@SimpleCompletionHandler
				}
				// More data is available, so flip the buffer and continue
				// decoding.
				readBuffer.flip()
				continueDecoding!!(readBuffer)
			},
			{
				close(SocketIOErrorReason(throwable))
			}
		).guardedDo {
			// Always begin decoding where we left off; there may already be
			// unread data in the buffer.
			MessageTag.decode(
				readBuffer,
				readMore = { proceed ->
					// We've read all of the data buffered so far, so now clear
					// the buffer and fetch more from the transport. Save the
					// argument continuation, so that we can resume decoding
					// when the completion handler finishes.
					assert(!readBuffer.hasRemaining())
					readBuffer.clear()
					continueDecoding = proceed
					transport.read(readBuffer, dummy, handler)
				},
				failed = { e, _ ->
					close(
						when (e)
						{
							is BadMessageException ->
								BadMessageCloseReason
							is BadAcknowledgmentCodeException ->
								BadMessageCloseReason
							else -> InternalErrorCloseReason(e)
						})
				}
			) { message, _ ->
				checkMessage(message) { BadProtocolCloseReason(it) }
					|| return@decode
				// Deliver the message to the channel. Don't keep processing
				// data from the buffer; wait until we receive another request
				// to read a message.
				receiveMessage(message)
			}
		}
	}

	/**
	 * A [queue][Deque] of [messages][Message] awaiting processing by the
	 * [server][AnvilServer].
	 */
	@GuardedBy("itself")
	private val receiveQueue: Deque<Message> = LinkedList()

	/**
	 * Receive an incoming [message][Message].
	 *
	 * @param message
	 *   A message.
	 */
	private fun receiveMessage (message: Message)
	{
		val beginReceiving: Boolean
		val resumeReading: Boolean
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
				size < MAX_RECV_QUEUE_DEPTH ->
				{
					// If the queue is nonempty and there is room available on
					// the message queue, then simply enqueue the message.
					receiveQueue.addLast(message)
					beginReceiving = false
					resumeReading = true
				}
				else ->
				{
					assert(size == MAX_RECV_QUEUE_DEPTH)
					beginReceiving = false
					resumeReading = false
				}
			}
		}
		// Resume reading messages from the transport.
		if (resumeReading)
		{
			readMessage()
		}
		// Initiate the asynchronous reception "loop".
		if (beginReceiving)
		{
			beginReceiving(message)
		}
	}

	/**
	 * Begin receipt of the enqueued messages, starting with the specified
	 * [message][Message]. The argument must be the first message in the message
	 * queue.
	 *
	 * @param message
	 *   The first message in the message queue (still enqueued).
	 */
	private fun beginReceiving (message: Message)
	{
		server.receiveMessage(message, this) {
			recurse { receiveMore ->
				val nextMessage: Message?
				val resumeReading: Boolean
				synchronized(receiveQueue) {
					// The message remains on the queue during reception (in
					// order to simplify the execution model). Remove it *after*
					// reception completes.
					receiveQueue.removeFirst()
					nextMessage = receiveQueue.peekFirst()
					assert(receiveQueue.size < MAX_RECV_QUEUE_DEPTH)
					// If the queue transitioned from full to non-full, then
					// resume reading from the transport.
					resumeReading =
						receiveQueue.size == MAX_RECV_QUEUE_DEPTH - 1
				}
				// Begin receipt of the next message.
				if (resumeReading)
				{
					readMessage()
				}
				// Process the next message.
				if (nextMessage !== null)
				{
					server.receiveMessage(nextMessage, this, receiveMore)
				}
			}
		}
	}

	/** Has a [close] already been handled? */
	private val closeHandled = AtomicBoolean(false)

	/**
	 * Shutdown the receiver immediately, applying the
	 * [close&#32;action][onClose] to the earliest provided
	 * [close&#32;reason][CloseReason]. Behaves idempotently.
	 *
	 * @param reason
	 *   The [CloseReason] for closing the channel.
	 */
	fun close (reason: CloseReason)
	{
		if (!closeHandled.getAndSet(true))
		{
			state = TERMINATED
			synchronized(sendQueue) {
				reason.log(logger, Level.INFO)
				if (isOpen)
				{
					// Actually close the transport if it's still open, ignoring
					// any error conditions encountered along the way.
					IO.close(transport)
				}
				onClose?.invoke(reason, this)
				onClose = null
			}
			server.deregisterChannel(this)
		}
	}

	companion object
	{
		/**
		 * The maximum number of [messages][Message] permitted on the
		 * [transmission&#32;queue][sendQueue].
		 */
		private const val MAX_SEND_QUEUE_DEPTH = 10

		/**
		 * The maximum number of [messages][Message] permitted on the
		 * [reception&#32;queue][sendQueue].
		 */
		private const val MAX_RECV_QUEUE_DEPTH = 10

		/** The size of the [read&#32;buffer][readBuffer]. */
		private const val READ_BUFFER_SIZE = 4096

		/** The size of the [write&#32;buffer][writeBuffer]. */
		private const val WRITE_BUFFER_SIZE = 4096

		/**
		 * Answer whether the remote end of some
		 * [transport][AsynchronousSocketChannel] closed based on the specified
		 * result.
		 *
		 * @param result
		 *   `-1` if the remote end closed.
		 * @return
		 *   `true` if the remote end closed, `false` otherwise.
		 */
		private fun remoteEndClosed (result: Int) = result == -1
	}
}
