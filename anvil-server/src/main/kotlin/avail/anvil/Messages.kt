/*
 * Messages.kt
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

package avail.anvil

import avail.anvil.AcknowledgmentCode.OK
import avail.anvil.MessageOrigin.CLIENT
import avail.anvil.MessageOrigin.SERVER
import avail.anvil.MessageTag.ACCEPTED_VERSION
import avail.anvil.MessageTag.ACKNOWLEDGED
import avail.anvil.MessageTag.DISCONNECT
import avail.anvil.MessageTag.IDENTIFY_CHANNEL
import avail.anvil.MessageTag.NEGOTIATE_VERSION
import avail.anvil.MessageTag.REBUTTED_VERSIONS
import avail.anvil.io.AnvilServerChannel
import avail.anvil.io.AnvilServerChannel.ProtocolState
import avail.anvil.io.AnvilServerChannel.ProtocolState.READY
import avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_NEGOTIATION
import avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_REBUTTED
import avail.anvil.io.DoneReading
import avail.anvil.io.DoneWriting
import avail.anvil.io.FailedReading
import avail.anvil.io.ReadMore
import avail.anvil.io.WriteMore
import avail.anvil.io.decodeList
import avail.anvil.io.encode
import avail.anvil.io.unvlqInt
import avail.anvil.io.unvlqLong
import avail.anvil.io.unzigzagLong
import avail.anvil.io.vlq
import avail.anvil.io.zigzag
import java.nio.ByteBuffer

////////////////////////////////////////////////////////////////////////////////
//                              Message origins.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [MessageOrigin] indicates the origin of a message, either client or server.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class MessageOrigin
{
	/** Originated by client. */
	CLIENT,

	/** Originated by server. */
	SERVER
}

////////////////////////////////////////////////////////////////////////////////
//                            Unifying interfaces.                            //
////////////////////////////////////////////////////////////////////////////////

/**
 * The common interface between [MessageTag] and [Message].
 */
private interface BasicMessage
{
	/**
	 * Are [tagged][MessageTag] [messages][Message] available in the specified
	 * protocol version?
	 */
	fun availableInVersion (version: Int): Boolean = version >= 1

	/**
	 * Do [tagged][MessageTag] [messages][Message] always start conversations?
	 */
	val mustStartConversation get () = false

	/**
	 * Can [tagged][MessageTag] [messages][Message] start conversations?
	 */
	val canStartConversation get () = mustStartConversation

	/**
	 * The allowed [origins][MessageOrigin] of [tagged][MessageTag]
	 * [messages][Message].
	 */
	val allowedOrigins: Set<MessageOrigin>

	/**
	 * The allowed [protocol&#32;states][ProtocolState] for transmission or
	 * receipt of [tagged][MessageTag] [messages][Message].
	 */
	val allowedStates: Set<ProtocolState> get () = setOf(READY)

	/**
	 * The [tags][MessageTag] of the allowed successor [messages][Message]. If
	 * empty, then receipt of the message ends the conversation.
	 */
	val allowedSuccessors: Set<MessageTag> get () = emptySet()

	/**
	 * Do [tagged][MessageTag] [messages][Message] end an associated
	 * [conversation][Conversation]?
	 */
	val endsConversation get () = allowedSuccessors.isEmpty()

	/**
	 * Should the [channel][AnvilServerChannel] be
	 * [closed][AnvilServerChannel.close] after transmitting a tagged
	 * [message][Message]?
	 */
	val closeAfterSending get () = false
}

////////////////////////////////////////////////////////////////////////////////
//                               Message tags.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [MessageTag] uniquely denotes the kind of a [message][Message]. When a
 * message tag is serialized, its ordinal is serialized directly (as a
 * variable-width integer), so _do not change the order of the variants_.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct the enumeration value, checking that the supplied ordinal is
 * accurate.
 *
 * @param ordinalCheck
 *   The value to check against the Kotlin-supplied ordinal.
 */
