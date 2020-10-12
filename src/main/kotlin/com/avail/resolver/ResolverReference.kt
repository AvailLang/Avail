/*
 * ResolverReference.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.resolver

import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedModuleException
import com.avail.error.ErrorCode
import com.avail.files.AvailFile
import com.avail.files.AvailModuleFile
import com.avail.files.FileManager
import com.avail.files.NullFileWrapper
import com.avail.utility.json.JSONWriter
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.net.URI
import java.security.MessageDigest
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
	 * `true` iff the [ResolverReference] represents a package, `false`
	 *   otherwise.
	 */
	val isPackage: Boolean = type == ResourceType.PACKAGE

	/** The children of this [ResolverReference]. */
	val modules = mutableListOf<ResolverReference>()

	/** The resources of the [ResolverReference]. */
	val resources = mutableListOf<ResolverReference>()

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
	val localName by lazy {
		if (localName.isEmpty()) moduleName.localName
		else localName
	}

	/**
	 * The [exception][Throwable] that prevented most recent attempt at
	 * accessing the source location of this [ResolverReference].
	 */
	var accessException: Throwable? = null

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
	 * Pass this [ResolverReference] to the provided lambda and recursively
	 * visit each child ([modules] and [resources]) providing child to the
	 * lambda.
	 *
	 * @param withReference
	 *   The lambda that accepts the `ResolverReference`s.
	 */
	fun recursivelyVisit (withReference: (ResolverReference) -> Unit)
	{
		withReference(this)
		if (modules.isNotEmpty() || resources.isNotEmpty())
		{
			modules.forEach { it.recursivelyVisit(withReference) }
			resources.forEach { it.recursivelyVisit(withReference) }
		}
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
	 * The cryptographic hash of the file's most recently reported contents.
	 */
	private var digest: ByteArray? = null

	/**
	 * Update the mutable state of this [ResolverReference].
	 *
	 * @param contents
	 *   The raw file bytes used to calculate the digest.
	 * @param lastModified
	 *   The last known time this was updated since the Unix Epoch.
	 * @param size
	 *   The size, in bytes, of the backing file, or 0 if [isPackage] is true.
	 */
	fun refresh (contents: ByteArray, lastModified: Long, size: Long)
	{
		val hasher =
			MessageDigest.getInstance(DIGEST_ALGORITHM)
		hasher.update(contents, 0, contents.size)
		this.digest = hasher.digest()
		this.lastModified = lastModified
		this.size = size
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
	fun digest (
		refresh: Boolean = false,
		withDigest: (ByteArray, Long)->Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		if (isPackage)
		{
			// Get Package Representative instead
			val representative = "$qualifiedName/$localName"
			resolver.provideResolverReference(
				representative,
				{
					it.digest(refresh, withDigest, failureHandler)
				}
			) { code, ex ->
				failureHandler(
					code,
					IOException("$representative not found", ex))
			}
			return
		}
		val currentDigest = digest
		if (!refresh && currentDigest !== null)
		{
			withDigest(currentDigest, lastModified)
			return
		}
		resolver.refreshResolverReference(
			this,
			{
				withDigest(digest!!, lastModified)
			},
			failureHandler)
	}



	/**
	 * Retrieve the resource and provide it with a request to obtain
	 * the raw file bytes.
	 *
	 * @param byPassFileManager
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
		byPassFileManager: Boolean,
		withContents: (ByteArray, UUID?) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		resolver.readFile(byPassFileManager, this, withContents, failureHandler)
	}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is ResolverReference) return false

		if (resolver.uri != other.resolver.uri)
		{
			return false
		}
		if (qualifiedName != other.qualifiedName) return false

		return true
	}

	override fun hashCode(): Int
	{
		var result = resolver.uri.hashCode()
		result = 31 * result + qualifiedName.hashCode()
		return result
	}

	override fun toString(): String = qualifiedName

	companion object
	{
		/** The name of the [MessageDigest] used to detect file changes. */
		private const val DIGEST_ALGORITHM = "SHA-256"
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
	val label get () = name.toLowerCase()
}