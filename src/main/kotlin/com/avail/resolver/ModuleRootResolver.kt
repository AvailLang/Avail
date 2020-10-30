/*
 * ModuleRootResolver.kt
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

package com.avail.resolver

import com.avail.AvailThread
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.error.ErrorCode
import com.avail.files.AbstractFileWrapper
import com.avail.files.FileManager
import com.avail.files.ManagedFileWrapper
import com.avail.persistence.cache.Repository
import java.net.URI
import java.util.UUID

/**
 * `ModuleRootResolver` declares an interface for accessing Avail [ModuleRoot]s
 * given a [URI]. It is responsible for asynchronously retrieving, creating,
 * deleting, and saving files and packages where the `ModuleRoot` is stored.
 *
 * It is responsible for producing [ResolverReference]s, the object linking a
 * resource in a module root to its actual file at the [URI] location of the
 * module root.
 *
 * All file actions are conducted via the [ModuleRootResolver.fileManager].
 * Given the files may originate anywhere from the local file system to a remote
 * database accessed via network API, a RESTful webserver, etc., all file
 * requests must be handled asynchronously.
 *
 * A `ModuleRootResolver` establishes access to a `ModuleRoot` based upon the
 * [URI.scheme] given the appropriate `ModuleRootResolver` that supports the
 * scheme. A `ModuleRootResolver` is created by a corresponding
 * [ModuleRootResolverFactory]. For each `ModuleRootResolver` type implemented a
 * `ModuleRootResolverFactory` must be implemented to enable resolver creation.
 * The `ModuleRootResolverFactory` must be
 * [registered][ModuleRootResolverRegistry.register] in the
 * [ModuleRootResolverRegistry]. A `ModuleRootResolver` cannot be created if
 * the factory has not been registered. Only one registered
 * `ModuleRootResolverFactory` is allowed per `URI` scheme.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface ModuleRootResolver
{
	/**
	 * The [URI] that identifies the location of the [ModuleRoot].
	 */
	val uri: URI

	/**
	 * The [ModuleRoot] this [ModuleRootResolver] resolves to.
	 */
	val moduleRoot: ModuleRoot

	/**
	 * The [FileManager] used to manage the files accessed via this
	 * [ModuleRootResolver].
	 */
	val fileManager: FileManager

	/**
	 * The [exception][Throwable] that prevented most recent attempt at
	 * accessing the source location of this [ModuleRootResolver].
	 */
	var accessException: Throwable?

	/**
	 * The [Map] from a UUID that represents an interested party to a lambda
	 * that accepts a [WatchEventType] that describes the event that occurred
	 * at the source location and a [ResolverReference] that identifies to what
	 * the event occurred to.
	 */
	val watchEventSubscriptions:
		MutableMap<UUID, (WatchEventType, ResolverReference)->Unit>

	/**
	 * Provide the non-`null` [ResolverReference] that represents the
	 * [moduleRoot]. There is no guarantee made by this interface as to how
	 * this should be run. It can be all executed on the calling thread or it
	 * can be executed on a separate thread.
	 *
	 * @param successHandler
	 *   Accepts the [resolved][resolve] `ResolverReference`.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	fun provideModuleRootTree(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Connect to the source of the [moduleRoot] and populate a
	 * [ResolverReference] with all the `ResolverReference`s from the module
	 * root. This is not required nor expected to be executed in the calling
	 * thread.
	 *
	 * @param successHandler
	 *   Accepts the [resolved][resolve] [ResolverReference].
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	fun resolve(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Asynchronously execute the provided task.
	 *
	 * @param task
	 *   The lambda to run outside the calling thread of execution.
	 */
	fun executeTask(task: ()->Unit)

	/**
	 * Close this [ModuleRootResolver]. Should be called at shutdown to ensure
	 * proper clean up of any open resources.
	 */
	fun close () = Unit

	/**
	 * Watch [moduleRoot] for changes to files that occur in the root outside
	 * of the currently running instance of Avail.
	 */
	fun watchRoot ()

	/**
	 * Subscribe to receive notifications of [WatchEventType]s occurring to this
	 * [ModuleRoot].
	 *
	 * @param
	 *   The lambda that accepts a [WatchEventType] that describes the event
	 *   that occurred at the source location and a [ResolverReference] that
	 *   identifies to what the event occurred to.
	 * @return
	 *   A [UUID] that uniquely identifies the subscription.
	 */
	fun subscribeRootWatcher(
		watchAction: (WatchEventType, ResolverReference)->Unit): UUID
	{
		val id = UUID.randomUUID()
		watchEventSubscriptions[id] = watchAction
		return id
	}

	/**
	 * Remove a watch subscription.
	 *
	 * @param id
	 *   The watch subscription id of the subscription to remove.
	 */
	fun unsubscribeRootWatcher(id: UUID)
	{
		watchEventSubscriptions.remove(id)
	}

	/**
	 * @return
	 *   `true` indicates the [uri] resolves to a valid Avail [ModuleRoot];
	 *   `false` otherwise.
	 */
	fun resolvesToValidModuleRoot (): Boolean

	/**
	 * Answer a [ModuleNameResolver.ModuleNameResolutionResult] when attempting
	 * to locate a module in this [ModuleRootResolver].
	 *
	 * @param qualifiedName
	 *   The [ModuleName] being searched for.
	 * @param initialCanonicalName
	 *   The canonical name used in case a rename is registered w/ the
	 *   [ModuleRootResolver].
	 * @param moduleNameResolver
	 *   The [ModuleRootResolver].
	 * @return
	 *   A `ModuleNameResolutionResult` or `null` if could not be located in
	 *   this root.
	 */
	fun find(
		qualifiedName: ModuleName,
		initialCanonicalName: ModuleName,
		moduleNameResolver: ModuleNameResolver)
			: ModuleNameResolver.ModuleNameResolutionResult?

	/**
	 * Provide the [ResolverReference] for the given qualified file name for a
	 * a file in [moduleRoot].
	 *
	 * @param qualifiedName
	 *   The qualified name of the target file.
	 * @param withReference
	 *   The lambda that accepts the retrieved reference.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	fun provideResolverReference(
		qualifiedName: String,
		withReference: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Answer the [ResolverReference] for the given qualified file name for a
	 * a file in [moduleRoot].
	 *
	 * This should not make async calls. Either the [ModuleRootResolver] has
	 * it readily available or it does not.
	 *
	 * @param qualifiedName
	 *   The qualified name of the target file.
	 * @return
	 *   The [ResolverReference] or `null` if not presently available.
	 */
	fun getResolverReference(qualifiedName: String): ResolverReference?

	/**
	 * Specifically refresh the [ResolverReference.digest] in the
	 * [Repository.ModuleArchive] for the most recent
	 * [ResolverReference.lastModified] timestamp. This also refreshes the
	 * metrics (mutable state) of the provided [ResolverReference].
	 * It should refresh:
	 *
	 *  * [ResolverReference.lastModified]
	 *  * [ResolverReference.size]
	 *
	 * **NOTE:** This might be run on a separate standard thread. If it is
	 * required that the callback(s) be run in an [AvailThread], that should be
	 * handled *inside* the callback.
	 *
	 * @param reference
	 *   The [ResolverReference] to refresh.
	 * @param successHandler
	 *   A function that accepts the new digest and last modified time to call
	 *   after the new digest has been created.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	fun refreshResolverReferenceDigest(
		reference: ResolverReference,
		successHandler: (ByteArray, Long)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Specifically refresh the [ResolverReference] mutable state:
	 *
	 *  * [ResolverReference.lastModified]
	 *  * [ResolverReference.size]
	 *
	 * @param reference
	 *   The [ResolverReference] to refresh.
	 * @param successHandler
	 *   A function that accepts the last modified time to call after the refresh
	 *   is complete.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	fun refreshResolverMetaData(
		reference: ResolverReference,
		successHandler: (Long)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Provide the full list of all [ResolverReference]s in this
	 * [ModuleRootResolver].
	 *
	 * @param forceRefresh
	 *   `true` indicates a requirement that the resolver refresh any locally
	 *   cached list it may have directly from the [ModuleRoot] source location;
	 *   `false` indicates, if there is a cached list, providing the cached list
	 *   is acceptable.
	 * @param withList
	 *   The lambda that accepts the [List] of [ResolverReference]s.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and a `nullable` [Throwable].
	 */
	fun rootManifest(
		forceRefresh: Boolean,
		withList: (List<ResolverReference>)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Retrieve the resource and provide it with a request to obtain
	 * the raw file bytes.
	 *
	 * @param byPassFileManager
	 *   `true` indicates the file should be read directly from the source
	 *   location; `false` indicates an attempt to read from the [FileManager]
	 *   should be made.
	 * @param reference
	 *   The [ResolverReference] that identifies the target file to read.
	 * @param withContents
	 *   A function that accepts the raw bytes of the file in the [moduleRoot]
	 *   and optionally a [FileManager] file [UUID] or `null` if `cacheFile`
	 *   is `false`.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and a `nullable` [Throwable].
	 */
	fun readFile(
		byPassFileManager: Boolean,
		reference: ResolverReference,
		withContents: (ByteArray, UUID?)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Create a file.
	 *
	 * @param qualifiedName
	 *   The [fully-qualified name][ModuleName] of the module or resource.
	 * @param mimeType
	 *   The MIME type of the file being created.
	 * @param completion
	 *   A function to be run upon the successful creation of the file.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the failure
	 *   and a `nullable` [Throwable].
	 */
	fun createFile(
		qualifiedName: String,
		mimeType: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Create a package and its representative Avail module.
	 *
	 * @param qualifiedName
	 *   The [fully-qualified name][ModuleName] of the package.
	 * @param completion
	 *   A function to be run upon the successful creation of the package.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the failure
	 *   and a `nullable` [Throwable].
	 */
	fun createPackage(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Create a directory.
	 *
	 * @param qualifiedName
	 *   The fully-qualified name of the directory.
	 * @param completion
	 *   A function to be run upon the successful creation of the directory.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the failure
	 *   and a `nullable` [Throwable].
	 */
	fun createDirectory(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Delete the [ResourceType] linked to the qualified name. If the
	 * [ResourceType] is a [ResourceType.PACKAGE] or a [ResourceType.DIRECTORY],
	 * all of its children should be deleted as well. All deleted references
	 * should be removed from the [reference tree][provideResolverReference].
	 *
	 * If it is a [ResourceType.REPRESENTATIVE], the package it represents
	 * should be deleted and handled as if the package were the target of
	 * deletion.
	 *
	 * @param qualifiedName
	 *   The fully-qualified name of the directory.
	 * @param completion
	 *   A function to be run upon the successful creation of the directory.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the failure
	 *   and a `nullable` [Throwable].
	 */
	fun deleteResource(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Save the file to where it is stored.
	 *
	 * @param reference
	 *   The [ResolverReference] that identifies the target file to save.
	 * @param fileContents
	 *   The contents of the file to save.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] that describes the nature
	 *   of the failure and a `nullable` [Throwable].
	 */
	fun saveFile(
		reference: ResolverReference,
		fileContents: ByteArray,
		successHandler: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)

	/**
	 * Answer a [AbstractFileWrapper] for the targeted file.
	 *
	 * @param id
	 *   The [AbstractFileWrapper.id].
	 * @param reference
	 *   The [AbstractFileWrapper] of the file.
	 * @return
	 *   A `AbstractFileWrapper`.
	 */
	fun fileWrapper(
		id: UUID, reference: ResolverReference): AbstractFileWrapper =
			ManagedFileWrapper(id, reference, fileManager)

	/**
	 * Answer the [URI] for a resource in the source [moduleRoot] given a
	 * qualified name. There is no guarantee the target exists.
	 *
	 * @param qualifiedName
	 *   The [fully-qualified name][ModuleName] of the module or resource.
	 * @return
	 *   A `URI` for the resource.
	 */
	fun fullResourceURI(qualifiedName: String): URI =
		URI("$uri/$qualifiedName")

	/**
	 * Answer a qualified name for the given [URI] that points to a file in the
	 * [moduleRoot].
	 *
	 * @param targetURI
	 *   The [URI] to transform into qualified name.
	 * @return The qualified name.
	 */
	fun getQualifiedName(targetURI: URI): String
	{
		assert(targetURI.path.startsWith(uri.path)) {
			"$targetURI is not in ModuleRoot, $moduleRoot"
		}
		val relative = targetURI.path.split(uri.path)[1]
		return "/${moduleRoot.name}" +
			if (relative.startsWith("/")) "" else "/" +
				relative
	}

	/**
	 * A `WatchEventType` indicates the types of events that can occur to
	 * resources at the represented [ModuleRoot] source location.
	 */
	enum class WatchEventType
	{
		/**
		 * A new [ResourceType] is created.
		 */
		CREATE,

		/**
		 * A [ResourceType] is changed. This should only happen to actual files.
		 */
		MODIFY,

		/**
		 * A [ResourceType] has been deleted.
		 */
		DELETE
	}
}
