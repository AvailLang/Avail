/*
 * ResolverReference.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.resolver

import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoot
import avail.builder.ResolvedModuleName
import avail.builder.UnresolvedModuleException
import avail.error.ErrorCode
import avail.files.AvailFile
import avail.files.AvailModuleFile
import avail.files.FileManager
import avail.files.NullFileWrapper
import avail.persistence.cache.Repository
import avail.utility.json.JSONWriter
import java.net.URI
import java.security.MessageDigest
import java.util.Deque
import java.util.LinkedList
import java.util.UUID

/**
 * A `ResolverReference` is a reference to a [module][ModuleName] or a module
 * resource within a [ModuleRoot] produced by a [ModuleRootResolver].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property resolver
 *   The [ModuleRoot] the file referenced by [ResolverReference] belongs to.
 * @property uri
 *   The [URI] of the file this [ResolverReference] refers to.
 * @property qualifiedName
 *   The [fully-qualified name][ModuleName] of the module or resource.
 * @property type
 *   The [ResourceType] that describes what this [ResolverReference] refers to.
 *
 * @constructor
 * Construct a [ResolverReference].
 *
 * @param resolver
 *   The [ModuleRoot] the file referenced by [ResolverReference] belongs to.
 * @param
 *   The [URI] of the file this [ResolverReference] refers to.
 * @param qualifiedName
 *   The [fully-qualified name][ModuleName] of the module or resource.
 * @param type
 *   The [ResourceType] that describes what this reference points to.
 * @param mimeType
 *   The file MIME type of the associated resource.
 * @param lastModified
 *   The time in millis since the unix epoch, preferably UTC, when this was last
 *   modified. This value will be zero for directories.
 * @param size
 *   The size in bytes of the file or 0 if a directory.
 */
