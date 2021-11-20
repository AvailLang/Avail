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

package avail.anvil.io

import avail.anvil.AnvilServer
import avail.anvil.BadAcknowledgmentCodeException
import avail.anvil.BadMessageException
import avail.anvil.Conversation
import avail.anvil.Message
import avail.anvil.MessageOrigin
import avail.anvil.MessageOrigin.SERVER
import avail.anvil.MessageTag
import avail.anvil.MessageTag.DISCONNECT
import avail.anvil.NegotiateVersionMessage
import avail.anvil.io.AnvilServerChannel.ProtocolState.TERMINATED
import avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_NEGOTIATION
import avail.io.SimpleCompletionHandler
import avail.utility.IO
import avail.utility.evaluation.Combinator.recurse
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.Deque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Level.FINER
import java.util.logging.Level.FINEST
import java.util.logging.Level.INFO
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
	/** The [AnvilServer]. */
	private val server get () = adapter.server

	/** The [logger][Logger]. */
	private val logger get () = server.logger

	/** `true` if the channel is open, `false` otherwise. */
	private val isOpen get () = transport.isOpen

	/** The channel identifier. */
	val channelId = nextChannelId.getAndIncrement()

	/**
	 * Log the specified entry.
	 *
	 * @param level
	 *   The logging level.
	 * @param entry
	 *   The log entry, lazily evaluated.
	 */
	private fun log (level: Level, entry: () -> String)
	{
		if (logger.isLoggable(level))
		{
			logger.log(level, "#$channelId: ${entry()}")
		}
	}

	/**
	 * Log the specified entry.
	 *
	 * @param level
	 *   The logging level.
	 * @param message
	 *   The message.
	 * @param entry
	 *   The log entry, lazily evaluated.
	 */
	internal fun log (
		level: Level,
		message: Message,
		entry: Message.() -> String)
	{
		if (logger.isLoggable(level))
		{
			logger.log(level, "#$channelId: @${message.id}: ${message.entry()}")
		}
	}

	init
	{
		log(INFO) { "channel created: ${transport.remoteAddress}" }
	}

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
		/** Protocol version must be negotiated. */
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

	/** The current [protocol&#32;state][ProtocolState]. */
	@Volatile
	internal var state: ProtocolState = VERSION_NEGOTIATION
		set (nextState)
		{
			log(FINEST) { "transition: $field -> $nextState" }
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
	 * @return
	 *   The requested conversation, or `null` if the conversation wasn't found.
	 */
	private fun lookupConversation (id: Long) = synchronized(conversations) {
		conversations[id]
	}

	/**
	 * A [ConversationStub] represents the message just received. It enables
	 * validation of correct conversation flow at the time when its reply is
	 * [enqueued][AnvilServerChannel.enqueueMessage].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 * Construct a new [ConversationStub].
	 *
	 * @param message
	 *   The latest message in the conversation.
	 */
	private inner class ConversationStub constructor (message: Message)
		: Conversation(message.id, message.allowedSuccessors)
	{
		override val isStub = true
	}

	/**
	 * Look up a [conversation][Conversation] by the specified message,
	 * replacing the conversation with a [stub][ConversationStub]. If no
	 * conversation is ongoing, then the specified message must be a
	 * [conversation&#32;starter][Message.canStartConversation]; in this case,
	 * apply the specified function to start an appropriate conversation.
	 *
	 * @param message
	 *   The message.
	 * @param start
	 *   How to start a new conversation if one is not already ongoing (and
	 *   `message` is a conversation starter).
	 * @return
	 *   The conversation to continue.
	 */
	internal fun continueConversation (
		message: Message,
		start: () -> Conversation
	) = synchronized(conversations) {
		val requested =
			if (message.endsConversation)
			{
				conversations.remove(message.id)
			}
			else
			{
				val old = conversations[message.id]
				assert(old != null || message.canStartConversation)
				conversations[message.id] = ConversationStub(message)
				old
			}
		assert(requested?.isStub != true)
		requested ?: run {
			assert(message.canStartConversation)
			start()
		}
	}

	/**
	 * Update the specified conversation.
	 *
	 * @param conversation
	 *   The conversation to start or continue. Must be a real conversation, not
	 *   a [stub][ConversationStub].
	 */
	private fun updateConversation (conversation: Conversation) =
		synchronized(conversations) {
			assert(!conversation.isStub)
			conversations[conversation.id] = conversation
		}

	/**
	 * End the specified conversation. Behaves idempotently.
	 *
	 * @param id
	 *   The identifier of the conversation to terminate.
	 */
	private fun endConversation (id: Long) =
		synchronized(conversations) {
			conversations.remove(id)
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
	 * @return `true` iff the message is deemed appropriate.
	 */
	@Throws(IllegalArgumentException::class)
	private fun checkMessage (
		message: Message,
		conversation: Conversation? = null
	): Boolean =
		when
		{
			!message.availableInVersion(negotiatedVersion) ->
			{
				// The originator attempted send a message not supported by the
				// negotiated protocol version.
				close(UnavailableInVersionCloseReason(
					message.origin, message, negotiatedVersion))
				false
			}
			!message.allowedOrigins.contains(message.origin) ->
			{
				// The originator attempted to send a message only appropriate
				// to the other side.
				close(BadOriginCloseReason(message.origin))
				false
			}
			!message.allowedStates.contains(state) ->
			{
				// The originator attempted to send a message during a
				// disallowed protocol state.
				close(BadStateCloseReason(message.origin, message, state))
				false
			}
			message.origin == SERVER
				&& message.endsConversation != (conversation === null) ->
			{
				// The server attempted to disrupt the flow of conversation,
				// either by ending a conversation prematurely or by continuing
				// a defunct conversation.
				close(BadEndFlowCloseReason(message))
				false
			}
			conversation !== null && message.id != conversation.id ->
			{
				// The originator attempted to continue the wrong
				// conversation.
				assert(message.origin == SERVER)
				close(WrongConversationCloseReason(message, conversation.id))
				false
			}
			else ->
			{
				val predecessor = lookupConversation(message.id)
				when
				{
					message.mustStartConversation && predecessor !== null ->
					{
						// The originator attempted to initiate or continue a
						// conversation with an inappropriate message.
						close(BadStartFlowCloseReason(message.origin, message))
						false
					}
					predecessor?.allowedSuccessors?.contains(message.tag)
						== false ->
					{
						// The originator attempted to continue a conversation
						// with an inappropriate message.
						close(UnexpectedReplyCloseReason(
							message.origin,
							message,
							predecessor.allowedSuccessors))
						false
					}
					else -> true
				}
			}
		}

	/**
	 * A [queue][Deque] of [messages][Message] awaiting transmission by the
	 * [adapter][SocketAdapter].
	 */
	@GuardedBy("itself")
	private val sendQueue = ArrayDeque<Message>()

	/**
	 * A [queue][Deque] of [pairs][Pair] of [messages][Message] and
	 * continuations, as supplied to calls of [enqueueMessage]. The maximum
	 * depth of this queue is proportional to the size of the I/O thread pool.
	 */
	@GuardedBy("sendQueue")
	private val senders = ArrayDeque<Pair<Message, ()->Unit>>()

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
		assert(conversation?.isStub != true)
		assert(checkMessage(message, conversation))
		val beginTransmitting: Boolean
		val invokeContinuationNow: Boolean
		if (conversation !== null)
		{
			// A conversation has been provided (and validated if checking is
			// enabled), so fearlessly install it into the conversation table.
			updateConversation(conversation)
		}
		else if (message.endsConversation)
		{
			// The conversation is defunct, so fearlessly uninstall it from the
			// conversation table.
			endConversation(message.id)
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
					// If there is no room available on the message queue, then
					// pause the client until room becomes available.
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
	private fun beginTransmission (message: Message)
	{
		log(FINER, message) { "sending: $this" }
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
						pair = senders.removeFirstOrNull()
						if (pair !== null)
						{
							sendQueue.addLast(pair.first)
						}
						nextMessage = sendQueue.firstOrNull()
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
	}

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

	/** The total number of bytes written. */
	private val totalBytesWritten = AtomicLong(0)

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
				val total = totalBytesWritten.addAndGet(value.toLong())
				log(FINEST) { "sent data: $value (of $total)"}
				if (writeBuffer.hasRemaining())
				{
					// Transmission did not completely exhaust the buffer, so we
					// need to continue transmission before writing new content
					// into the buffer.
					transport.write(writeBuffer, Unit, handler)
					return@SimpleCompletionHandler
				}
				// This will continue the encoding process if it's still
				// incomplete, otherwise it will invoke the success handler.
				writeBuffer.clear()
				try
				{
					afterWriting!!(writeBuffer)
				}
				catch (e: Throwable)
				{
					close(InternalErrorCloseReason(e))
				}
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
					assert(!writeBuffer.hasRemaining())
					afterWriting = proceed
					writeBuffer.flip()
					transport.write(writeBuffer, Unit, handler)
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
				transport.write(writeBuffer, Unit, handler)
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
	 *
	 * Force the initial buffer to appear empty, so that the first attempt to
	 * read will force a fetch from the socket.
	 */
	@GuardedBy("receiveQueue")
	private val readBuffer =
		ByteBuffer.allocateDirect(READ_BUFFER_SIZE).apply { flip() }

	/** The total number of bytes read. */
	private val totalBytesRead = AtomicLong(0)

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
				val total = totalBytesRead.addAndGet(value.toLong())
				log(FINEST) { "received data: $value (of $total)" }
				// More data is available, so flip the buffer and continue
				// decoding.
				readBuffer.flip()
				try
				{
					continueDecoding!!(readBuffer)
				}
				catch (e: Throwable)
				{
					close(InternalErrorCloseReason(e))
				}
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
					transport.read(readBuffer, Unit, handler)
				},
				failed = { e, _ ->
					close(
						when (e)
						{
							is BadMessageException ->
								BadMessageCloseReason(e.badTag)
							is BadAcknowledgmentCodeException ->
								BadAcknowledgmentCodeReason(e.badCode)
							else -> InternalErrorCloseReason(e)
						})
				}
			) { message, _ ->
				log(FINER, message) { "received: $this" }
				checkMessage(message) || return@decode
				if (message.closeAfterSending)
				{
					// This is the only chance that we have to detect an orderly
					// shutdown, lest the server's message processor race with
					// the channel reader.
					assert(message.tag == DISCONNECT)
					log(FINER, message) { "short-circuit dispatch: $message" }
					close(OrderlyClientCloseReason)
				}
				else
				{
					// Deliver the message to the channel. Don't keep processing
					// data from the buffer; wait until we receive another
					// request to read a message.
					receiveMessage(message)
				}
			}
		}
	}

	/**
	 * A [queue][Deque] of [messages][Message] awaiting processing by the
	 * [server][AnvilServer].
	 */
	@GuardedBy("itself")
	private val receiveQueue = ArrayDeque<Message>()

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
					// If there is no room available on the message queue, then
					// pause the transport until room becomes available.
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
					nextMessage = receiveQueue.firstOrNull()
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
				if (isOpen)
				{
					// Actually close the transport if it's still open, ignoring
					// any error conditions encountered along the way.
					IO.close(transport)
				}
				onClose?.invoke(reason, this)
				onClose = null
				log(INFO) { "closed channel: $reason" }
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

		/** The next channel identifier to allocate. */
		private val nextChannelId = AtomicLong(1)

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