enum class MessageTag constructor (ordinalCheck: Int) : BasicMessage
{
	/** Perform an orderly shutdown of the connection. */
	DISCONNECT(0)
	{
		override val mustStartConversation get() = true
		override val allowedOrigins = setOf(CLIENT, SERVER)
		override val allowedStates =
			setOf(READY, VERSION_NEGOTIATION, VERSION_REBUTTED)
		override val closeAfterSending get() = true

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is DisconnectMessage)
			done(bytes)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			done(DisconnectMessage(CLIENT, id), bytes)
		}
	},

	/** Negotiate a protocol version. */
	NEGOTIATE_VERSION(1)
	{
		override fun availableInVersion (version: Int) = true
		override val canStartConversation get() = true
		override val allowedOrigins = setOf(CLIENT)
		override val allowedStates =
			setOf(VERSION_NEGOTIATION, VERSION_REBUTTED)
		override val allowedSuccessors get () =
			setOf(ACCEPTED_VERSION, REBUTTED_VERSIONS)

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is NegotiateVersionMessage)
			message.versions.toList().encode(
				bytes,
				encodeOne = Int::vlq,
				writeMore = writeMore,
				done = done)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			decodeList<Int>(
				bytes,
				decodeOne = { bytes1, readMore1, _, again ->
					unvlqInt(bytes1, readMore = readMore1, done = again)
				},
				readMore = readMore,
				failed = failed
			) { versions, bytes1 ->
				done(NegotiateVersionMessage(
					CLIENT, id, versions.toSet()), bytes1)
			}
		}
	},

	/** Accept an offered protocol version. */
	ACCEPTED_VERSION(2)
	{
		override val allowedOrigins = setOf(SERVER)

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is AcceptedVersionMessage)
			message.version.vlq(bytes, writeMore, done)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			unvlqInt(bytes, readMore = readMore) { version, bytes1 ->
				done(AcceptedVersionMessage(CLIENT, id, version), bytes1)
			}
		}
	},

	/**
	 * Reject all offered protocol versions, rebutting with supported versions.
	 */
	REBUTTED_VERSIONS(3)
	{
		override fun availableInVersion (version: Int) = true
		override val allowedOrigins = setOf(SERVER)
		override val allowedStates = setOf(VERSION_REBUTTED)
		override val allowedSuccessors get () = setOf(NEGOTIATE_VERSION)

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is RebuttedVersionsMessage)
			message.supportedVersions.toList().encode(
				bytes,
				encodeOne = Int::vlq,
				writeMore = writeMore,
				done = done)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			decodeList<Int>(
				bytes,
				decodeOne = { bytes1, readMore1, _, again ->
					unvlqInt(bytes1, readMore = readMore1, done = again)
				},
				readMore = readMore,
				failed = failed
			) { supported, bytes1 ->
				done(RebuttedVersionsMessage(
					CLIENT, id, supported.toSet()), bytes1)
			}
		}
	},

	/** Acknowledged. */
	ACKNOWLEDGED(4)
	{
		override val allowedOrigins get () = setOf(CLIENT, SERVER)

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is AcknowledgedMessage)
			message.code.encode(bytes, writeMore, done)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			AcknowledgmentCode.decode(bytes, readMore, failed) { code, bytes1 ->
				done(AcknowledgedMessage(CLIENT, id, code), bytes1)
			}
		}
	},

	/** Identify a newly connected channel. */
	IDENTIFY_CHANNEL(5)
	{
		override fun availableInVersion (version: Int) = true
		override val mustStartConversation get() = true
		override val allowedOrigins = setOf(SERVER)
		override val allowedStates =
			setOf(VERSION_NEGOTIATION, VERSION_REBUTTED, READY)

		override fun encodeContent(
			message: Message,
			bytes: ByteBuffer,
			writeMore: WriteMore,
			done: DoneWriting)
		{
			require(message is IdentifyChannelMessage)
			message.channelId.vlq(bytes, writeMore, done)
		}

		override fun decodeContent(
			id: Long,
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>)
		{
			unvlqLong(bytes, readMore = readMore) { channelId, bytes1 ->
				done(IdentifyChannelMessage(CLIENT, id, channelId), bytes1)
			}
		}
	};

	init
	{
		assert(ordinalCheck == ordinal)
	}

	/**
	 * Encode a tagged [message][Message] onto the specified [ByteBuffer].
	 * Encode the [tag][MessageTag] and then [conversation&#32;id][Message.id],
	 * then call [encodeContent] to finish encoding message-specific content.
	 *
	 * @param message
	 *   The message to encode.
	 * @param bytes
	 *   The target buffer.
	 * @param writeMore
	 *   How to write more if the target buffer is fills up prematurely.
	 * @param done
	 *   What to do when the message is fully encoded.
	 */
	fun encode (
		message: Message,
		bytes: ByteBuffer,
		writeMore: WriteMore,
		done: DoneWriting)
	{
		assert(message.tag == this)
		ordinal.vlq(bytes, writeMore) { bytes1 ->
			message.id.zigzag(bytes1, writeMore) { bytes2 ->
				encodeContent(message, bytes2, writeMore, done)
			}
		}
	}

	/**
	 * Encode the message-specific content of the specified [message][Message],
	 * having already encoded its [tag][MessageTag] and
	 * [conversation&#32;id][Message.id].
	 *
	 * @param message
	 *   The message to finish encoding.
	 * @param bytes
	 *   The target buffer.
	 * @param writeMore
	 *   How to write more if the target buffer is fills up prematurely.
	 * @param done
	 *   What to do when the message is fully encoded.
	 */
	protected abstract fun encodeContent (
		message: Message,
		bytes: ByteBuffer,
		writeMore: WriteMore,
		done: DoneWriting)

	/**
	 * Decode the message-specific content for a [message][Message] tagged by
	 * the receiver.
	 *
	 * @param id
	 *   The conversation id.
	 * @param bytes
	 *   The source buffer.
	 * @param readMore
	 *   How to read more if the source buffer exhausts prematurely.
	 * @param failed
	 *   What to do if decoding fails.
	 * @param done
	 *   What to do when the message is fully decoded.
	 */
	protected abstract fun decodeContent (
		id: Long,
		bytes: ByteBuffer,
		readMore: ReadMore,
		failed: FailedReading,
		done: DoneReading<Message>)

	companion object
	{
		/**
		 * Decode a tagged [message][Message] from the specified [ByteBuffer].
		 * Decode the [tag][MessageTag] and the
		 * [conversation&#32;id][Message.id], then call [decodeContent] to
		 * finish decoding message-specific content.
		 *
		 * @param bytes
		 *   The source buffer.
		 * @param readMore
		 *   How to read more if the source buffer exhausts prematurely.
		 * @param failed
		 *   What to do if decoding fails.
		 * @param done
		 *   What to do when the message is fully decoded.
		 */
		fun decode(
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<Message>
		) = unvlqInt(bytes, readMore = readMore) { ordinal, bytes1 ->
			val tag = values().getOrNull(ordinal)
				?: return@unvlqInt failed(badMessage(ordinal), bytes1)
			unzigzagLong(bytes1, readMore = readMore) { id, bytes2 ->
				tag.decodeContent(id, bytes2, readMore, failed, done)
			}
		}

		/**
		 * Generate a [BadMessageException] with an attached stack trace, to
		 * assist debugging.
		 *
		 * @param badTag
		 *   The bogus tag ordinal.
		 */
		private fun badMessage (badTag: Int) =
			try
			{
				throw BadMessageException(badTag)
			}
			catch (e: BadMessageException)
			{
				e
			}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                          Message abstractions.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * An [AnvilServer] sends and receives messages. In general, a [Message]
 * received by the server represents a command from the client, whereas a
 * [Message] sent by the server represents a response to a command or a
 * notification of an event.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property tag
 *   The message tag.
 * @property origin
 *   The origin of the message.
 * @property id
 *   The conversation id of the message: negative for server-originated
 *   conversations, positive for client-originated conversations.
 *
 * @constructor
 * Construct a new [Message].
 *
 * @param tag
 *   The message tag.
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message: negative for server-originated
 *   conversations, positive for client-originated conversations.
 */
sealed class Message constructor (
	val tag: MessageTag,
	open val origin: MessageOrigin,
	open val id: Long
) : BasicMessage by tag
{
	/**
	 * Visit the visitor upon the receiver.
	 *
	 * @param visitor
	 *   The visitor.
	 * @param after
	 *   What to do after processing the message.
	 */
	abstract fun visit (visitor: MessageVisitor, after: AfterMessage)

	/**
	 * Encode the receiver onto the specified [ByteBuffer].
	 *
	 * @param bytes
	 *   The target buffer.
	 * @param writeMore
	 *   How to write more if the target buffer is fills up prematurely.
	 * @param done
	 *   What to do when the message is fully encoded.
	 */
	fun encode (bytes: ByteBuffer, writeMore: WriteMore, done: DoneWriting) =
		tag.encode(this, bytes, writeMore, done)
}

////////////////////////////////////////////////////////////////////////////////
//                            Disconnect messages.                            //
////////////////////////////////////////////////////////////////////////////////

/**
 * Perform an orderly shutdown of the connection.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [DisconnectMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 */
data class DisconnectMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long
) : Message(DISCONNECT, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

////////////////////////////////////////////////////////////////////////////////
//                             Version messages.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * Negotiate a protocol version.
 *
 * @property versions
 *   The offered protocol versions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [NegotiateVersionMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 * @param versions
 *   The offered protocol versions.
 */
data class NegotiateVersionMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long,
	val versions: Set<Int>
) : Message(NEGOTIATE_VERSION, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

/**
 * Accept an offered protocol version.
 *
 * @property version
 *   The accepted protocol version.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [AcceptedVersionMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 * @param version
 *   The accepted protocol version.
 */
data class AcceptedVersionMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long,
	val version: Int
) : Message(ACCEPTED_VERSION, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

/**
 * Reject all offered versions, rebutting with supported versions.
 *
 * @property supportedVersions
 *   The protocol versions supported by the [AnvilServer].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [RebuttedVersionsMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 * @param supportedVersions
 *   The protocol versions supported by the [AnvilServer].
 */
data class RebuttedVersionsMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long,
	val supportedVersions: Set<Int>
) : Message(REBUTTED_VERSIONS, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

////////////////////////////////////////////////////////////////////////////////
//                              Acknowledgments.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * A simple acknowledgment code.
 *
 * @constructor
 * Construct the enumeration value, checking that the supplied ordinal is
 * accurate.
 *
 * @param ordinalCheck
 *   The value to check against the Kotlin-supplied ordinal.
 */
enum class AcknowledgmentCode constructor (ordinalCheck: Int)
{
	/** The request was accomplished without exception or fanfare. */
	OK(0);

	init
	{
		assert(ordinalCheck == ordinal)
	}

	/**
	 * Encode the receiver to the specified buffer.
	 *
	 * @param bytes
	 *   The target buffer.
	 * @param writeMore
	 *   How to write more if the target buffer is fills up prematurely.
	 * @param done
	 *   What to do when the code is fully encoded.
	 */
	fun encode (bytes: ByteBuffer, writeMore: WriteMore, done: DoneWriting)
	{
		ordinal.vlq(bytes, writeMore, done)
	}

	companion object
	{
		/**
		 * Decode an [AcknowledgmentCode] from the specified buffer.
		 *
		 * @param bytes
		 *   The source buffer.
		 * @param readMore
		 *   How to read more if the source buffer exhausts prematurely.
		 * @param failed
		 *   What to do if decoding fails.
		 * @param done
		 *   What to do when the code is fully decoded.
		 */
		fun decode(
			bytes: ByteBuffer,
			readMore: ReadMore,
			failed: FailedReading,
			done: DoneReading<AcknowledgmentCode>
		) = unvlqInt(bytes, readMore = readMore) { ordinal, bytes1 ->
			val code = values().getOrNull(ordinal)
				?: return@unvlqInt failed(badCode(ordinal), bytes1)
			done(code, bytes1)
		}

		/**
		 * Generate a [BadAcknowledgmentCodeException] with an attached stack
		 * trace, to assist debugging.
		 *
		 * @param badCode
		 *   The bogus tag ordinal.
		 */
		private fun badCode(badCode: Int) =
			try
			{
				throw BadAcknowledgmentCodeException(badCode)
			}
			catch (e: BadAcknowledgmentCodeException)
			{
				e
			}
	}
}

/**
 * Acknowledged.
 *
 * @property code
 *   The acknowledgment code.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [AcknowledgedMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 * @param code
 *   The acknowledgment code.
 */
data class AcknowledgedMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long,
	val code: AcknowledgmentCode = OK
) : Message(ACKNOWLEDGED, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

////////////////////////////////////////////////////////////////////////////////
//                          Channel identification.                           //
////////////////////////////////////////////////////////////////////////////////

/**
 * Identify a newly connected channel.
 *
 * @property channelId
 *   The channel identifier.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [AcknowledgedMessage].
 *
 * @param origin
 *   The origin of the message.
 * @param id
 *   The conversation id of the message.
 * @param channelId
 *   The channel identifier.
 */
data class IdentifyChannelMessage constructor (
	override val origin: MessageOrigin,
	override val id: Long,
	val channelId: Long
) : Message(IDENTIFY_CHANNEL, origin, id)
{
	override fun visit (visitor: MessageVisitor, after: AfterMessage) =
		visitor.visit(this, after)
}

////////////////////////////////////////////////////////////////////////////////
//                             Message visitors.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * What to do after processing a message.
 */
typealias AfterMessage = () -> Unit

/**
 * [MessageVisitor] provides the capability of visiting every
 * [kind][MessageTag] of [message][Message]. Each entry point accepts (1) the
 * message to visit and (2) what to do after processing the message.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface MessageVisitor
{
	fun visit (message: DisconnectMessage, after: AfterMessage)
	fun visit (message: NegotiateVersionMessage, after: AfterMessage)
	fun visit (message: AcceptedVersionMessage, after: AfterMessage)
	fun visit (message: RebuttedVersionsMessage, after: AfterMessage)
	fun visit (message: AcknowledgedMessage, after: AfterMessage)
	fun visit (message: IdentifyChannelMessage, after: AfterMessage)
}

/**
 * The methods of [AbstractMessageVisitor] default to throwing
 * [UnsupportedOperationException], so implementers should override the methods
 * corresponding to the messages that they wish to intercept.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class AbstractMessageVisitor : MessageVisitor
{
	override fun visit (
		message: DisconnectMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()

	override fun visit (
		message: NegotiateVersionMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()

	override fun visit (
		message: AcceptedVersionMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()

	override fun visit (
		message: RebuttedVersionsMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()

	override fun visit (
		message: AcknowledgedMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()

	override fun visit (
		message: IdentifyChannelMessage,
		after: AfterMessage
	): Unit = throw UnsupportedOperationException()
}

////////////////////////////////////////////////////////////////////////////////
//                               Conversations.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * An ongoing conversation between a client and an [AnvilServer], recording the
 * expected successor [message][Message] [kinds][MessageTag] and providing
 * behavior to process such messages.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property id
 *   The conversation identifier.
 * @property allowedSuccessors
 *   The tags of the allowed successor messages in this conversation.
 *
 * @constructor
 * Construct a new [Conversation].
 *
 * @param id
 *   The conversation identifier.
 * @param allowedSuccessors
 *   The tags of the allowed successor messages in this conversation.
 */
abstract class Conversation (
	val id: Long,
	val allowedSuccessors: Set<MessageTag>
) : AbstractMessageVisitor()
{
	/**
	 * Is the conversation just a stub, i.e., a placeholder for flow checking?
	 */
	open val isStub = false

	/**
	 * Construct a new [Conversation] for the supplied message.
	 *
	 * @param message
	 *   The message that awaits further conversation.
	 */
	constructor (message: Message) : this(message.id, message.allowedSuccessors)
}

////////////////////////////////////////////////////////////////////////////////
//                                  Errors.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * [Message][Message] [tag][MessageTag] [decoding][MessageTag.decode] produces a
 * [BadMessageException] when it encounters an unrecognized tag.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property badTag
 *   The unrecognized tag.
 *
 * @constructor
 * Construct a new [BadMessageException].
 *
 * @param badTag
 *   The unrecognized tag.
 */
internal data class BadMessageException constructor (
	val badTag: Int): Exception()

/**
 * [AcknowledgmentCode] [decoding][AcknowledgmentCode.decode] produces a
 * [BadAcknowledgmentCodeException] when it encounters an unrecognized code.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property badCode
 *   The unrecognized code.
 *
 * @constructor
 * Construct a new [BadAcknowledgmentCodeException].
 *
 * @param badCode
 *   The unrecognized code.
 */
internal data class BadAcknowledgmentCodeException constructor (
	val badCode: Int): Exception()
