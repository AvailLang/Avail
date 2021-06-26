/*
 * SocketAdapter.kt
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

import com.avail.anvil.AnvilServer
import com.avail.anvil.Message
import com.avail.io.SimpleCompletionHandler
import com.avail.utility.IO
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.AcceptPendingException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A `SocketAdapter` provides a low-level [socket][AsynchronousSocketChannel]
 * interface to an [Anvil&#32;server][AnvilServer]. The transport protocol is
 * minimal; each [message][Message] encodes itself as leading variable-width
 * [tag][Message.tag] followed by a binary encoding of the message itself.
 *
 * @property server
 *   The [Anvil&#32;server][AnvilServer].
 * @property adapterAddress
 *   The [address][InetSocketAddress] of the
 *   [server&#32;socket&#32;channel][AsynchronousServerSocketChannel].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SocketAdapter` for the specified [server][AnvilServer] that
 * listens on the specified [socket&#32;address][InetSocketAddress].
 *
 * @param server
 *   An Anvil server.
 * @param adapterAddress
 *   The socket address of the listener.
 * @param onChannelClose
 *   What to do whenever an [AnvilServerChannel] closes.
 * @throws IOException
 *   If the [server&#32;socket][AsynchronousServerSocketChannel] could not be
 *   opened.
 */
class SocketAdapter @Throws(IOException::class) constructor (
	val server: AnvilServer,
	private val adapterAddress: InetSocketAddress,
	private val onChannelClose: (CloseReason, AnvilServerChannel) -> Unit =
		{ _, _ -> })
{
	/** The [server&#32;socket&#32;channel][AsynchronousServerSocketChannel]. */
	private val serverChannel = server.runtime.ioSystem.openServerSocket()

	init
	{
		this.serverChannel.bind(adapterAddress)
		acceptConnections()
	}

	/** The [logger][Logger]. */
	private val logger get () = server.logger

	/**
	 * Asynchronously accept incoming connections.
	 */
	private fun acceptConnections ()
	{
		SimpleCompletionHandler<AsynchronousSocketChannel>(
			{
				// Asynchronously accept a subsequent connection.
				handler.guardedDo {
					serverChannel.accept(dummy, handler)
				}
				val channel = AnvilServerChannel(
					this@SocketAdapter, value, onChannelClose)
				server.registerChannel(channel)
				server.identifyChannel(channel)
				channel.readMessage()
			},
			{
				// If there was a race between two accepts, then simply
				// ignore one of them.
				if (throwable !is AcceptPendingException)
				{
					logger.log(
						Level.WARNING,
						"accept failed on $adapterAddress",
						throwable)
					close()
				}
			}
		).guardedDo { serverChannel.accept(dummy, handler) }
	}

	/**
	 * Close the
	 * [server&#32;socket&#32;channel][AsynchronousServerSocketChannel].
	 */
	fun close ()
	{
		if (serverChannel.isOpen)
		{
			IO.close(serverChannel)
		}
	}
}