class ResolverReference constructor(
	val resolver: ModuleRootResolver,
	val uri: URI,
	val qualifiedName: String,
	val type: ResourceType,
	mimeType: String, // TODO can be updated? Error on creation?
	lastModified: Long,
	size: Long,
	localName: String = "")
{
	/**
	 * `true` iff the [ResolverReference] represents a root, `false` otherwise.
	 */
	val isRoot: Boolean get() = type == ResourceType.ROOT

	/**
	 * `true` iff the [ResolverReference] represents a package, `false`
	 *  otherwise.
	 */
	val isPackage: Boolean get() = type == ResourceType.PACKAGE

	/**
	 * `true` iff the [ResolverReference] represents a package representative
	 * module, `false` otherwise.
	 */
	val isPackageRepresentative: Boolean get() =
		type == ResourceType.REPRESENTATIVE

	/**
	 * `true` iff the [ResolverReference] represents a module, `false`
	 *  otherwise.
	 */
	val isModule: Boolean get() =
		type == ResourceType.MODULE || type == ResourceType.REPRESENTATIVE

	/**
	 * Indicates whether or not this [ResolverReference] is a resource, not a
	 * [module][isModule] nor [package][isPackage]. `true` if it is either a
	 * [ResourceType.RESOURCE] or [ResourceType.DIRECTORY]; `false` otherwise.
	 */
	val isResource: Boolean get() =
		type == ResourceType.RESOURCE || type == ResourceType.DIRECTORY

	/**
	 * `true` indicates that either [modules] or [resources] is not empty.
	 * `false` indicates they are both empty.
	 *
	 * **NOTE** only a [package][ResourceType.PACKAGE],
	 * [root][ResourceType.ROOT], or [directory][ResourceType.DIRECTORY] can
	 * have child [ResolverReference]s.
	 */
	val hasChildren: Boolean get() =
		modules.isNotEmpty() || resources.isNotEmpty()

	/** The children of this [ResolverReference]. */
	val modules = mutableListOf<ResolverReference>()

	/** The resources of the [ResolverReference]. */
	val resources = mutableListOf<ResolverReference>()

	/**
	 * Answer the List of child [ResolverReference]s owned by the
	 * [ResolverReference].
	 *
	 * @param includeResources
	 *   `true` indicates inclusion of [resources]; `false` indicates only
	 *   [modules].
	 */
	fun childReferences (includeResources: Boolean): List<ResolverReference> =
		if (includeResources) modules + resources
		else modules

	/**
	 * Is this [ResolverReference] a descendant of the provided
	 * `ResolverReference`?
	 *
	 * @param reference
	 *   The potential ancestor.
	 * @return
	 *   `true` if this is a descendant; `false` otherwise.
	 */
	@Suppress("unused")
	fun isDescendantOf (reference: ResolverReference): Boolean =
		reference.hasChildren
			&& qualifiedName.startsWith(reference.qualifiedName)

	/**
	 * Is this [ResolverReference] a direct child of the provided
	 * `ResolverReference`?
	 *
	 * @param reference
	 *   The potential parent.
	 * @return
	 *   `true` if this is a descendant; `false` otherwise.
	 */
	@Suppress("unused")
	fun isChildOf (reference: ResolverReference): Boolean =
		reference.hasChildren
			&& childReferences(true).contains(this)

	/**
	 * Answer the [Repository] where the source of this file should be stored.
	 */
	private val repository: Repository get() = resolver.moduleRoot.repository

	/**
	 * Answer the [Repository.ModuleArchive] for the file this
	 * [ResolverReference] points to.
	 */
	private val archive: Repository.ModuleArchive get() =
		repository.getArchive(qualifiedName)

	/**
	 * The file MIME type of the associated resource or empty String if
	 * package/directory.
	 */
	var mimeType: String = mimeType
		private set

	/**
	 * The [ModuleName] of this module/resource.
	 */
	val moduleName: ModuleName by lazy { ModuleName(qualifiedName, false) }

	/**
	 * The local name of the file referenced by this `ModuleName`.
	 */
	val localName: String by lazy {
		localName.ifEmpty { moduleName.localName }
	}

	val parentName: String by lazy {
		qualifiedName.substringBeforeLast("/")
	}

	/**
	 * The last known time this was updated since the Unix Epoch.
	 */
	var lastModified: Long
		private set

	/**
	 * The size, in bytes, of the backing file, or 0 if [isPackage] is true.
	 */
	var size: Long
		private set

	/**
	 * The cryptographic hash of the file's most recently reported contents.
	 */
	private val digest: ByteArray? get() = archive.provideDigest(this)

	/**
	 * The [exception][Throwable] that prevented the most recent attempt at
	 * accessing the source location of this [ResolverReference].
	 */
	var accessException: Throwable? = null

	init
	{
		this.lastModified = lastModified
		this.size = size
	}

	/**
	 * Answer an [AvailFile] for the given raw bytes. This should be the
	 * contents of the file represented by this [ResolverReference].
	 *
	 * @param rawBytes
	 *   The raw binary file bytes.
	 */
	fun file (rawBytes: ByteArray): AvailFile =
		when (type)
		{
			ResourceType.MODULE,
			ResourceType.REPRESENTATIVE,
			ResourceType.RESOURCE ->
				AvailModuleFile(
					rawBytes,
					NullFileWrapper(rawBytes, this, resolver.fileManager))
			ResourceType.PACKAGE,
			ResourceType.ROOT,
			ResourceType.DIRECTORY ->
				throw UnsupportedOperationException(
					"$qualifiedName ($type) cannot be an AvailFile")
		}

	/**
	 * Recursively write the receiver to the supplied [JSONWriter].
	 *
	 * @param writer
	 *   A `JSONWriter`.
	 */
	private fun recursivelyWriteOn(writer: JSONWriter, builder: AvailBuilder)
	{
		// Representatives should not have a visible footprint in the
		// tree; we want their enclosing packages to represent them.
		if (type !== ResourceType.REPRESENTATIVE)
		{
			writer.writeObject {
				at("localName") { write(localName) }
				at("qualifiedName") { write(qualifiedName) }
				at("type") { write(type.label) }
				accessException?.let {
					at("error") { write(it.localizedMessage) }
				}
				when (type)
				{
					ResourceType.PACKAGE ->
					{
						// Handle a missing representative as a special
						// kind of error, but only if another error
						// hasn't already been reported.
						if (accessException === null
							&& modules.none {
								it.localName == localName
							})
						{
							at("error") {
								write("Missing representative")
							}
						}
						writeResolutionInformationOn(writer, builder)
					}
					ResourceType.MODULE ->
						writeResolutionInformationOn(writer, builder)
					else -> { }
				}
				if (modules.isNotEmpty() || resources.isNotEmpty())
				{
					at("childNodes") {
						writeArray {
							modules.forEach { module ->
								module.recursivelyWriteOn(writer, builder)
							}
							resources.forEach { resource ->
								resource.recursivelyWriteOn(writer, builder)
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Write information that requires [module resolution][ModuleNameResolver].
	 *
	 * @param writer
	 *   A `JSONWriter`.
	 */
	private fun writeResolutionInformationOn(
		writer: JSONWriter, builder: AvailBuilder) =
		with(writer)
		{
			val resolver = builder.runtime.moduleNameResolver
			var resolved: ResolvedModuleName? = null
			var resolutionException: Throwable? = null
			val loaded =
				try
				{
					resolved = resolver.resolve(ModuleName(qualifiedName))
					builder.getLoadedModule(resolved) !== null
				}
				catch (e: UnresolvedModuleException)
				{
					resolutionException = e
					false
				}
			at("status") {
				write(if (loaded) "loaded" else "not loaded")
			}
			if (resolved?.isRename == true)
			{
				at("resolvedName") { write(resolved.qualifiedName) }
			}
			else if (accessException === null && resolutionException !== null)
			{
				at("error") {
					write(resolutionException.localizedMessage)
				}
			}
			resolver.renameRulesInverted[qualifiedName]?.let {
				at("redirectedNames") {
					writeArray { it.forEach(this::write) }
				}
			}
		}

	/**
	 * Write the `ModuleNode` to the supplied [JSONWriter].
	 *
	 * @param writer
	 *   A `JSONWriter`.
	 */
	fun writeOn(writer: JSONWriter, builder: AvailBuilder)
	{
		recursivelyWriteOn(writer, builder)
	}

	/**
	 * Update the mutable state of this [ResolverReference].
	 *
	 * @param lastModified
	 *   The last known time this was updated since the Unix Epoch.
	 * @param size
	 *   The size, in bytes, of the backing file, or 0 if [isPackage] is true.
	 */
	fun refresh (lastModified: Long, size: Long)
	{
		if (this.lastModified < lastModified)
		{
			// this represents the latest and greatest!
			this.lastModified = lastModified
			this.size = size
		}
	}

	/**
	 * Retrieve the cryptographic hash of the file's most recently reported
	 * contents.
	 *
	 * @param refresh
	 *   `true` forces a recalculation of the digest; `false` supplies the last
	 *   known digest presuming the file has not changed.
	 * @param withDigest
	 *   The function that accepts the [digest] once it is retrieved/calculated
	 *   and the [lastModified] of this [ResolverReference].
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
	 *   to be called in the event of failure.
	 */
	internal fun digest (
		refresh: Boolean = false,
		withDigest: (ByteArray, Long)->Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		val currentDigest = digest
		if (!refresh && currentDigest !== null)
		{
			withDigest(currentDigest, lastModified)
			return
		}
		resolver.refreshResolverReferenceDigest(
			this,
			{ newDigest, fromSaveTime ->
				withDigest(newDigest, fromSaveTime)
			},
			failureHandler)
	}

	/**
	 * Retrieve the resource and provide it with a request to obtain
	 * the raw file bytes.
	 *
	 * @param bypassFileManager
	 *   `true` indicates the file should be read directly from the source
	 *   location; `false` indicates an attempt to read from the [FileManager]
	 *   should be made.
	 * @param withContents
	 *   A function that accepts the raw bytes of the read file.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and a `nullable` [Throwable].
	 */
	fun readFile (
		bypassFileManager: Boolean,
		withContents: (ByteArray, UUID?) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		resolver.readFile(bypassFileManager, this, withContents, failureHandler)
	}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is ResolverReference) return false

		return resolver.uri == other.resolver.uri
			&& qualifiedName == other.qualifiedName
	}

	override fun hashCode(): Int
	{
		var result = resolver.uri.hashCode()
		result = 31 * result + qualifiedName.hashCode()
		return result
	}

	override fun toString(): String = qualifiedName

	/**
	 * Walk the [children][childReferences] of this [ResolverReference]. This
	 * reference **must** be either a [root][ResourceType.ROOT],
	 * [package][ResourceType.PACKAGE], or
	 * [directory][ResourceType.DIRECTORY].
	 *
	 * **NOTE** Graph uses depth-first traversal to visit each reference.
	 *
	 * @param visitResources
	 *   `true` indicates [resources][ResolverReference.isResource] should
	 *   be included in the walk; `false` indicates walk should be
	 *   restricted to packages and [modules][ResourceType.MODULE].
	 * @param withReference
	 *   The lambda that accepts the visited [ResolverReference]. This
	 *   `ResolverReference` will not be provided to the lambda.
	 * @param afterAllVisited
	 *   The lambda that accepts the total number of
	 *   [modules][ResolverReference.isModule] visited to be called after
	 *   all `ResolverReference`s have been visited.
	 */
	fun walkChildrenThen(
		visitResources: Boolean,
		withReference: (ResolverReference)->Unit,
		afterAllVisited: (Int)->Unit = {})
	{
		if (
			type != ResourceType.ROOT
				&& !isPackage
				&& type != ResourceType.DIRECTORY)
		{
			// If there's nothing to walk, then walk nothing.
			return
		}
		val top = modules + LinkedList(
			if (visitResources) childReferences(true)
			else emptyList())
		afterAllVisited(
			top.fold(0) { count, module ->
				count + visitReference(
					module, visitResources, withReference = withReference)
			})
	}

	companion object
	{
		/** The name of the [MessageDigest] used to detect file changes. */
		internal const val DIGEST_ALGORITHM = "SHA-256"

		/**
		 * Visit the provided [ResolverReference] by handing it to the provided
		 * lambda then add all its [children][childReferences] to a stack.
		 * Then pop the next `ResolverReference` and iteratively visit it until
		 * the stack has been emptied.
		 *
		 * **NOTE** Graph uses depth-first traversal to visit each reference.
		 *
		 * @param reference
		 *   The [ResolverReference] being visited.
		 * @param visitResources
		 *   `true` indicates [resources][ResolverReference.isResource] should
		 *   be included in the walk; `false` indicates walk should be
		 *   restricted to packages and [modules][ResourceType.MODULE].
		 * @param withReference
		 *   The lambda that accepts the visited [ResolverReference]. This
		 *   `ResolverReference` will not be provided to the lambda.
		 * @return
		 *   The number of entries that were processed.
		 */
		private fun visitReference (
			reference: ResolverReference,
			visitResources: Boolean,
			withReference: (ResolverReference)->Unit
		): Int
		{
			var ref = reference
			var visited = 0
			val stack: Deque<ResolverReference> = LinkedList()
			while (true)
			{
				if (visitResources || !ref.isResource)
				{
					withReference(ref)
					if (ref.hasChildren)
					{
						// Add this to the stack, when it is removed, we know we've
						// visited all its descendants.
						ref.childReferences(visitResources)
							.forEach(stack::addFirst)
					}
				}
				if (stack.isEmpty()) return visited
				visited++
				ref = stack.removeFirst()
			}
		}
	}
}

/**
 * A `ResourceType` represents the type of a [ResolverReference].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class ResourceType
{
	/** Represents an ordinary Avail module. */
	MODULE,

	/** Represents an Avail package representative. */
	REPRESENTATIVE,

	/** Represents an Avail package. */
	PACKAGE,

	/** Represents an Avail root. */
	ROOT,

	/** Represents an arbitrary directory. */
	DIRECTORY,

	/** Represents an arbitrary resource. */
	RESOURCE;

	/** A short description of the receiver. */
	val label get() = name.lowercase()
}
