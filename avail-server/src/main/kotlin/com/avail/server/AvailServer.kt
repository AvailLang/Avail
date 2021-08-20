/*
 * AvailServer.kt
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

package com.avail.server

import com.avail.AvailRuntime
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParserException
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedDependencyException
import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.module.A_Module
import com.avail.error.ErrorCodeRangeRegistry
import com.avail.files.FileManager
import com.avail.interpreter.execution.Interpreter
import com.avail.persistence.IndexedFileException
import com.avail.persistence.cache.Repository
import com.avail.server.configuration.AvailServerConfiguration
import com.avail.server.configuration.CommandLineConfigurator
import com.avail.server.configuration.EnvironmentConfigurator
import com.avail.server.error.ServerErrorCode
import com.avail.server.error.ServerErrorCodeRange
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.AvailServerChannel.ProtocolState.BINARY
import com.avail.server.io.AvailServerChannel.ProtocolState.COMMAND
import com.avail.server.io.AvailServerChannel.ProtocolState.ELIGIBLE_FOR_UPGRADE
import com.avail.server.io.AvailServerChannel.ProtocolState.IO
import com.avail.server.io.AvailServerChannel.ProtocolState.VERSION_NEGOTIATION
import com.avail.server.io.RunCompletionDisconnect
import com.avail.server.io.RunFailureDisconnect
import com.avail.server.io.ServerInputChannel
import com.avail.server.io.ServerMessageDisconnect
import com.avail.server.io.SocketAdapter
import com.avail.server.io.WebSocketAdapter
import com.avail.server.messages.CommandMessage
import com.avail.server.messages.CommandParseException
import com.avail.server.messages.LoadModuleCommandMessage
import com.avail.server.messages.Message
import com.avail.server.messages.RunEntryPointCommandMessage
import com.avail.server.messages.SimpleCommandMessage
import com.avail.server.messages.TextCommand
import com.avail.server.messages.UnloadModuleCommandMessage
import com.avail.server.messages.UpgradeCommandMessage
import com.avail.server.messages.VersionCommandMessage
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.messages.binary.editor.ErrorBinaryMessage
import com.avail.server.session.Session
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.json.JSONWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections.sort
import java.util.Collections.synchronizedMap
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * A `AvailServer` manages an Avail environment.
 *
 * @property configuration
 *   The [configuration][AvailServerConfiguration].
 * @property runtime
 *   The [Avail runtime][AvailRuntime] managed by this
 *   [server][AvailServer].
 * @property fileManager
 *   The [FileManager] used to manage files by this [AvailServer].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailServer` that manages the given
 * [Avail runtime][AvailRuntime].
 *
 * @param configuration
 *   An [configuration][AvailServerConfiguration].
 * @param runtime
 *   An Avail runtime.
 * @param fileManager
 *   The [FileManager] used to manage files by this [AvailServer].
 */
