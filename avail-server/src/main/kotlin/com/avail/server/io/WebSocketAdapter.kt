/*
 * WebSocketAdapter.kt
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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.avail.server.io

import com.avail.io.SimpleCompletionHandler
import com.avail.server.AvailServer
import com.avail.server.AvailServer.Companion.logger
import com.avail.server.messages.Message
import com.avail.utility.IO
import com.avail.utility.MutableOrNull
import com.avail.utility.evaluation.Combinator.recurse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AcceptPendingException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.logging.Level
import java.util.regex.Pattern
import javax.xml.bind.DatatypeConverter
import kotlin.experimental.and
import kotlin.experimental.xor

/**
 * A `WebSocketAdapter` provides a WebSocket interface to an
 * [Avail&#32;server][AvailServer].
 *
 * @property server
 *   The [Avail&#32;server][AvailServer].
 * @property adapterAddress
 *   The [address][InetSocketAddress] of the [server&#32;socket
 *   channel][AsynchronousServerSocketChannel].
 * @property serverAuthority
 *   The [server][WebSocketAdapter]'s authority, e.g., the host name of this
 *   node.
 * @property heartbeatFailureThreshold
 *   The number of consecutive times the `heartbeatTimeout` is allowed to be
 *   reached before disconnecting the client.
 * @property heartbeatInterval
 *   The time in milliseconds between each [Heartbeat] request made by the
 *   server to the client after receiving a `Heartbeat` from the client.
 * @property heartbeatTimeout
 *   The amount of time, in milliseconds, after which the heartbeat will fail if
 *   a heartbeat is not received from the client by the server.
 * @property onChannelCloseAction
 *   The custom action that is to be called when the input channel is closed in
 *   order to support implementation-specific requirements for the closing of
 *   a channel.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @see [RFC&#32;6455:&#32;The WebSocket&#32;Protocol](http://tools.ietf.org/html/rfc6455)
 *
 * @constructor
 *
 * Construct a new [WebSocketAdapter] for the specified [server][AvailServer]
 * that listens on the specified [socket&#32;address][InetSocketAddress].
 *
 * @param server
 *   An Avail server.
 * @param adapterAddress
 *   The socket address of the listener.
 * @param serverAuthority
 *   The server's authority, e.g., the host name of this node.
 * @param heartbeatFailureThreshold
 *   The number of consecutive times the `heartbeatTimeout` is allowed to be
 *   reached before disconnecting the client.
 * @param heartbeatInterval
 *   The time in milliseconds between each [Heartbeat] request made by the
 *   server to the client after receiving a `Heartbeat` from the client.
 * @param heartbeatTimeout
 *   The amount of time, in milliseconds, after which the heartbeat will fail if
 *   a heartbeat is not received from the client by the server.
 * @param onChannelCloseAction
 *   The custom action that is to be called when the input channel is closed in
 *   order to support implementation-specific requirements for the closing of
 *   a channel. *Does nothing by default.*
 * @throws IOException
 *   If the [server&#32;socket][AsynchronousServerSocketChannel] could not be
 *   opened.
 */
