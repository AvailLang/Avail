/*
 * Disconnect.kt
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

package avail.anvil.io

import avail.anvil.AcknowledgmentCode
import avail.anvil.Message
import avail.anvil.MessageOrigin
import avail.anvil.MessageOrigin.CLIENT
import avail.anvil.MessageOrigin.SERVER
import avail.anvil.MessageTag
import avail.anvil.io.AnvilServerChannel.ProtocolState

/**
 * `DisconnectReason` encapsulates all information known about the
 * disconnection of an [AnvilServerChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class CloseReason
{
	/**
	 * The party that originated the disconnect.
	 */
	abstract val origin: MessageOrigin

	override fun toString (): String
	{
		val self = this
		return buildString {
			append(self.javaClass.simpleName)
			append('(')
			append(origin)
			append(')')
		}
	}
}

/**
 * [OrderlyClientCloseReason] specifies that the client initiated an orderly
 * shutdown.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object OrderlyClientCloseReason: CloseReason()
{
	override val origin get() = CLIENT
}

/**
 * [OrderlyServerCloseReason] specifies that the server initiated an orderly
 * shutdown.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object OrderlyServerCloseReason: CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [DisorderlyClientCloseReason] specifies that the client suddenly disconnected
 * from the server without warning, which is not permitted by the orderly
 * shutdown protocol.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object DisorderlyClientCloseReason : CloseReason()
{
	override val origin get() = CLIENT
}

/**
 * [UnavailableInVersionCloseReason] specifies that the originator sent a
 * [message][Message] that is unavailable in the negotiated version.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 * @property version
 *   The negotiated version.
 *
 * @constructor
 * Construct a new [UnavailableInVersionCloseReason].
 *
 * @param origin
 *   The [message&#32;origin][MessageOrigin].
 * @param message
 *   The offending message.
 * @param version
 *   The negotiated version.
 */
data class UnavailableInVersionCloseReason constructor (
	override val origin: MessageOrigin,
	private val message: Message,
	private val version: Int
) : CloseReason()

/**
 * [BadOriginCloseReason] specifies that the originator sent a
 * [message][Message] that can only originate from the other party.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [BadOriginCloseReason].
 *
 * @param origin
 *   The [message&#32;origin][MessageOrigin].
 */
data class BadOriginCloseReason constructor (
	override val origin: MessageOrigin
) : CloseReason()

/**
 * [BadStateCloseReason] specifies that the [message][Message] is inappropriate
 * for the current [protocol&#32;state][ProtocolState].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 * @property state
 *   The protocol state.
 *
 * @constructor
 * Construct a new [BadStateCloseReason].
 *
 * @param origin
 *   The [message&#32;origin][MessageOrigin].
 * @param message
 *   The offending message.
 * @param state
 *   The protocol state.
 */
data class BadStateCloseReason constructor (
	override val origin: MessageOrigin,
	private val message: Message,
	private val state: ProtocolState
) : CloseReason()

/**
 * [BadEndFlowCloseReason] specifies that a [message][Message] attempted to
 * disrupt the flow of conversation, either by ending a conversation prematurely
 * or by continuing a defunct conversation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 *
 * @constructor
 * Construct a new [BadEndFlowCloseReason].
 *
 * @param message
 *   The offending message.
 */
data class BadEndFlowCloseReason constructor (
	private val message: Message
) : CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [WrongConversationCloseReason] specifies that the server inserted a
 * [message][Message] into the wrong conversation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 * @property badId
 *   The incorrect conversation identifier.
 *
 * @constructor
 * Construct a new [WrongConversationCloseReason].
 *
 * @param message
 *   The offending message.
 * @param badId
 *   The incorrect conversation identifier.
 */
data class WrongConversationCloseReason constructor (
	private val message: Message,
	private val badId: Long
) : CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [BadStartFlowCloseReason] specifies that a [message][Message] attempted to
 * disrupt the flow of conversation, either by starting a conversation
 * prematurely or continuing an unknown conversation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 *
 * @constructor
 * Construct a new [BadStartFlowCloseReason].
 *
 * @param origin
 *   The [message&#32;origin][MessageOrigin].
 * @param message
 *   The offending message.
 */
data class BadStartFlowCloseReason constructor (
	override val origin: MessageOrigin,
	private val message: Message
) : CloseReason()

/**
 * [UnexpectedReplyCloseReason] specifies that a [message][Message] was not
 * expected at a particular locus of conversation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property message
 *   The offending message.
 * @property expected
 *   The [tags][MessageTag] of the expected messages.
 *
 * @constructor
 * Construct a new [UnexpectedReplyCloseReason].
 *
 * @param origin
 *   The [message&#32;origin][MessageOrigin].
 * @param message
 *   The offending message.
 * @param expected
 *   The [tags][MessageTag] of the expected messages.
 */
data class UnexpectedReplyCloseReason constructor (
	override val origin: MessageOrigin,
	private val message: Message,
	private val expected: Set<MessageTag>
) : CloseReason()

/**
 * [SocketIOErrorReason] indicates that the server disconnected due to an
 * unrecoverable I/O error on the [AnvilServerChannel].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property cause
 *   The causal exception.
 *
 * @constructor
 * Construct a [SocketIOErrorReason].
 *
 * @param cause
 *   The causal exception.
 */
data class SocketIOErrorReason constructor (
	private val cause: Throwable
) : CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [BadMessageCloseReason] indicates that the server received an unrecognized
 * [message][Message] [tag][MessageTag] or otherwise malformed message.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property badTag
 *   The invalid tag.
 *
 * @constructor
 * Construct a [BadMessageCloseReason].
 *
 * @param badTag
 *   The invalid tag.
 */
data class BadMessageCloseReason constructor (
	private val badTag: Int
): CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [BadAcknowledgmentCodeReason] indicates that the server received an
 * unrecognized [acknowledgment&#32;code][AcknowledgmentCode].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property badCode
 *   The invalid code.
 *
 * @constructor
 * Construct a [BadMessageCloseReason].
 *
 * @param badCode
 *   The invalid code.
 */
data class BadAcknowledgmentCodeReason constructor (
	private val badCode: Int
): CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [BadProtocolVersion] indicates that the client reiterated a bad protocol
 * version after being told the supported versions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object BadProtocolVersion : CloseReason()
{
	override val origin get() = SERVER
}

/**
 * [InternalErrorCloseReason] indicates that the root cause for a
 * disconnection was an internal server error. Note that server-originated
 * protocol errors should be treated as internal errors.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @property cause
 *   The causal exception.
 *
 *
 * @constructor
 * Construct a new [InternalErrorCloseReason].
 *
 * @param cause
 *   The causal exception.
 */
class InternalErrorCloseReason (private val cause: Throwable) : CloseReason()
{
	override val origin get() = SERVER
}
