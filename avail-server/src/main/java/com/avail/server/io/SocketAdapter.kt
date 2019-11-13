/*
 * SocketAdapter.kt
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

import com.avail.io.SimpleCompletionHandler
import com.avail.server.AvailServer
import com.avail.server.AvailServer.Companion.logger
import com.avail.server.messages.Message
import com.avail.utility.IO
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AcceptPendingException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.logging.Level


/**
 * A `SocketAdapter` provides a low-level [socket][AsynchronousSocketChannel]
 * interface to an [Avail server][AvailServer]. The transport protocol is
 * minimal; [messages][Message] are encoded as size-prefixed
 * [UTF-8][StandardCharsets.UTF_8] [strings][String].
 *
 * @property server
 *   The [Avail server][AvailServer].
 * @property adapterAddress
 *   The [address][InetSocketAddress] of the [server socket
 *   channel][AsynchronousServerSocketChannel].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SocketAdapter` for the specified [server][AvailServer] that
 * listens on the specified [socket address][InetSocketAddress].
 *
 * @param server
 *   An Avail server.
 * @param adapterAddress
 *   The socket address of the listener.
 * @param onChannelCloseAction
 *   The custom action that is to be called when the input channel is closed in
 *   order to support implementation-specific requirements for the closing of
 *   a channel.
 * @throws IOException
 *   If the [server socket][AsynchronousServerSocketChannel] could not be
 *   opened.
 */
