/*
 * SocketAdapter.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.server.io;

import com.avail.io.SimpleCompletionHandler;
import com.avail.server.AvailServer;
import com.avail.server.messages.Message;
import com.avail.utility.IO;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AcceptPendingException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static com.avail.server.AvailServer.logger;

/**
 * A {@code SocketAdapter} provides a low-level {@linkplain
 * AsynchronousSocketChannel socket} interface to an {@linkplain AvailServer
 * Avail server}. The transport protocol is minimal; {@linkplain Message
 * messages} are encoded as size-prefixed {@linkplain StandardCharsets#UTF_8
 * UTF-8} {@linkplain String strings}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class SocketAdapter
implements TransportAdapter<AsynchronousSocketChannel>
{
	/** The {@linkplain AvailServer Avail server}. */
	final AvailServer server;

	@Override
	public AvailServer server ()
	{
		return server;
	}

	/**
	 * The {@linkplain InetSocketAddress address} of the {@linkplain
	 * AsynchronousServerSocketChannel server socket channel}.
	 */
	final InetSocketAddress adapterAddress;

	/**
	 * The {@linkplain AsynchronousServerSocketChannel server socket channel}.
	 */
	final AsynchronousServerSocketChannel serverChannel;

	/**
	 * Construct a new {@code SocketAdapter} for the specified {@linkplain
	 * AvailServer server} that listens on the specified {@linkplain
	 * InetSocketAddress socket address}.
	 *
	 * @param server
	 *        An Avail server.
	 * @param adapterAddress
	 *        The socket address of the listener.
	 * @throws IOException
	 *         If the {@linkplain AsynchronousServerSocketChannel server socket
	 *         channel} could not be opened.
	 */
	public SocketAdapter (
			final AvailServer server,
			final InetSocketAddress adapterAddress)
		throws IOException
	{
		this.server = server;
		this.adapterAddress = adapterAddress;
		this.serverChannel = server.runtime().ioSystem().openServerSocket();
		this.serverChannel.bind(adapterAddress);
		acceptConnections();
	}

	/**
	 * Asynchronously accept incoming connections.
	 */
	private void acceptConnections ()
	{
		serverChannel.accept(
			null,
			new SimpleCompletionHandler<>(
				(transport, unused, handler) ->
				{
					// Asynchronously accept a subsequent connection.
					serverChannel.accept(null, handler);
					final SocketChannel channel =
						new SocketChannel(SocketAdapter.this, transport);
					readMessage(channel);
				},
				(e, unused, handler) ->
				{
					// If there was a race between two accepts, then simply
					// ignore one of them.
					if (!(e instanceof AcceptPendingException))
					{
						logger.log(
							Level.WARNING,
							"accept failed on " + adapterAddress,
							e);
						close();
					}
				}));
	}

	/**
	 * Answer whether the remote end of the {@linkplain
	 * AsynchronousSocketChannel transport} closed. If it did, then log this
	 * information.
	 *
	 * @param transport
	 *        A channel.
	 * @param result
	 *        {@code -1} if the remote end closed.
	 * @return {@code true} if the remote end closed, {@code false} otherwise.
	 */
	static boolean remoteEndClosed (
		final AsynchronousSocketChannel transport,
		final Integer result)
	{
		if (result == -1)
		{
			logger.log(Level.INFO, transport + " closed");
			IO.close(transport);
			return true;
		}
		return false;
	}

	/**
	 * Read a complete message from the specified {@linkplain SocketChannel
	 * channel}.
	 *
	 * @param weakChannel
	 *        A channel.
	 */
	@Override
	public void readMessage (
		final AbstractTransportChannel<AsynchronousSocketChannel> weakChannel)
	{
		final SocketChannel channel = (SocketChannel) weakChannel;
		final AsynchronousSocketChannel transport = channel.transport();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(4);
		transport.read(
			buffer,
			null,
			new SimpleCompletionHandler<>(
				(result, unused, handler) ->
				{
					if (remoteEndClosed(transport, result))
					{
						return;
					}
					if (buffer.hasRemaining())
					{
						transport.read(buffer, null, handler);
					}
					else
					{
						buffer.flip();
						final int payloadLength = buffer.getInt();
						// A payload length of zero means that the client has
						// done a polite shutdown.
						if (payloadLength == 0)
						{
							IO.close(transport);
						}
						// If the message is too big, then close the transport.
						else if (payloadLength > Message.MAX_SIZE)
						{
							IO.close(transport);
						}
						else
						{
							readPayloadThen(
								channel,
								payloadLength,
								content ->
								{
									// The buffer should have been flipped
									// already.
									assert content.position() == 0;
									final Message message = new Message(
										new String(
											content.array(),
											StandardCharsets.UTF_8));
									channel.receiveMessage(message);
								});
						}
					}
				},
				(e, unused, handler) ->
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload length",
						e);
					IO.close(channel);
				}));
	}

	/**
	 * Read a {@linkplain Message message} payload from the specified
	 * {@linkplain SocketChannel channel}.
	 *
	 * @param channel
	 *        A {@linkplain SocketChannel channel}.
	 * @param payloadLength
	 *        The length of the payload, in bytes.
	 * @param continuation
	 *        What to do with the payload.
	 */
	static void readPayloadThen (
		final SocketChannel channel,
		final int payloadLength,
		final Continuation1NotNull<ByteBuffer> continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocate(payloadLength);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new SimpleCompletionHandler<>(
				(result, unused, handler) ->
				{
					if (remoteEndClosed(transport, result))
					{
						return;
					}
					if (buffer.hasRemaining())
					{
						transport.read(buffer, null, handler);
					}
					else
					{
						buffer.flip();
						continuation.value(buffer);
					}
				},
				(e, unused, handler) ->
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload",
						e);
					IO.close(channel);
				}));
	}

	@Override
	public void sendUserData (
		final AbstractTransportChannel<AsynchronousSocketChannel> weakChannel,
		final Message payload,
		final @Nullable Continuation0 success,
		final @Nullable Continuation1<Throwable> failure)
	{
		final SocketChannel channel = (SocketChannel) weakChannel;
		final ByteBuffer buffer = StandardCharsets.UTF_8.encode(
			payload.content());
		final AsynchronousSocketChannel transport = channel.transport();
		transport.write(
			buffer,
			null,
			new SimpleCompletionHandler<>(
				(result, unused, handler) ->
				{
					if (buffer.hasRemaining())
					{
						transport.write(buffer, null, handler);
					}
					else if (success != null)
					{
						success.value();
					}
				},
				(e, unused, handler) ->
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to send message",
						e);
					IO.close(transport);
					if (failure != null)
					{
						failure.value(e);
					}
				}));
	}

	@Override
	public void sendClose (
		final AbstractTransportChannel<AsynchronousSocketChannel> weakChannel)
	{
		final SocketChannel channel = (SocketChannel) weakChannel;
		final ByteBuffer buffer = ByteBuffer.allocateDirect(4);
		buffer.putInt(0);
		buffer.rewind();
		final AsynchronousSocketChannel transport = channel.transport();
		transport.write(
			buffer,
			null,
			new SimpleCompletionHandler<Integer, Void>(
				(result, unused, handler) ->
				{
					if (buffer.hasRemaining())
					{
						transport.write(buffer, null, handler);
					}
					else
					{
						IO.close(transport);
					}
				},
				(e, unused, handler) ->
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to send close notification",
						e);
					IO.close(transport);
				}));
	}

	@Override
	public synchronized void close ()
	{
		if (serverChannel.isOpen())
		{
			IO.close(serverChannel);
		}
	}
}
