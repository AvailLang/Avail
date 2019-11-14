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
import com.avail.builder.*
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Module
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.interpreter.Interpreter
import com.avail.persistence.IndexedFileException
import com.avail.persistence.IndexedRepositoryManager
import com.avail.server.configuration.AvailServerConfiguration
import com.avail.server.configuration.CommandLineConfigurator
import com.avail.server.configuration.EnvironmentConfigurator
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.AvailServerChannel.ProtocolState
import com.avail.server.io.AvailServerChannel.ProtocolState.COMMAND
import com.avail.server.io.AvailServerChannel.ProtocolState.ELIGIBLE_FOR_UPGRADE
import com.avail.server.io.AvailServerChannel.ProtocolState.VERSION_NEGOTIATION
import com.avail.server.io.ServerInputChannel
import com.avail.server.io.WebSocketAdapter
import com.avail.server.messages.*
import com.avail.utility.IO
import com.avail.utility.Mutable
import com.avail.utility.MutableOrNull
import com.avail.utility.Nulls.stripNull
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.evaluation.Continuation0
import com.avail.utility.evaluation.Continuation3NotNull
import com.avail.utility.json.JSONWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.Collections.*
import java.util.concurrent.Semaphore
import java.util.logging.Logger
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.Set
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.listOf
import kotlin.collections.set

/**
 * A `AvailServer` manages an Avail environment.
 *
 * @property configuration
 *   The [configuration][AvailServerConfiguration].
 * @property runtime
 *   The [Avail runtime][AvailRuntime] managed by this [server][AvailServer].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailServer` that manages the given [Avail
 * runtime][AvailRuntime].
 *
 * @param configuration
 *   An [configuration][AvailServerConfiguration].
 * @param runtime
 *   An Avail runtime.
 */
