/*
 * Disconnect.kt
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

import com.avail.server.messages.Message
import java.nio.channels.ClosedChannelException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A `DisconnectOrigin` is an enum that specifies whether it was the client or
 * the server to disconnect the [AvailServerChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class DisconnectOrigin
{
	/** The client disconnected the connection */
	CLIENT_ORIGIN,

	/** The server disconnected the connection. */
	SERVER_ORIGIN
}

/**
 * The `DisconnectReason` is an interface that defines the behavior for
 * providing a reason for a disconnected [AvailServerChannel].
 *
 * All negative [DisconnectReason.code]s are reserved for system use. Nonzero
 * `codes` are reserved for application specific implementations.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface DisconnectReason
{
	/**
	 * The [DisconnectReason] that describes why the channel was closed.
	 */
	val origin: DisconnectOrigin

	/**
	 * A code that represents the [DisconnectReason] in a compact form.
	 *
	 * All negative [DisconnectReason.code]s are reserved for system use.
	 * Nonzero `codes` are reserved for application specific implementations.
	 */
	val code: Int

	/**
	 * `true` indicates the disconnect occurred normally; `false` otherwise.
	 */
	val normalDisconnect: Boolean get() = false

	/**
	 * The loggable String describing the reason for the disconnect.
	 */
	val logEntry: String get() = "$origin($code): ${javaClass.simpleName}"

	/**
	 * An optional [Throwable] associated with this [DisconnectReason]. `null`
	 * if no associated error.
	 */
	val error: Throwable? get() = null

	/**
	 * Log this [DisconnectReason] to the provided [Logger] at the provided
	 * [Level].
	 *
	 * @param logger
	 *   The `Logger` to log this `Disconnect` to.
	 * @param level
	 *   The `Level` of the log entry.
	 * @param msg
	 *   An optional custom message desired for logging. *Defaults to `null`.*
	 * @param e
	 *   An optional [Throwable] that can be logged.
	 */
	fun log (
		logger: Logger,
		level: Level,
		msg: String? = null,
		e: Throwable? = null)
	{
		val message = msg?.let {
			"$logEntry [$it]"
		} ?: logEntry
		e?.let {
			logger.log(level, message, e)
		} ?: logger.log(level, message)
	}
}

/**
 * A `RunCompletionDisconnect` is a [DisconnectReason] that specifies that the
 * disconnect originated from the server due to a running application ending
 * normally.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object RunCompletionDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -1
	override val normalDisconnect: Boolean get() = true
}

/**
 * The `UnspecifiedDisconnectReason` is the [DisconnectReason] provided for a
 * close when no explicit `DisconnectReason` was supplied.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object UnspecifiedDisconnectReason: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -2
}

/**
 * A `ClientDisconnect` is a [DisconnectReason] that specifies that the
 * disconnect originated from the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ClientDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.CLIENT_ORIGIN
	override val code get () = -3
	// We don't know that the close was healthy, only that the client closed
	// the connection. We should presume it can have a negative cascading impact
	override val error: Throwable? = ClosedChannelException()
}

/**
 * A `HeartbeatFailureDisconnect` is a [DisconnectReason] that specifies that
 * the disconnect originated from the server due to failure to receive any
 * heartbeat responses from the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object HeartbeatFailureDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -4
	override val error: Throwable? = ClosedChannelException()
}

/**
 * A `RunFailureDisconnect` is a [DisconnectReason] that specifies that the
 * disconnect originated from the server due to a application failing to run
 * normally.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object RunFailureDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -5
}

/**
 * A `ServerMessageDisconnect` is a [DisconnectReason] that specifies that the
 * disconnect originated from the server due to a [Message.closeAfterSending]
 * being `true`.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ServerMessageDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -6
	override val normalDisconnect: Boolean get() = true
}

/**
 * A `MismatchDisconnect` is a [DisconnectReason] that indicates that the
 * disconnect originated from the server due to the client not being compatible
 * with the server.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object MismatchDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -7
}

/**
 * A `CommunicationErrorDisconnect` is a [DisconnectReason] that indicates that
 * the disconnect originated from the server due to an exception on the server.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property e
 *   The [Throwable] related to the error or `null` if none.
 *
 * @constructor
 * Construct a [CommunicationErrorDisconnect].
 *
 * @param e
 *   The [Throwable] related to the error or `null` if none.
 */
class CommunicationErrorDisconnect constructor(override val error: Throwable?)
	: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -8
	override val logEntry: String
		get() = error?.let {
			"${super.logEntry} - ${it.message}"
		} ?: super.logEntry
}

/**
 * A `BadMessageDisconnect` is a [DisconnectReason] that indicates that the
 * disconnect originated from the server due to a bad request/message from the
 * client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object BadMessageDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -9
}

/**
 * An `UnsupportedFormatDisconnect` is a [DisconnectReason] that indicates that
 * the disconnect originated from the server due to receiving a message in the
 * wrong format.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property msg
 *   A message describing the format mismatch.
 *
 * @constructor
 * Construct a [UnsupportedFormatDisconnect].
 *
 * @param msg
 *   A message describing the format mismatch.
 */
class UnsupportedFormatDisconnect constructor(private val msg: String)
	: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -10

	override val logEntry: String get() = "${super.logEntry} - $msg"
}

/**
 * A `ProtocolErrorDisconnect` is a [DisconnectReason] that indicates that
 * the disconnect originated from the server due to receiving a message that
 * did not comply with the communication protocol.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property msg
 *   A message describing the format mismatch.
 *
 * @constructor
 * Construct a [ProtocolErrorDisconnect].
 *
 * @param msg
 *   A message describing the error.
 */
class ProtocolErrorDisconnect constructor(private val msg: String)
	: DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -11

	override val logEntry: String get() = "${super.logEntry} - $msg"
}

/**
 * A `ParentChannelDisconnect` is a [DisconnectReason] that indicates that
 * the disconnect originated from the server the parent [AvailServerChannel]
 * having disconnected.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ParentChannelDisconnect : DisconnectReason
{
	override val origin get () = DisconnectOrigin.SERVER_ORIGIN
	override val code get () = -12
}