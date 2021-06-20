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

package com.avail.anvil.io

import com.avail.anvil.Message
import com.avail.anvil.MessageTag
import com.avail.anvil.io.CloseOrigin.CLIENT
import com.avail.anvil.io.CloseOrigin.SERVER
import java.nio.channels.ClosedChannelException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The originating party of an [AnvilServerChannel] disconnection.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class CloseOrigin
{
	/** The client disconnected. */
	CLIENT,

	/** The server disconnected. */
	SERVER
}

/**
 * `DisconnectReason` encapsulates all information known about the
 * disconnection of an [AnvilServerChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed interface CloseReason
{
	/**
	 * The party that originated the disconnect.
	 */
	val origin: CloseOrigin

	/**
	 * Was the [AnvilServerChannel] closed voluntarily by the originating party?
	 */
	val normalDisconnect get () = false

	/**
	 * An entry suitable for logging the disconnect.
	 */
	val logEntry get () = "$origin: ${javaClass.simpleName}"

	/**
	 * The [Throwable] responsible for the disconnection, if one is available.
	 */
	val cause: Throwable? get () = null

	/**
	 * Log the receiver to the provided [logger][Logger].
	 *
	 * @param logger
	 *   The target logger.
	 * @param level
	 *   The logging level.
	 * @param custom
	 *   A situationally tailored message, if any.
	 */
	fun log (
		logger: Logger,
		level: Level,
		custom: String? = null)
	{
		val message = custom?.let { "$logEntry [$it]" } ?: logEntry
		cause?.let { logger.log(level, message, it) }
			?: logger.log(level, message)
	}
}

/**
 * [InternalErrorCloseReason] indicates that the root cause for a
 * disconnection was an internal server error. Note that server-originated
 * protocol errors should be treated as internal errors.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class InternalErrorCloseReason (
	override val cause: Throwable
) : CloseReason
{
	override val origin = SERVER
}

/**
 * [DisorderlyClientCloseReason] specifies that the client suddenly disconnected
 * from the server without warning, which is not permitted by the orderly
 * shutdown protocol.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object DisorderlyClientCloseReason: CloseReason
{
	override val origin = CLIENT
	override val cause: ClosedChannelException get () =
		try { throw ClosedChannelException() }
		catch (e: ClosedChannelException) { e }
}

/**
 * [OrderlyClientCloseReason] specifies that the client initiated an orderly
 * shutdown.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object OrderlyClientCloseReason: CloseReason
{
	override val origin = CLIENT
	override val normalDisconnect = true
}

/**
 * [OrderlyServerCloseReason] specifies that the server initiated an orderly
 * shutdown.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object OrderlyServerCloseReason: CloseReason
{
	override val origin = SERVER
	override val normalDisconnect = true
}

/**
 * [SocketIOErrorReason] indicates that the server disconnected due to an
 * unrecoverable I/O error on the [AnvilServerChannel].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property cause
 *   The causal exception.
 *
 * @constructor
 * Construct a [SocketIOErrorReason].
 *
 * @param cause
 *   The causal exception.
 */
class SocketIOErrorReason constructor (
	override val cause: Throwable
) : CloseReason
{
	override val origin = SERVER
}

/**
 * [BadMessageCloseReason] indicates that the server received an unrecognized
 * [message][Message] [tag][MessageTag] or otherwise malformed message.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object BadMessageCloseReason: CloseReason
{
	override val origin = SERVER
}

/**
 * [BadProtocolCloseReason] indicates that the server detected a violation of
 * the communication protocol established by the negotiated version. Note that
 * server-originated protocol errors should be treated as
 * [internal&#32;errors][InternalErrorCloseReason] instead.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class BadProtocolCloseReason constructor (
	override val cause: Throwable
): CloseReason
{
	override val origin = SERVER
}

/**
 * [BadProtocolVersion] indicates that the client reiterated a bad protocol
 * version after being told the supported versions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object BadProtocolVersion : CloseReason
{
	override val origin = SERVER
}