class SocketAdapter @Throws(IOException::class) constructor(
	override val server: AvailServer,
	@Suppress("MemberVisibilityCanBePrivate")
	internal val adapterAddress: InetSocketAddress,
	override val onChannelCloseAction:
		(DisconnectReason, AbstractTransportChannel<AsynchronousSocketChannel>)
			-> Unit = { _, _ -> /* Do nothing */})
	: TransportAdapter<AsynchronousSocketChannel>
{
	/** The [server socket channel][AsynchronousServerSocketChannel]. */
	@Suppress("MemberVisibilityCanBePrivate")
	internal val serverChannel: AsynchronousServerSocketChannel =
		server.runtime.ioSystem().openServerSocket()

	init
	{
		this.serverChannel.bind(adapterAddress)
		acceptConnections()
	}

	override val timer =
		ScheduledThreadPoolExecutor(
			1,
			ThreadFactory { r ->
				val thread = Thread(r)
				thread.isDaemon = true
				thread.name = "SocketAdapterTimer ${thread.id}"
				thread
			})

	/**
	 * Asynchronously accept incoming connections.
	 */
	private fun acceptConnections()
	{
		serverChannel.accept<Any>(
			null,
			SimpleCompletionHandler(
				{ transport, _, handler ->
					// Asynchronously accept a subsequent connection.
					serverChannel.accept<Any>(null, handler)
					val channel = SocketChannel(this, transport)
					readMessage(channel)
				},
				{ e, _, _ ->
					// If there was a race between two accepts, then simply
					// ignore one of them.
					if (e !is AcceptPendingException)
					{
						logger.log(
							Level.WARNING,
							"accept failed on $adapterAddress",
							e)
						close()
					}
				}))
	}

	/**
	 * Read a complete message from the specified [channel][SocketChannel].
	 *
	 * @param channel
	 *   A channel.
	 */
	override fun readMessage(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		val strongChannel = channel as SocketChannel
		val transport = strongChannel.transport
		val buffer = ByteBuffer.allocateDirect(4)
		transport.read<Any>(
			buffer, null,
			SimpleCompletionHandler(
				{ result, _, handler ->
					if (remoteEndClosed(transport, result!!))
					{
						return@SimpleCompletionHandler
					}
					if (buffer.hasRemaining())
					{
						transport.read<Any>(buffer, null, handler)
					}
					else
					{
						buffer.flip()
						val payloadLength = buffer.int
						// A payload length of zero means that the client has
						// done a polite shutdown.
						when
						{
							payloadLength == 0 ->
								IO.close(transport)
							payloadLength > Message.MAX_SIZE ->
								// If the message is too big, then close the
								// transport.
								IO.close(transport)
							else ->
								readPayloadThen(strongChannel, payloadLength) {
									content ->
									// The buffer should have been flipped
									// already.
									assert(content.position() == 0)
									val message = Message(
										String(
											content.array(),
											StandardCharsets.UTF_8))
									strongChannel.receiveMessage(message)
								}
						}
					}
				},
				{ e, _, _ ->
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload length",
						e)
					IO.close(strongChannel)
				}))
	}

	override fun sendUserData(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>,
		payload: Message,
		success: (()->Unit)?,
		failure: ((Throwable)->Unit)?)
	{
		val strongChannel = channel as SocketChannel
		val buffer = StandardCharsets.UTF_8.encode(
			payload.content)
		val transport = strongChannel.transport
		transport.write<Any>(
			buffer, null,
			SimpleCompletionHandler(
				{ _, _, handler ->
					if (buffer.hasRemaining())
					{
						transport.write<Any>(buffer, null, handler)
					}
					else success?.invoke()
				},
				{ e, _, _ ->
					logger.log(
						Level.WARNING,
						"failed while attempting to send message",
						e)
					IO.close(transport)
					failure?.invoke(e)
				}))
	}

	override fun sendClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		val strongChannel = channel as SocketChannel
		val buffer = ByteBuffer.allocateDirect(4)
		buffer.putInt(0)
		buffer.rewind()
		val transport = strongChannel.transport
		transport.write<Void>(
			buffer, null,
			SimpleCompletionHandler(
				{ _, _, handler ->
					if (buffer.hasRemaining())
					{
						transport.write<Void>(buffer, null, handler)
					}
					else
					{
						IO.close(transport)
					}
					Unit
				},
				{ e, _, _ ->
					logger.log(
						Level.WARNING,
						"failed while attempting to send close notification",
						e)
					IO.close(transport)
					Unit
				}))
	}

	@Synchronized
	override fun close()
	{
		if (serverChannel.isOpen)
		{
			IO.close(serverChannel)
		}
	}

	companion object
	{
		/**
		 * Answer whether the remote end of the
		 * [transport][AsynchronousSocketChannel] closed. If it did, then log
		 * this information.
		 *
		 * @param transport
		 *   A channel.
		 * @param result
		 *   `-1` if the remote end closed.
		 * @return
		 *   `true` if the remote end closed, `false` otherwise.
		 */
		internal fun remoteEndClosed(
			transport: AsynchronousSocketChannel,
			result: Int): Boolean
		{
			if (result == -1)
			{
				logger.log(Level.INFO, "$transport closed")
				IO.close(transport)
				return true
			}
			return false
		}

		/**
		 * Read a [message][Message] payload from the specified
		 * [channel][SocketChannel].
		 *
		 * @param channel
		 *   A [channel][SocketChannel].
		 * @param payloadLength
		 *   The length of the payload, in bytes.
		 * @param continuation
		 *   What to do with the payload.
		 */
		internal fun readPayloadThen(
			channel: SocketChannel,
			payloadLength: Int,
			continuation: (ByteBuffer)->Unit)
		{
			val buffer = ByteBuffer.allocate(payloadLength)
			val transport = channel.transport
			transport.read<Any>(
				buffer, null,
				SimpleCompletionHandler(
					{ result, _, handler ->
						if (remoteEndClosed(transport, result!!))
						{
							return@SimpleCompletionHandler
						}
						if (buffer.hasRemaining())
						{
							transport.read<Any>(buffer, null, handler)
						}
						else
						{
							buffer.flip()
							continuation(buffer)
						}
						Unit
					},
					{ e, _, _ ->
						logger.log(
							Level.WARNING,
							"failed while attempting to read payload",
							e)
						IO.close(channel)
						Unit
					}))
		}
	}
}