class WebSocketAdapter @Throws(IOException::class) constructor(
	override val server: AvailServer,
	internal val adapterAddress: InetSocketAddress,
	internal val serverAuthority: String,
	private val heartbeatFailureThreshold: Int = defaultHbFailThreshold,
	private val heartbeatInterval: Long = defaultHbInterval,
	private val heartbeatTimeout: Long = defaultHbTimeout,
	override val onChannelCloseAction:
		(DisconnectReason, AvailServerChannel) -> Unit = { _, _ -> })
	: TransportAdapter<AsynchronousSocketChannel>
{
	/** The [server socket channel][AsynchronousServerSocketChannel]. */
	internal val serverChannel = server.runtime.ioSystem().openServerSocket()

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
				thread.name = "WebSocketAdapterTimer" + thread.id
				thread
			})

	/**
	 * A `HttpHeaderState` represents a state of the
	 * [client&#32;handshake][ClientHandshake]
	 * [recognizer][ClientHandshake.readClientHandshake].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private enum class HttpHeaderState
	{
		/** Beginning to read, or previously read an "ordinary" character.  */
		START
		{
			override fun nextState(c: Int) =
				if (c == '\r'.toInt()) FIRST_CARRIAGE_RETURN else START
		},

		/** Just read the first carriage return.  */
		FIRST_CARRIAGE_RETURN
		{
			override fun nextState(c: Int) =
				if (c == '\n'.toInt()) FIRST_LINE_FEED else START
		},

		/** Just read the first carriage return + line feed.  */
		FIRST_LINE_FEED
		{
			override fun nextState(c: Int) =
				if (c == '\r'.toInt()) SECOND_CARRIAGE_RETURN else START
		},

		/** Just read the second carriage return.  */
		SECOND_CARRIAGE_RETURN
		{
			override fun nextState(c: Int) =
				if (c == '\n'.toInt()) SECOND_LINE_FEED else START
		},

		/** Just read the second carriage return + line feed. */
		SECOND_LINE_FEED
		{
			override val isAcceptState get() = true

			override fun nextState(c: Int): HttpHeaderState
			{
				throw RuntimeException("no states after final state")
			}
		};

		/**
		 * Is this an accept [state][HttpHeaderState]?
		 *
		 * @return
		 *   `true` if this is an accept state, `false` otherwise.
		 */
		open val isAcceptState: Boolean get() = false

		/**
		 * Answer the next [state][HttpHeaderState] given a transition on the
		 * specified character.
		 *
		 * @param c
		 *   A character.
		 * @return
		 *   The next state.
		 */
		abstract fun nextState(c: Int): HttpHeaderState
	}

	/**
	 * An `HttpRequestMethod` represents one of the accepted HTTP request
	 * methods.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @see [Method&#32;Definitions](https://tools.ietf.org/html/rfc7231.section-4.3)
	 */
	private enum class HttpRequestMethod
	{
		/**
		 * Request the metadata and content of a particular resource. Must be
		 * free of side effects.
		 */
		GET;

		companion object
		{
			/**
			 * A [map][Map] from HTTP request method names to
			 * [HTTP&#32;request&#32;methods][HttpRequestMethod].
			 */
			private val methodsByName = HashMap<String, HttpRequestMethod>()

			init
			{
				for (method in values())
				{
					methodsByName[method.name.toLowerCase()] = method
				}
			}

			/**
			 * Answer the [request&#32;method][HttpRequestMethod] with the
			 * specified name.
			 *
			 * @param name
			 *   The request method name.
			 * @return
			 *   The named request method, or `null` if no such request method
			 *   exists.
			 */
			internal fun named(name: String): HttpRequestMethod? =
				methodsByName[name.toLowerCase()]
		}
	}

	/**
	 * `HttpStatusCode` represents various HTTP status codes. The enumeration
	 * comprises only those status codes used by the WebSocket implementation;
	 * it is not intended to be comprehensive.
	 *
	 * @property statusCode
	 *   The HTTP status code.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [HttpStatusCode].
	 *
	 * @param statusCode
	 *   The status code.
	 */
	private enum class HttpStatusCode constructor(val statusCode: Int)
	{
		/** Switching protocols.  */
		SWITCHING_PROTOCOLS(101),

		/** Bad request.  */
		BAD_REQUEST(400),

		/** Not found.  */
		NOT_FOUND(404),

		/** Method not allowed.  */
		METHOD_NOT_ALLOWED(405)
	}

	/**
	 * A `ClientRequest` represents an arbitrary client handshake.
	 *
	 * @property method
	 *   The [request&#32;method][HttpRequestMethod].
	 * @property uri
	 *   The request URI.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [ClientRequest].
	 *
	 * @param method
	 *   The [request&#32;method][HttpRequestMethod].
	 * @param uri
	 *   The request URI.
	 * @param headers
	 *   The parsed headers.
	 */
	private open class ClientRequest internal constructor(
		internal val method: HttpRequestMethod,
		internal val uri: String,
		headers: Map<String, String>)
	{
		/** The HTTP headers.  */
		internal val headers = Collections.unmodifiableMap(headers)

		companion object
		{
			/** A [Pattern] for splitting HTTP headers.  */
			private val splitHeaders = Pattern.compile("(?:\r\n)+")

			/** A [Pattern] for identifying one or more spaces.  */
			private val manySpaces = Pattern.compile(" +")

			/**
			 * Write an appropriate HTTP error response to the specified
			 * [channel][WebSocketChannel].
			 *
			 * @param channel
			 *   A channel.
			 * @param statusCode
			 *   The HTTP status code.
			 * @param reason
			 *   The reason message.
			 * @see [Status&#32;Code&#32;Definitions](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html)
			 */
			internal fun badRequest(
				channel: WebSocketChannel,
				statusCode: HttpStatusCode,
				reason: String)
			{
				val formatter = Formatter()
				formatter.format(
					"HTTP/1.1 %03d %s\r\n\r\n"
						+ "<html><head><title>Bad Request</title></head>"
						+ "<body><strong>%2\$s</strong></body></html>",
					statusCode.statusCode,
					reason)
				val bytes = StandardCharsets.US_ASCII.encode(
					formatter.toString())
				val transport = channel.transport
				SimpleCompletionHandler<Int>(
					{
						if (bytes.hasRemaining())
						{
							handler.guardedDo {
								transport.write(bytes, dummy, handler)
							}
						}
						else
						{
							channel.scheduleClose(BadMessageDisconnect)
						}
					},
					{
						logger.log(
							Level.WARNING,
							"unable to write HTTP response to $channel",
							throwable)
						channel.closeImmediately(
							CommunicationErrorDisconnect(throwable))
					}
				).guardedDo { transport.write(bytes, dummy, handler) }
			}

			/**
			 * Answer the parsed [request][ClientRequest]. If the headers do not
			 * describe a valid request, then
			 * [fail&#32;the&#32;connection][badRequest] and answer `null`.
			 *
			 * @param channel
			 *   A [channel][WebSocketChannel].
			 * @param adapter
			 *   A [adapter][WebSocketAdapter].
			 * @param headersText
			 *   The HTTP headers, as a single string. The individual headers
			 *   are separated by carriage return + line feed.
			 * @return
			 *   The parsed headers, or `null` if the specified headers do not
			 *   constitute a valid request.
			 */
			internal fun readRequest(
				channel: WebSocketChannel,
				adapter: WebSocketAdapter,
				headersText: String): ClientRequest?
			{
				val headers = splitHeaders.split(headersText)
				// Deal with the Request-Line specially.
				val requestLine = headers[0]
				val requestParts = manySpaces.split(requestLine)
				if (requestParts.size != 3)
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid Request-Line")
					return null
				}
				val method = HttpRequestMethod.named(requestParts[0])
				if (method == null)
				{
					badRequest(
						channel,
						HttpStatusCode.METHOD_NOT_ALLOWED,
						"Method Not Allowed")
					return null
				}
				if (!requestParts[2].equals(
						"HTTP/1.1",
						ignoreCase = true))
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid HTTP Version")
					return null
				}
				// Parse the remaining lines into a map.
				val map = HashMap<String, String>()
				for (i in 1 until headers.size - 1)
				{
					val pair = headers[i].split(":".toRegex(), 2)
					map[pair[0].trim { it <= ' ' }.toLowerCase()] =
						pair[1].trim { it <= ' ' }
				}
				// Validate the request.
				val host = map["host"]
				if (host != null)
				{
					val hostParts = host.split(":".toRegex(), 2)
					if (!adapter.serverAuthority.equals(
							hostParts[0],
							ignoreCase = true))
					{
						badRequest(
							channel,
							HttpStatusCode.BAD_REQUEST,
							String.format(
								"Invalid Server Authority (%s != %s)",
								adapter.serverAuthority,
								hostParts[0]))
						return null
					}
					if (hostParts.size == 2
						&& adapter.adapterAddress.port
						!= Integer.parseInt(hostParts[1]))
					{
						badRequest(
							channel,
							HttpStatusCode.BAD_REQUEST,
							"Invalid Port Number")
						return null
					}
				}
				else
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Host Not Specified")
					return null
				}
				return ClientRequest(method, requestParts[1], map)
			}

			/**
			 * Read a [client&#32;request][ClientRequest] from the specified
			 * [channel][WebSocketChannel].
			 *
			 * @param channel
			 *   A channel.
			 * @param adapter
			 *   A [adapter][WebSocketAdapter].
			 * @param continuation
			 *   A continuation that processes a valid request.
			 */
			internal fun receiveThen(
				channel: WebSocketChannel,
				adapter: WebSocketAdapter,
				continuation: (ClientRequest)->Unit)
			{
				val bytes = ByteArrayOutputStream(1024)
				val buffer = ByteBuffer.allocate(1024)
				val state = MutableOrNull(HttpHeaderState.START)
				val transport = channel.transport
				SimpleCompletionHandler<Int>(
					{
						if (remoteEndClosed(transport, value))
						{
							return@SimpleCompletionHandler
						}
						buffer.flip()
						while (buffer.hasRemaining()
							&& !state.value().isAcceptState)
						{
							state.value = state.value().nextState(
								buffer.get().toInt())
						}
						if (buffer.hasRemaining())
						{
							badRequest(
								channel,
								HttpStatusCode.BAD_REQUEST,
								"Data Following Headers")
						}
						else
						{
							buffer.rewind()
							bytes.write(
								buffer.array(),
								buffer.position(),
								buffer.remaining())
							if (!state.value().isAcceptState)
							{
								buffer.clear()
								handler.guardedDo {
									transport.read(buffer, dummy, handler)
								}
							}
							else
							{
								val request = readRequest(
									channel,
									adapter,
									String(
										bytes.toByteArray(),
										StandardCharsets.US_ASCII))
								if (request != null)
								{
									continuation(request)
								}
							}
						}
					},
					{
						logger.log(
							Level.WARNING,
							"failed while attempting to read client handshake",
							throwable)
						channel.closeImmediately(
							CommunicationErrorDisconnect(throwable))
					}
				).guardedDo { transport.read(buffer, dummy, handler) }
			}
		}
	}

	/**
	 * A `ClientHandshake` represents a WebSocket client handshake.
	 *
	 * @property key
	 *   The WebSocket key.
	 * @property protocols
	 *   The requested protocols.
	 * @property extensions
	 *   The requested extensions.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [ClientHandshake].
	 *
	 * @param request
	 *   The [request][ClientRequest].
	 * @param key
	 *   The WebSocket key.
	 * @param protocols
	 *   The requested protocols.
	 * @param extensions
	 *   The requested extensions.
	 */
	private class ClientHandshake private constructor(
		request: ClientRequest,
		internal val key: ByteArray,
		internal val protocols: List<String>,
		internal val extensions: List<String>)
		: ClientRequest(request.method, request.uri, request.headers)
	{
		companion object
		{
			/** A [Pattern] to recognize space padded commas.  */
			private val paddedComma = Pattern.compile(" *, *")

			/**
			 * Answer a [client&#32;handshake][ClientHandshake] based on the
			 * specified [request][ClientRequest]. If the headers do not
			 * describe a valid WebSocket client handshake, then
			 * [fail&#32;the&#32;connection][ClientRequest.badRequest] and
			 * answer `null`.
			 *
			 * @param channel
			 *   A [channel][WebSocketChannel].
			 * @param request
			 *   The request.
			 * @return
			 *   A client handshake, or `null` if the specified headers do not
			 *   constitute a valid WebSocket client handshake.
			 */
			internal fun readClientHandshake(
				channel: WebSocketChannel,
				request: ClientRequest): ClientHandshake?
			{
				val map = request.headers
				if (!"websocket".equals(map["upgrade"], ignoreCase = true))
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid Upgrade Header")
					return null
				}
				val connection = map["connection"]
				if (connection != null)
				{
					val tokens = paddedComma.split(connection)
					var includesUpgrade = false
					for (token in tokens)
					{
						if ("upgrade".equals(token, ignoreCase = true))
						{
							includesUpgrade = true
							break
						}
					}
					if (!includesUpgrade)
					{
						badRequest(
							channel,
							HttpStatusCode.BAD_REQUEST,
							"Invalid Connection Header")
						return null
					}
				}
				else
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Missing Connection Header")
					return null
				}
				if ("13" != map["sec-websocket-version"])
				{
					badVersion(
						channel,
						Integer.parseInt(map["sec-websocket-version"]))
					return null
				}
				if (!map.containsKey("sec-websocket-key"))
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Missing WebSocket Key")
					return null
				}
				val key = DatatypeConverter.parseBase64Binary(
					map["sec-websocket-key"])
				if (key.size != 16)
				{
					badRequest(
						channel,
						HttpStatusCode.BAD_REQUEST,
						"Invalid WebSocket Key")
					return null
				}
				val protocols = listOf(
					*if (map.containsKey("sec-websocket-protocol"))
						paddedComma.split(map["sec-websocket-protocol"])
					else
						arrayOfNulls<String>(0))
				val extensions = listOf(
					*if (map.containsKey("sec-websocket-extensions"))
						paddedComma.split(map["sec-websocket-extensions"])
					else
						arrayOfNulls<String>(0))
				return ClientHandshake(request, key, protocols, extensions)
			}

			/**
			 * Write an HTTP error response to the specified
			 * [channel][WebSocketChannel] that tells the client which WebSocket
			 * versions the [adapter][WebSocketAdapter] supports.
			 *
			 * @param channel
			 *   A channel.
			 * @param badVersion
			 *   The (unsupported) WebSocket version requested by the client.
			 */
			internal fun badVersion(channel: WebSocketChannel, badVersion: Int)
			{
				val formatter = Formatter()
				formatter.format(
					"HTTP/1.1 %03d Bad Request\r\n"
						+ "Sec-WebSocket-Version: 13\r\n"
						+ "<html><head><title>Bad Handshake</title></head>"
						+ "<body>"
						+ "<strong>WebSocket Version %d Is Not Supported</strong>"
						+ "</body></html>",
					HttpStatusCode.BAD_REQUEST.statusCode,
					badVersion)
				val bytes = StandardCharsets.US_ASCII.encode(
					formatter.toString())
				val transport = channel.transport
				SimpleCompletionHandler<Int>(
					{
						if (bytes.hasRemaining())
						{
							handler.guardedDo {
								transport.write(bytes, dummy, handler)
							}
						}
						else
						{
							channel.scheduleClose(MismatchDisconnect)
						}
					},
					{
						logger.log(
							Level.WARNING,
							"unable to write HTTP response to $channel",
							throwable)
						channel.closeImmediately(
							CommunicationErrorDisconnect(throwable))
					}
				).guardedDo { transport.write(bytes, dummy, handler) }
			}
		}
	}

	/**
	 * A `ServerHandshake` represents a WebSocket server handshake.
	 *
	 * @property protocols
	 *   The selected protocols.
	 * @property extensions
	 *   The selected extensions.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [ServerHandshake].
	 *
	 * @param key
	 *   The client-supplied WebSocket key.
	 * @param protocols
	 *   The selected protocols.
	 * @param extensions
	 *   The selected extensions.
	 */
	private class ServerHandshake internal constructor(
		key: ByteArray,
		internal val protocols: List<String>,
		internal val extensions: List<String>)
	{
		/** The WebSocket accept key. */
		internal val acceptKey: String

		/**
		 * Compute the WebSocket accept key from the client-supplied key.
		 *
		 * @param key
		 *   The client-supplied WebSocket key.
		 * @return
		 *   The WebSocket accept key.
		 */
		private fun computeAcceptKey(key: ByteArray): String
		{
			val digest: MessageDigest
			try
			{
				digest = MessageDigest.getInstance("SHA-1")
			}
			catch (e: NoSuchAlgorithmException)
			{
				throw RuntimeException("SHA-1 not available", e)
			}

			val stringKey =
				DatatypeConverter.printBase64Binary(key) +
					"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
			val acceptBytes =
				digest.digest(stringKey.toByteArray(StandardCharsets.US_ASCII))
			return DatatypeConverter.printBase64Binary(acceptBytes)
		}

		init
		{
			this.acceptKey = computeAcceptKey(key)
		}

		/**
		 * Send the [server&#32;handshake][ServerHandshake] across the specified
		 * [channel][AsynchronousSocketChannel].
		 *
		 * @param channel
		 *   A channel.
		 * @param continuation
		 *   What to do after sending the server handshake.
		 */
		fun sendThen(
			channel: AsynchronousSocketChannel,
			continuation: ()->Unit)
		{
			val formatter = Formatter()
			formatter.format(
				"HTTP/1.1 %03d Switching Protocols\r\n"
					+ "Upgrade: websocket\r\n"
					+ "Connection: Upgrade\r\n"
					+ "Sec-WebSocket-Accept: %s\r\n",
				HttpStatusCode.SWITCHING_PROTOCOLS.statusCode,
				acceptKey)
			if (protocols.isNotEmpty())
			{
				formatter.format("Sec-WebSocket-Protocol: ")
				var first = true
				for (protocol in protocols)
				{
					if (!first)
					{
						formatter.format(", ")
					}
					formatter.format("%s", protocol)
					first = false
				}
				formatter.format("\r\n")
			}
			if (extensions.isNotEmpty())
			{
				formatter.format("Sec-WebSocket-Extensions: ")
				var first = true
				for (extension in extensions)
				{
					if (!first)
					{
						formatter.format(", ")
					}
					formatter.format("%s", extension)
					first = false
				}
				formatter.format("\r\n")
			}
			formatter.format("\r\n")
			val bytes = StandardCharsets.US_ASCII.encode(
				formatter.toString())
			SimpleCompletionHandler<Int>(
				{
					if (bytes.hasRemaining())
					{
						handler.guardedDo {
							channel.write(bytes, dummy, handler)
						}
					}
					else
					{
						continuation()
					}
				},
				{
					logger.log(
						Level.WARNING,
						"unable to write HTTP response to $channel",
						throwable)
					IO.close(channel)
				}
			).guardedDo { channel.write(bytes, dummy, handler) }
}
	}

	/**
	 * Asynchronously accept incoming connections.
	 */
	private fun acceptConnections()
	{
		SimpleCompletionHandler<AsynchronousSocketChannel>(
			{
				// Asynchronously accept a subsequent connection.
				handler.guardedDo { serverChannel.accept(dummy, handler) }
				val channel =
					WebSocketChannel(
						this@WebSocketAdapter,
						value,
						heartbeatFailureThreshold,
						heartbeatInterval,
						heartbeatTimeout,
						onChannelCloseAction)
				// Process the client request.
				ClientRequest.receiveThen(channel, this@WebSocketAdapter) {
					request -> processRequest(request, channel)
				}
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
	 * Process the specified [request][ClientRequest].
	 *
	 * @param request
	 *   The request.
	 * @param channel
	 *   The [channel][WebSocketChannel] along which the request arrived.
	 */
	private fun processRequest(
		request: ClientRequest,
		channel: WebSocketChannel)
	{
		// Process a GET request.
		if (request.method == HttpRequestMethod.GET)
		{
			// Process a WebSocket request.
			if (request.headers.containsKey("upgrade"))
			{
				val handshake =
					ClientHandshake.readClientHandshake(channel, request)
				if (handshake != null)
				{
					if (handshake.uri != "/avail")
					{
						ClientRequest.badRequest(
							channel,
							HttpStatusCode.NOT_FOUND,
							"Not Found")
					}
					else
					{
						val empty = emptyList<String>()
						val serverHandshake =
							ServerHandshake(handshake.key, empty, empty)
						serverHandshake.sendThen(channel.transport) {
							channel.handshakeSucceeded()
							readMessage(channel)
							channel.heartbeat.sendHeartbeat()
						}
					}
				}
			}
			else
			{
				ClientRequest.badRequest(
					channel,
					HttpStatusCode.NOT_FOUND,
					"Not Found")
			}
		}
		else
		{
			ClientRequest.badRequest(
				channel,
				HttpStatusCode.METHOD_NOT_ALLOWED,
				"Method Not Allowed")
		}
	}

	/**
	 * `Opcode` represents a WebSocket opcode.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Suppress("unused")
	private enum class Opcode
	{
		/*
		 * Do not change the order of these values! Their ordinals correspond
		 * to the WebSocket opcodes, and the adapter uses this ordinals to
		 * dispatch control.
		 */

		/** A continuation frame. */
		CONTINUATION
		{
			override val isValid get() = true
		},

		/** A text frame. */
		TEXT
		{
			override val isValid get() = true
		},

		/** A binary frame. */
		BINARY
		{
			override val isValid get() = true
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
			override val isValid get() = true
		},

		/** A ping frame. */
		PING
		{
			override val isValid get() = true
		},

		/** A pong frame. */
		PONG
		{
			override val isValid get() = true
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

		/** `true` if the opcode is valid, `false` otherwise. */
		open val isValid get() = false

		companion object
		{
			/** An array of all [Opcode] enumeration values.  */
			val all = values()
		}
	}

	/**
	 * `WebSocketStatusCode` represents one of the generic WebSocket status
	 * codes.
	 *
	 * @property statusCode
	 *   The WebSocket status code.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new [WebSocketStatusCode].
	 *
	 * @param statusCode
	 *   The WebSocket status code.
	 */
	@Suppress("unused")
	private enum class WebSocketStatusCode constructor(val statusCode: Int)
	{
		/** Normal closure. */
		NORMAL_CLOSURE(1000),

		/** Endpoint is going away. */
		ENDPOINT_GOING_AWAY(1001),

		/** Protocol error. */
		PROTOCOL_ERROR(1002),

		/** Unsupported message. */
		UNSUPPORTED_MESSAGE(1003),

		/** Reserved. */
		RESERVED_1004(1004),

		/** No status code. */
		NO_STATUS_CODE(1005),

		/** No [CLOSE][Opcode.CLOSE] [frame][Frame]. */
		NO_CLOSE(1006),

		/** Bad data. */
		BAD_DATA(1007),

		/** Bad policy. */
		BAD_POLICY(1008),

		/** Received message was too big. */
		MESSAGE_TOO_BIG(1009),

		/** Unsupported extension. */
		UNSUPPORTED_EXTENSION(1010),

		/** Server error. */
		SERVER_ERROR(1011),

		/** Bad TLS handshake. */
		BAD_TLS_HANDSHAKE(1015);
	}

	/**
	 * `Frame` represents a WebSocket frame.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private class Frame
	{
		/** Is this the final fragment of the message? */
		internal var isFinalFragment: Boolean = false

		/** Is this fragment masked? */
		internal var isMasked: Boolean = false

		/** The [opcode][Opcode]. */
		internal var opcode: Opcode? = null

		/** The length of the payload. */
		internal var payloadLength: Long = 0

		/** The masking key (valid only if [isMasked] is `true`). */
		internal var maskingKey: ByteBuffer? = null

		/**
		 * The payload. This must not be
		 * [allocated&#32;directly][ByteBuffer.allocateDirect], as access to the
		 * [backing&#32;array][ByteBuffer.array] is required.
		 */
		internal var payloadData: ByteBuffer? = null

		/**
		 * Answer a [buffer][ByteBuffer] that encodes the WebSocket
		 * [frame][Frame].
		 *
		 * @return
		 *   A buffer.
		 */
		fun asByteBuffer(): ByteBuffer
		{
			assert(opcode!!.isValid)
			assert(payloadLength == payloadData!!.limit().toLong())
			var len = payloadLength.toInt()
			assert(len.toLong() == payloadLength)
			// Compute the length of the entire frame.
			len += 2
			val ext =
				when
				{
					payloadLength < 126 -> 0
					payloadLength < 65536 -> 2
					else -> 8
				}
			len += ext
			len += if (isMasked) 4 else 0
			val buffer = ByteBuffer.allocateDirect(len)
			buffer.put(
				((if (isFinalFragment) 0x80 else 0x00)
					or opcode!!.ordinal).toByte())
			buffer.put(
				((if (isMasked) 0x80 else 0x00)
					or when
					{
						payloadLength < 126 -> payloadLength.toInt()
						payloadLength < 65536 -> 126
						else -> 127
					}).toByte())
			when (ext)
			{
				2 -> buffer.putShort(payloadLength.toShort())
				8 -> buffer.putLong(payloadLength)
				else -> {}
			}
			val payload = payloadData!!
			payload.rewind()
			if (isMasked)
			{
				val mask = maskingKey!!
				buffer.put(mask)
				mask.rewind()
				for (i in 0 until payloadLength.toInt())
				{
					val j = i and 3
					buffer.put((payload.get(i) xor mask.get(j)))
				}
			}
			else
			{
				buffer.put(payload)
			}
			assert(!buffer.hasRemaining())
			buffer.rewind()
			return buffer
		}
	}

	/**
	 * Read a complete message from the specified [channel][WebSocketChannel]. A
	 * complete message may span multiple WebSocket [frames][Frame].
	 *
	 * @param channel
	 *   A channel.
	 */
	override fun readMessage(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		val strongChannel = channel as WebSocketChannel
		val bytes = ByteArrayOutputStream(1024)
		val processMessage = {
			val message = Message(bytes.toByteArray(), strongChannel.state)
			strongChannel.receiveMessage(message)
		}
		recurse { readFrame ->
			readFrameThen(strongChannel) { frame ->
				try
				{
					bytes.write(frame.payloadData!!.array())
				}
				catch (e: IOException)
				{
					assert(false) { "This never happens!" }
					throw RuntimeException(e)
				}
				when (val opcode = frame.opcode!!)
				{
					Opcode.CONTINUATION ->
					{
						// This isn't legal as the first frame of a
						// message.
						fail(
							strongChannel,
							WebSocketStatusCode.PROTOCOL_ERROR,
							"message cannot begin with continuation frame")
						return@readFrameThen
					}
					Opcode.TEXT ->
					{
						if (strongChannel.state.generalBinary)
						{
							val failMsg =
								"only binary frames expected but received " +
								"text frame"
							fail(
								strongChannel,
								WebSocketStatusCode.UNSUPPORTED_MESSAGE,
								failMsg,
								UnsupportedFormatDisconnect(failMsg))
						}
					}
					Opcode.BINARY ->
					{
						if (strongChannel.state.generalTextIO)
						{
							val failMsg =
								"only text frames expected but received " +
								"binary frame on ${strongChannel.state} channel"
							fail(
								strongChannel,
								WebSocketStatusCode.UNSUPPORTED_MESSAGE,
								failMsg,
								UnsupportedFormatDisconnect(failMsg))
						}
					}
					Opcode.CLOSE ->
					{
						if (!frame.isFinalFragment)
						{
							fail(
								strongChannel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"close must be final fragment")
						}
						else
						{
							receiveClose(strongChannel)
						}
						return@readFrameThen
					}
					Opcode.PING ->
					{
						if (!frame.isFinalFragment)
						{
							fail(
								strongChannel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"ping must be final fragment")
						}
						else
						{
							channel.heartbeat.receiveHeartbeat()
							sendPong(strongChannel, bytes.toByteArray())
						}
						readMessage(strongChannel)
						return@readFrameThen
					}
					Opcode.PONG ->
					{
						if (!frame.isFinalFragment)
						{
							fail(
								strongChannel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"pong must be final fragment")
						}
						// Ignore an unsolicited (but valid) pong.
						readMessage(strongChannel)
						channel.heartbeat.receiveHeartbeat()
						return@readFrameThen
					}
					else ->
					{
						// Fail on receipt of a reserved opcode.
						assert(!opcode.isValid)
						val msg = "opcode ${opcode.ordinal} is reserved"
						fail(
							strongChannel,
							WebSocketStatusCode.PROTOCOL_ERROR,
							msg,
							ProtocolErrorDisconnect(msg))
						return@readFrameThen
					}
				}
				// A frame was processed.
				if (frame.isFinalFragment)
				{
					processMessage()
				}
				else
				{
					readFrameThen(strongChannel) { continuationFrame ->
						try
						{
							bytes.write(continuationFrame.payloadData!!.array())
						}
						catch (e: IOException)
						{
							assert(false) { "This never happens!" }
							throw RuntimeException(e)
						}

						if (continuationFrame.opcode !== Opcode.CONTINUATION)
						{
							fail(
								strongChannel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"received "
									+ continuationFrame.opcode!!.name
									+ " instead of continuation frame")
						}
						else if (!continuationFrame.isFinalFragment)
						{
							readFrame()
						}
						else
						{
							processMessage()
						}
					}
				}
			}
		}
	}

	/**
	 * Send a [frame][Frame] bearing user data over the specified
	 * [channel][WebSocketChannel].
	 *
	 * @param channel
	 *   A channel.
	 * @param payload
	 *   A payload.
	 * @param success
	 *   What to do after sending the frame.
	 * @param failure
	 *   What to do if sending the frame fails.
	 */
	override fun sendUserData(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>,
		payload: Message,
		success: ()->Unit,
		failure: (Throwable)->Unit)
	{
		val strongChannel = channel as WebSocketChannel
		val content = payload.stringContent
		val buffer = StandardCharsets.UTF_8.encode(content)
		sendFrame(strongChannel, Opcode.TEXT, buffer, success, failure)
	}

	/**
	 * Send a [CLOSE][Opcode.CLOSE] [frame][Frame] over the specified
	 * [channel][WebSocketChannel]. Close the channel when the frame has been
	 * sent.
	 *
	 * @param channel
	 *   A channel.
	 */
	override fun sendClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		if (!channel.transport.isOpen)
		{
			return
		}
		val strongChannel = channel as WebSocketChannel
		val buffer = ByteBuffer.allocateDirect(2)
		buffer.putShort(
			WebSocketStatusCode.NORMAL_CLOSURE.statusCode.toShort())
		sendFrame(
			strongChannel,
			Opcode.CLOSE,
			buffer,
			{
				// Do nothing as attempting to close here will lead to eventual
				// ClosedChannelExceptions
			})
		{
			strongChannel.closeImmediately(
				CommunicationErrorDisconnect(it))
		}
	}

	/**
	 * Send a [CLOSE][Opcode.CLOSE] [frame][Frame] over the specified
	 * [channel][WebSocketChannel]. Close the channel when the frame has been
	 * sent.
	 *
	 * @param channel
	 *   A channel.
	 * @param reason
	 *   The [reason][DisconnectReason] for the sending of the close.
	 */
	override fun sendClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>,
		reason: DisconnectReason)
	{
		if (!channel.transport.isOpen)
		{
			return
		}
		channel.channelCloseHandler.reason = reason
		val strongChannel = channel as WebSocketChannel
		val buffer = ByteBuffer.allocateDirect(2)
		buffer.putShort(
			WebSocketStatusCode.NORMAL_CLOSURE.statusCode.toShort())
		sendFrame(strongChannel, Opcode.CLOSE, buffer, {})
		{
			strongChannel.closeTransport()
		}
	}

	override fun receiveClose(
		channel: AbstractTransportChannel<AsynchronousSocketChannel>)
	{
		if (!channel.transport.isOpen)
		{
			// Presume closed handler already run.
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
		 * The default consecutive number [heartbeatTimeout] is allowed to
		 * expire without receiving a response before the underlying
		 * [WebSocketChannel] is closed.
		 */
		const val defaultHbFailThreshold: Int = 3

		/**
		 * The default time in milliseconds between each [Heartbeat] request
		 * made by the server to the client after receiving a `Heartbeat` from
		 * the client.
		 */
		const val defaultHbInterval: Long = 12000

		/**
		 * The default amount of time, in milliseconds, after which the
		 * heartbeat will fail if a heartbeat is not received from the client by
		 * the server.
		 */
		const val defaultHbTimeout: Long = 15000

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
			result: Int?): Boolean
		{
			assert(result != null)
			if (result == -1)
			{
				logger.log(Level.INFO, "$transport closed")
				IO.close(transport)
				return true
			}
			return false
		}

		/**
		 * Read a WebSocket [frame][Frame].
		 *
		 * @param channel
		 *   A channel.
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readFrameThen(
			channel: WebSocketChannel,
			continuation: (Frame)->Unit)
		{
			val frame = Frame()
			readOpcodeThen(channel, frame) { continuation(frame) }
		}

		/**
		 * Read a WebSocket [opcode][Opcode] and `FIN` bit.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readOpcodeThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit)
		{
			val buffer = ByteBuffer.allocateDirect(1)
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
						val b = buffer.get().toInt()
						assert(!buffer.hasRemaining())
						frame.isFinalFragment = b and 0x80 == 0x80
						frame.opcode = Opcode.all[b and 0x0F]
						readPayloadLengthThen(channel, frame, continuation)
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read opcode",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}

		/**
		 * Read a WebSocket payload length and MASK bit.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readPayloadLengthThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit)
		{
			val buffer = ByteBuffer.allocateDirect(1)
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
						val b = buffer.get().toInt()
						assert(!buffer.hasRemaining())
						frame.isMasked = b and 0x80 == 0x80
						when (val len = b and 0x7F)
						{
							126 -> readPayloadLength2ByteExtensionThen(
								channel, frame, continuation)
							127 -> readPayloadLength8ByteExtensionThen(
								channel, frame, continuation)
							else ->
							{
								frame.payloadLength = len.toLong()
								if (frame.isMasked)
								{
									readMaskingKeyThen(
										channel, frame, continuation)
								}
								else
								{
									readPayloadDataThen(
										channel, frame, continuation)
								}
							}
						}
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read payload size",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}

		/**
		 * Read a 16-bit WebSocket payload length.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readPayloadLength2ByteExtensionThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit)
		{
			val buffer = ByteBuffer.allocateDirect(2)
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
						val len = buffer.short and 0xFFFF.toShort()
						assert(!buffer.hasRemaining())
						if (len < 126)
						{
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"2-byte encoding for length=$len")
						}
						else
						{
							frame.payloadLength = len.toLong()
							if (frame.isMasked)
							{
								readMaskingKeyThen(
									channel, frame, continuation)
							}
							else
							{
								readPayloadDataThen(
									channel, frame, continuation)
							}
						}
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read 2-byte "
						+ "payload size",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}

		/**
		 * Read a 64-bit WebSocket payload length.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readPayloadLength8ByteExtensionThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit)
		{
			val buffer = ByteBuffer.allocateDirect(8)
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
						val len = buffer.long
						assert(!buffer.hasRemaining())
						if (len < 65536)
						{
							// Note that this covers the case where the MSB
							// is set (which is forbidden).
							fail(
								channel,
								WebSocketStatusCode.PROTOCOL_ERROR,
								"8-byte encoding for length=$len")
						}
						else if (len > Message.MAX_SIZE)
						{
							fail(
								channel,
								WebSocketStatusCode.MESSAGE_TOO_BIG,
								"length="
								+ len
								+ " exceeds maximum length of "
								+ Message.MAX_SIZE)
						}
						else
						{
							frame.payloadLength = len
							if (frame.isMasked)
							{
								readMaskingKeyThen(
									channel, frame, continuation)
							}
							else
							{
								readPayloadDataThen(
									channel, frame, continuation)
							}
						}
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read 8-byte "
						+ "payload size",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}

		/**
		 * Read the payload masking key.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readMaskingKeyThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit)
		{
			val buffer = ByteBuffer.allocateDirect(4)
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
						}}
					else
					{
						buffer.flip()
						frame.maskingKey = buffer
						readPayloadDataThen(channel, frame, continuation)
					}
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to read masking key",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
				}
			).guardedDo { transport.read(buffer, dummy, handler) }
		}

		/**
		 * Read the payload data.
		 *
		 * @param channel
		 *   A channel.
		 * @param frame
		 *   The current incoming [frame][Frame].
		 * @param continuation
		 *   What to do after the complete frame has been read.
		 */
		private fun readPayloadDataThen(
			channel: WebSocketChannel,
			frame: Frame,
			continuation: ()->Unit
		) {
			val len = frame.payloadLength.toInt()
			assert(len.toLong() == frame.payloadLength)
			val buffer = ByteBuffer.allocate(len)
			val transport = channel.transport
			SimpleCompletionHandler<Int>(
				{
					if (remoteEndClosed(transport, value)) {
						return@SimpleCompletionHandler
					}
					if (buffer.hasRemaining()) {
						handler.guardedDo {
							transport.read(buffer, dummy, handler)
						}
					} else {
						buffer.flip()
						if (frame.isMasked) {
							val mask = frame.maskingKey!!
							for (i in 0 until frame.payloadLength.toInt()) {
								val j = i and 3
								buffer.put(
									i, (buffer.get(i) xor mask.get(j)))
							}
						}
						assert(buffer.position() == 0)
						frame.payloadData = buffer
						// The complete frame has been read, so invoke the
						// continuation now.
						continuation()
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

		/**
		 * Send a WebSocket [frame][Frame] based on the specified
		 * [opcode][Opcode] and [payload][ByteBuffer].
		 *
		 * @param channel
		 *   A [channel][WebSocketChannel].
		 * @param opcode
		 *   The opcode.
		 * @param payload
		 *   The payload.
		 * @param success
		 *   What to do after sending the frame, or `null` if no post-send
		 *   action is necessary.
		 * @param failure
		 *   What to do if the send fails, or `null` if no failure action is
		 *   necessary.
		 */
		private fun sendFrame(
			channel: WebSocketChannel,
			opcode: Opcode,
			payload: ByteBuffer,
			success: (()->Unit)? = null,
			failure: ((Throwable)->Unit)? = null)
		{
			val frame = Frame()
			frame.isFinalFragment = true
			frame.isMasked = false
			frame.opcode = opcode
			frame.payloadData = payload
			frame.payloadLength = payload.limit().toLong()
			val buffer = frame.asByteBuffer()
			val transport = channel.transport
			SimpleCompletionHandler<Int>(
				{
					if (buffer.hasRemaining())
					{
						handler.guardedDo {
							transport.write(buffer, dummy, handler)
						}
					}
					else success?.invoke()
				},
				{
					logger.log(
						Level.WARNING,
						"failed while attempting to send $opcode",
						throwable)
					channel.closeImmediately(
						CommunicationErrorDisconnect(throwable))
					failure?.invoke(throwable)
				}
			).guardedDo { transport.write(buffer, dummy, handler) }
		}

		/**
		 * Send a [PING][Opcode.PING] [frame][Frame] over the specified
		 * [channel][WebSocketChannel].
		 *
		 * @param channel
		 *   A channel.
		 * @param payloadData
		 *   The payload.
		 * @param success
		 *   What to do after sending the frame.
		 * @param failure
		 *   What to do if sending the frame fails.
		 */
		@Suppress("unused")
		internal fun sendPing(
			channel: WebSocketChannel,
			payloadData: ByteArray,
			success: (()->Unit)?,
			failure: ((Throwable)->Unit)?)
		{
			val buffer = ByteBuffer.wrap(payloadData)
			sendFrame(channel, Opcode.PING, buffer, success, failure)
		}

		/**
		 * Send a [PONG][Opcode.PONG] [frame][Frame] over the specified
		 * [channel][WebSocketChannel].
		 *
		 * @param channel
		 *   A channel.
		 * @param payloadData
		 *   The payload (which was supplied by a leading [PING][Opcode.PING]
		 *   frame).
		 * @param success
		 *   What to do after sending the frame.
		 * @param failure
		 *   What to do if sending the frame fails.
		 */
		internal fun sendPong(
			channel: WebSocketChannel,
			payloadData: ByteArray,
			success: (()->Unit)? = null,
			failure: ((Throwable)->Unit)? = null)
		{
			val buffer = ByteBuffer.wrap(payloadData)
			sendFrame(channel, Opcode.PONG, buffer, success, failure)
		}

		/**
		 * Fail the WebSocket connection.
		 *
		 * @param channel
		 *   A channel.
		 * @param statusCode
		 *   The [status&#32;code][WebSocketStatusCode].
		 * @param reasonMessage
		 *   The reason message.
		 */
		private fun fail(
			channel: WebSocketChannel,
			statusCode: WebSocketStatusCode,
			reasonMessage: String)
		{
			val utf8 = StandardCharsets.UTF_8.encode(reasonMessage)
			val buffer = ByteBuffer.allocateDirect(utf8.limit() + 2)
			buffer.putShort(statusCode.statusCode.toShort())
			buffer.put(utf8)
			sendFrame(channel, Opcode.CLOSE, buffer)
				{ channel.scheduleClose(CommunicationErrorDisconnect(it)) }
		}

		/**
		 * Fail the WebSocket connection.
		 *
		 * @param channel
		 *   A channel.
		 * @param statusCode
		 *   The [status&#32;code][WebSocketStatusCode].
		 * @param reasonMessage
		 *   The reason message.
		 * @param reason
		 *   The [DisconnectReason].
		 */
		private fun fail(
			channel: WebSocketChannel,
			statusCode: WebSocketStatusCode,
			reasonMessage: String,
			reason: DisconnectReason)
		{
			val utf8 = StandardCharsets.UTF_8.encode(reasonMessage)
			val buffer = ByteBuffer.allocateDirect(utf8.limit() + 2)
			buffer.putShort(statusCode.statusCode.toShort())
			buffer.put(utf8)
			channel.channelCloseHandler.reason = reason
			sendFrame(channel, Opcode.CLOSE, buffer) {
				channel.scheduleClose(CommunicationErrorDisconnect(it))
			}
		}
	}
}