class AvailServer constructor(
	@Suppress("MemberVisibilityCanBePrivate")
	val configuration: AvailServerConfiguration,
	val runtime: AvailRuntime,
	val fileManager: FileManager)
{
	init
	{
		fileManager.associateRuntime(runtime)
	}

	/**
	 * The [Avail builder][AvailBuilder] responsible for managing build and
	 * execution tasks.
	 */
	private val builder: AvailBuilder = AvailBuilder(runtime)

	/**
	 * The catalog of pending upgrade requests, as a [map][Map] from [UUID]s to
	 * the continuations that should be invoked to proceed after the client has
	 * satisfied an upgrade request. The continuation is invoked with the
	 * upgraded [channel][AvailServerChannel], the `UUID`, and another
	 * continuation that permits the `AvailServer` to continue processing
	 * [messages][Message] for the upgraded channel.
	 */
	private val pendingUpgrades =
		mutableMapOf<UUID, (AvailServerChannel, UUID, ()->Unit)->Unit>()

	/**
	 * The [Map] from [Session.id] to [Session]. It contains all open
	 * `Session`s
	 */
	val sessions = ConcurrentHashMap<UUID, Session>()

	/**
	 * The [Map] from [AvailServerChannel.id]s to [AvailServerChannel]. It
	 * contains all `AvailServerChannel`s that have not gone through the
	 * [upgrade][upgradeThen] process to either become a
	 * [command channel][AvailServerChannel.ProtocolState.COMMAND] or a child
	 * channel of a `command channel`.
	 */
	val newChannels = ConcurrentHashMap<UUID, AvailServerChannel>()

	/**
	 * Record an upgrade request issued by this `AvailServer` in response to a
	 * [command][TextCommand].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] that requested the upgrade.
	 * @param uuid
	 *   The UUID that identifies the upgrade request.
	 * @param continuation
	 *   What to do with the upgraded [channel][AvailServerChannel].
	 */
	private fun recordUpgradeRequest(
		channel: AvailServerChannel,
		uuid: UUID,
		continuation: (AvailServerChannel, UUID, ()->Unit)->Unit)
	{
		synchronized(pendingUpgrades) {
			pendingUpgrades.put(uuid, continuation)
		}
		channel.recordUpgradeRequest(uuid)
	}

	/**
	 * Discontinue the specified pending upgrade requests.
	 *
	 * @param uuids
	 *   The [UUID]s of the pending upgrades that should be discontinued.
	 */
	fun discontinueUpgradeRequests(uuids: Set<UUID>)
	{
		synchronized(pendingUpgrades) {
			for (uuid in uuids)
			{
				pendingUpgrades.remove(uuid)
			}
		}
	}

	/**
	 * List all [module roots][ModuleRoot].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [MODULE_ROOTS][TextCommand.MODULE_ROOTS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.MODULE_ROOTS)
		val message = newSuccessMessage(channel, command) {
			runtime.moduleRoots().writeOn(this)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * List all [module root paths][ModuleRoots.writePathsOn].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [MODULE_ROOT_PATHS][TextCommand.MODULE_ROOT_PATHS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootPathsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.MODULE_ROOT_PATHS)
		val message = newSuccessMessage(channel, command) {
			runtime.moduleRoots().writePathsOn(this)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * Answer the [module roots path][ModuleRoots.modulePath].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [MODULE_ROOT_PATHS][TextCommand.MODULE_ROOT_PATHS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootsPathThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.MODULE_ROOTS_PATH)
		val message = newSuccessMessage(channel, command) {
			write(runtime.moduleRoots().modulePath)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * List all source modules reachable from the [module roots][ModuleRoots].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [SOURCE_MODULES][TextCommand.SOURCE_MODULES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun sourceModulesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.SOURCE_MODULES)
		runtime.moduleRoots().moduleRootTreesThen { resolved, failed ->
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				at("content") {
					writeArray {
						resolved.forEach { it.writeOn(writer, builder) }
					}
				}
				at("failures") {
					writeArray {
						failed.forEach {
							writeObject {
								at("root") { write(it.first) }
								at("code") { write(it.second.code) }
								System.err.println(it.third)
							}
						}
					}
				}
			}
			channel.enqueueMessageThen(
				Message(writer, channel.state), continuation)

		}
	}

	/**
	 * Provide all source modules' entry points reachable from the
	 * [module roots][ModuleRoots].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [ENTRY_POINTS][TextCommand.ENTRY_POINTS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun entryPointsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.ENTRY_POINTS)
		newSuccessMessagePayloadThen(command) {
			val map = synchronizedMap(mutableMapOf<String, List<String>>())
			builder.traceDirectoriesThen({ name, version, after ->
				val entryPoints = version.getEntryPoints()
				if (entryPoints.isNotEmpty())
				{
					map[name.qualifiedName] = entryPoints
				}
				after()
			}) {
				writeArray {
					map.forEach { (key, value) ->
						writeObject {
							at(key) {
								writeArray {
									value.forEach(this::write)
								}
							}
						}
					}
				}
			}
			channel.enqueueMessageThen(
				Message(this, channel.state), continuation)
		}
	}

	/**
	 * Clear all [binary module repositories][Repository].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [CLEAR_REPOSITORIES][TextCommand.CLEAR_REPOSITORIES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun clearRepositoriesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.CLEAR_REPOSITORIES)
		val message = try
		{
			for (root in runtime.moduleNameResolver.moduleRoots.roots)
			{
				// TODO [RAA] only clear repos w/ resolvers
				root.clearRepository()
			}
			newSimpleSuccessMessage(channel, command)
		}
		catch (e: IndexedFileException)
		{
			newErrorMessage(channel, command, e.localizedMessage)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * Upgrade the specified [channel][AvailServerChannel].
	 *
	 * @param channel
	 *   The channel on which the [response][CommandMessage] should be sent.
	 * @param command
	 *   An [UPGRADE][TextCommand.UPGRADE] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun upgradeThen(
		channel: AvailServerChannel,
		command: UpgradeCommandMessage,
		continuation: ()->Unit)
	{
		if (!channel.state.eligibleForUpgrade)
		{
			val message = newErrorMessage(
				channel, command, "channel not eligible for upgrade")
			channel.enqueueMessageThen(message, continuation)
			return
		}
		val upgrader = synchronized(pendingUpgrades) {
			pendingUpgrades.remove(command.uuid)
		}
		if (upgrader === null)
		{
			val message = newErrorMessage(
				channel, command, "no such upgrade")
			channel.enqueueMessageThen(message, continuation)
			return
		}
		upgrader(channel, command.uuid, continuation)
	}

	/**
	 * Request new text I/O-upgraded [channels][AvailServerChannel].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   The [command][CommandMessage] on whose behalf the upgrade should be
	 *   requested.
	 * @param afterUpgraded
	 *   What to do after the upgrades have been completed by the client. The
	 *   argument is the upgraded channel.
	 * @param afterEnqueuing
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	private fun requestIOTextUpgradesThen(
		channel: AvailServerChannel,
		command: CommandMessage,
		afterUpgraded: (AvailServerChannel)->Unit,
		afterEnqueuing: ()->Unit)
	{
		val uuid = UUID.randomUUID()
		recordUpgradeRequest(channel,uuid) {
				upgradedChannel, receivedUUID, resumeUpgrader ->
			assert(uuid == receivedUUID)
			val oldId = upgradedChannel.id
			upgradedChannel.id = receivedUUID
			upgradedChannel.parentId = channel.id
			upgradedChannel.upgradeToIOChannel()
			newChannels.remove(oldId)
			// TODO [RAA] how to handle not found session?
			sessions[channel.id]?.addChildChannel(upgradedChannel)
			resumeUpgrader()
			afterUpgraded(upgradedChannel)
			logger.log(
				Level.FINEST,
				"Channel [$oldId] upgraded to [$upgradedChannel]")
		}
		channel.enqueueMessageThen(
			newUpgradeRequestMessage(channel, command, uuid),
			afterEnqueuing)
	}

	/**
	 * Request to receive server push notifications.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   An [OPEN_EDITOR][TextCommand.OPEN_EDITOR] command message.
	 * @param afterEnqueuing
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestEditorThen(
		channel: AvailServerChannel,
		command: CommandMessage,
		afterEnqueuing: ()->Unit)
	{
		assert(command.command === TextCommand.OPEN_EDITOR)
		val uuid = UUID.randomUUID()
		recordUpgradeRequest(channel,uuid) {
			upgradedChannel, receivedUUID, resumeUpgrader ->
			assert(uuid == receivedUUID)
			val oldId = upgradedChannel.id
			upgradedChannel.id = receivedUUID
			upgradedChannel.parentId = channel.id
			newChannels.remove(oldId)
			sessions[channel.id]?.addChildChannel(upgradedChannel)
			logger.log(
				Level.FINEST,
				"Channel [$oldId] upgraded to [$upgradedChannel]")
			val message =
				newSimpleSuccessMessage(upgradedChannel, command)
			upgradedChannel.enqueueMessageThen(message) {
				upgradedChannel.state = BINARY
				resumeUpgrader()
			}
		}
		channel.enqueueMessageThen(
			newUpgradeRequestMessage(channel, command, uuid),
			afterEnqueuing)
	}

	/**
	 * Request server notifications be stopped.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   An [OPEN_EDITOR][TextCommand.OPEN_EDITOR] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestSubscribeNotificationsThen(
		channel: AvailServerChannel,
		command: CommandMessage,
		continuation: () -> Unit)
	{
		assert(command.command === TextCommand.SUBSCRIBE_NOTIFICATIONS)
		channel.session?.let {
			it.receiveNotifications()
			channel.enqueueMessageThen(
				newSimpleSuccessMessage(channel, command),
				continuation)
		} ?: channel.enqueueMessageThen(
			newErrorMessage(channel, command, "Could not locate session"),
			continuation)
	}

	/**
	 * Request new file-editing [channel][AvailServerChannel].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   An [OPEN_EDITOR][TextCommand.OPEN_EDITOR] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUnsubscribeNotificationsThen(
		channel: AvailServerChannel,
		command: CommandMessage,
		continuation: () -> Unit)
	{
		assert(command.command === TextCommand.UNSUBSCRIBE_NOTIFICATIONS)
		channel.session?.let {
			it.receiveNotifications(false)
			channel.enqueueMessageThen(
				newSimpleSuccessMessage(channel, command),
				continuation)
		} ?: channel.enqueueMessageThen(
			newErrorMessage(channel, command, "Could not locate session"),
			continuation)
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel] to support
	 * [AvailBuilder.buildTarget] module loading}.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [LOAD_MODULE][TextCommand.LOAD_MODULE] [command][TextCommand].
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForLoadModuleThen(
		channel: AvailServerChannel,
		command: LoadModuleCommandMessage,
		continuation: ()->Unit)
	{
		requestIOTextUpgradesThen(
			channel,
			command,
			{ ioChannel -> loadModule(channel, ioChannel, command) },
			continuation)
	}

	/**
	 * Load the specified [module][ModuleName].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param ioChannel
	 *   The upgraded I/O channel.
	 * @param command
	 *   A [LOAD_MODULE][TextCommand.LOAD_MODULE] command message.
	 */
	internal fun loadModule(
		channel: AvailServerChannel,
		ioChannel: AvailServerChannel,
		command: LoadModuleCommandMessage)
	{
		assert(!channel.state.generalTextIO)
		assert(ioChannel.state.generalTextIO)
		val nothing = {}
		channel.enqueueMessageThen(
			newSuccessMessage(channel, command) { write("begin")},
			nothing)
		val localUpdates = mutableListOf<JSONWriter>()
		val globalUpdates = mutableListOf<JSONWriter>()
		val updater = object : TimerTask()
		{
			override fun run()
			{
				val locals: List<JSONWriter>
				synchronized(localUpdates) {
					locals = localUpdates.toList()
					localUpdates.clear()
				}
				val globals: List<JSONWriter>
				synchronized(globalUpdates) {
					globals = globalUpdates.toList()
					globalUpdates.clear()
				}
				if (locals.isNotEmpty() && globals.isNotEmpty())
				{
					val message = newSuccessMessage(channel, command) {
						writeObject {
							at("local") {
								writeArray { locals.forEach(this::write) }
							}
							at("global") {
								writeArray { globals.forEach(this::write) }
							}
						}
					}
					channel.enqueueMessageThen(message, nothing)
				}
			}
		}
		runtime.timer.schedule(
			updater,
			buildProgressIntervalMillis.toLong(),
			buildProgressIntervalMillis.toLong())
		builder.textInterface = ioChannel.textInterface!!
		builder.buildTarget(
			command.target,
			{ name, size, position, line ->
				val writer = JSONWriter()
				writer.writeObject {
					at("module") { write(name.qualifiedName) }
					at("size") { write(size) }
					at("position") { write(position) }
					at("line") { write(line) }
				}
				synchronized(localUpdates) {
					localUpdates.add(writer)
				}
			},
			{ bytesSoFar, totalBytes ->
				val writer = JSONWriter()
				writer.writeObject {
					at("bytesSoFar") { write(bytesSoFar) }
					at("totalBytes") { write(totalBytes) }
				}
				synchronized(globalUpdates) {
					globalUpdates.add(writer)
				}
			},
			builder.buildProblemHandler)
		updater.cancel()
		updater.run()
		assert(localUpdates.isEmpty())
		assert(globalUpdates.isEmpty())
		channel.enqueueMessageThen(
			newSuccessMessage(channel, command) { write("end") }
		) {
			ioChannel.scheduleClose(ServerMessageDisconnect)
		}
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel] to support
	 * [module unloading][AvailBuilder.unloadTarget].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [LOAD_MODULE][TextCommand.LOAD_MODULE] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForUnloadModuleThen(
		channel: AvailServerChannel,
		command: UnloadModuleCommandMessage,
		continuation: ()->Unit)
	{
		val moduleName: ResolvedModuleName
		try
		{
			moduleName = runtime.moduleNameResolver.resolve(
				command.target, null)
		}
		catch (e: UnresolvedDependencyException)
		{
			val message = newErrorMessage(
				channel, command, e.toString())
			channel.enqueueMessageThen(message) {}
			return
		}
		requestIOTextUpgradesThen(
			channel,
			command,
			{ioChannel -> unloadModule(channel, ioChannel, command, moduleName)},
			continuation)
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel] to support
	 * [builder][AvailBuilder.unloadTarget] unloading all modules}.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   An [UNLOAD_ALL_MODULES][TextCommand.UNLOAD_ALL_MODULES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForUnloadAllModulesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.UNLOAD_ALL_MODULES)
		requestIOTextUpgradesThen(
			channel,
			command,
			{ ioChannel -> unloadModule(channel, ioChannel, command, null) },
			continuation)
	}

	/**
	 * Unload the specified [module][ResolvedModuleName].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param ioChannel
	 *   The upgraded I/O channel.
	 * @param command
	 *   An [UNLOAD_MODULE][TextCommand.UNLOAD_MODULE] or
	 *   [UNLOAD_ALL_MODULES][TextCommand.UNLOAD_ALL_MODULES] command message.
	 * @param target
	 *   The resolved name of the target [module][A_Module], or `null` if all
	 *   modules should be unloaded.
	 */
	private fun unloadModule(
		channel: AvailServerChannel,
		ioChannel: AvailServerChannel,
		command: CommandMessage,
		target: ResolvedModuleName?)
	{
		assert(!channel.state.generalTextIO)
		assert(ioChannel.state.generalTextIO)
		channel.enqueueMessageThen(
			newSuccessMessage(channel, command) { write("begin") }
		) {
			// Do nothing.
		}
		builder.textInterface = ioChannel.textInterface!!
		builder.unloadTarget(target)
		channel.enqueueMessageThen(
			newSuccessMessage(channel, command) { write("end") }
		) {
			ioChannel.scheduleClose(ServerMessageDisconnect)
		}
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel] to support
	 * [builder][AvailBuilder.attemptCommand] command execution}.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [RUN_ENTRY_POINT][TextCommand.RUN_ENTRY_POINT] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForRunThen(
		channel: AvailServerChannel,
		command: RunEntryPointCommandMessage,
		continuation: ()->Unit)
	{
		requestIOTextUpgradesThen(
			channel,
			command,
			{ ioChannel -> run(channel, ioChannel, command) },
			continuation)
	}

	/**
	 * Run the specified command (i.e., entry point expression).
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param ioChannel
	 *   The upgraded I/O channel.
	 * @param command
	 *   A [RUN_ENTRY_POINT][TextCommand.RUN_ENTRY_POINT] command message.
	 */
	private fun run(
		channel: AvailServerChannel,
		ioChannel: AvailServerChannel,
		command: RunEntryPointCommandMessage)
	{
		assert(!channel.state.generalTextIO)
		assert(ioChannel.state.generalTextIO)
		builder.textInterface = ioChannel.textInterface!!
		builder.attemptCommand(
			command.expression,
			{ _, _ ->
				// TODO: [TLS] Disambiguate.
			},
			{ value, cleanup ->
				if (value.isNil)
				{
					val message = newSuccessMessage(channel, command) {
						writeObject {
							at("expression") { write(command.expression) }
							at("result") { writeNull() }
						}
					}
					channel.enqueueMessageThen(message) {
						cleanup.invoke {
							ioChannel.scheduleClose(RunCompletionDisconnect)
						}
					}
					return@attemptCommand
				}
				Interpreter.stringifyThen(
					runtime,
					ioChannel.textInterface!!,
					value
				) { string ->
					val message = newSuccessMessage(channel, command) {
						writeObject {
							at("expression") { write(command.expression) }
							at("result") { write(string) }
						}
					}
					channel.enqueueMessageThen(message) {
						cleanup.invoke {
							ioChannel.scheduleClose(RunCompletionDisconnect)
						}
					}
				}
			},
			{
				ioChannel.scheduleClose(RunFailureDisconnect)
			})
	}

	/**
	 * Report all [fibers][A_Fiber] that have not yet
	 * [retired][ExecutionState.RETIRED] and been reclaimed by garbage
	 * collection.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [ALL_FIBERS][TextCommand.ALL_FIBERS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun allFibersThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === TextCommand.ALL_FIBERS)
		val allFibers = runtime.allFibers()
		val message = newSuccessMessage(channel, command) {
			writeArray {
				for (fiber in allFibers)
				{
					writeObject {
						at("id") { write(fiber.uniqueId()) }
						at("name") { write(fiber.fiberName()) }
					}
				}
			}
		}
		channel.enqueueMessageThen(message, continuation)
	}

	companion object
	{
		/** The [logger][Logger]. */
		val logger: Logger = Logger.getLogger(AvailServer::class.java.name)

		/** The current server protocol version. */
		private const val protocolVersion = 6

		/** The supported client protocol versions. */
		private val supportedProtocolVersions = setOf(protocolVersion)

		/**
		 * Write an `"ok"` field into the JSON object being written.
		 *
		 * @param ok
		 *   `true` if the operation succeeded, `false` otherwise.
		 * @param writer
		 *   A [JSONWriter].
		 */
		private fun writeStatusOn(ok: Boolean, writer: JSONWriter) =
			writer.at("ok") { write(ok) }

		/**
		 * Write a `"command"` field into the JSON object being written.
		 *
		 * @param command
		 *   The [command][TextCommand].
		 * @param writer
		 *   A [JSONWriter].
		 */
		private fun writeCommandOn(command: TextCommand, writer: JSONWriter) =
			writer.at("command") {
				write(command.name.lowercase().replace('_', ' '))
			}

		/**
		 * Write an `"id"` field into the JSON object being written.
		 *
		 * @param commandId
		 *   The command identifier.
		 * @param writer
		 *   A [JSONWriter].
		 */
		private fun writeCommandIdentifierOn(
			commandId: Long,
			writer: JSONWriter
		) = writer.at("id") { write(commandId) }

		/**
		 * Answer an error [message][Message] that incorporates the specified
		 * reason.
		 *
		 * @param channel
		 *   The [AvailServerChannel] the message will be sent on.
		 * @param command
		 *   The [command][CommandMessage] that failed, or `null` if the command
		 *   could not be determined.
		 * @param reason
		 *   The reason for the failure.
		 * @param closeAfterSending
		 *   `true` if the [channel][AvailServerChannel] should be
		 *   [closed][AvailServerChannel.scheduleClose] after transmitting this
		 *   message.
		 * @return
		 *   A message.
		 */
		@JvmOverloads
		internal fun newErrorMessage(
			channel: AvailServerChannel,
			command: CommandMessage?,
			reason: String,
			closeAfterSending: Boolean = false): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(false, writer)
				if (command !== null)
				{
					writeCommandOn(command.command, writer)
					writeCommandIdentifierOn(command.commandId, writer)
				}
				at("reason") { write(reason) }
			}
			return Message(writer, channel.state, closeAfterSending)
		}

		/**
		 * Answer a notification [message][Message] that incorporates the
		 * specified message,
		 *
		 * @param channel
		 *   The [AvailServerChannel] the message will be sent on.
		 * @param message
		 *   The message the notification is meant to contain.
		 * @return
		 *   A message.
		 */
		internal fun newNotificationMessage(
			channel: AvailServerChannel,
			message: String): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeCommandIdentifierOn(0, writer)
				at("message") { write(message) }
			}
			return Message(writer, channel.state)
		}

		/**
		 * Answer a simple [message][Message] that just affirms success.
		 *
		 * @param
		 *   The [AvailServerChannel] this message is for.
		 * @param command
		 *   The [command][CommandMessage] for which this is a response.
		 * @return
		 *   A message.
		 */
		internal fun newSimpleSuccessMessage(
			channel: AvailServerChannel, command: CommandMessage): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
			}
			return Message(writer, channel.state)
		}

		/**
		 * Answer a success [message][Message] that incorporates the specified
		 * generated content.
		 *
		 * @param command
		 *   The [command][CommandMessage] for which this is a response.
		 * @param content
		 *   How to write the content of the message.
		 * @return
		 *   A message.
		 */
		internal fun newSuccessMessage(
			channel: AvailServerChannel,
			command: CommandMessage,
			content: JSONWriter.() -> Unit): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				write("content")
				content()
			}
			return Message(writer, channel.state)
		}

		/**
		 * Provide a success [message][Message] initialized [JSONWriter]
		 * to the specified content writer.
		 *
		 * @param command
		 *   The [command][CommandMessage] for which this is a response.
		 * @param content
		 *   How to write the content of the message.
		 */
		internal fun newSuccessMessagePayloadThen(
			command: CommandMessage,
			content: JSONWriter.() -> Unit)
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				at("content", content)
			}
		}

		/**
		 * Answer an upgrade request [message][Message] that incorporates the
		 * specified [UUID].
		 *
		 * @param channel
		 *   The [AvailServerChannel] requesting the upgrade.
		 * @param command
		 *   The [command][CommandMessage] on whose behalf the upgrade is
		 *   requested.
		 * @param uuid
		 *   The `UUID` that denotes the I/O connection.
		 * @return
		 *   A message.
		 */
		internal fun newUpgradeRequestMessage(
			channel: AvailServerChannel,
			command: CommandMessage,
			uuid: UUID): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				at("upgrade") { write(uuid.toString()) }
			}
			return Message(writer, channel.state)
		}

		/**
		 * Receive a [message][Message] from the specified
		 * [channel][AvailServerChannel].
		 *
		 * @param message
		 *   A message.
		 * @param channel
		 *   The channel on which the message was received.
		 * @param receiveNext
		 *   How to receive the next message from the channel (when the
		 *   `AvailServer` has processed this message sufficiently).
		 */
		fun receiveMessageThen(
			message: Message,
			channel: AvailServerChannel,
			receiveNext: ()->Unit)
		{
			when (channel.state)
			{
				VERSION_NEGOTIATION ->
				{
					val command = TextCommand.VERSION.parse(
						message.stringContent)
					if (command !== null)
					{
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					else
					{
						val rebuttal = newErrorMessage(
							channel,
							null,
							"must negotiate version before issuing "
								+ "other commands",
							true)
						channel.enqueueMessageThen(rebuttal, receiveNext)
					}
				}
				ELIGIBLE_FOR_UPGRADE ->
				{
					try
					{
						val command = TextCommand.parse(message)
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					catch (e: CommandParseException)
					{
						val rebuttal = newErrorMessage(
							channel, null, e.localizedMessage)
						channel.enqueueMessageThen(rebuttal, receiveNext)
					}
					finally
					{
						// Only allow a single opportunity to upgrade the
						// channel, even if the command was gibberish.
						if (channel.state.eligibleForUpgrade)
						{
							channel.state = COMMAND
							channel.server.newChannels.remove(channel.id)
							Session(channel).let {
								channel.server.sessions[it.id] = it
							}
						}
					}
				}
				COMMAND ->
				{
					try
					{
						val command = TextCommand.parse(message)
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					catch (e: CommandParseException)
					{
						val rebuttal = newErrorMessage(
							channel, null, e.localizedMessage)
						channel.enqueueMessageThen(rebuttal, receiveNext)
					}
				}
				IO ->
				{
					val input =
						channel.textInterface!!.inputChannel as
							ServerInputChannel
					input.receiveMessageThen(message, receiveNext)
				}
				BINARY ->
				{
					if (message.content.size < 8)
					{
						channel.enqueueMessageThen(
							ErrorBinaryMessage(
									0, // No transaction id available
									ServerErrorCode.MALFORMED_MESSAGE,
									true,
									"Only received ${message.content.size} bytes.")
								.message) {}
					}
					else
					{
						val buffer = ByteBuffer.wrap(message.content)
						val id = buffer.int
						val commandId = buffer.long
						BinaryCommand.command(id).receiveThen(
							id,
							commandId,
							buffer,
							channel,
							receiveNext)
					}
				}
			}
		}

		/**
		 * Negotiate a version. If the
		 * [requested version][VersionCommandMessage.version] is
		 * [supported][supportedProtocolVersions], then echo this version back
		 * to the client. Otherwise, send a list of the supported versions for
		 * the client to examine. If the client cannot (or does not wish to)
		 * deal with the requested versions, then it must disconnect.
		 *
		 * @param channel
		 *   The [channel][AvailServerChannel] on which the
		 *   [response][CommandMessage] should be sent.
		 * @param command
		 *   A [VERSION][TextCommand.VERSION] command message.
		 * @param continuation
		 *   What to do when sufficient processing has occurred (and the
		 *   `AvailServer` wishes to begin receiving messages again).
		 */
		fun negotiateVersionThen(
			channel: AvailServerChannel,
			command: VersionCommandMessage,
			continuation: ()->Unit)
		{
			if (channel.state.versionNegotiated)
			{
				val message = newErrorMessage(
					channel, command, "version already negotiated")
				channel.enqueueMessageThen(message, continuation)
				return
			}
			val version = command.version
			val message: Message
			if (supportedProtocolVersions.contains(version))
			{
				message = newSuccessMessage(channel, command) { write(version) }
				channel.state = ELIGIBLE_FOR_UPGRADE
			}
			else
			{
				message = newSuccessMessage(channel, command) {
					writeObject {
						at("supported") {
							writeArray {
								supportedProtocolVersions.forEach(this::write)
							}
						}
					}
				}
			}
			channel.enqueueMessageThen(message, continuation)
		}

		/**
		 * List syntax guides for all of the [commands][TextCommand] understood by
		 * the `AvailServer`.
		 *
		 * @param channel
		 *   The [channel][AvailServerChannel] on which the
		 *   [response][CommandMessage] should be sent.
		 * @param command
		 *   A [COMMANDS][TextCommand.COMMANDS] command message.
		 * @param continuation
		 *   What to do when sufficient processing has occurred (and the
		 *   `AvailServer` wishes to begin receiving messages again).
		 */
		fun commandsThen(
			channel: AvailServerChannel,
			command: SimpleCommandMessage,
			continuation: ()->Unit)
		{
			assert(command.command === TextCommand.COMMANDS)
			val message = newSuccessMessage(channel, command) {
				val commands = TextCommand.all
				val help = commands.mapTo(mutableListOf()) { it.syntaxHelp }
				sort(help)
				writeArray { help.forEach(this::write) }
			}
			channel.enqueueMessageThen(message, continuation)
		}

		/**
		 * The progress interval for [building][loadModule], in milliseconds.
		 */
		private const val buildProgressIntervalMillis = 200

		/**
		 * Obtain the [configuration][AvailServerConfiguration] of the
		 * `AvailServer`.
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
		private fun configure(
			fileManager: FileManager,
			args: Array<String>): AvailServerConfiguration
		{
			val configuration = AvailServerConfiguration(fileManager)
			val environmentConfigurator = EnvironmentConfigurator(configuration)
			environmentConfigurator.updateConfiguration()
			val commandLineConfigurator =
				CommandLineConfigurator(configuration, args, System.out)
			commandLineConfigurator.updateConfiguration()
			return configuration
		}

		/**
		 * The entry point for command-line invocation of the
		 * [Avail server][AvailServer].
		 *
		 * @param args
		 *   The command-line arguments.
		 */
		@JvmStatic
		fun main(args: Array<String>)
		{
			ErrorCodeRangeRegistry.register(ServerErrorCodeRange)
			val fileManager = FileManager()
			val configuration: AvailServerConfiguration
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
			val server = AvailServer(configuration, runtime, fileManager)
			try
			{
				if (configuration.startWebSocketAdapter)
				{
					WebSocketAdapter(
						server,
						InetSocketAddress(
							configuration.serverAuthority,
							configuration.serverPort),
						configuration.serverAuthority)
				}
				else
				{
					SocketAdapter(
						server,
						InetSocketAddress(
							configuration.serverAuthority,
							configuration.serverPort))
				}

				// Prevent the Avail server from exiting.
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
				server.fileManager.close()
				runtime.destroy()
			}
		}
	}
}