class AvailServer constructor(
	@Suppress("MemberVisibilityCanBePrivate")
	val configuration: AvailServerConfiguration,
	val runtime: AvailRuntime)
{
	/**
	 * The [Avail builder][AvailBuilder] responsible for managing build and
	 * execution tasks.
	 */
	private val builder: AvailBuilder = AvailBuilder(runtime)

	/**
	 * The catalog of pending upgrade requests, as a [map][Map] from [UUID]s to
	 * the [continuations][Continuation3NotNull] that should be invoked to
	 * proceed after the client has satisfied an upgrade request. The
	 * continuation is invoked with the upgraded [channel][AvailServerChannel],
	 * the `UUID`, and another [continuation][Continuation0] that permits the
	 * `AvailServer` to continue processing [messages][Message] for the upgraded
	 * channel.
	 */
	private val pendingUpgrades =
		HashMap<UUID, (AvailServerChannel, UUID, ()->Unit)->Unit>()

	/**
	 * Record an upgrade request issued by this `AvailServer` in response to a
	 * [command][Command].
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
	 *   A [MODULE_ROOTS][Command.MODULE_ROOTS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.MODULE_ROOTS)
		val message = newSuccessMessage(command) { writer ->
			runtime.moduleRoots().writeOn(writer)
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
	 *   A [MODULE_ROOT_PATHS][Command.MODULE_ROOT_PATHS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootPathsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.MODULE_ROOT_PATHS)
		val message = newSuccessMessage(command) { writer ->
			runtime.moduleRoots().writePathsOn(writer)
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
	 *   A [MODULE_ROOT_PATHS][Command.MODULE_ROOT_PATHS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun moduleRootsPathThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.MODULE_ROOTS_PATH)
		val message = newSuccessMessage(command) { writer ->
			writer.write(runtime.moduleRoots().modulePath)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * A `ModuleNode` represents a node in a module tree.
	 *
	 * @property name
	 *   The name associated with the [node][ModuleNode].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `ModuleNode`.
	 *
	 * @param name
	 *   The name.
	 */
	internal class ModuleNode constructor(val name: String)
	{
		/** The children of the [node][ModuleNode]. */
		private var modules: MutableList<ModuleNode>? = null

		/** The resources of the [node][ModuleNode]. */
		private var resources: MutableList<ModuleNode>? = null

		/**
		 * The [exception][Throwable] that prevented evaluation of this
		 * [node][ModuleNode].
		 */
		var exception: Throwable? = null

		/**
		 * Add the specified `ModuleNode` as a module.
		 *
		 * @param node
		 *   The child node.
		 */
		fun addModule(node: ModuleNode)
		{
			var list: MutableList<ModuleNode>? = modules
			if (list == null)
			{
				list = ArrayList()
				modules = list
			}
			list.add(node)
		}

		/**
		 * Add the specified `ModuleNode` as a resource.
		 *
		 * @param node
		 *   The child node.
		 */
		fun addResource(node: ModuleNode)
		{
			var list: MutableList<ModuleNode>? = resources
			if (list == null)
			{
				list = ArrayList()
				resources = list
			}
			list.add(node)
		}

		/**
		 * Recursively write the `ModuleNode` to the supplied [JSONWriter].
		 *
		 * @param isRoot
		 *   `true` if the receiver represents a [module root][ModuleRoot],
		 *   `false` otherwise.
		 * @param isResource
		 *   `true` if the receiver represents a resource, `false` otherwise.
		 * @param writer
		 *   A `JSONWriter`.
		 */
		private fun recursivelyWriteOn(
			isRoot: Boolean,
			isResource: Boolean,
			writer: JSONWriter)
		{
			writer.writeObject {
				writer.write("text")
				writer.write(name)
				if (isRoot)
				{
					writer.write("isRoot")
					writer.write(true)
				}
				val mods = modules
				val isPackage = !isRoot && mods != null
				if (isPackage)
				{
					writer.write("isPackage")
					writer.write(true)
				}
				if (isResource)
				{
					writer.write("isResource")
					writer.write(true)
				}
				val res = resources
				if (mods != null || res != null)
				{
					writer.write("state")
					writer.writeObject {
						writer.write("opened")
						writer.write(isRoot)
					}
					var missingRepresentative = !isResource
					writer.write("children")
					writer.writeArray {
						if (mods != null)
						{
							for (mod in mods)
							{
								mod.recursivelyWriteOn(false, false, writer)
								if (mod.name == name)
								{
									missingRepresentative = false
								}
							}
						}
						if (res != null)
						{
							for (r in res)
							{
								r.recursivelyWriteOn(false, true, writer)
							}
						}
					}
					if (missingRepresentative)
					{
						writer.write("missingRepresentative")
						writer.write(true)
					}
				}
				val e = exception
				if (e != null)
				{
					writer.write("error")
					writer.write(e.localizedMessage)
				}
			}
		}

		/**
		 * Write the `ModuleNode` to the supplied [JSONWriter].
		 *
		 * @param writer
		 *   A `JSONWriter`.
		 */
		fun writeOn(writer: JSONWriter)
		{
			recursivelyWriteOn(true, false, writer)
		}
	}

	/**
	 * List all source modules reachable from the [module roots][ModuleRoots].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [SOURCE_MODULES][Command.SOURCE_MODULES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun sourceModulesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.SOURCE_MODULES)
		val message = newSuccessMessage(command) { writer ->
			val roots = runtime.moduleRoots()
			writer.writeArray {
				for (root in roots)
				{
					val tree = MutableOrNull<ModuleNode>()
					val directory = root.sourceDirectory
					if (directory != null)
					{
						try
						{
							Files.walkFileTree(
								Paths.get(directory.absolutePath),
								EnumSet.of(FileVisitOption.FOLLOW_LINKS),
								Integer.MAX_VALUE,
								sourceModuleVisitor(root, tree))
						}
						catch (e: IOException)
						{
							// This shouldn't happen, since we never raise any
							// exceptions in the visitor.
						}
					}
					tree.value().writeOn(writer)
				}
			}
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
	 *   A [ENTRY_POINTS][Command.ENTRY_POINTS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun entryPointsThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.ENTRY_POINTS)
		val message = newSuccessMessage(command) { writer ->
			val map = synchronizedMap(HashMap<String, List<String>>())
			builder.traceDirectories { name, version, after ->
				val entryPoints = version.getEntryPoints()
				if (entryPoints.isNotEmpty())
				{
					map[name.qualifiedName] = entryPoints
				}
				after()
			}
			writer.writeArray {
				for ((key, value) in map)
				{
					writer.writeObject {
						writer.write(key)
						writer.writeArray {
							for (entryPoint in value)
							{
								writer.write(entryPoint)
							}
						}
					}
				}
			}
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * Clear all [binary module repositories][IndexedRepositoryManager].
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [CLEAR_REPOSITORIES][Command.CLEAR_REPOSITORIES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun clearRepositoriesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.CLEAR_REPOSITORIES)
		val message = try
		{
			for (root in runtime.moduleNameResolver().moduleRoots.roots)
			{
				root.clearRepository()
			}
			newSimpleSuccessMessage(command)
		}
		catch (e: IndexedFileException)
		{
			newErrorMessage(command, e.localizedMessage)
		}
		channel.enqueueMessageThen(message, continuation)
	}

	/**
	 * Upgrade the specified [channel][AvailServerChannel].
	 *
	 * @param channel
	 *   The channel on which the [response][CommandMessage] should be sent.
	 * @param command
	 *   An [UPGRADE][Command.UPGRADE] command message.
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
				command, "channel not eligible for upgrade")
			channel.enqueueMessageThen(message, continuation)
			return
		}
		val upgrader = synchronized(pendingUpgrades) {
			pendingUpgrades.remove(command.uuid)
		}
		if (upgrader == null)
		{
			val message = newErrorMessage(
				command, "no such upgrade")
			channel.enqueueMessageThen(message, continuation)
			return
		}
		upgrader(channel, command.uuid, continuation)
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel].
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
	private fun requestUpgradesThen(
		channel: AvailServerChannel,
		command: CommandMessage,
		afterUpgraded: (AvailServerChannel)->Unit,
		afterEnqueuing: ()->Unit)
	{
		val uuid = UUID.randomUUID()
		recordUpgradeRequest(channel,uuid) {
				upgradedChannel, receivedUUID, resumeUpgrader ->
			assert(uuid == receivedUUID)
			upgradedChannel.upgradeToIOChannel()
			resumeUpgrader()
			afterUpgraded(upgradedChannel)
		}
		channel.enqueueMessageThen(
			newIOUpgradeRequestMessage(command, uuid),
			afterEnqueuing)
	}

	/**
	 * Request new I/O-upgraded [channels][AvailServerChannel] to support
	 * [AvailBuilder.buildTarget] module loading}.
	 *
	 * @param channel
	 *   The [channel][AvailServerChannel] on which the
	 *   [response][CommandMessage] should be sent.
	 * @param command
	 *   A [LOAD_MODULE][Command.LOAD_MODULE] [command][Command].
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForLoadModuleThen(
		channel: AvailServerChannel,
		command: LoadModuleCommandMessage,
		continuation: ()->Unit)
	{
		requestUpgradesThen(
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
	 *   A [LOAD_MODULE][Command.LOAD_MODULE] command message.
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
			newSuccessMessage(command) {writer -> writer.write("begin")},
			nothing)
		val localUpdates = ArrayList<JSONWriter>()
		val globalUpdates = ArrayList<JSONWriter>()
		val updater = object : TimerTask()
		{
			override fun run()
			{
				val locals: List<JSONWriter>
				synchronized(localUpdates) {
					locals = ArrayList(localUpdates)
					localUpdates.clear()
				}
				val globals: List<JSONWriter>
				synchronized(globalUpdates) {
					globals = ArrayList(globalUpdates)
					globalUpdates.clear()
				}
				if (locals.isNotEmpty() && globals.isNotEmpty())
				{
					val message = newSuccessMessage(command) { writer ->
						writer.writeObject {
							writer.write("local")
							writer.writeArray {
								for (local in locals)
								{
									writer.write(local)
								}
							}
							writer.write("global")
							writer.writeArray {
								for (global in globals)
								{
									writer.write(global)
								}
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
			{ name, _, position ->
				val writer = JSONWriter()
				writer.writeObject {
					writer.write("module")
					writer.write(name.qualifiedName)
					writer.write("position")
					writer.write(position)
				}
				synchronized(localUpdates) {
					localUpdates.add(writer)
				}
			},
			{ bytesSoFar, totalBytes ->
				val writer = JSONWriter()
				writer.writeObject {
					writer.write("bytesSoFar")
					writer.write(bytesSoFar)
					writer.write("totalBytes")
					writer.write(totalBytes)
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
			newSuccessMessage(command) { writer -> writer.write("end") }
		) {
			IO.close(ioChannel)
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
	 *   A [LOAD_MODULE][Command.LOAD_MODULE] command message.
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
			moduleName = runtime.moduleNameResolver().resolve(
				command.target, null)
		}
		catch (e: UnresolvedDependencyException)
		{
			val message = newErrorMessage(command, e.toString())
			channel.enqueueMessageThen(message) {}
			return
		}
		requestUpgradesThen(
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
	 *   An [UNLOAD_ALL_MODULES][Command.UNLOAD_ALL_MODULES] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForUnloadAllModulesThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.UNLOAD_ALL_MODULES)
		requestUpgradesThen(
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
	 *   An [UNLOAD_MODULE][Command.UNLOAD_MODULE] or
	 *   [UNLOAD_ALL_MODULES][Command.UNLOAD_ALL_MODULES] command message.
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
			newSuccessMessage(command) { writer -> writer.write("begin") }
		) {
			// Do nothing.
		}
		builder.textInterface = ioChannel.textInterface!!
		builder.unloadTarget(target)
		channel.enqueueMessageThen(
			newSuccessMessage(command) { writer -> writer.write("end") }
		) {
			IO.close(ioChannel)
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
	 *   A [RUN_ENTRY_POINT][Command.RUN_ENTRY_POINT] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun requestUpgradesForRunThen(
		channel: AvailServerChannel,
		command: RunEntryPointCommandMessage,
		continuation: ()->Unit)
	{
		requestUpgradesThen(
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
	 *   A [RUN_ENTRY_POINT][Command.RUN_ENTRY_POINT] command message.
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
				if (value.equalsNil())
				{
					val message = newSuccessMessage(command) { writer ->
						writer.writeObject {
							writer.write("expression")
							writer.write(command.expression)
							writer.write("result")
							writer.writeNull()
						}
					}
					channel.enqueueMessageThen(message) {
						cleanup.invoke {
							IO.close(ioChannel)
						}
					}
					return@attemptCommand
				}
				Interpreter.stringifyThen(
					runtime,
					ioChannel.textInterface!!,
					value
				) { string ->
					val message = newSuccessMessage(command) { writer ->
						writer.writeObject {
							writer.write("expression")
							writer.write(command.expression)
							writer.write("result")
							writer.write(string)
						}
					}
					channel.enqueueMessageThen(message) {
						cleanup.invoke {
							IO.close(ioChannel)
						}
					}
				}
			},
			{
				IO.close(ioChannel)
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
	 *   A [ALL_FIBERS][Command.ALL_FIBERS] command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the
	 *   `AvailServer` wishes to begin receiving messages again).
	 */
	fun allFibersThen(
		channel: AvailServerChannel,
		command: SimpleCommandMessage,
		continuation: ()->Unit)
	{
		assert(command.command === Command.ALL_FIBERS)
		val allFibers = runtime.allFibers()
		val message = newSuccessMessage(command) { writer ->
			writer.writeArray {
				for (fiber in allFibers)
				{
					writer.writeObject {
						writer.write("id")
						writer.write(fiber.uniqueId())
						writer.write("name")
						writer.write(fiber.fiberName())
					}
				}
			}
		}
		channel.enqueueMessageThen(message, continuation)
	}

	companion object
	{
		/** The [logger][Logger].  */
		val logger: Logger = Logger.getLogger(AvailServer::class.java.name)

		/** The current server protocol version.  */
		private const val protocolVersion = 4

		/** The supported client protocol versions.  */
		private val supportedProtocolVersions: Set<Int> =
			unmodifiableSet(HashSet(listOf(protocolVersion)))

		/**
		 * Write an `"ok"` field into the JSON object being written.
		 *
		 * @param ok
		 *   `true` if the operation succeeded, `false` otherwise.
		 * @param writer
		 *   A [JSONWriter].
		 */
		private fun writeStatusOn(ok: Boolean, writer: JSONWriter)
		{
			writer.write("ok")
			writer.write(ok)
		}

		/**
		 * Write a `"command"` field into the JSON object being written.
		 *
		 * @param command
		 *   The [command][Command].
		 * @param writer
		 *   A [JSONWriter].
		 */
		private fun writeCommandOn(command: Command, writer: JSONWriter)
		{
			writer.write("command")
			writer.write(command.name.toLowerCase().replace('_', ' '))
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
			writer: JSONWriter)
		{
			writer.write("id")
			writer.write(commandId)
		}

		/**
		 * Answer an error [message][Message] that incorporates the specified
		 * reason.
		 *
		 * @param command
		 *   The [command][CommandMessage] that failed, or `null` if the command
		 *   could not be determined.
		 * @param reason
		 *   The reason for the failure.
		 * @param closeAfterSending
		 *   `true` if the [channel][AvailServerChannel] should be
		 *   [closed][AvailServerChannel.close] after transmitting this message.
		 * @return
		 *   A message.
		 */
		@JvmOverloads
		internal fun newErrorMessage(
			command: CommandMessage?,
			reason: String,
			closeAfterSending: Boolean = false): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(false, writer)
				if (command != null)
				{
					writeCommandOn(command.command, writer)
					writeCommandIdentifierOn(command.commandId, writer)
				}
				writer.write("reason")
				writer.write(reason)
			}
			return Message(writer.toString(), closeAfterSending)
		}

		/**
		 * Answer a simple [message][Message] that just affirms success.
		 *
		 * @param command
		 *   The [command][CommandMessage] for which this is a response.
		 * @return
		 *   A message.
		 */
		internal fun newSimpleSuccessMessage(command: CommandMessage): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
			}
			return Message(writer.toString())
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
			command: CommandMessage,
			content: (JSONWriter)->Unit): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				writer.write("content")
				content(writer)
			}
			return Message(writer.toString())
		}

		/**
		 * Answer an I/O upgrade request [message][Message] that incorporates
		 * the specified [UUID].
		 *
		 * @param command
		 *   The [command][CommandMessage] on whose behalf the upgrade is
		 *   requested.
		 * @param uuid
		 *   The `UUID` that denotes the I/O connection.
		 * @return
		 *   A message.
		 */
		internal fun newIOUpgradeRequestMessage(
			command: CommandMessage,
			uuid: UUID): Message
		{
			val writer = JSONWriter()
			writer.writeObject {
				writeStatusOn(true, writer)
				writeCommandOn(command.command, writer)
				writeCommandIdentifierOn(command.commandId, writer)
				writer.write("upgrade")
				writer.write(uuid.toString())
			}
			return Message(writer.toString())
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
					val command = Command.VERSION.parse(
						message.content)
					if (command != null)
					{
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					else
					{
						val rebuttal = newErrorMessage(
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
						val command = Command.parse(message)
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					catch (e: CommandParseException)
					{
						val rebuttal = newErrorMessage(null, e.localizedMessage)
						channel.enqueueMessageThen(rebuttal, receiveNext)
					}
					finally
					{
						// Only allow a single opportunity to upgrade the
						// channel, even if the command was gibberish.
						if (channel.state.eligibleForUpgrade)
						{
							channel.state = COMMAND
						}
					}
				}
				COMMAND ->
				{
					try
					{
						val command = Command.parse(message)
						command.commandId = channel.nextCommandId
						command.processThen(channel, receiveNext)
					}
					catch (e: CommandParseException)
					{
						val rebuttal = newErrorMessage(null, e.localizedMessage)
						channel.enqueueMessageThen(rebuttal, receiveNext)
					}
				}
				ProtocolState.IO ->
				{
					val input =
						channel.textInterface!!.inputChannel as
							ServerInputChannel
					input.receiveMessageThen(message, receiveNext)
				}
			}
		}

		/**
		 * Negotiate a version. If the [requested
		 * version][VersionCommandMessage.version] is
		 * [supported][supportedProtocolVersions], then echo this version back
		 * to the client. Otherwise, send a list of the supported versions for
		 * the client to examine. If the client cannot (or does not wish to)
		 * deal with the requested versions, then it must disconnect.
		 *
		 * @param channel
		 *   The [channel][AvailServerChannel] on which the
		 *   [response][CommandMessage] should be sent.
		 * @param command
		 *   A [VERSION][Command.VERSION] command message.
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
					command, "version already negotiated")
				channel.enqueueMessageThen(message, continuation)
				return
			}
			val version = command.version
			val message: Message
			if (supportedProtocolVersions.contains(version))
			{
				message = newSuccessMessage(command) { writer ->
					writer.write(version)
				}
			}
			else
			{
				message = newSuccessMessage(command) { writer ->
					writer.writeObject {
						writer.write("supported")
						writer.writeArray {
							for (supported in supportedProtocolVersions)
							{
								writer.write(supported)
							}
						}
					}
				}
			}
			// Transition to the next state. If the client cannot handle any of
			// the specified versions, then it must disconnect.
			channel.state = ELIGIBLE_FOR_UPGRADE
			channel.enqueueMessageThen(message, continuation)
		}

		/**
		 * List syntax guides for all of the [commands][Command] understood by
		 * the `AvailServer`.
		 *
		 * @param channel
		 *   The [channel][AvailServerChannel] on which the
		 *   [response][CommandMessage] should be sent.
		 * @param command
		 *   A [COMMANDS][Command.COMMANDS] command message.
		 * @param continuation
		 *   What to do when sufficient processing has occurred (and the
		 *   `AvailServer` wishes to begin receiving messages again).
		 */
		fun commandsThen(
			channel: AvailServerChannel,
			command: SimpleCommandMessage,
			continuation: ()->Unit)
		{
			assert(command.command === Command.COMMANDS)
			val message = newSuccessMessage(command) { writer ->
				val commands = Command.all
				val help = ArrayList<String>(commands.size)
				for (c in commands)
				{
					help.add(c.syntaxHelp)
				}
				sort(help)
				writer.writeArray {
					for (h in help)
					{
						writer.write(h)
					}
				}
			}
			channel.enqueueMessageThen(message, continuation)
		}

		/**
		 * Answer a [visitor][FileVisitor] able to visit every source module
		 * beneath the specified [module root][ModuleRoot].
		 *
		 * @param root
		 *   A module root.
		 * @param tree
		 *   The [holder][MutableOrNull] for the resultant tree of
		 *   [modules][ModuleNode].
		 * @return
		 *   A `FileVisitor`.
		 */
		internal fun sourceModuleVisitor(
			root: ModuleRoot,
			tree: MutableOrNull<ModuleNode>): FileVisitor<Path>
		{
			val extension = ModuleNameResolver.availExtension
			val isRoot = Mutable(true)
			val stack = ArrayDeque<ModuleNode>()
			return object : FileVisitor<Path>
			{
				override fun preVisitDirectory(
					dir: Path?,
					attrs: BasicFileAttributes?): FileVisitResult
				{
					assert(dir != null)
					if (isRoot.value)
					{
						isRoot.value = false
						val node = ModuleNode(root.name)
						tree.value = node
						stack.add(node)
						return FileVisitResult.CONTINUE
					}
					val fileName = dir!!.fileName.toString()
					if (fileName.endsWith(extension))
					{
						val localName = fileName.substring(
							0, fileName.length - extension.length)
						val node = ModuleNode(localName)
						stripNull(stack.peekFirst()).addModule(node)
						stack.addFirst(node)
						return FileVisitResult.CONTINUE
					}
					// This is a resource.
					val node = ModuleNode(fileName)
					stripNull(stack.peekFirst()).addResource(node)
					stack.addFirst(node)
					return FileVisitResult.CONTINUE
				}

				override fun postVisitDirectory(
					dir: Path?,
					e: IOException?): FileVisitResult
				{
					stack.removeFirst()
					return FileVisitResult.CONTINUE
				}

				override fun visitFile(
					file: Path?,
					attrs: BasicFileAttributes?): FileVisitResult
				{
					assert(file != null)
					// The root should be a directory, not a file.
					if (isRoot.value)
					{
						tree.value = ModuleNode(root.name)
						return FileVisitResult.TERMINATE
					}
					val fileName = file!!.fileName.toString()
					if (fileName.endsWith(extension))
					{
						val localName = fileName.substring(
							0, fileName.length - extension.length)
						val node = ModuleNode(localName)
						stripNull(stack.peekFirst()).addModule(node)
					}
					else
					{
						val node = ModuleNode(fileName)
						stripNull(stack.peekFirst()).addResource(node)
					}
					return FileVisitResult.CONTINUE
				}

				override fun visitFileFailed(
					file: Path?,
					e: IOException?): FileVisitResult
				{
					assert(file != null)
					val fileName = file!!.fileName.toString()
					if (fileName.endsWith(extension))
					{
						val localName = fileName.substring(
							0, fileName.length - extension.length)
						val node = ModuleNode(localName)
						node.exception = e
						stripNull(stack.peekFirst()).addModule(node)
					}
					else
					{
						val node = ModuleNode(fileName)
						node.exception = e
						stripNull(stack.peekFirst()).addResource(node)
					}
					return FileVisitResult.CONTINUE
				}
			}
		}

		/**
		 * The progress interval for [building][loadModule], in milliseconds.
		 */
		private const val buildProgressIntervalMillis = 100

		/**
		 * Obtain the [configuration][AvailServerConfiguration] of the
		 * `AvailServer`.
		 *
		 * @param args
		 *   The command-line arguments.
		 * @return
		 *   A viable configuration.
		 * @throws ConfigurationException
		 *   If configuration fails for any reason.
		 */
		@Throws(ConfigurationException::class)
		private fun configure(args: Array<String>): AvailServerConfiguration
		{
			val configuration = AvailServerConfiguration()
			val environmentConfigurator = EnvironmentConfigurator(configuration)
			environmentConfigurator.updateConfiguration()
			val commandLineConfigurator =
				CommandLineConfigurator(configuration, args, System.out)
			commandLineConfigurator.updateConfiguration()
			return configuration
		}

		/**
		 * The entry point for command-line invocation of the [Avail
		 * server][AvailServer].
		 *
		 * @param args
		 *   The command-line arguments.
		 */
		@JvmStatic
		fun main(args: Array<String>)
		{
			val configuration: AvailServerConfiguration
			val resolver: ModuleNameResolver
			try
			{
				configuration = configure(args)
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

			val runtime = AvailRuntime(resolver)
			val server = AvailServer(configuration, runtime)
			try
			{
				WebSocketAdapter(
					server,
					InetSocketAddress(configuration.serverPort),
					configuration.serverAuthority)
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
				runtime.destroy()
			}
		}
	}
}
