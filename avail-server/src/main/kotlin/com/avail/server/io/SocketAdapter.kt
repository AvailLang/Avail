/*
 * SocketAdapter.kt
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
 * interface to an [Avail&#32;server][AvailServer]. The transport protocol is
 * minimal; [messages][Message] are encoded as size-prefixed
 * [UTF-8][StandardCharsets.UTF_8] [strings][String].
 *
 * @property server
 *   The [Avail&#32;server][AvailServer].
 * @property adapterAddress
 *   The [address][InetSocketAddress] of the
 *   [server&#32;socket&#32;channel][AsynchronousServerSocketChannel].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SocketAdapter` for the specified [server][AvailServer] that
 * listens on the specified [socket&#32;address][InetSocketAddress].
 *
 * @param server
 *   An Avail server.
 * @param adapterAddress
 *   The socket address of the listener.
 * @param onChannelCloseAction
 *   The custom action that is to be called when the input channel is closed in
 *   order to support implementation-specific requirements for the closing of
 *   a channel. *Does nothing by default.*
 * @throws IOException
 *   If the [server&#32;socket][AsynchronousServerSocketChannel] could not be
 *   opened.
 */
class SocketAdapter @Throws(IOException::class) constructor(
	override val server: AvailServer,
	@Suppress("MemberVisibilityCanBePrivate")
	internal val adapterAddress: InetSocketAddress,
	override val onChannelCloseAction:
		(DisconnectReason, AvailServerChannel) -> Unit = { _, _ -> })
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
		SimpleCompletionHandler<AsynchronousSocketChannel>(
			{
				// Asynchronously accept a subsequent connection.
				handler.guardedDo {
					serverChannel.accept(dummy, handler)
					server.newChannels[channel.id] = channel
				}
				val channel = SocketChannel(this@SocketAdapter, value)
				readMessage(channel)
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
		SimpleCompletionHandler<Int>(
			{
				if (remoteEndClosed(transport, value))
				{
					return@SimpleCompletionHandler
				}
				if (buffer.hasRemaining())
				{
					handler.guardedDo { transport.read(buffer, dummy, handler) }
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
						{
							strongChannel.closeImmediately(ClientDisconnect)
						}
						payloadLength > Message.MAX_SIZE ->
						{
							// If the message is too big, then close the
							// transport.
							strongChannel
								.closeImmediately(BadMessageDisconnect)
						}
						else ->
							readPayloadThen(strongChannel, payloadLength) {
								content ->
								// The buffer should have been flipped
								// already.
								assert(content.position() == 0)
								val message =
									Message(content.array(), channel.state)
								strongChannel.receiveMessage(message)
							}
					}
				}
			},
			{
				logger.log(
					Level.WARNING,
					"failed while attempting to read payload length",
					throwable)
				strongChannel.closeImmediately(
					CommunicationErrorDisconnect(throwable))
			}
		).guardedDo { transport.read(buffer, dummy, handler) }
	}

	override fun sendUserData(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>,
		payload: Message,
		success: ()->Unit,
		failure: (Throwable)->Unit)
	{
		val strongChannel = channel as SocketChannel
		val buffer = StandardCharsets.UTF_8.encode(payload.stringContent)
		val transport = strongChannel.transport
		SimpleCompletionHandler<Int>(
			{
				if (buffer.hasRemaining())
				{
					handler.guardedDo {
						transport.write(buffer, dummy, handler)
					}
				}
				else success()
			},
			{
				logger.log(
					Level.WARNING,
					"failed while attempting to send message",
					throwable)
				strongChannel.closeImmediately(
					CommunicationErrorDisconnect(throwable))
				failure(throwable)
			}
		).guardedDo { transport.write(buffer, dummy, handler) }
	}

	override fun sendClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		val strongChannel = channel as SocketChannel
		val buffer = ByteBuffer.allocateDirect(4)
		buffer.putInt(0)
		buffer.rewind()
		val transport = strongChannel.transport
		SimpleCompletionHandler<Int>(
			{
				if (buffer.hasRemaining())
				{
					handler.guardedDo {
						transport.write(buffer, dummy, handler)
					}
				}
				else
				{
					strongChannel.closeTransport()
				}
			},
			{
				logger.log(
					Level.WARNING,
					"failed while attempting to send close notification",
					throwable)
				strongChannel.closeImmediately(
					CommunicationErrorDisconnect(throwable))
			}
		).guardedDo { transport.write(buffer, dummy, handler) }
	}

	override fun sendClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>,
		reason: DisconnectReason)
	{
		val strongChannel = channel as SocketChannel
		val buffer = ByteBuffer.allocateDirect(4)
		buffer.putInt(0)
		buffer.rewind()
		val transport = strongChannel.transport
		SimpleCompletionHandler<Int>(
			{
				if (buffer.hasRemaining())
				{
					handler.guardedDo {
						transport.write(buffer, dummy, handler)
					}
				}
				else
				{
					strongChannel.closeImmediately(reason)
				}
			},
			{
				logger.log(
					Level.WARNING,
					"failed while attempting to send close notification",
					throwable)
				strongChannel.closeImmediately(
					CommunicationErrorDisconnect(throwable))
			}
		).guardedDo { transport.write(buffer, dummy, handler) }
	}

	override fun receiveClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		if (!channel.transport.isOpen)
		{
			// Presumes close was originated by server and already closed.
			return
		}
		channel.channelCloseHandler.reason?.let{
			channel.closeTransport()
		} ?: channel.closeImmediately(ClientDisconnect)
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
			SimpleCompletionHandler<Int>(
				{
					if (remoteEndClosed(transport, value))
					{
						return@SimpleCompletionHandler
					}
					if (buffer.hasRemaining())
					{
						handler.guardedDo {
							transport.read(buffer, dummy, handler)
						}
					}
					else
					{
						buffer.flip()
						continuation(buffer)
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}
	}
}
