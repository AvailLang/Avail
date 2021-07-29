/*
 * FileManager.kt
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

package com.avail.files

import com.avail.AvailRuntime
import com.avail.AvailThread
import com.avail.builder.ModuleName
import com.avail.builder.ModuleRoot
import com.avail.error.ErrorCode
import com.avail.files.FileErrorCode.*
import com.avail.io.AvailClient
import com.avail.io.IOSystem
import com.avail.resolver.ModuleRootResolver
import com.avail.resolver.ResolverReference
import com.avail.utility.LRUCache
import com.avail.utility.Mutable
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ThreadPoolExecutor

/**
 * `FileManager` manages the access to the source and resource files in Avail
 * [ModuleRoot](s). It provides an LRU caching mechanism by which open files can
 * be added and removed as needed to control the number of open files in memory.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class FileManager
{
	/**
	 * The [IOSystem] used by this [FileManager].
	 */
	val ioSystem = IOSystem()

	/**
	 * Close this [FileManager]. Should be called at shutdown to ensure proper
	 * clean up of any open resources.
	 */
	open fun close () = Unit

	/**
	 * The [EnumSet] of [StandardOpenOption]s used when creating files.
	 */
	@Suppress("unused")
	protected val fileCreateOptions: EnumSet<StandardOpenOption> =
		EnumSet.of(
			StandardOpenOption.READ,
			StandardOpenOption.WRITE,
			StandardOpenOption.CREATE_NEW)

	companion object
	{
		// TODO make the softCapacity and strongCapacity configurable,
		//  not just magic numbers; magic numbers used as defaults
		// The LRUCache capacities
		const val SOFT_CAPACITY = 10000
		const val STRONG_CAPACITY = 10
	}

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for asynchronous file
	 * operations performed on behalf of this [FileManager].
	 */
	private val fileExecutor: ThreadPoolExecutor get() = ioSystem.fileExecutor

	/**
	 * The associated [AvailRuntime] or `null` if no `AvailRuntime` in use with
	 * this [FileManager].
	 */
	private var runtime: AvailRuntime? = null

	/**
	 * Associate an [AvailRuntime] with this [FileManager].
	 *
	 * @param runtime
	 *   The `AvailRuntime` to associate.
	 */
	fun associateRuntime (runtime: AvailRuntime)
	{
		this.runtime = runtime
	}

	/**
	 * Answer the [AvailRuntime] associated with this [FileManager].
	 *
	 * [associateRuntime] must have been called for this to work.
	 *
	 * @return
	 *   An `AvailRuntime`.
	 */
	fun runtime (): AvailRuntime = runtime!!

	/**
	 * Maintain an [LRUCache] of [AvailFile]s opened by the Avail server.
	 *
	 * This cache works in conjunction with [resolverRefToId] to maintain links
	 * between
	 */
	protected val fileCache = LRUCache<UUID, Mutable<AbstractFileWrapper?>>(
		SOFT_CAPACITY,
		STRONG_CAPACITY,
		{
			var reference: ResolverReference? = null
			resolverRefToId.forEach { (k, v) ->
				if (v == it)
				{
					reference = k
					return@forEach
				}
			}
			val wrapper: AbstractFileWrapper? =
				try
				{
					if (reference === null)
					{
						null
					}
					else
					{
						fileWrapper(it, reference!!)
					}
				}
				catch (e: NoSuchFileException)
				{
					ErrorFileWrapper(
						it, reference!!, this, e, FILE_NOT_FOUND)
					null
				}
				catch (e: Throwable)
				{
					ErrorFileWrapper(
						it, reference!!, this, e, UNSPECIFIED)
					null
				}
			Mutable(wrapper)
		},
		{ _, value ->
			try
			{
				value.value?.close()
			}
			catch (e: IOException)
			{
				// Do nothing
			}
		})

	/**
	 * Answer a [ManagedFileWrapper] for the targeted file.
	 *
	 * @param id
	 *   The [ManagedFileWrapper.id].
	 * @param reference
	 *   The [ResolverReference] of the file.
	 * @return
	 *   A `ServerFileWrapper`.
	 */
	protected fun fileWrapper(
		id: UUID,
		reference: ResolverReference): AbstractFileWrapper =
			reference.resolver.fileWrapper(id, reference)

	/**
	 * Fully remove the file associated with the provided [fileCache] id. This
	 * also removes it from [resolverRefToId].
	 *
	 * @param id
	 *   The [UUID] that uniquely identifies the target file in the cache.
	 */
	fun remove (id: UUID)
	{
		fileCache[id].value?.let {
			fileCache.remove(id)
			resolverRefToId.remove(it.reference)
			idToResolverRef.remove(id)
		} ?: idToResolverRef.remove(id)?.let { resolverRefToId.remove(it) }
	}

	/**
	 * Deregister interest in the file associated with the provided [fileCache]
	 * id. If the resulting [interest count][ManagedFileWrapper.interestCount]
	 * is 0, the file is closed and fully removed from the [fileCache].
	 *
	 * @param id
	 *   The [UUID] that uniquely identifies the target file in the cache.
	 * @param interestedPartId
	 *   The unique identifier of the party claiming it is no longer interested
	 *   in the file.
	 */
	fun deregisterInterest (
		id: UUID,
		@Suppress("UNUSED_PARAMETER")
		interestedPartId: UUID? = null)
	{
		// TODO does interested party id matter?
		fileCache[id].value.let {
			if (it?.interestCount?.decrementAndGet() == 0)
			{
				remove(id)
				it.close()
			}
		}
	}

	/**
	 * Check if interest in the file associated with the provided [fileCache]
	 * id still exists ([interest count][ManagedFileWrapper.interestCount] > 0).
	 * If there is no file interest the file is closed and fully removed from
	 * the [fileCache].
	 *
	 * @param id
	 *   The [UUID] that uniquely identifies the target file in the cache.
	 */
	fun checkInterest (id: UUID)
	{
		fileCache[id].value.let {
			if (it?.interestCount?.get()== 0)
			{
				remove(id)
				it.close()
			}
		}
	}

	/**
	 * A [Map] from the [ResolverReference] of a [file][AvailFile] to
	 * the [UUID] that uniquely identifies that file in the [fileCache].
	 *
	 * This map will never be cleared of values as cached files that have been
	 * removed from the `fileCache` must maintain association with the
	 * server-assigned [UUID] that identifies the file for all interested
	 * clients. If a client requests a file action with a given UUID and it is
	 * not found in the `fileCache`, this map will be used to retrieve the
	 * associated file from disk and placed back in the `fileCache`.
	 */
	private val resolverRefToId = mutableMapOf<ResolverReference, UUID>()

	/**
	 * Answer the [FileManager] file id for the provided [ResolverReference].
	 *
	 * @param resolverReference
	 *   The `ResolverReference` of the file to get the file id for.
	 * @return The file id or `null` if not in file manager.
	 */
	fun fileId (resolverReference: ResolverReference): UUID? =
		resolverRefToId[resolverReference]

	/**
	 * Attempt to provide a file if it already exists in this [FileManager]
	 * without directly requesting it be added.
	 *
	 * @param resolverReference
	 *   The [ResolverReference] that identifies the target file to read.
	 * @param successHandler
	 *   A function that accepts the [FileManager.fileCache] [UUID] that
	 *   uniquely identifies the file and the [AvailFile].
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 * @return `true` if file is available and being provided to `consumer` or
	 *   `false` if the file is not present.
	 */
	fun optionallyProvideExistingFile (
		resolverReference: ResolverReference,
		successHandler: (UUID, AvailFile) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit): Boolean
	{
		val fileId = resolverRefToId[resolverReference]
		if (fileId === null)
		{
			return false
		}
		val wrapper = fileCache[fileId].value ?: return false
		if (wrapper.isError || wrapper.isClosed)
		{
			return false
		}

		val success: (UUID, String, AvailFile) -> Unit =
			{ uuid, _, file -> successHandler(uuid, file) }
		// This shouldn't need to happen on a separate thread...
		wrapper.provide(false, success, failureHandler)
		return true
	}

	/**
	 * A [Map] from the file cache [id][UUID] that uniquely identifies that file
	 * in the [fileCache] to the String [Path] location of a
	 * [file][AvailFile].
	 *
	 * This map will never be cleared of values as cached files that have been
	 * removed from the `fileCache` must maintain association with the
	 * server-assigned [UUID] that identifies the file for all interested
	 * clients. If a client requests a file action with a given UUID and it is
	 * not found in the `fileCache`, this map will be used to retrieve the
	 * associated file from disk and place it back in the `fileCache`.
	 */
	private val idToResolverRef = mutableMapOf<UUID, ResolverReference>()

	/**
	 * Schedule the specified task for eventual execution
	 * by the [thread pool executor][ThreadPoolExecutor] for
	 * asynchronous file operations. The implementation is free to run the task
	 * immediately or delay its execution arbitrarily. The task will not execute
	 * on an [Avail thread][AvailThread].
	 *
	 * @param task
	 *   A task.
	 */
	fun executeFileTask(task: () -> Unit)
	{
		fileExecutor.execute(task)
	}

	/**
	 * Retrieve the [ManagedFileWrapper] and provide it with a request to obtain
	 * the [raw file bytes][AvailFile.rawContent].
	 *
	 * @param id
	 *   The [ManagedFileWrapper] cache id of the file to act upon.
	 * @param fileAction
	 *   The [FileAction] to execute.
	 * @param originator
	 *   The [AvailClient.id] of the client that originated this change.
	 * @param continuation
	 *   What to do when sufficient processing has occurred.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature of
	 *   the failure and an optional [Throwable].
	 */
	fun executeAction (
		id: UUID,
		fileAction: FileAction,
		originator: UUID,
		continuation: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		fileCache[id].value?.execute(fileAction, originator, continuation)
			?: failureHandler(BAD_FILE_ID, null)
	}

	/**
	 * Save the [AvailFile] to where it is stored.
	 *
	 * @param
	 *   The `AvailFile` to save.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	fun saveFile (
		availFile: AvailFile,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		val reference = availFile.fileWrapper.reference
		reference.resolver.saveFile(
			reference,
			availFile.getSavableContent(),
			{
				val saveTime = System.currentTimeMillis()
				availFile.conditionallyClearDirty(saveTime)
				availFile.lastModified = saveTime

			},
			failureHandler)
	}

	/**
	 * Delete the file at the provided [ResolverReference].
	 *
	 * @param reference
	 *   The [ResolverReference] that indicates the location of the file.
	 * @param success
	 *   Accepts the [FileManager] file id if remove successful. Maybe `null`
	 *   if file not present in `FileManager`.
	 * @param failure
	 *   A function that accepts an [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	fun delete (
		reference: ResolverReference,
		success: (UUID?) -> Unit,
		failure: (ErrorCode, Throwable?) -> Unit)
	{
		resolverRefToId.remove(reference)?.let { id ->
			idToResolverRef.remove(id)
			fileCache.remove(id)?.value?.delete(success, failure)
		}
	}

	/**
	 * Retrieve the [AbstractFileWrapper] and provide it with a request to
	 * obtain the [raw file bytes][AvailFile.rawContent].
	 *
	 * @param qualifiedName
	 *   The [fully-qualified name][ModuleName] of the module or resource.
	 * @param resolver
	 *   The [ModuleRootResolver] used to access the target [ModuleRoot].
	 * @param withFile
	 *   A function that accepts the [FileManager.fileCache] [UUID], the file
	 *   mime type, and [AvailFile].
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 * @return
	 *   The [FileManager] file id for the file.
	 */
	open fun readFile (
		qualifiedName: String,
		resolver: ModuleRootResolver,
		withFile: (UUID, String, AvailFile) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		resolver.provideResolverReference(
			qualifiedName,
			{ reference ->
				val uuid: UUID
				synchronized(resolverRefToId)
				{
					uuid = resolverRefToId.getOrPut(reference) {
						UUID.randomUUID()
					}
					idToResolverRef[uuid] = reference
				}
				val value = fileCache[uuid]
				executeFileTask {
					value.value?.provide(true, withFile, failureHandler)
						?: failureHandler(FILE_NOT_FOUND, null)
				}
			},
			failureHandler)
	}

	/**
	 * Create a file.
	 *
	 * @param qualifiedName
	 *   The [fully-qualified name][ModuleName] of the module or resource.
	 * @param mimeType
	 *   The MIME type of the file being created.
	 * @param resolver
	 *   The [ModuleRootResolver] used to access the target [ModuleRoot].
	 * @param completion
	 *   A function to call once the file is successfully created.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the failure
	 *   and an optional [Throwable].
	 */
	fun createFile (
		qualifiedName: String,
		mimeType: String,
		resolver: ModuleRootResolver,
		completion: () -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		resolver.createFile(
			qualifiedName, mimeType, completion, failureHandler)
	}
}
