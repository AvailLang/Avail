/*
 * SocketChannel.kt
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

package avail.server.io

import avail.server.messages.Message
import avail.utility.IO

import java.nio.channels.AsynchronousSocketChannel

/**
 * A `SocketChannel` encapsulates an [AsynchronousSocketChannel] created by a
 * `SocketAdapter`.
 *
 * @property adapter
 *   The [SocketAdapter] that created this [channel][SocketChannel].
 * @property transport
 *   The [channel][AsynchronousSocketChannel] used by the associated
 *   [SocketAdapter].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SocketChannel`.
 *
 * @param adapter
 *   The [SocketAdapter].
 * @param transport
 *   The [channel][AsynchronousSocketChannel].
 * @param closeAction
 *   The custom action that is to be called when the this channel is closed in
 *   order to support implementation-specific requirements during the closing of
 *   a channel.
 */
internal class SocketChannel constructor(
	override val adapter: SocketAdapter,
	override val transport: AsynchronousSocketChannel,
	closeAction: (DisconnectReason, AvailServerChannel) -> Unit = {_,_->})
: AbstractTransportChannel<AsynchronousSocketChannel>(closeAction)
{
	override val isOpen get() = transport.isOpen
	override val maximumSendQueueDepth = MAX_QUEUE_DEPTH
	override val maximumReceiveQueueDepth = MAX_QUEUE_DEPTH

	override fun closeTransport ()
	{
		if (transport.isOpen)
		{
			heartbeat.cancel()
			IO.close(transport)
			channelCloseHandler.close()
		}
	}

	override fun scheduleClose(reason: DisconnectReason)
	{
		synchronized(sendQueue) {
			if (!sendQueue.isEmpty())
			{
				shouldCloseAfterEmptyingSendQueue = true
				channelCloseHandler.reason = reason
			}
			else
			{
				adapter.sendClose(this, reason)
			}
		}
	}

	companion object
	{
		/**
		 * The maximum number of [messages][Message] permitted on the
		 * [queue][sendQueue].
		 */
		private const val MAX_QUEUE_DEPTH = 10
	}
}
