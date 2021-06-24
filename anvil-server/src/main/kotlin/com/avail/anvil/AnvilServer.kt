/*
 * AnvilServer.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.anvil

import com.avail.AvailRuntime
import com.avail.anvil.MessageOrigin.SERVER
import com.avail.anvil.configuration.AnvilServerConfiguration
import com.avail.anvil.configuration.CommandLineConfigurator
import com.avail.anvil.configuration.EnvironmentConfigurator
import com.avail.anvil.io.AnvilServerChannel
import com.avail.anvil.io.AnvilServerChannel.ProtocolState.READY
import com.avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_NEGOTIATION
import com.avail.anvil.io.AnvilServerChannel.ProtocolState.VERSION_REBUTTED
import com.avail.anvil.io.BadProtocolVersion
import com.avail.anvil.io.InternalErrorCloseReason
import com.avail.anvil.io.SocketAdapter
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleNameResolver
import com.avail.builder.RenamesFileParserException
import com.avail.files.FileManager
import com.avail.utility.configuration.ConfigurationException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Semaphore
import java.util.logging.Level.FINE
import java.util.logging.Level.FINER
import java.util.logging.Logger
import javax.annotation.concurrent.GuardedBy

/**
 * An `AnvilServer` manages an Avail environment on behalf of Anvil, the Avail
 * IDE.
 *
 * @property configuration
 *   The [configuration][AnvilServerConfiguration].
 * @property runtime
 *   The [Avail&#32;runtime][AvailRuntime] managed by this
 *   [server][AnvilServer].
 * @property fileManager
 *   The [FileManager] used to manage files by this [AnvilServer].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AnvilServer` that manages the given
 * [Avail runtime][AvailRuntime].
 *
 * @param configuration
 *   An [configuration][AnvilServerConfiguration].
 * @param runtime
 *   An Avail runtime.
 * @param fileManager
 *   The [FileManager] used to manage files by this [AnvilServer].
 */
