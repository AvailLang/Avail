/*
 * TransportAdapter.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.server.io

import avail.server.AvailServer
import avail.server.messages.Message
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A `TransportAdapter` hides the details of using a particular transport
 * mechanism to send and receive [messages][Message].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @param T
 *   The type of the underlying transport.
 */
interface TransportAdapter<T> : AutoCloseable
{
	/**
	 * The [Avail&#32;server][AvailServer] attached to this
	 * [adapter][TransportAdapter].
	 */
	val server: AvailServer

	/**
	 * Read a complete message from the specified
	 * [channel][AbstractTransportChannel].
	 *
	 * @param channel
	 *   A channel.
	 */
	fun readMessage(channel: AbstractTransportChannel<T>)

	/**
	 * Send a [message][Message] bearing user data over the specified
	 * [channel][AbstractTransportChannel].
	 *
	 * @param channel
	 *   A channel.
	 * @param payload
	 *   A payload.
	 * @param success
	 *   What to do after sending the message.
	 * @param failure
	 *   What to do if sending the message fails.
	 */
	fun sendUserData(
		channel: AbstractTransportChannel<T>,
		payload: Message,
		success: ()->Unit,
		failure: (Throwable)->Unit)

	/**
	 * Send a polite close notification across the given
	 * [channel][AbstractTransportChannel].
	 *
	 * @param channel
	 *   A channel.
	 */
	fun sendClose(channel: AbstractTransportChannel<T>)

	/**
	 * Send a polite close notification across the given
	 * [channel][AbstractTransportChannel].
	 *
	 * @param channel
	 *   A channel.
	 * @param reason
	 *   The reason for the disconnect
	 */
	fun sendClose(channel: AbstractTransportChannel<T>, reason: DisconnectReason)

	/**
	 * Receive a polite close notification across the given
	 * [channel][AbstractTransportChannel].
	 *
	 * @param channel
	 *   A channel.
	 */
	fun receiveClose(channel: AbstractTransportChannel<T>)

	/**
	 * The custom action that is to be called when the input channel is closed
	 * in order to support implementation-specific requirements for the closing
	 * of a channel.
	 */
	val onChannelCloseAction: (DisconnectReason, AvailServerChannel) -> Unit

	/**
	 * The [timer][ScheduledExecutorService] for
	 * [scheduling&#32;activities][ScheduledFuture] related to managing
	 * communication.
	 */
	val timer: ScheduledExecutorService

	/**
	 * Execute the supplied task in approximately [task] milliseconds.
	 *
	 * @param task
	 *        The task.
	 * @param action
	 *        The task.
	 */
	fun millisTimer (task: Long, action: () -> Unit): ScheduledFuture<*> =
		timer.schedule(action, task, TimeUnit.MILLISECONDS)
}
