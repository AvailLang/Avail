/**
 * SocketAdapter.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.server.AvailServer.logger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AcceptPendingException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import com.avail.annotations.InnerAccess;
import org.jetbrains.annotations.Nullable;
import com.avail.server.AvailServer;
import com.avail.server.messages.Message;
import com.avail.utility.IO;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;

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
	@InnerAccess final AvailServer server;

	@Override
	public AvailServer server ()
	{
		return server;
	}

	/**
	 * The {@linkplain InetSocketAddress address} of the {@linkplain
	 * AsynchronousServerSocketChannel server socket channel}.
	 */
	@InnerAccess final InetSocketAddress adapterAddress;

	/**
	 * The {@linkplain AsynchronousServerSocketChannel server socket channel}.
	 */
	@InnerAccess final AsynchronousServerSocketChannel serverChannel;

	/**
	 * Construct a new {@link SocketAdapter} for the specified {@linkplain
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
		this.serverChannel = server.runtime().openServerSocket();
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
			new CompletionHandler<AsynchronousSocketChannel, Void>()
			{
				@Override
				public void completed (
					final @Nullable AsynchronousSocketChannel transport,
					final @Nullable Void unused)
				{
					assert transport != null;
					// Asynchronously accept a subsequent connection.
					serverChannel.accept(unused, this);
					final SocketChannel channel =
						new SocketChannel(SocketAdapter.this, transport);
					readMessage(channel);
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					assert e != null;
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
				}
			});
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
	@InnerAccess static boolean remoteEndClosed (
		final AsynchronousSocketChannel transport,
		final @Nullable Integer result)
	{
		assert result != null;
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
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer result,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, result))
					{
						return;
					}
					if (buffer.hasRemaining())
					{
						transport.read(buffer, null, this);
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
								new Continuation1<ByteBuffer>()
								{
									@Override
									public void value (
										final @Nullable ByteBuffer content)
									{
										assert content != null;
										// The buffer should have been flipped
										// already.
										assert content.position() == 0;
										final Message message = new Message(
											new String(
												content.array(),
												StandardCharsets.UTF_8));
										channel.receiveMessage(message);
									}
								});
						}
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload length",
						e);
					IO.close(channel);
				}
			});
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
	@InnerAccess void readPayloadThen (
		final SocketChannel channel,
		final int payloadLength,
		final Continuation1<ByteBuffer> continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocate(payloadLength);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer result,
					final @Nullable Void attachment)
				{
					if (remoteEndClosed(transport, result))
					{
						return;
					}
					if (buffer.hasRemaining())
					{
						transport.read(buffer, null, this);
					}
					else
					{
						buffer.flip();
						continuation.value(buffer);
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload",
						e);
					IO.close(channel);
				}
			});
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
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer result,
					final @Nullable Void attachment)
				{
					if (buffer.hasRemaining())
					{
						transport.write(buffer, null, this);
					}
					else if (success != null)
					{
						success.value();
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
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
				}
			});
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
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer result,
					final @Nullable Void unused)
				{
					if (buffer.hasRemaining())
					{
						transport.write(buffer, null, this);
					}
					else
					{
						IO.close(transport);
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to send close notification",
						e);
					IO.close(transport);
				}
			});
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
