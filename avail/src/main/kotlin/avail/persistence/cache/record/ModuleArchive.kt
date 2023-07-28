/*
 * ModuleArchive.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.persistence.cache.record

import avail.builder.ResolvedModuleName
import avail.descriptor.module.ModuleDescriptor
import avail.error.ErrorCode
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.LimitedCache
import avail.resolver.ResolverReference
import avail.utility.decodeString
import avail.utility.sizedString
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap
import kotlin.concurrent.withLock

/**
 * All information associated with a particular module name in this module,
 * across all known versions.
 *
 * @property
 *   The enclosing [Repository].
 *
 * @constructor
 *   Create a blank ModuleArchive.
 * @param repository
 *   The enclosing [Repository].
 * @param rootRelativeName
 *   This module's name, relative to its root.
 */
class ModuleArchive
constructor(
	internal val repository: Repository,
	internal val rootRelativeName: String)
{

	/** The latest `N` versions of this module. */
	private val versions = LinkedHashMap<ModuleVersionKey, ModuleVersion>(
		MAX_RECORDED_VERSIONS_PER_MODULE, 0.75f, true)

	/**
	 * The time of the most recent digest placed in the [digestCache].
	 */
	private var lastUpdate: Long = 0L

	/**
	 * A [LimitedCache] used to avoid computing digests of files when the file's
	 * timestamp has not changed.  Each key is a [Long] representing the file's
	 * [last][File.lastModified].  The value is a byte array holding the SHA-256
	 * digest of the file content.
	 */
	private val digestCache =
		LimitedCache<Long, ByteArray>(MAX_RECORDED_DIGESTS_PER_MODULE)

	/**
	 * Immediately answer the [digest][ByteArray] for the given
	 * [ResolverReference] or `null` if it has not been calculated.
	 *
	 * @param reference
	 *   The `ResolverReference` that points to the module to retrieve the
	 *   digest for.
	 */
	internal fun provideDigest (reference: ResolverReference): ByteArray?
	{
		require(rootRelativeName == reference.moduleName.rootRelativeName) {
			"${reference.qualifiedName} attempted to access archive for " +
				rootRelativeName
		}
		return digestCache[reference.lastModified]
	}

	/**
	 * Answer an immutable [Map] from [ModuleVersionKey] to [ModuleVersion],
	 * containing entries for every version still tracked by this
	 * `ModuleArchive`.
	 *
	 * @return
	 *   An immutable [Map] from [ModuleVersionKey] to [ModuleVersion].
	 */
	val allKnownVersions: SortedMap<ModuleVersionKey, ModuleVersion>
		get()
		{
			val map = repository.lock.withLock { versions.toMap() }
			return Collections.unmodifiableSortedMap(TreeMap(map))
		}

	override fun toString(): String =
		this::class.simpleName +
			versions.entries.sortedBy { it.key }.joinToString(
				", ", " $rootRelativeName (", ")"
			) { (key, value) ->
				"${key.shortString}=${value.compilations.size}"
			}

	/**
	 * Determine the cryptographic hash of the file's current contents. Since we
	 * assume that the same filename and modification time implies the same
	 * digest, we cache the digest under that combination for performance.
	 *
	 * @param resolvedModuleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module, in case the
	 *   backing source file must be read to produce a digest.
	 * @param forceRefreshDigest
	 *   `true` forces a recalculation of the digest; `false` supplies the last
	 *   known digest presuming the file has not changed.
	 * @param withDigest
	 *   What to execute when the digest has been computed or looked up.
	 * @param failureHandler
	 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable] to
	 *   be called in the event of failure.
	 */
	fun digestForFile(
		resolvedModuleName: ResolvedModuleName,
		forceRefreshDigest: Boolean,
		withDigest: (ByteArray)->Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		assert(resolvedModuleName.rootRelativeName == rootRelativeName)
		val sourceReference = resolvedModuleName.resolverReference
		val lastModification = sourceReference.lastModified
		val digest: ByteArray? = digestCache[lastModification]
		if (digest !== null && !forceRefreshDigest)
		{
			withDigest(digest)
			return
		}

		sourceReference.digest(
			forceRefreshDigest,
			withDigest = { newDigest, lastModified ->
				assert(newDigest.size == Repository.DIGEST_SIZE)
				if (lastModified >= lastModification)
				{
					repository.lock.withLock {
						digestCache[lastModified] = newDigest
						lastUpdate = lastModified
						repository.markDirty()
					}
				}
				withDigest(newDigest)
			},
			failureHandler)
	}

	/**
	 * Output this `ModuleArchive` to the provided [DataOutputStream].  It can
	 * later be reconstituted via the constructor taking a
	 * [DataInputStream].
	 *
	 * @param binaryStream
	 *   A [DataOutputStream] on which to write this module archive.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	fun write(binaryStream: DataOutputStream)
	{
		binaryStream.sizedString(rootRelativeName)
		binaryStream.vlq(digestCache.size)
		for ((key, value) in digestCache)
		{
			binaryStream.writeLong(key)
			binaryStream.write(value)
		}
		binaryStream.vlq(versions.size)
		for ((key, value) in versions)
		{
			key.write(binaryStream)
			value.write(binaryStream)
		}
	}

	/**
	 * Reconstruct a `ModuleArchive`, having previously been written via
	 * [write].
	 *
	 * @param binaryStream
	 *   Where to read the module archive from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(
		repository: Repository,
		binaryStream: DataInputStream
	): this(repository, binaryStream.decodeString())
	{
		var digestCount = binaryStream.unvlqInt()
		while (digestCount-- > 0)
		{
			val lastModification = binaryStream.readLong()
			val digest = ByteArray(Repository.DIGEST_SIZE)
			binaryStream.readFully(digest)
			digestCache[lastModification] = digest
		}
		var versionCount = binaryStream.unvlqInt()
		while (versionCount-- > 0)
		{
			val versionKey = ModuleVersionKey(binaryStream)
			val version = ModuleVersion(repository,binaryStream)
			versions[versionKey] = version
		}
	}

	/**
	 * If this [ModuleVersion] exists in the repository, then answer it;
	 * otherwise answer `null`.
	 *
	 * @param versionKey
	 *   The [ModuleVersionKey] identifying the version of a module's source.
	 * @return
	 *   The associated [ModuleVersion] if present, otherwise `null`.
	 */
	fun getVersion(versionKey: ModuleVersionKey): ModuleVersion? =
		repository.lock.withLock { versions[versionKey] }

	/**
	 * Record a [version][ModuleVersion] of a [module][ModuleDescriptor]. This
	 * includes information about the source's digest and the list of local
	 * imports.
	 *
	 * There must not already be a version with that key in the repository.
	 *
	 * @param versionKey
	 *   The [ModuleVersionKey] identifying the version of a module's
	 *   source.
	 * @param version
	 *   The [ModuleVersion] to add.
	 */
	fun putVersion(versionKey: ModuleVersionKey, version: ModuleVersion) =
		repository.lock.withLock {
			versions[versionKey] = version
			repository.markDirty()
		}

	/**
	 * Record a new [compilation][ModuleCompilation] of a [ModuleVersion].  The
	 * version must already exist in the repository.  The compilation
	 * [key][ModuleCompilationKey] must not yet have a [ModuleCompilation]
	 * associated with it.
	 *
	 * @param versionKey
	 *   The [ModuleVersionKey] identifying the version of a module's source.
	 * @param compilationKey
	 *   The [ModuleCompilationKey] under which to record the compilation.
	 * @param compilation
	 *   The [ModuleCompilation] to add.
	 */
	fun putCompilation(
		versionKey: ModuleVersionKey,
		compilationKey: ModuleCompilationKey,
		compilation: ModuleCompilation
	) = repository.lock.withLock {
		val version = versions[versionKey]!!
		version.compilations[compilationKey] = compilation
		repository.markDirty()
	}

	/**
	 * Delete all compiled versions of this module.  Don't remove the cached
	 * file digests.  Note that the compiled versions are still in the
	 * repository, they're just not reachable from the root metadata any
	 * longer.
	 */
	fun cleanCompilations() = versions.clear()

	companion object
	{
		/** The maximum number of versions to keep for each module. */
		private const val MAX_RECORDED_VERSIONS_PER_MODULE = 10

		/** The maximum number of digests to cache per module. */
		private const val MAX_RECORDED_DIGESTS_PER_MODULE = 20
	}
}