class AnvilServer constructor (
	@Suppress("MemberVisibilityCanBePrivate")
	val configuration: AnvilServerConfiguration,
	val runtime: AvailRuntime,
	val fileManager: FileManager)
{
	/** The [logger][Logger].  */
	val logger = Logger.getLogger(AnvilServer::class.java.name)!!

	init
	{
		fileManager.associateRuntime(runtime)
	}

	/**
	 * The [Avail&#32;builder][AvailBuilder] responsible for managing build and
	 * execution tasks.
	 */
	private val builder = AvailBuilder(runtime)

	/**
	 * Every connected [AnvilServerChannel], keyed by
	 * [identifier][AnvilServerChannel.channelId]
	 */
	@GuardedBy("itself")
	private val channels = mutableMapOf<Long, AnvilServerChannel>()

	/**
	 * Register the specified [channel][AnvilServerChannel].
	 *
	 * @param channel
	 *   The new channel to register.
	 */
	internal fun registerChannel (channel: AnvilServerChannel) =
		synchronized(channels) {
			channels[channel.channelId] = channel
		}

	/**
	 * Deregister the specified [channel][AnvilServerChannel].
	 *
	 * @param channel
	 *   The defunct channel to deregister.
	 */
	internal fun deregisterChannel (channel: AnvilServerChannel) =
		synchronized(channels) {
			channels.remove(channel.channelId)
		}

	/**
	 * Receive a [message][Message] from the specified
	 * [channel][AnvilServerChannel].
	 *
	 * @param message
	 *   An incoming message.
	 * @param channel
	 *   The channel which supplied the message.
	 * @param after
	 *   How to receive the next message from the channel (when the
	 *   [AnvilServer] has processed this message sufficiently). Used to provide
	 *   high-level flow control.
	 */
	internal fun receiveMessage (
		message: Message,
		channel: AnvilServerChannel,
		after: () -> Unit)
	{
		try
		{
			val conversation = channel.continueConversation(message) {
				StartConversationVisitor(message, channel)
			}
			channel.log(FINER, message) { "dispatch: ${this.tag}" }
			message.visit(conversation, after)
		}
		catch (e: Throwable)
		{
			channel.close(InternalErrorCloseReason(e))
		}
	}

	/**
	 * All [client][MessageOrigin.CLIENT] [messages][Message] that
	 * [start&#32;conversations][Message.canStartConversation] must override
	 * handlers here.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @property channel
	 *   The [AnvilServerChannel] for this conversation.
	 *
	 * @constructor
	 * Construct a new [StartConversationVisitor].
	 *
	 * @param message
	 *   The message that started the new conversation.
	 * @param channel
	 *   The [AnvilServerChannel] for this conversation.
	 */
	inner class StartConversationVisitor constructor (
		message: Message,
		private val channel: AnvilServerChannel
	) : Conversation(message.id, message.allowedSuccessors)
	{
		override fun visit (
			message: NegotiateVersionMessage,
			after: AfterMessage)
		{
			when (channel.state)
			{
				VERSION_NEGOTIATION -> negotiateVersion(message, channel, after)
				else -> check(false)
			}
		}
	}

	/**
	 * Negotiate a version.
	 *
	 * @param message
	 *   The message.
	 * @param channel
	 *   The channel which supplied this message.
	 * @param after
	 *   How to receive the next message from the channel (when the
	 *   [AnvilServer] has processed this message sufficiently). Used to provide
	 *   high-level flow control.
	 */
	private fun negotiateVersion (
		message: NegotiateVersionMessage,
		channel: AnvilServerChannel,
		after: () -> Unit)
	{
		val commonVersions =
			supportedProtocolVersions.intersect(message.versions)
		commonVersions.ifEmpty {
			// No versions were in common, so rebut with our supported
			// versions. Only allow one more attempt to comply, now that we
			// are proactively announcing our supported versions.
			channel.state = VERSION_REBUTTED
			val reply = RebuttedVersionsMessage(
				SERVER, message.id, supportedProtocolVersions)
			return channel.enqueueMessage(
				reply,
				object : Conversation(reply)
				{
					override fun visit(
						message: NegotiateVersionMessage,
						after: AfterMessage
					) = renegotiateVersion(message, channel, after)
				},
				enqueueSucceeded = after
			)
		}
		// Use the newest common version.
		val newestVersion = commonVersions.maxOrNull()!!
		channel.state = READY
		channel.negotiatedVersion = newestVersion
		channel.log(FINE, message) { "accepted version: $newestVersion" }
		channel.enqueueMessage(
			AcceptedVersionMessage(SERVER, message.id, newestVersion),
			enqueueSucceeded = after
		)
	}

	/**
	 * Renegotiate a version.
	 *
	 * @param message
	 *   The message.
	 * @param channel
	 *   The channel which supplied this message.
	 * @param after
	 *   How to receive the next message from the channel (when the
	 *   [AnvilServer] has processed this message sufficiently). Used to provide
	 *   high-level flow control.
	 */
	private fun renegotiateVersion (
		message: NegotiateVersionMessage,
		channel: AnvilServerChannel,
		after: () -> Unit)
	{
		val commonVersions =
			supportedProtocolVersions.intersect(message.versions)
		commonVersions.ifEmpty {
			// No versions were in common, despite having told the client what
			// versions we support, so close the connection; the client had
			// their chance and blew it.
			return channel.close(BadProtocolVersion)
		}
		// Use the newest common version.
		val newestVersion = commonVersions.maxOrNull()!!
		channel.state = READY
		channel.negotiatedVersion = newestVersion
		channel.log(FINE, message) { "accepted version: $newestVersion" }
		channel.enqueueMessage(
			AcceptedVersionMessage(SERVER, message.id, newestVersion),
			enqueueSucceeded = after
		)
	}

	companion object
	{
		/**
		 * The supported client protocol versions:
		 *
		 * 1. Initial protocol version, featuring all original capabilities.
		 */
		private val supportedProtocolVersions = setOf(1)

		/**
		 * A protocol version guaranteed to be invalid, as a safe starting
		 * sentinel for [AnvilServerChannel] to use during initialization.
		 */
		internal const val invalidProtocolVersion = -1

		/**
		 * Obtain the [configuration][AnvilServerConfiguration] of the
		 * `AnvilServer`.
		 *
		 * @param fileManager
		 *   The [FileManager] to be used to manage Avail files.
		 * @param args
		 *   The command-line arguments.
		 * @return
		 *   A viable configuration.
		 * @throws ConfigurationException
		 *   If configuration fails for any reason.
		 */
		@Throws(ConfigurationException::class)
		private fun configure (
			fileManager: FileManager,
			args: Array<String>
		): AnvilServerConfiguration
		{
			val configuration = AnvilServerConfiguration(fileManager)
			val environmentConfigurator = EnvironmentConfigurator(configuration)
			environmentConfigurator.updateConfiguration()
			val commandLineConfigurator =
				CommandLineConfigurator(configuration, args, System.out)
			commandLineConfigurator.updateConfiguration()
			return configuration
		}

		/**
		 * The entry point for command-line invocation of the
		 * [Anvil&#32;server][AnvilServer].
		 *
		 * @param args
		 *   The command-line arguments.
		 */
		@JvmStatic
		fun main (args: Array<String>)
		{
			val fileManager = FileManager()
			val configuration: AnvilServerConfiguration
			val resolver: ModuleNameResolver
			try
			{
				configuration = configure(fileManager, args)
				resolver = configuration.moduleNameResolver()
			}
			catch (e: ConfigurationException)
			{
				System.err.println(e.message)
				return
			}
			catch (e: FileNotFoundException)
			{
				System.err.println(e.message)
				return
			}
			catch (e: RenamesFileParserException)
			{
				System.err.println(e.message)
				return
			}
			catch (e: Throwable)
			{
				System.err.println(e.message)
				return
			}
			val runtime = AvailRuntime(resolver, fileManager)
			val server = AnvilServer(configuration, runtime, fileManager)
			var adapter: SocketAdapter? = null
			try
			{
				adapter = SocketAdapter(
					server,
					InetSocketAddress(
						configuration.serverAuthority,
						configuration.serverPort))
				// Prevent the Anvil server from exiting.
				Semaphore(0).acquire()
			}
			catch (e: NumberFormatException)
			{
				e.printStackTrace()
			}
			catch (e: IOException)
			{
				e.printStackTrace()
			}
			catch (e: InterruptedException)
			{
				e.printStackTrace()
			}
			finally
			{
				adapter?.close()
				server.fileManager.close()
				runtime.destroy()
			}
		}
	}
}
