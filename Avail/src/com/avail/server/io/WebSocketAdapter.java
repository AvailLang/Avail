/**
 * WebSocketAdapter.java
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AcceptPendingException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.server.AvailServer;
import com.avail.server.messages.Message;
import com.avail.utility.IO;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;

/**
 * A {@code WebSocketAdapter} provides a WebSocket interface to an {@linkplain
 * AvailServer Avail server}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a href="http://tools.ietf.org/html/rfc6455">RFC 6455: The WebSocket Protocol</a>
 */
public final class WebSocketAdapter
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
	 * The {@linkplain WebSocketAdapter server}'s authority, e.g., the host name
	 * of this node.
	 */
	@InnerAccess final String serverAuthority;

	/**
	 * The {@linkplain AsynchronousServerSocketChannel server socket channel}.
	 */
	@InnerAccess final AsynchronousServerSocketChannel serverChannel;

	/**
	 * Construct a new {@link WebSocketAdapter} for the specified {@linkplain
	 * AvailServer server} that listens on the specified {@linkplain
	 * InetSocketAddress socket address}.
	 *
	 * @param server
	 *        An Avail server.
	 * @param adapterAddress
	 *        The socket address of the listener.
	 * @param serverAuthority
	 *        The server's authority, e.g., the host name of this node.
	 * @throws IOException
	 *         If the {@linkplain AsynchronousServerSocketChannel server socket
	 *         channel} could not be opened.
	 */
	public WebSocketAdapter (
			final AvailServer server,
			final InetSocketAddress adapterAddress,
			final String serverAuthority)
		throws IOException
	{
		this.server = server;
		this.adapterAddress = adapterAddress;
		this.serverAuthority = serverAuthority;
		this.serverChannel = server.runtime().openServerSocket();
		this.serverChannel.bind(adapterAddress);
		acceptConnections();
	}

	/**
	 * A {@code HttpHeaderState} represents a state of the {@linkplain
	 * ClientHandshake client handshake} {@linkplain
	 * ClientHandshake#receiveThen(WebSocketChannel, WebSocketAdapter,
	 * Continuation1) recognizer}.
	 */
	private static enum HttpHeaderState
	{
		/** Beginning to read, or previously read an "ordinary" character. */
		START
		{
			@Override
			public HttpHeaderState nextState (final int c)
			{
				return c == '\r' ? FIRST_CARRIAGE_RETURN : START;
			}
		},

		/** Just read the first carriage return. */
		FIRST_CARRIAGE_RETURN
		{
			@Override
			public HttpHeaderState nextState (final int c)
			{
				return c == '\n' ? FIRST_LINE_FEED : START;
			}
		},

		/** Just read the first carriage return + line feed. */
		FIRST_LINE_FEED
		{
			@Override
			public HttpHeaderState nextState (final int c)
			{
				return c == '\r' ? SECOND_CARRIAGE_RETURN : START;
			}
		},

		/** Just read the second carriage return. */
		SECOND_CARRIAGE_RETURN
		{
			@Override
			public HttpHeaderState nextState (final int c)
			{
				return c == '\n' ? SECOND_LINE_FEED : START;
			}
		},

		/** Just read the second carriage return + line feed. */
		SECOND_LINE_FEED
		{
			@Override
			public HttpHeaderState nextState (final int c)
			{
				throw new RuntimeException("no states after final state");
			}

			@Override
			public boolean isAcceptState ()
			{
				return true;
			}
		};

		/**
		 * Answer the next {@linkplain HttpHeaderState state} given a
		 * transition on the specified character.
		 *
		 * @param c
		 *        A character.
		 * @return The next state.
		 */
		public abstract HttpHeaderState nextState (final int c);

		/**
		 * Is this an accept {@linkplain HttpHeaderState state}?
		 *
		 * @return {@code true} if this is an accept state, {@code false}
		 *         otherwise.
		 */
		public boolean isAcceptState ()
		{
			return false;
		}
	}

	/**
	 * {@code HttpStatusCode} represents various HTTP status codes. The
	 * enumeration comprises only those status codes used by the WebSocket
	 * implementation; it is not intended to be comprehensive.
	 */
	private static enum HttpStatusCode
	{
		/** Switching protocols. */
		SWITCHING_PROTOCOLS (101),

		/** Bad request. */
		BAD_REQUEST (400),

		/** Not found. */
		NOT_FOUND (404),

		/** Method not allowed. */
		METHOD_NOT_ALLOWED (405);

		/** The HTTP status code. */
		private final int statusCode;

		/**
		 * Answer the numeric status code.
		 *
		 * @return The numeric status code.
		 */
		public int statusCode ()
		{
			return statusCode;
		}

		/**
		 * Construct a new {@link WebSocketAdapter.HttpStatusCode}.
		 *
		 * @param statusCode
		 *        The status code.
		 */
		private HttpStatusCode (final int statusCode)
		{
			this.statusCode = statusCode;
		}
	}

	/**
	 * A {@code ClientHandshake} represents a WebSocket client handshake.
	 */
	private static final class ClientHandshake
	{
		/** The request URI. */
		final String requestURI;

		/** The WebSocket key. */
		final byte[] key;

		/** The requested protocols. */
		@SuppressWarnings("unused")
		final List<String> protocols;

		/** The requested extensions. */
		@SuppressWarnings("unused")
		final List<String> extensions;

		/**
		 * Construct a new {@link ClientHandshake}.
		 *
		 * @param requestURI
		 *        The request URI.
		 * @param key
		 *        The WebSocket key.
		 * @param protocols
		 *        The requested protocols.
		 * @param extensions
		 *        The requested extensions.
		 */
		private ClientHandshake (
			final String requestURI,
			final byte[] key,
			final List<String> protocols,
			final List<String> extensions)
		{
			this.requestURI = requestURI;
			this.key = key;
			this.protocols = protocols;
			this.extensions = extensions;
		}

		/**
		 * Read a {@linkplain ClientHandshake client handshake} from the
		 * specified {@linkplain WebSocketChannel channel}.
		 *
		 * @param channel
		 *        A channel.
		 * @param adapter
		 *        A {@linkplain WebSocketAdapter adapter}.
		 * @param continuation
		 *        A {@linkplain Continuation1 continuation} that processes a
		 *        valid client handshake.
		 */
		static void receiveThen (
			final WebSocketChannel channel,
			final WebSocketAdapter adapter,
			final Continuation1<ClientHandshake> continuation)
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			final ByteBuffer buffer = ByteBuffer.allocate(1024);
			final MutableOrNull<HttpHeaderState> state =
				new MutableOrNull<>(HttpHeaderState.START);
			final AsynchronousSocketChannel transport = channel.transport();
			transport.read(
				buffer,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer bytesRead,
						final @Nullable Void unused)
					{
						if (remoteEndClosed(transport, bytesRead))
						{
							return;
						}
						buffer.flip();
						while (
							buffer.hasRemaining()
							&& !state.value().isAcceptState())
						{
							state.value = state.value().nextState(buffer.get());
						}
						if (buffer.hasRemaining())
						{
							badHandshake(
								channel,
								HttpStatusCode.BAD_REQUEST,
								"Data Following Headers");
						}
						else
						{
							buffer.rewind();
							try
							{
								bytes.write(buffer.array());
							}
							catch (final IOException e)
							{
								assert false : "This never happens";
							}
							if (!state.value().isAcceptState())
							{
								buffer.clear();
								transport.read(buffer, unused, this);
							}
							else
							{
								final ClientHandshake handshake =
									parseHttpHeaders(
										channel,
										adapter,
										new String(
											bytes.toByteArray(),
											StandardCharsets.US_ASCII));
								if (handshake != null)
								{
									continuation.value(handshake);
								}
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
							"failed while attempting to read client handshake",
							e);
						IO.close(channel);
					}
				});
		}

		/**
		 * Answer a {@linkplain ClientHandshake client handshake} based on the
		 * specified HTTP headers. If the headers do not describe a valid
		 * WebSocket client handshake, then {@linkplain
		 * #badHandshake(WebSocketChannel, HttpStatusCode, String)
		 * fail the connection} and answer {@code null}.
		 *
		 * @param channel
		 *        A {@linkplain WebSocketChannel channel}.
		 * @param adapter
		 *        A {@linkplain WebSocketAdapter adapter}.
		 * @param headersText
		 *        The HTTP headers, as a single string. The individual headers
		 *        are separated by carriage return + line feed.
		 * @return A client handshake, or {@code null} if the specified headers
		 *         do not constitute a valid WebSocket client handshake.
		 */
		@InnerAccess static @Nullable ClientHandshake parseHttpHeaders (
			final WebSocketChannel channel,
			final WebSocketAdapter adapter,
			final String headersText)
		{
			final String[] headers = headersText.split("(?:\r\n)+");
			// Deal with the Request-Line specially.
			final String requestLine = headers[0];
			final String[] requestParts = requestLine.split(" +");
			if (requestParts.length != 3)
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Invalid Request-Line");
				return null;
			}
			if (!requestParts[0].equalsIgnoreCase("get"))
			{
				badHandshake(
					channel,
					HttpStatusCode.METHOD_NOT_ALLOWED,
					"Method Not Allowed");
				return null;
			}
			if (!requestParts[2].equalsIgnoreCase("HTTP/1.1"))
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Invalid HTTP Version");
				return null;
			}
			// Parse the remaining lines into a map.
			final Map<String, String> map = new HashMap<>();
			for (int i = 1; i < headers.length - 1; i++)
			{
				final String[] pair = headers[i].split(":", 2);
				map.put(pair[0].trim().toLowerCase(), pair[1].trim());
			}
			// Validate the request.
			final String host = map.get("host");
			if (host != null)
			{
				final String[] hostParts = host.split(":", 2);
				if (!adapter.serverAuthority.equalsIgnoreCase(hostParts[0]))
				{
					badHandshake(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid Server Authority");
					return null;
				}
				if (hostParts.length == 2
					&& adapter.adapterAddress.getPort()
						!= Integer.parseInt(hostParts[1]))
				{
					badHandshake(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid Port Number");
					return null;
				}
			}
			else
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Host Not Specified");
				return null;
			}
			if (!"websocket".equalsIgnoreCase(map.get("upgrade")))
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Invalid Upgrade Header");
				return null;
			}
			final String connection = map.get("connection");
			if (connection != null)
			{
				final String[] tokens = connection.split(" *, *");
				boolean includesUpgrade = false;
				for (final String token : tokens)
				{
					if ("upgrade".equalsIgnoreCase(token))
					{
						includesUpgrade = true;
					}
				}
				if (!includesUpgrade)
				{
					badHandshake(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid Connection Header");
					return null;
				}
			}
			else
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Missing Connection Header");
				return null;
			}
			if (!"13".equals(map.get("sec-websocket-version")))
			{
				badVersion(
					channel,
					Integer.parseInt(map.get("sec-websocket-version")));
				return null;
			}
			if (!map.containsKey("sec-websocket-key"))
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Missing WebSocket Key");
				return null;
			}
			final byte[] key = DatatypeConverter.parseBase64Binary(
				map.get("sec-websocket-key"));
			if (key.length != 16)
			{
				badHandshake(
					channel,
					HttpStatusCode.BAD_REQUEST,
					"Invalid WebSocket Key");
				return null;
			}
			final List<String> protocols = Arrays.asList(
				map.containsKey("sec-websocket-protocol")
				? map.get("sec-websocket-protocol").split(" *, *")
				: new String[0]);
			final List<String> extensions = Arrays.asList(
				map.containsKey("sec-websocket-extensions")
				? map.get("sec-websocket-extensions").split(" *, *")
				: new String[0]);
			return new ClientHandshake(
				requestParts[1], key, protocols, extensions);
		}

		/**
		 * Write an appropriate HTTP error response to the specified {@linkplain
		 * WebSocketChannel channel}.
		 *
		 * @param channel
		 *        A channel.
		 * @param statusCode
		 *        The HTTP status code.
		 * @param reason
		 *        The reason message.
		 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">
		 *      Status Code Definitions</a>
		 */
		@InnerAccess static void badHandshake (
			final WebSocketChannel channel,
			final HttpStatusCode statusCode,
			final String reason)
		{
			@SuppressWarnings("resource")
			final Formatter formatter = new Formatter();
			formatter.format(
				"HTTP/1.1 %03d %s\r\n\r\n"
				+ "<html><head><title>Bad Handshake</title></head>"
				+ "<body><strong>%2$s</strong></body></html>",
				statusCode.statusCode(),
				reason);
			final ByteBuffer bytes = StandardCharsets.US_ASCII.encode(
				formatter.toString());
			final AsynchronousSocketChannel transport = channel.transport();
			transport.write(
				bytes,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer result,
						final @Nullable Void unused)
					{
						if (bytes.hasRemaining())
						{
							transport.write(bytes, unused, this);
						}
						else
						{
							IO.close(channel);
						}
					}

					@Override
					public void failed (
						final @Nullable Throwable e,
						final @Nullable Void unused)
					{
						logger.log(
							Level.WARNING,
							"unable to write HTTP response to " + channel,
							e);
						IO.close(channel);
					}
				});
		}

		/**
		 * Write an HTTP error response to the specified {@linkplain
		 * WebSocketChannel channel} that tells the client which
		 * WebSocket versions the {@linkplain WebSocketAdapter adapter}
		 * supports.
		 *
		 * @param channel
		 *        A channel.
		 * @param badVersion
		 *        The (unsupported) WebSocket version requested by the client.
		 */
		@InnerAccess static void badVersion (
			final WebSocketChannel channel,
			final int badVersion)
		{
			@SuppressWarnings("resource")
			final Formatter formatter = new Formatter();
			formatter.format(
				"HTTP/1.1 %03d Bad Request\r\n"
				+ "Sec-WebSocket-Version: 13\r\n"
				+ "<html><head><title>Bad Handshake</title></head>"
				+ "<body>"
				+ "<strong>WebSocket Version %d Is Not Supported</strong>"
				+ "</body></html>",
				HttpStatusCode.BAD_REQUEST,
				badVersion);
			final ByteBuffer bytes = StandardCharsets.US_ASCII.encode(
				formatter.toString());
			final AsynchronousSocketChannel transport = channel.transport();
			transport.write(
				bytes,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer result,
						final @Nullable Void unused)
					{
						if (bytes.hasRemaining())
						{
							transport.write(bytes, unused, this);
						}
						else
						{
							IO.close(channel);
						}
					}

					@Override
					public void failed (
						final @Nullable Throwable e,
						final @Nullable Void unused)
					{
						logger.log(
							Level.WARNING,
							"unable to write HTTP response to " + channel,
							e);
						IO.close(channel);
					}
				});
		}
	}

	/**
	 * A {@code ServerHandshake} represents a WebSocket server handshake.
	 */
	private static final class ServerHandshake
	{
		/** The WebSocket accept key. */
		final String acceptKey;

		/** The selected protocols. */
		final List<String> protocols;

		/** The selected extensions. */
		final List<String> extensions;

		/**
		 * Compute the WebSocket accept key from the client-supplied key.
		 *
		 * @param key
		 *        The client-supplied WebSocket key.
		 * @return The WebSocket accept key.
		 */
		private String computeAcceptKey (final byte[] key)
		{
			final MessageDigest digest;
			try
			{
				digest = MessageDigest.getInstance("SHA-1");
			}
			catch (final NoSuchAlgorithmException e)
			{
				throw new RuntimeException("SHA-1 not available", e);
			}
			final String stringKey = DatatypeConverter.printBase64Binary(key)
				+ "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
			final byte[] acceptBytes =
				digest.digest(stringKey.getBytes(StandardCharsets.US_ASCII));
			return DatatypeConverter.printBase64Binary(acceptBytes);
		}

		/**
		 * Construct a new {@link ServerHandshake}.
		 *
		 * @param key
		 *        The client-supplied WebSocket key.
		 * @param protocols
		 *        The selected protocols.
		 * @param extensions
		 *        The selected extensions.
		 */
		public ServerHandshake (
			final byte[] key,
			final List<String> protocols,
			final List<String> extensions)
		{
			this.acceptKey = computeAcceptKey(key);
			this.protocols = protocols;
			this.extensions = extensions;
		}

		/**
		 * Send the {@linkplain ServerHandshake server handshake} across the
		 * specified {@linkplain AsynchronousSocketChannel channel}.
		 *
		 * @param channel
		 *        A channel.
		 * @param continuation
		 *        What to do after sending the server handshake.
		 */
		public void sendThen (
			final AsynchronousSocketChannel channel,
			final Continuation0 continuation)
		{
			@SuppressWarnings("resource")
			final Formatter formatter = new Formatter();
			formatter.format(
				"HTTP/1.1 %03d Switching Protocols\r\n"
				+ "Upgrade: websocket\r\n"
				+ "Connection: Upgrade\r\n"
				+ "Sec-WebSocket-Accept: %s\r\n",
				HttpStatusCode.SWITCHING_PROTOCOLS.statusCode(),
				acceptKey);
			if (!protocols.isEmpty())
			{
				formatter.format("Sec-WebSocket-Protocol: ");
				boolean first = true;
				for (final String protocol : protocols)
				{
					if (!first)
					{
						formatter.format(", ");
					}
					formatter.format("%s", protocol);
					first = false;
				}
				formatter.format("\r\n");
			}
			if (!extensions.isEmpty())
			{
				formatter.format("Sec-WebSocket-Extensions: ");
				boolean first = true;
				for (final String extension : extensions)
				{
					if (!first)
					{
						formatter.format(", ");
					}
					formatter.format("%s", extension);
					first = false;
				}
				formatter.format("\r\n");
			}
			formatter.format("\r\n");
			final ByteBuffer bytes = StandardCharsets.US_ASCII.encode(
				formatter.toString());
			channel.write(
				bytes,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer result,
						final @Nullable Void unused)
					{
						if (bytes.hasRemaining())
						{
							channel.write(bytes, unused, this);
						}
						else
						{
							continuation.value();
						}
					}

					@Override
					public void failed (
						final @Nullable Throwable e,
						final @Nullable Void unused)
					{
						logger.log(
							Level.WARNING,
							"unable to write HTTP response to " + channel,
							e);
						IO.close(channel);
					}
				});
		}
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
					final WebSocketChannel channel =
						new WebSocketChannel(WebSocketAdapter.this, transport);
					// Process the client handshake, then send the server
					// handshake.
					ClientHandshake.receiveThen(
						channel,
						WebSocketAdapter.this,
						new Continuation1<ClientHandshake>()
						{
							@Override
							public void value (
								final @Nullable ClientHandshake handshake)
							{
								assert handshake != null;
								if (!handshake.requestURI.equalsIgnoreCase(
									"/avail"))
								{
									ClientHandshake.badHandshake(
										channel,
										HttpStatusCode.NOT_FOUND,
										"Not Found");
								}
								else
								{
									final ServerHandshake serverHandshake =
										new ServerHandshake(
											handshake.key,
											Collections.<String>emptyList(),
											Collections.<String>emptyList());
									serverHandshake.sendThen(
										transport,
										new Continuation0()
										{
											@Override
											public void value ()
											{
												channel.handshakeSucceeded();
												readMessage(channel);
											}
										});
								}
							}
						});
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
	 * {@code Opcode} represents a WebSocket opcode.
	 */
	private static enum Opcode
	{
		/*
		 * Do not change the order of these values! Their ordinals correspond
		 * to the WebSocket opcodes, and the adapter uses this ordinals to
		 * dispatch control.
		 */

		/** A continuation frame. */
		CONTINUATION
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A text frame. */
		TEXT
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A binary frame. */
		BINARY
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A reserved frame. */
		RESERVED_3,

		/** A reserved frame. */
		RESERVED_4,

		/** A reserved frame. */
		RESERVED_5,

		/** A reserved frame. */
		RESERVED_6,

		/** A reserved frame. */
		RESERVED_7,

		/** A close frame. */
		CLOSE
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A ping frame. */
		PING
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A pong frame. */
		PONG
		{
			@Override
			public boolean isValid ()
			{
				return true;
			}
		},

		/** A reserved frame. */
		RESERVED_11,

		/** A reserved frame. */
		RESERVED_12,

		/** A reserved frame. */
		RESERVED_13,

		/** A reserved frame. */
		RESERVED_14,

		/** A reserved frame. */
		RESERVED_15;

		/** An array of all {@link Opcode} enumeration values. */
		private static Opcode[] all = values();

		/**
		 * Answer an array of all {@link Opcode} enumeration values.
		 *
		 * @return An array of all {@link Opcode} enum values.  Do not
		 *         modify the array.
		 */
		public static Opcode[] all ()
		{
			return all;
		}

		/**
		 * Is the {@linkplain Opcode opcode} valid?
		 *
		 * @return {@code true} if the opcode is valid, {@code false}
		 *         otherwise.
		 */
		public boolean isValid ()
		{
			return false;
		}
	}

	/**
	 * {@code WebSocketStatusCode} represents one of the generic WebSocket
	 * status codes.
	 */
	private static enum WebSocketStatusCode
	{
		/** Normal closure. */
		NORMAL_CLOSURE (1000),

		/** Endpoint is going away. */
		ENDPOINT_GOING_AWAY (1001),

		/** Protocol error. */
		PROTOCOL_ERROR (1002),

		/** Unsupported message. */
		UNSUPPORTED_MESSAGE (1003),

		/** Reserved. */
		RESERVED_1004 (1004),

		/** No status code. */
		NO_STATUS_CODE (1005),

		/** No {@link Opcode#CLOSE CLOSE} {@linkplain Frame frame}. */
		NO_CLOSE (1006),

		/** Bad data. */
		BAD_DATA (1007),

		/** Bad policy. */
		BAD_POLICY (1008),

		/** Received message was too big. */
		MESSAGE_TOO_BIG (1009),

		/** Unsupported extension. */
		UNSUPPORTED_EXTENSION (1010),

		/** Server error. */
		SERVER_ERROR (1011),

		/** Bad TLS handshake. */
		BAD_TLS_HANDSHAKE (1015);

		/** The WebSocket status code. */
		private final int statusCode;

		/**
		 * Answer the numeric status code.
		 *
		 * @return The numeric status code.
		 */
		public int statusCode ()
		{
			return statusCode;
		}

		/**
		 * Construct a new {@link WebSocketStatusCode}.
		 *
		 * @param statusCode
		 *        The WebSocket status code.
		 */
		private WebSocketStatusCode (final int statusCode)
		{
			this.statusCode = statusCode;
		}
	}

	/**
	 * {@code Frame} represents a WebSocket frame.
	 */
	private static final class Frame
	{
		/** Is this the final fragment of the message? */
		boolean isFinalFragment;

		/** Is this fragment masked? */
		boolean isMasked;

		/** The {@linkplain Opcode opcode}. */
		@Nullable Opcode opcode;

		/**
		 * Answer the {@linkplain Opcode opcode}.
		 *
		 * @return The opcode.
		 */
		Opcode opcode ()
		{
			final Opcode op = opcode;
			assert op != null;
			return op;
		}

		/** The length of the payload. */
		long payloadLength;

		/**
		 * The masking key (valid only if {@link #isMasked} is {@code true}).
		 */
		@Nullable ByteBuffer maskingKey;

		/**
		 * Answer the {@linkplain ByteBuffer masking key} (valid only if
		 * {@link #isMasked} is {@code true}).
		 *
		 * @return The masking key.
		 */
		ByteBuffer maskingKey ()
		{
			final ByteBuffer buffer = maskingKey;
			assert buffer != null;
			return buffer;
		}

		/**
		 * The payload. This must not be {@linkplain
		 * ByteBuffer#allocateDirect(int) allocated directly}, as access to the
		 * {@linkplain ByteBuffer#array() backing array} is required.
		 */
		@Nullable ByteBuffer payloadData;

		/**
		 * Answer the {@linkplain ByteBuffer payload}.
		 *
		 * @return The payload.
		 */
		ByteBuffer payloadData ()
		{
			final ByteBuffer buffer = payloadData;
			assert buffer != null;
			return buffer;
		}

		/**
		 * Construct a new {@link Frame}.
		 */
		public Frame ()
		{
			// No implementation required.
		}

		/**
		 * Answer a {@linkplain ByteBuffer buffer} that encodes the WebSocket
		 * {@linkplain Frame frame}.
		 *
		 * @return A buffer.
		 */
		public ByteBuffer asByteBuffer ()
		{
			assert opcode().isValid();
			assert payloadLength == payloadData().limit();
			int len = (int) payloadLength;
			assert len == payloadLength;
			// Compute the length of the entire frame.
			len += 2;
			final int ext =
				payloadLength < 126 ? 0 : payloadLength < 65536 ? 2 : 8;
			len += ext;
			len += isMasked ? 4 : 0;
			final ByteBuffer buffer = ByteBuffer.allocateDirect(len);
			buffer.put(
				(byte) ((isFinalFragment ? 0x80 : 0x00) | opcode().ordinal()));
			buffer.put(
				(byte) ((isMasked ? 0x80 : 0x00)
				| (payloadLength < 126
					? payloadLength : payloadLength < 65536
					? 126 : 127)));
			if (ext == 2)
			{
				buffer.putShort((short) payloadLength);
			}
			else if (ext == 8)
			{
				buffer.putLong(payloadLength);
			}
			else
			{
				assert ext == 0;
			}
			final ByteBuffer payload = payloadData();
			payload.rewind();
			if (isMasked)
			{
				final ByteBuffer mask = maskingKey();
				buffer.put(mask);
				mask.rewind();
				for (int i = 0; i < payloadLength; i++)
				{
					final int j = i & 3;
					buffer.put((byte) (payload.get(i) ^ mask.get(j)));
				}
			}
			else
			{
				buffer.put(payload);
			}
			assert !buffer.hasRemaining();
			buffer.rewind();
			return buffer;
		}
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
	 * Read a complete message from the specified {@linkplain WebSocketChannel
	 * channel}. A complete message may span multiple WebSocket {@linkplain
	 * Frame frames}.
	 *
	 * @param weakChannel
	 *        A channel.
	 */
	@Override
	public void readMessage (
		final AbstractTransportChannel<AsynchronousSocketChannel> weakChannel)
	{
		final WebSocketChannel channel = (WebSocketChannel) weakChannel;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
		final Continuation0 processMessage = new Continuation0()
		{
			@Override
			public void value ()
			{
				final Message message = new Message(new String(
					bytes.toByteArray(), StandardCharsets.UTF_8));
				channel.receiveMessage(message);
			}
		};
		readFrameThen(
			channel,
			new Continuation1<Frame>()
			{
				@Override
				public void value (final @Nullable Frame frame)
				{
					assert frame != null;
					try
					{
						bytes.write(frame.payloadData().array());
					}
					catch (final IOException e)
					{
						assert false : "This never happens!";
						throw new RuntimeException(e);
					}
					switch (frame.opcode())
					{
						case CONTINUATION:
							// This isn't legal as the first frame of a message.
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"message cannot begin with continuation frame");
							return;
						case TEXT:
							break;
						case BINARY:
							fail(
								channel,
								WebSocketStatusCode.UNSUPPORTED_MESSAGE,
								"only text frames are supported");
							break;
						case CLOSE:
							if (!frame.isFinalFragment)
							{
								fail(
									channel,
									WebSocketStatusCode.PROTOCOL_ERROR,
									"close must be final fragment");
							}
							else
							{
								sendClose(channel);
							}
							return;
						case PING:
							if (!frame.isFinalFragment)
							{
								fail(
									channel,
									WebSocketStatusCode.PROTOCOL_ERROR,
									"ping must be final fragment");
							}
							else
							{
								sendPong(
									channel, bytes.toByteArray(), null, null);
							}
							readMessage(channel);
							return;
						case PONG:
							if (!frame.isFinalFragment)
							{
								fail(
									channel,
									WebSocketStatusCode.PROTOCOL_ERROR,
									"pong must be final fragment");
							}
							// Ignore an unsolicited (but valid) pong.
							readMessage(channel);
							return;
						default:
							// Fail on receipt of a reserved opcode.
							assert !frame.opcode().isValid();
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"opcode "
								+ frame.opcode().ordinal()
								+ "is reserved");
							return;
					}
					// A text frame was processed.
					if (frame.isFinalFragment)
					{
						processMessage.value();
					}
					else
					{
						readFrameThen(
							channel,
							new Continuation1<Frame>()
							{
								@Override
								public void value (
									final @Nullable Frame continuationFrame)
								{
									assert continuationFrame != null;
									try
									{
										bytes.write(continuationFrame
											.payloadData().array());
									}
									catch (final IOException e)
									{
										assert false : "This never happens!";
										throw new RuntimeException(e);
									}
									if (continuationFrame.opcode
										!= Opcode.CONTINUATION)
									{
										fail(
											channel,
											WebSocketStatusCode.PROTOCOL_ERROR,
											"received "
											+ continuationFrame.opcode().name()
											+ " instead of continuation frame");
									}
									else if (!continuationFrame.isFinalFragment)
									{
										readFrameThen(channel, this);
									}
									else
									{
										processMessage.value();
									}
								}
							});
					}
				}
			});
	}

	/**
	 * Read a WebSocket {@linkplain Frame frame}.
	 *
	 * @param channel
	 *        A channel.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readFrameThen (
		final WebSocketChannel channel,
		final Continuation1<Frame> continuation)
	{
		final Frame frame = new Frame();
		readOpcodeThen(
			channel,
			frame,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(frame);
				}
			});
	}

	/**
	 * Read a WebSocket {@linkplain Opcode opcode} and FIN bit.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	private void readOpcodeThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocateDirect(1);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						final int b = buffer.get();
						assert !buffer.hasRemaining();
						frame.isFinalFragment = (b & 0x80) == 0x80;
						frame.opcode = Opcode.all()[b & 0x0F];
						readPayloadLengthThen(channel, frame, continuation);
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read opcode",
						e);
					IO.close(channel);
				}
			});
	}

	/**
	 * Read a WebSocket payload length and MASK bit.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readPayloadLengthThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocateDirect(1);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						final int b = buffer.get();
						assert !buffer.hasRemaining();
						frame.isMasked = (b & 0x80) == 0x80;
						final int len = b & 0x7F;
						switch (len)
						{
							default:
								frame.payloadLength = len;
								if (frame.isMasked)
								{
									readMaskingKeyThen(
										channel, frame, continuation);
								}
								else
								{
									readPayloadDataThen(
										channel, frame, continuation);
								}
								break;
							case 126:
								readPayloadLength2ByteExtensionThen(
									channel, frame, continuation);
								break;
							case 127:
								readPayloadLength8ByteExtensionThen(
									channel, frame, continuation);
								break;
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
						"failed while attempting to read payload size",
						e);
					IO.close(channel);
				}
			});
	}

	/**
	 * Read a 16-bit WebSocket payload length.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readPayloadLength2ByteExtensionThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocateDirect(2);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						final int len = buffer.getShort() & 0xFFFF;
						assert !buffer.hasRemaining();
						if (len < 126)
						{
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"2-byte encoding for length=" + len);
						}
						else
						{
							frame.payloadLength = len;
							if (frame.isMasked)
							{
								readMaskingKeyThen(
									channel, frame, continuation);
							}
							else
							{
								readPayloadDataThen(
									channel, frame, continuation);
							}
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
						"failed while attempting to read 2-byte payload size",
						e);
					IO.close(channel);
				}
			});
	}

	/**
	 * Read a 64-bit WebSocket payload length.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readPayloadLength8ByteExtensionThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocateDirect(8);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						final long len = buffer.getLong();
						assert !buffer.hasRemaining();
						if (len < 65536)
						{
							// Note that this covers the case where the MSB is
							// set (which is forbidden).
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"8-byte encoding for length=" + len);
						}
						else if (len > Message.MAX_SIZE)
						{
							fail(
								channel,
								WebSocketStatusCode.MESSAGE_TOO_BIG,
								"length="
								+ len
								+ " exceeds maximum length of "
								+ Message.MAX_SIZE);
						}
						else
						{
							frame.payloadLength = len;
							if (frame.isMasked)
							{
								readMaskingKeyThen(
									channel, frame, continuation);
							}
							else
							{
								readPayloadDataThen(
									channel, frame, continuation);
							}
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
						"failed while attempting to read 8-byte payload size",
						e);
					IO.close(channel);
				}
			});
	}

	/**
	 * Read the payload masking key.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readMaskingKeyThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final ByteBuffer buffer = ByteBuffer.allocateDirect(4);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						frame.maskingKey = buffer;
						readPayloadDataThen(channel, frame, continuation);
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable e,
					final @Nullable Void unused)
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read masking key",
						e);
					IO.close(channel);
				}
			});
	}

	/**
	 * Read the payload data.
	 *
	 * @param channel
	 *        A channel.
	 * @param frame
	 *        The current incoming {@linkplain Frame frame}.
	 * @param continuation
	 *        What to do after the complete frame has been read.
	 */
	@InnerAccess void readPayloadDataThen (
		final WebSocketChannel channel,
		final Frame frame,
		final Continuation0 continuation)
	{
		final int len = (int) frame.payloadLength;
		assert len == frame.payloadLength;
		final ByteBuffer buffer = ByteBuffer.allocate(len);
		final AsynchronousSocketChannel transport = channel.transport();
		transport.read(
			buffer,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					if (remoteEndClosed(transport, bytesRead))
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
						if (frame.isMasked)
						{
							final ByteBuffer mask = frame.maskingKey;
							assert mask != null;
							for (int i = 0; i < frame.payloadLength; i++)
							{
								final int j = i & 3;
								buffer.put(
									i, (byte) (buffer.get(i) ^ mask.get(j)));
							}
						}
						assert buffer.position() == 0;
						frame.payloadData = buffer;
						// The complete frame has been read, so invoke the
						// continuation now.
						continuation.value();
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

	/**
	 * Send a WebSocket {@linkplain Frame frame} based on the specified
	 * {@linkplain Opcode opcode} and {@linkplain ByteBuffer payload}.
	 *
	 * @param channel
	 *        A {@linkplain WebSocketChannel channel}.
	 * @param opcode
	 *        The opcode.
	 * @param payload
	 *        The payload.
	 * @param success
	 *        What to do after sending the frame, or {@code null} if no
	 *        post-send action is necessary.
	 * @param failure
	 *        What to do if the send fails, or {@code null} if no
	 */
	private void sendFrame (
		final WebSocketChannel channel,
		final Opcode opcode,
		final ByteBuffer payload,
		final @Nullable Continuation0 success,
		final @Nullable Continuation1<Throwable> failure)
	{
		final Frame frame = new Frame();
		frame.isFinalFragment = true;
		frame.isMasked = false;
		frame.opcode = opcode;
		frame.payloadData = payload;
		frame.payloadLength = payload.limit();
		final ByteBuffer buffer = frame.asByteBuffer();
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
						"failed while attempting to send " + opcode,
						e);
					// Don't recurse.
					if (opcode == Opcode.CLOSE)
					{
						IO.close(transport);
					}
					else
					{
						IO.close(channel);
					}
					if (failure != null)
					{
						failure.value(e);
					}
				}
			});
		}

	/**
	 * Send a {@linkplain Frame frame} bearing user data over the specified
	 * {@linkplain WebSocketChannel channel}.
	 *
	 * @param channel
	 *        A channel.
	 * @param payload
	 *        A payload.
	 * @param success
	 *        What to do after sending the frame.
	 * @param failure
	 *        What to do if sending the frame fails.
	 */
	@Override
	public void sendUserData (
		final AbstractTransportChannel<AsynchronousSocketChannel> channel,
		final Message payload,
		final @Nullable Continuation0 success,
		final @Nullable Continuation1<Throwable> failure)
	{
		final WebSocketChannel strongChannel = (WebSocketChannel) channel;
		final String content = payload.content();
		final ByteBuffer buffer = StandardCharsets.UTF_8.encode(content);
		sendFrame(strongChannel, Opcode.TEXT, buffer, success, failure);
	}

	/**
	 * Send a {@link Opcode#CLOSE CLOSE} {@linkplain Frame frame} over the
	 * specified {@linkplain WebSocketChannel channel}. Close the channel when
	 * the frame has been sent.
	 *
	 * @param weakChannel
	 *        A channel.
	 */
	@Override
	public void sendClose (
		final AbstractTransportChannel<AsynchronousSocketChannel> weakChannel)
	{
		final WebSocketChannel channel = (WebSocketChannel) weakChannel;
		final ByteBuffer buffer = ByteBuffer.allocateDirect(2);
		buffer.putShort(
			(short) WebSocketStatusCode.NORMAL_CLOSURE.statusCode());
		sendFrame(
			channel,
			Opcode.CLOSE,
			buffer,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					IO.close(channel.transport());
				}
			},
			null);
	}

	/**
	 * Send a {@link Opcode#PING PING} {@linkplain Frame frame} over the
	 * specified {@linkplain WebSocketChannel channel}.
	 *
	 * @param channel
	 *        A channel.
	 * @param payloadData
	 *        The payload.
	 * @param success
	 *        What to do after sending the frame.
	 * @param failure
	 *        What to do if sending the frame fails.
	 */
	@InnerAccess void sendPing (
		final WebSocketChannel channel,
		final byte[] payloadData,
		final @Nullable Continuation0 success,
		final @Nullable Continuation1<Throwable> failure)
	{
		final ByteBuffer buffer = ByteBuffer.wrap(payloadData);
		sendFrame(channel, Opcode.PING, buffer, success, failure);
	}

	/**
	 * Send a {@link Opcode#PONG PONG} {@linkplain Frame frame} over the
	 * specified {@linkplain WebSocketChannel channel}.
	 *
	 * @param channel
	 *        A channel.
	 * @param payloadData
	 *        The payload (which was supplied by a leading {@link Opcode#PING
	 *        PING} frame).
	 * @param success
	 *        What to do after sending the frame.
	 * @param failure
	 *        What to do if sending the frame fails.
	 */
	@InnerAccess void sendPong (
		final WebSocketChannel channel,
		final byte[] payloadData,
		final @Nullable Continuation0 success,
		final @Nullable Continuation1<Throwable> failure)
	{
		final ByteBuffer buffer = ByteBuffer.wrap(payloadData);
		sendFrame(channel, Opcode.PONG, buffer, success, failure);
	}

	/**
	 * Fail the WebSocket connection.
	 *
	 * @param channel
	 *        A channel.
	 * @param statusCode
	 *        The {@linkplain WebSocketStatusCode status code}.
	 * @param reasonMessage
	 *        The reason message.
	 */
	@InnerAccess void fail (
		final WebSocketChannel channel,
		final WebSocketStatusCode statusCode,
		final String reasonMessage)
	{
		final ByteBuffer utf8 = StandardCharsets.UTF_8.encode(reasonMessage);
		final ByteBuffer buffer = ByteBuffer.allocateDirect(utf8.limit() + 2);
		buffer.putShort((short) statusCode.statusCode());
		buffer.put(utf8);
		sendFrame(
			channel,
			Opcode.CLOSE,
			buffer,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					IO.close(channel);
				}
			},
			null);
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