/*
 * Repository.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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

package avail.persistence.cache

import avail.builder.ModuleRoot
import avail.builder.ResolvedModuleName
import avail.compiler.ModuleHeader
import avail.compiler.ModuleManifestEntry
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.representation.AvailObject.Companion.multiplier
import avail.descriptor.tokens.CommentTokenDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*
import avail.error.ErrorCode
import avail.persistence.cache.Repository.PhraseNode.PhraseNodeToken
import avail.resolver.ResolverReference
import avail.serialization.Serializer
import avail.utility.Mutable
import avail.utility.decodeString
import avail.utility.iterableWith
import avail.utility.sizedString
import avail.utility.unvlqInt
import avail.utility.unvlqLong
import avail.utility.unzigzagInt
import avail.utility.unzigzagLong
import avail.utility.vlq
import avail.utility.zigzag
import org.availlang.persistence.IndexedFile
import org.availlang.persistence.IndexedFile.ByteArrayOutputStream
import org.availlang.persistence.IndexedFileBuilder
import org.availlang.persistence.IndexedFileException
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableSortedMap
import java.util.Formatter
import java.util.Map.Entry.comparingByKey
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * A [Repository] manages a persistent [IndexedFile] of compiled
 * [modules][ModuleDescriptor].
 *
 * ```
 * **Metadata:**
 * 1. #modules
 * 2. For each module,
 *    2a. moduleArchive
 *
 * **ModuleArchive:**
 * 1. UTF8 rootRelativeName
 * 2. digestCache size
 * 3. For each cached digest,
 *    3a. timestamp (long)
 *    3b. digest (32 bytes)
 * 4. #versions
 * 5. For each version,
 *    5a. ModuleVersionKey
 *    5b. ModuleVersion
 *
 * **ModuleVersionKey:**
 * 1. isPackage (byte)
 * 2. digest (32 bytes)
 *
 * **ModuleVersion:**
 * 1. moduleSize (long)
 * 2. localImportNames size (int)
 * 3. For each import name,
 *    3a. UTF8 import name
 * 4. entryPoints size (int)
 * 5. For each entry point,
 *    5a. UTF8 entry point name
 * 6. compilations size (int)
 * 7. For each compilation.
 *    7a. ModuleCompilationKey
 *    7b. ModuleCompilation
 * 8. moduleHeaderRecordNumber (long)
 * 9. stacksRecordNumber (long)
 *
 * **ModuleCompilationKey:**
 * 1. predecessorCompilationTimes length (int)
 * 2. For each predecessor compilation time,
 *    2a. predecessor compilation time (long)
 *
 * **ModuleCompilation:**
 * 1. compilationTime (long)
 * 2. recordNumber (long)
 * 3. recordNumberOfBlockPhrases (long)
 * 4. recordNumberOfManifestEntries (long)
 * 5. recordNumberOfStyling (long)
 * 6. recordNumberOfPhrasePaths (long)
 * ```
 *
 * @property rootName
 *   The name of the [Avail&#32;root][ModuleRoot] represented by this
 *   [IndexedFile].
 * @property fileName
 *   The [filename][File] of the [IndexedFile].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `Repository`.
 *
 * @param rootName
 *   The name of the Avail root represented by the `Repository`.
 * @param fileName
 *   The [path][File] to the indexed repository.
 * @throws IndexedFileException
 *   If an [exception][Exception] occurs.
 */
class Repository constructor(
	private val rootName: String,
	val fileName: File) : Closeable
{
	/**
	 * `IndexedRepositoryBuilder` is a builder for opening the [IndexedFile]s
	 * used to hold a collection of compiled Avail [modules][ModuleDescriptor].
	 *
	 * NOTE: Update the version number at the end, to ensure all existing
	 * repositories will be discarded and rebuilt upon next use.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	private object IndexedRepositoryBuilder : IndexedFileBuilder(
		"Avail compiled module repository V12")

	/**
	 * The [lock][ReentrantLock] responsible for guarding against unsafe
	 * concurrent access.
	 */
	internal val lock = ReentrantLock()

	/**
	 * The [repository][IndexedFile] that stores this [Repository]'s compiled
	 * [modules][ModuleDescriptor].
	 */
	internal var repository: IndexedFile? = null

	/**
	 * Keep track of whether changes have happened since the last commit, and
	 * when the first such change happened.
	 */
	private var dirtySince = 0L

	/**
	 * A [Map] from the
	 * [root-relative&#32;name][ResolvedModuleName.rootRelativeName] of each
	 * module that has ever been compiled within this repository to the
	 * corresponding ModuleArchive.
	 */
	private val moduleMap = mutableMapOf<String, ModuleArchive>()

	/**
	 * Produce an alphabetized list of all modules known to this repository.
	 *
	 * @return
	 *   An immutable [List] of [ModuleArchive]s.
	 */
	internal val allArchives: List<ModuleArchive>
		get() = unmodifiableList(
			lock.withLock {
				moduleMap.entries.stream()
					.sorted(comparingByKey())
					.map { it.value }
					.toList()
			})

	/** Is the `Repository` open? */
	private var isOpen = false

	/**
	 * A [Map] which discards the oldest entry whenever an attempt is made
	 * to store more than the [maximumSize] elements in it.
	 *
	 * @param K
	 *   The keys of the cache.
	 * @param V
	 *   The values associated with keys of the cache.
	 * @property maximumSize
	 *   The largest size that this cache can be after any public operation.
	 *
	 * @constructor
	 *
	 * Construct a new `LimitedCache` with the given maximum size.
	 *
	 * @param maximumSize
	 *   The maximum cache size.
	 */
	class LimitedCache<K, V> constructor(
		private val maximumSize: Int)
	: LinkedHashMap<K, V>(maximumSize, 0.75f, true)
	{
		init
		{
			assert(maximumSize > 0)
		}

		override fun removeEldestEntry(eldest: Map.Entry<K, V>?) =
			size > maximumSize
	}

	/**
	 * All information associated with a particular module name in this module,
	 * across all known versions.
	 */
	inner class ModuleArchive
	{
		/** Expose the enclosing Repository as a property. */
		val repository: Repository get() = this@Repository

		/** The latest `N` versions of this module. */
		private val versions = LinkedHashMap<ModuleVersionKey, ModuleVersion>(
			MAX_RECORDED_VERSIONS_PER_MODULE,
			0.75f,
			true)

		/** This module's name, relative to its root. */
		internal val rootRelativeName: String

		/**
		 * The time of the most recent digest placed in the [digestCache].
		 */
		private var lastUpdate: Long = 0L

		/**
		 * A [LimitedCache] used to avoid computing digests of files when the
		 * file's timestamp has not changed.  Each key is a [Long] representing
		 * the file's [last][File.lastModified].  The value is a byte array
		 * holding the SHA-256 digest of the file content.
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
				val map = lock.withLock { versions.toMap() }
				return unmodifiableSortedMap(TreeMap(map))
			}

		override fun toString(): String =
			this::class.simpleName +
				versions.entries.sortedWith(comparingByKey()).joinToString(
					", ", " $rootRelativeName (", ")"
				) { (key, value) ->
					"${key.shortString}=${value.compilations.size}"
				}

		/**
		 * Determine the cryptographic hash of the file's current contents.
		 * Since we assume that the same filename and modification time implies
		 * the same digest, we cache the digest under that combination for
		 * performance.
		 *
		 * @param resolvedModuleName
		 *   The [resolved&#32;name][ResolvedModuleName] of the module, in case
		 *   the backing source file must be read to produce a digest.
		 * @param forceRefreshDigest
		 *   `true` forces a recalculation of the digest; `false` supplies the last
		 *   known digest presuming the file has not changed.
		 * @param failureHandler
		 *   A function that accepts an [ErrorCode] and a `nullable` [Throwable]
		 *   to be called in the event of failure.
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

			val success: (ByteArray, Long) -> Unit =
				{ newDigest, lastModified ->
					assert(newDigest.size == DIGEST_SIZE)
					if (lastModified >= lastModification)
					{
						lock.withLock {
							digestCache[lastModified] = newDigest
							lastUpdate = lastModified
							markDirty()
						}
					}
					withDigest(newDigest)
				}
			sourceReference.digest(forceRefreshDigest, success, failureHandler)
		}

		/**
		 * Output this `ModuleArchive` to the provided [DataOutputStream].  It
		 * can later be reconstituted via the constructor taking a
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
		internal constructor(binaryStream: DataInputStream)
		{
			rootRelativeName = binaryStream.decodeString()
			var digestCount = binaryStream.unvlqInt()
			while (digestCount-- > 0)
			{
				val lastModification = binaryStream.readLong()
				val digest = ByteArray(DIGEST_SIZE)
				binaryStream.readFully(digest)
				digestCache[lastModification] = digest
			}
			var versionCount = binaryStream.unvlqInt()
			while (versionCount-- > 0)
			{
				val versionKey = ModuleVersionKey(binaryStream)
				val version = ModuleVersion(binaryStream)
				versions[versionKey] = version
			}
		}

		/**
		 * Construct a new `ModuleArchive`.
		 *
		 * @param rootRelativeName
		 *   The name of the module, relative to the root of this repository.
		 */
		constructor(rootRelativeName: String)
		{
			this.rootRelativeName = rootRelativeName
		}

		/**
		 * If this [ModuleVersion] exists in the repository, then answer it;
		 * otherwise answer `null`.
		 *
		 * @param versionKey
		 *   The [ModuleVersionKey] identifying the version of a module's
		 *   source.
		 * @return
		 *   The associated [ModuleVersion] if present, otherwise `null`.
		 */
		fun getVersion(versionKey: ModuleVersionKey): ModuleVersion? =
			lock.withLock { versions[versionKey] }

		/**
		 * Record a [version][ModuleVersion] of a [module][ModuleDescriptor].
		 * This includes information about the source's digest and the list of
		 * local imports.
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
			lock.withLock {
				versions[versionKey] = version
				markDirty()
			}

		/**
		 * Record a new [compilation][ModuleCompilation] of a
		 * [module&#32;version][ModuleVersion].  The version must already exist
		 * in the repository.  The [compilation&#32;key][ModuleCompilationKey]
		 * must not yet have a [compilation][ModuleCompilation] associated with
		 * it.
		 *
		 * @param versionKey
		 *   The [ModuleVersionKey] identifying the version of a module's
		 *   source.
		 * @param compilationKey
		 *   The [ModuleCompilationKey] under which to record the compilation.
		 * @param compilation
		 *   The [ModuleCompilation] to add.
		 */
		fun putCompilation(
				versionKey: ModuleVersionKey,
				compilationKey: ModuleCompilationKey,
				compilation: ModuleCompilation) =
			lock.withLock {
				val version = versions[versionKey]!!
				version.compilations[compilationKey] = compilation
				markDirty()
			}

		/**
		 * Delete all compiled versions of this module.  Don't remove the cached
		 * file digests.  Note that the compiled versions are still in the
		 * repository, they're just not reachable from the root metadata any
		 * longer.
		 */
		fun cleanCompilations() = versions.clear()
	}

	/**
	 * An immutable key which specifies a version of some module.  It includes
	 * whether the module's name refers to a package (a directory), and the
	 * digest of the file's contents.
	 */
	class ModuleVersionKey : Comparable<ModuleVersionKey>
	{
		/** Is the [module][ModuleDescriptor] a package representative? */
		private val isPackage: Boolean

		/**
		 * The SHA256 digest of the UTF-8 representation of the module's source
		 * code.
		 */
		val sourceDigest: ByteArray

		/** A hash of all the fields except the index. */
		private val hash: Int

		override fun hashCode() = hash

		/**
		 * Calculate my hash.
		 *
		 * @return
		 *   The hash of my immutable content.
		 */
		private fun computeHash(): Int
		{
			var h = if (isPackage) 0xDEAD_BEEF else 0xA_CABBA6E
			for (digestByte in sourceDigest)
			{
				h = h * multiplier + digestByte
			}
			return h.toInt()
		}

		override fun equals(other: Any?): Boolean
		{
			if (other === null)
			{
				return false
			}
			if (other !is ModuleVersionKey)
			{
				return false
			}
			val key = other as ModuleVersionKey?
			return (hash == key!!.hash
				&& isPackage == key.isPackage
				&& sourceDigest.contentEquals(key.sourceDigest))
		}

		/**
		 * Output this module version key to the provided [DataOutputStream].
		 * An equal key can later be rebuilt via the constructor taking a
		 * [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this key.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		fun write(binaryStream: DataOutputStream)
		{
			binaryStream.writeBoolean(isPackage)
			binaryStream.write(sourceDigest)
		}

		override fun toString(): String =
			String.format("VersionKey(@%s...)", shortString)

		/**
		 * Reconstruct a `ModuleVersionKey`, having previously been written via
		 * [write].
		 *
		 * @param binaryStream
		 *   Where to read the version key from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(binaryStream: DataInputStream)
		{
			isPackage = binaryStream.readBoolean()
			sourceDigest = ByteArray(DIGEST_SIZE)
			binaryStream.readFully(sourceDigest)
			hash = computeHash()
		}

		/**
		 * Construct a new `ModuleVersionKey`.
		 *
		 * @param moduleName
		 *   The [resolved&#32;name][ResolvedModuleName] of the module.
		 * @param sourceDigest
		 *   The digest of the module, which (cryptographically) uniquely
		 *   identifies which source code is present within this version.
		 */
		constructor(
			moduleName: ResolvedModuleName,
			sourceDigest: ByteArray)
		{
			assert(sourceDigest.size == DIGEST_SIZE)
			this.sourceDigest = sourceDigest.clone()
			this.isPackage = moduleName.isPackage
			this.hash = computeHash()
		}

		/**
		 * Answer a short identifier of the module version.  Use a short prefix
		 * of the digest.
		 *
		 * @return
		 *   A short [String] to help identify this module version.
		 */
		val shortString: String get() =
			String.format(
				"%02x%02x%02x",
				sourceDigest[0],
				sourceDigest[1],
				sourceDigest[2])

		override fun compareTo(other: ModuleVersionKey): Int
		{
			var i = 0
			while (i < sourceDigest.size && i < other.sourceDigest.size)
			{
				// Compare as unsigned bytes.
				val d =
					(sourceDigest[i].toInt() and 255) -
						(other.sourceDigest[i].toInt() and 255)
				if (d != 0)
				{
					return d
				}
				i++
			}
			return sourceDigest.size - other.sourceDigest.size
		}
	}

	/**
	 * An immutable key which specifies a version of a module and its context at
	 * the time of compilation.  It does not explicitly contain the
	 * [ModuleVersionKey], but it includes the compilation times of the module's
	 * predecessors.
	 */
	class ModuleCompilationKey
	{
		/**
		 * The times at which this module's predecessors were compiled, in the
		 * order specified by the Uses/Extends declarations.
		 */
		private val predecessorCompilationTimes: LongArray

		/** A hash of all the fields except the index. */
		private val hash: Int

		override fun hashCode() = hash

		/**
		 * Calculate my hash.
		 *
		 * @return The hash of my immutable content.
		 */
		private fun computeHash(): Int
		{
			var h = 0x9E5_90125.toInt()
			for (predecessorCompilationTime in predecessorCompilationTimes)
			{
				h = mix(h, predecessorCompilationTime)
			}
			return h
		}

		override fun equals(other: Any?): Boolean
		{
			if (other === null)
			{
				return false
			}
			if (other !is ModuleCompilationKey)
			{
				return false
			}
			val key = other as ModuleCompilationKey?
			return hash == key!!.hash &&
				predecessorCompilationTimes.contentEquals(
					key.predecessorCompilationTimes)
		}

		/**
		 * Output this module compilation key to the provided
		 * [DataOutputStream].  An equal key can later be rebuilt via the
		 * constructor taking a [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this key.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		fun write(binaryStream: DataOutputStream)
		{
			binaryStream.vlq(predecessorCompilationTimes.size)
			for (predecessorCompilationTime in predecessorCompilationTimes)
			{
				binaryStream.writeLong(predecessorCompilationTime)
			}
		}

		/**
		 * Reconstruct a `ModuleCompilationKey`, having previously been
		 * written via [write].
		 *
		 * @param binaryStream
		 *   Where to read the compilation key from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(binaryStream: DataInputStream)
		{
			val predecessorsCount = binaryStream.unvlqInt()
			predecessorCompilationTimes = LongArray(predecessorsCount)
			for (i in 0 until predecessorsCount)
			{
				predecessorCompilationTimes[i] = binaryStream.readLong()
			}
			hash = computeHash()
		}

		/**
		 * Construct a new `ModuleCompilationKey`.
		 *
		 * @param predecessorCompilationTimes
		 *   The compilation times of this module's predecessors, in the order
		 *   of import declaration.
		 */
		constructor(predecessorCompilationTimes: LongArray)
		{
			this.predecessorCompilationTimes =
				predecessorCompilationTimes.clone()
			hash = computeHash()
		}
	}

	/**
	 * Information kept in memory about a specific version of a
	 * [module][ModuleDescriptor] file.
	 */
	inner class ModuleVersion
	{
		/**
		 * The size of the [module][ModuleDescriptor]'s source code, in bytes.
		 */
		private val moduleSize: Long

		/**
		 * The names of the modules being imported by this version of this
		 * module.  The names are local names, in the order they occur in the
		 * module source.
		 */
		private val localImportNames: MutableList<String>

		/**
		 * The list of entry points declared by this version of the module. Note
		 * that because the entry point declarations are in the module header
		 * and in a fixed syntax, all valid compilations of the module would
		 * produce the same list of entry points.  Therefore, the entry points
		 * belong here in the module version, not with a compilation.
		 */
		private val entryPoints: MutableList<String>

		/**
		 * The `N` most recently recorded compilations of this version of the
		 * module.
		 */
		internal val compilations =
			LimitedCache<ModuleCompilationKey, ModuleCompilation>(
				MAX_HISTORICAL_VERSION_COMPILATIONS)

		/**
		 * Answer the list of local module names imported by this version of the
		 * module.
		 *
		 * @return
		 *   The list of local module names.
		 */
		val imports: List<String>
			get() = unmodifiableList(localImportNames)

		/**
		 * The persistent record number of the [module][ModuleHeader] for this
		 * [version][ModuleVersion].
		 */
		private var moduleHeaderRecordNumber: Long = -1L

		/**
		 * Answer the [serialized][Serializer] [module&#32;header][ModuleHeader]
		 * associated with this [version][ModuleVersion].
		 *
		 * @return
		 *   A serialized module header.
		 */
		val moduleHeader: ByteArray
			get()
			{
				assert(moduleHeaderRecordNumber != -1L)
				return lock.withLock {
					repository!![moduleHeaderRecordNumber]
				}
			}

		/**
		 * The persistent record number of the Stacks
		 * [comments][CommentTokenDescriptor] associated with this
		 * [version][ModuleVersion] of the [module][ModuleDescriptor].
		 */
		private var stacksRecordNumber = -1L

		/**
		 * Answer the [serialized][Serializer] [tuple][TupleDescriptor] of
		 * [comment][CommentTokenDescriptor] associated with this `ModuleVersion
		 * version`.
		 *
		 * @return
		 *   A serialized tuple of comment tokens, or `null` if the
		 *   [module][ModuleDescriptor] has not been compiled yet.
		 */
		val comments: ByteArray?
			get() =
				if (stacksRecordNumber == -1L) null
				else lock.withLock { repository!![stacksRecordNumber] }

		/**
		 * An immutable [List] of compilations for this module version. There
		 * may be multiple compilations due to changes in ancestor module
		 * versions that forced this module to be recompiled.
		 */
		val allCompilations: List<ModuleCompilation>
			get() = lock.withLock { compilations.values.toList() }

		/**
		 * Look up the [ModuleCompilation] associated with the provided
		 * [ModuleCompilationKey], answering `null` if unavailable.
		 *
		 * @param compilationKey
		 *   The context information about a compilation.
		 * @return
		 *   The corresponding compilation or `null`.
		 */
		fun getCompilation(
				compilationKey: ModuleCompilationKey) =
			lock.withLock { compilations[compilationKey] }

		/**
		 * The list of entry point names declared by this version of the module.
		 *
		 * @return
		 *   The list of entry point names.
		 */
		fun getEntryPoints(): List<String> = unmodifiableList(entryPoints)

		/**
		 * Write the specified byte array (encoding a [ModuleHeader]) into the
		 * indexed file. Record the record position for subsequent retrieval.
		 *
		 * @param bytes
		 *   A [serialized][Serializer] module header.
		 */
		fun putModuleHeader(bytes: ByteArray) =
			lock.withLock {
				// Write the serialized data to the end of the repository.
				val repo = repository!!
				moduleHeaderRecordNumber = repo.add(bytes)
				markDirty()
			}

		/**
		 * Write the specified byte array (encoding a [tuple][TupleDescriptor]
		 * of [comment][CommentTokenDescriptor]) into the indexed file. Record
		 * the record position for subsequent retrieval.
		 *
		 * @param bytes
		 *   A [serialized][Serializer] tuple of comment tokens.
		 */
		fun putComments(bytes: ByteArray) =
			lock.withLock {
				// Write the comment tuple to the end of the repository.
				val repo = repository!!
				stacksRecordNumber = repo.add(bytes)
				markDirty()
			}

		/**
		 * Output this module version to the provided [DataOutputStream].  It
		 * can later be reconstructed via the constructor taking a
		 * [DataInputStream].
		 *
		 * @param binaryStream
		 *   A [DataOutputStream] on which to write this module version.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal fun write(binaryStream: DataOutputStream)
		{
			binaryStream.vlq(moduleSize)
			binaryStream.vlq(localImportNames.size)
			for (importName in localImportNames)
			{
				binaryStream.sizedString(importName)
			}
			binaryStream.vlq(entryPoints.size)
			for (entryPoint in entryPoints)
			{
				binaryStream.sizedString(entryPoint)
			}
			binaryStream.vlq(compilations.size)
			for ((key, value) in compilations)
			{
				key.write(binaryStream)
				value.write(binaryStream)
			}
			binaryStream.zigzag(moduleHeaderRecordNumber)
			binaryStream.zigzag(stacksRecordNumber)
		}

		override fun toString(): String =
			String.format(
				"Version:%n"
					+ "\t\timports=%s%s%n"
					+ "\t\tcompilations=%s%n"
					+ "\t\tmoduleHeaderRecordNumber=%d%n"
					+ "\t\tstacksRecordNumber=%d%n",
				localImportNames,
				if (entryPoints.isEmpty())
					""
				else
					"\n\t\tentry points=$entryPoints",
				compilations.values,
				moduleHeaderRecordNumber,
				stacksRecordNumber)

		/**
		 * Reconstruct a `ModuleVersion`, having previously been written via
		 * [write].
		 *
		 * @param binaryStream
		 *   Where to read the key from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(binaryStream: DataInputStream)
		{
			moduleSize = binaryStream.unvlqLong()
			var localImportCount = binaryStream.unvlqInt()
			localImportNames = mutableListOf()
			while (localImportCount-- > 0)
			{
				localImportNames.add(binaryStream.decodeString())
			}
			var entryPointCount = binaryStream.unvlqInt()
			entryPoints = mutableListOf()
			while (entryPointCount-- > 0)
			{
				entryPoints.add(binaryStream.decodeString())
			}
			var compilationsCount = binaryStream.unvlqInt()
			while (compilationsCount-- > 0)
			{
				compilations[ModuleCompilationKey(binaryStream)] =
					ModuleCompilation(binaryStream)
			}
			moduleHeaderRecordNumber = binaryStream.unzigzagLong()
			stacksRecordNumber = binaryStream.unzigzagLong()
		}

		/**
		 * Construct a new `ModuleVersion`.
		 *
		 * @param moduleSize
		 *   The size of the compiled module, in bytes.
		 * @param localImportNames
		 *   The list of module names being imported.
		 * @param entryPoints
		 *   The list of entry points defined in the module.
		 */
		constructor(
			moduleSize: Long,
			localImportNames: List<String>,
			entryPoints: List<String>)
		{
			this.moduleSize = moduleSize
			this.localImportNames = localImportNames.toMutableList()
			this.entryPoints = entryPoints.toMutableList()
		}
	}

	/**
	 * Information kept in memory about a compilation of a
	 * [module][ModuleDescriptor].
	 */
	inner class ModuleCompilation
	internal constructor(
		/** The time at which this module was compiled. */
		val compilationTime: Long,

		/**
		 * The persistent record number of this version of the compiled
		 * [module][ModuleDescriptor].
		 */
		val recordNumber: Long,

		/**
		 * The record number at which a tuple of block phrases for this
		 * compilation is stored.  This can be fetched on demand, separately
		 * from the [A_RawFunction]s needed to load the module.
		 */
		val recordNumberOfBlockPhrases: Long,

		/**
		 * The record number at which a [ByteArray] was recorded for this
		 * module. That record should be fetched as needed and decoded into a
		 * [ManifestRecord].
		 */
		val recordNumberOfManifest: Long,

		/**
		 * The record number at which a [ByteArray] was recorded for this
		 * module. That record should be fetched as needed and decoded into a
		 * [StylingRecord].
		 */
		val recordNumberOfStyling: Long,

		/**
		 * The record number at which a [ByteArray] was recorded for this
		 * module, in which is an encoding that can be used to convert from a
		 * file position to a series of phrases (of descending extent) which
		 * ultimately contain the token at that file position.
		 */
		val recordNumberOfPhrasePaths: Long
	) {
		/** The byte array containing a serialization of this compilation. */
		val bytes: ByteArray
			get() = lock.withLock { repository!![recordNumber] }

		/**
		 * Output this module compilation to the provided [DataOutputStream].
		 * It can later be reconstructed via the constructor taking a
		 * [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this module compilation.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal fun write(binaryStream: DataOutputStream)
		{
			binaryStream.writeLong(compilationTime)
			binaryStream.zigzag(recordNumber)
			binaryStream.zigzag(recordNumberOfBlockPhrases)
			binaryStream.zigzag(recordNumberOfManifest)
			binaryStream.zigzag(recordNumberOfStyling)
			binaryStream.zigzag(recordNumberOfPhrasePaths)
		}

		override fun toString(): String =
			String.format(
				"Compilation(%tFT%<tTZ, rec=%d, phrases=%d, manifest=%d, " +
					"styling=%d, phrase paths=%d)",
				compilationTime,
				recordNumber,
				recordNumberOfBlockPhrases,
				recordNumberOfManifest,
				recordNumberOfStyling,
				recordNumberOfPhrasePaths)

		/**
		 * Reconstruct a `ModuleCompilation`, having previously been written via
		 * [write].
		 *
		 * @param binaryStream
		 *   Where to read the key from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(binaryStream: DataInputStream) : this(
			compilationTime = binaryStream.readLong(),
			recordNumber = binaryStream.unzigzagLong(),
			recordNumberOfBlockPhrases = binaryStream.unzigzagLong(),
			recordNumberOfManifest = binaryStream.unzigzagLong(),
			recordNumberOfStyling = binaryStream.unzigzagLong(),
			recordNumberOfPhrasePaths = binaryStream.unzigzagLong())
	}

	/**
	 * Construct a new `ModuleCompilation`, adding the serialized compiled
	 * module bytes to the repository without committing.
	 *
	 * @param compilationTime
	 *   The compilation time of this module.
	 * @param serializedBody
	 *   The [serialized][Serializer] form of the compiled module.
	 * @param serializedBlockPhrases
	 *   The [serialized][Serializer] form of the module's block phrases.
	 * @param manifest
	 *   The [ManifestRecord] containing each [ModuleManifestEntry] captured for
	 *   this module during this just-completed compilation.
	 * @param stylingRecord
	 *   The [StylingRecord] that captures which symbolic styles should be
	 *   applied to which parts of the source code.
	 * @param phrasePaths
	 *   The [PhrasePathRecord] containing information about file position
	 *   ranges for tokens, and the trees of phrases containing them.
	 */
	fun createModuleCompilation(
		compilationTime: Long,
		serializedBody: ByteArray,
		serializedBlockPhrases: ByteArray,
		manifest: ManifestRecord,
		stylingRecord: StylingRecord,
		phrasePaths: PhrasePathRecord
	) : ModuleCompilation
	{
		// No need to hold a lock during initialization.
		val manifestBytes = ByteArrayOutputStream(4096)
		manifest.write(DataOutputStream(manifestBytes))
		val innerStylingRecordBytes = ByteArrayOutputStream(4096)
		stylingRecord.write(DataOutputStream(innerStylingRecordBytes))
		val innerPhrasePathsBytes = ByteArrayOutputStream(4096)
		phrasePaths.write(DataOutputStream(innerPhrasePathsBytes))
		return repository!!.run {
			lock.withLock {
				ModuleCompilation(
					compilationTime = compilationTime,
					recordNumber = add(serializedBody),
					recordNumberOfBlockPhrases = add(serializedBlockPhrases),
					recordNumberOfManifest = add(manifestBytes.toByteArray()),
					recordNumberOfStyling = add(
						innerStylingRecordBytes.toByteArray()),
					recordNumberOfPhrasePaths = add(
						innerPhrasePathsBytes.toByteArray()))
			}
		}
	}

	/**
	 * Manifest information that was collected during compilation of a
	 * [module][A_Module].  This keeps track of where certain declarations and
	 * definitions occurred within a module during compilation, each summarized
	 * as a [ModuleManifestEntry].
	 */
	class ManifestRecord
	{
		/**
		 * The [List] of each [ModuleManifestEntry] present in this module.
		 */
		val manifestEntries: List<ModuleManifestEntry>

		/**
		 * Output this styling record to the provided [DataOutputStream].
		 * It can later be reconstructed via the constructor taking a
		 * [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this styling record.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal fun write(binaryStream: DataOutputStream)
		{
			manifestEntries.forEach { entry ->
				entry.write(binaryStream)
			}
		}

		override fun toString(): String =
			String.format(
				"ManifestRecord (%d entries)",
				manifestEntries.size)

		/**
		 * Reconstruct a [ManifestRecord], having previously been written via
		 * [write].
		 *
		 * @param bytes
		 *   Where to read the [ManifestRecord] from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(bytes: ByteArray)
		{
			val binaryStream = DataInputStream(ByteArrayInputStream(bytes))
			val entries = mutableListOf<ModuleManifestEntry>()
			while (binaryStream.available() > 0)
			{
				entries.add(ModuleManifestEntry(binaryStream))
			}
			manifestEntries = entries
		}

		/**
		 * Construct a new [ManifestRecord] from a [List] of each
		 * [ModuleManifestEntry].
		 *
		 * @param entries
		 *   The [List] of each [ModuleManifestEntry].
		 */
		constructor(entries: List<ModuleManifestEntry>)
		{
			this.manifestEntries = entries
		}
	}

	/**
	 * Styling information that was collected during compilation of a
	 * [module][A_Module].
	 */
	class StylingRecord
	{
		/**
		 * An ascending sequence of non-overlapping, non-empty [IntRange]s, with
		 * the style name to apply to that range.
		 */
		val styleRuns: List<StyleRun>

		/**
		 * Information about variable uses and definitions.  The pairs go from
		 * use to definition.
		 */
		val variableUses: List<Pair<IntRange, IntRange>>

		/**
		 * Output this styling record to the provided [DataOutputStream].
		 * It can later be reconstructed via the constructor taking a
		 * [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this styling record.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal fun write(binaryStream: DataOutputStream)
		{
			val styleToIndex = mutableMapOf<String, Int>()
			val stylesList = mutableListOf<String>()
			styleRuns.forEach { (_, styleName) ->
				styleToIndex.computeIfAbsent(styleName) {
					stylesList.add(it)
					stylesList.size
				}
			}
			// Output all style names.
			binaryStream.vlq(styleToIndex.size)
			stylesList.forEach(binaryStream::sizedString)
			var pos = 0
			// Collect the <style#, length> pairs, dropping any zero-length
			// spans.
			val nonemptyRuns = mutableListOf<Pair<Int, Int>>()
			styleRuns.forEach { (run, styleName) ->
				val delta = run.first - pos
				assert (delta >= 0)
				if (delta > 0)
				{
					// Output an unstyled span.
					nonemptyRuns.add(0 to delta)
				}
				assert (run.last >= run.first)
				nonemptyRuns.add(
					styleToIndex[styleName]!! to run.last - run.first + 1)
				pos = run.last + 1
			}
			// Output contiguous styled (and unstyled) spans.
			binaryStream.vlq(nonemptyRuns.size)
			nonemptyRuns.forEach { (styleNumber, length) ->
				binaryStream.vlq(styleNumber)
				binaryStream.vlq(length)
			}
			val declarationsWithUses = variableUses
				// Convert to map<decl, list<uses>>.
				.groupBy({ it.second }) { it.first }
				// Convert to list<pair<decl, sorted_list<uses>>>.
				.map { (k, v) -> k to v.sortedBy { it.first }}
				// Convert to sorted_list<pair<decl, sorted_list<uses>>>.
				.sortedBy { it.first.first }
			// Output declaration information.
			binaryStream.vlq(declarationsWithUses.size)
			var previousDeclarationEnd = 0
			declarationsWithUses.forEach { (decl, uses) ->
				// Write decl's start relative to previous decl's end
				binaryStream.vlq(decl.first - previousDeclarationEnd)
				// And the decl string size.
				val declSize = decl.last - decl.first + 1
				binaryStream.vlq(declSize)
				// Use a compact encoding if the uses are all after the
				// declaration, and the uses all have the same size as the
				// declaration.  This will be the usual case.
				val isCompact = uses.all {
					it.last - it.first + 1 == declSize
						&& it.first > decl.last
				}
				if (isCompact)
				{
					binaryStream.vlq(uses.size)
					var previousUseEnd = previousDeclarationEnd
					uses.forEach { use ->
						binaryStream.vlq(use.first - previousUseEnd)
						previousUseEnd = use.last + 1
					}
				}
				else
				{
					// Flag to indicate it was non-compact
					binaryStream.vlq(0)
					binaryStream.vlq(uses.size)
					var previousUseEnd = 0
					uses.forEach { use ->
						binaryStream.vlq(use.first - previousUseEnd)
						binaryStream.vlq(use.last - use.first + 1)
						previousUseEnd = use.last + 1
					}
				}
				previousDeclarationEnd = decl.last + 1
			}
		}

		override fun toString(): String =
			String.format(
				"StylingRecord (%d styled runs)",
				styleRuns.size)

		/**
		 * Reconstruct a [StylingRecord], having previously been written via
		 * [write].
		 *
		 * @param bytes
		 *   Where to read the [StylingRecord] from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(bytes: ByteArray)
		{
			val binaryStream = DataInputStream(ByteArrayInputStream(bytes))
			val styles = Array(binaryStream.unvlqInt()) {
				binaryStream.decodeString()
			}
			// Read all the spans, including unstyled ones.
			var pos = 0

			val allRuns = mutableListOf<StyleRun>()
			repeat(binaryStream.unvlqInt()) {
				val styleNumber = binaryStream.unvlqInt()
				val length = binaryStream.unvlqInt()
				pos += length
				if (styleNumber > 0)
				{
					allRuns.add(
						(pos - length until pos)
							to styles[styleNumber - 1])
				}
			}
			styleRuns = allRuns
			val usesToDeclarations = mutableListOf<Pair<IntRange, IntRange>>()
			var previousDeclarationEnd = 0
			repeat(binaryStream.unvlqInt()) {
				val declStart = previousDeclarationEnd + binaryStream.unvlqInt()
				val length = binaryStream.unvlqInt()
				val decl = declStart until declStart + length
				val usagesOrZero = binaryStream.unvlqInt()
				if (usagesOrZero == 0)
				{
					// Special form, where uses may precede declaration, or have
					// tokens of a different size.  Rare.
					var previousUseEnd = 0
					repeat(binaryStream.unvlqInt()) {
						val useStart = previousUseEnd + binaryStream.unvlqInt()
						val size = binaryStream.unvlqInt()
						val use = useStart until useStart + size
						usesToDeclarations.add(use to decl)
						previousUseEnd = use.last + 1
					}
				}
				else
				{
					// Compact form.  Uses must follow declaration, and must all
					// have the same size token as the declaration.
					var previousUseEnd = previousDeclarationEnd
					repeat(usagesOrZero) {
						val useStart = previousUseEnd + binaryStream.unvlqInt()
						val use = useStart until useStart + length
						usesToDeclarations.add(use to decl)
						previousUseEnd = use.last + 1
					}
				}
				previousDeclarationEnd = declStart + length
			}
			variableUses = usesToDeclarations
			assert(binaryStream.available() == 0)
		}

		/**
		 * Construct a new [StylingRecord] from its parts.
		 *
		 * @param styleRuns
		 *   An ascending sequence of non-overlapping, non-empty [IntRange]s,
		 *   with the style name to apply to that range.
		 * @param variableUses
		 *   Information about variable uses and definitions.  The pairs go from
		 *   use to definition.
		*/
		constructor(
			styleRuns: List<StyleRun>,
			variableUses: List<Pair<IntRange, IntRange>>)
		{
			this.styleRuns = styleRuns
			this.variableUses = variableUses
		}
	}

	/**
	 * A node of a tree that represents an occurrence of an [A_Phrase] in this
	 * [A_Module].  If the phrase is a send or macro invocation, the information
	 * about which atom's bundle was sent is available, as are the tokens that
	 * are part of the phrase (but not its subphrases).
	 *
	 * @constructor
	 * Create a [PhraseNode] from its parts.  The list of [children] is mutable,
	 * and can be provided here or added later.
	 *
	 * @property atomModuleName
	 *   If this node is a [SEND_PHRASE] or [MACRO_SUBSTITUTION_PHRASE], this is
	 *   the name of the module in which the sent bundle's atom was defined.
	 *   Otherwise `null`.
	 * @property atomName
	 *   If this node is a [SEND_PHRASE] or [MACRO_SUBSTITUTION_PHRASE], this is
	 *   the name of the sent bundle's atom.  Otherwise `null`.
	 * @property tokenSpans
	 *   The regions of the file that tokens of this phrase occupy.  Each region
	 *   is a [PhraseNodeToken] representing the one-based start and pastEnd
	 *   positions in the source string, adjusted to UCS-2 ("Char") positions.
	 *   It also contains an index into the [splitter]'s tuple of parts, to say
	 *   what the token was, or zero if it was not a literal part of the message
	 *   name.
	 * @property children
	 *   The children of this phrase, which roughly correspond to subphrases.
	 *   For a send phrase or macro send phrase, these may be the argument
	 *   phrases or the [list][ListPhraseDescriptor] phrases that group them,
	 *   depending on the structure of the sent bundle's name (see
	 *   [MessageSplitter]).
	 * @property parent
	 *   The node representing the optional parent phrase of this node's phrase.
	 *   This can be provided here, or left null to be set later.
	 */
	class PhraseNode
	constructor(
		val atomModuleName: A_String?,
		val atomName: A_String?,
		val tokenSpans: List<PhraseNodeToken>,
		val children: MutableList<PhraseNode> = mutableListOf(),
		var parent: PhraseNode?)
	{
		/**
		 * An entry in the [tokenSpans] of a PhraseNode.  The [start] and
		 * [pastEnd] identify where the token occurs in the UCS-2 source
		 * [String], but using one-based indices.  The [tokenIndexInName] is
		 * either zero or a one-based index into the atom's [MessageSplitter]'s
		 * [MessageSplitter.messageParts], indicating the part of the message
		 * that this token matched during parsing.
		 *
		 * TODO 2022.12.19 - This just indicates the first equivalent token in
		 *  the tokenized message, since we don't preserve which tokens matched
		 *  what during parsing.  So when we parse "a<b<c" as invocation of
		 *  "_<_<_", we can't (yet) tell afterward that the "<" between a and b
		 *  was the first "<" in the method name, and the "<" between b and c
		 *  was the second "<" in the method name.
		 *
		 *  @property start
		 *    The one-based index into the UCS-2 [String] at which the token
		 *    begins.
		 *  @property pastEnd
		 *    The one-based index into the UCS-2 [String] just past the token.
		 *  @property tokenIndexInName
		 *    Either zero to indicate this was not a token that occurred in the
		 *    actual method name, or the one-based index into the [PhraseNode]'s
		 *    [MessageSplitter]'s tuple of tokenized parts.
		 */
		data class PhraseNodeToken(
			val start: Int,
			val pastEnd: Int,
			val tokenIndexInName: Int)

		/**
		 * If the [atomName] is not null, this is a lazily-computed
		 * [MessageSplitter] derived from that name.  Otherwise this is null.
		 */
		var splitter: MessageSplitter? = null
			get() = field ?: atomName?.let {
				field = MessageSplitter.split(it)
				field
			}
			private set

		/**
		 * This is the 1-based index of this node within its parent, or -1 if
		 * there is no parent.
		 */
		@Volatile
		var indexInParent: Int = -1
			get()
			{
				if (field == -1)
				{
					// Fill in the index of every child, for efficiency.
					parent?.children?.forEachIndexed { i, child ->
						child.indexInParent = i + 1
					}
				}
				return field
			}
			private set

		/**
		 * Write this [PhraseNode] to the provided stream.  Translate the
		 * [atomModuleName] and [atomName], if present, to [Int]s using the
		 * provided already-populated [Map]s.  Note that the indices in the
		 * map values are 1-based.
		 */
		@Throws(IOException::class)
		internal fun write(
			binaryStream: DataOutputStream,
			moduleNameMap: Map<A_String, Int>,
			atomNameMap: Map<A_String, Int>,
			tokenCursor: Mutable<Int>)
		{
			binaryStream.vlq(atomModuleName?.let(moduleNameMap::get) ?: 0)
			binaryStream.vlq(atomName?.let(atomNameMap::get) ?: 0)
			binaryStream.vlq(tokenSpans.size)
			tokenSpans.forEach { (start, pastEnd, tokenIndexInName) ->
				binaryStream.zigzag(start - tokenCursor.value)
				tokenCursor.value = start
				binaryStream.zigzag(pastEnd - tokenCursor.value)
				tokenCursor.value = pastEnd
				binaryStream.vlq(tokenIndexInName)
			}
		}

		fun depth() = iterableWith(PhraseNode::parent).count()

		override fun toString(): String
		{
			val indexInfo = when (val p = parent)
			{
				null -> ""
				else -> "($indexInParent/${p.children.size}) "
			}
			return tokenSpans.joinToString(
				", ",
				"PhraseNode $indexInfo$atomName: "
			) { (start, pastEnd, tokenIndex) ->
				"[$start..$pastEnd #$tokenIndex)"
			}
		}

		companion object
		{
			/**
			 * Extract this node from the input [binaryStream], using the
			 * pre-constructed lists of module names and atom names to decode
			 * the atom information from the original phrase.
			 *
			 * This should mirror the data produced by [write].
			 */
			fun read(
				binaryStream: DataInputStream,
				moduleNameList: List<A_String>,
				atomNameList: List<A_String>,
				tokenCursor: Mutable<Int>
			): PhraseNode
			{
				val atomModuleName = when (val index = binaryStream.unvlqInt())
				{
					0 -> null
					else -> moduleNameList[index - 1]
				}
				val atomName = when (val index = binaryStream.unvlqInt())
				{
					0 -> null
					else -> atomNameList[index - 1]
				}
				val tokenSpans = (1..binaryStream.unvlqInt()).map {
					val start = tokenCursor.value + binaryStream.unzigzagInt()
					tokenCursor.value = start
					val pastEnd = tokenCursor.value + binaryStream.unzigzagInt()
					tokenCursor.value = pastEnd
					val tokenIndexInName = binaryStream.unvlqInt()
					PhraseNodeToken(start, pastEnd, tokenIndexInName)
				}
				return PhraseNode(
					atomModuleName, atomName, tokenSpans, parent = null)
			}
		}
	}

	/**
	 * Information for efficiently navigating from any position in a module's
	 * source to the hierarchy of [A_Phrase]s containing that position.
	 *
	 * @constructor
	 * Construct an instance from an optional list of [PhraseNode]s.
	 *
	 * @property rootTrees
	 *   The [PhraseNode]s representing the sequence of top-level phrases of the
	 *   module.
	 *
	 */
	class PhrasePathRecord
	constructor (
		val rootTrees: MutableList<PhraseNode> = mutableListOf())
	{
		/**
		 * For each [PhraseNode] in this module, working top-down, invoke the
		 * action with that [PhraseNode].
		 *
		 * @param action
		 *   What to do with each [PhraseNode].
		 */
		fun phraseNodesDo(
			action: (PhraseNode) -> Unit)
		{
			val workStack = mutableListOf<PhraseNode>()
			workStack.addAll(rootTrees.reversed())
			while (workStack.isNotEmpty())
			{
				val node = workStack.removeLast()
				action(node)
				workStack.addAll(node.children.reversed())
			}
		}

		/**
		 * Output information about all the [rootTrees] to the provided
		 * [DataOutputStream]. It can later be reconstructed via the constructor
		 * taking a [DataInputStream].
		 *
		 * @param binaryStream
		 *   A DataOutputStream on which to write this phrase path record.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal fun write(binaryStream: DataOutputStream)
		{
			val moduleNameMap = mutableMapOf<A_String, Int>()
			val atomNameMap = mutableMapOf<A_String, Int>()
			// First pre-scan all the trees to populate the maps.
			val workStack = rootTrees.reversed().toMutableList()
			while (workStack.isNotEmpty())
			{
				val node = workStack.removeLast()
				node.atomModuleName?.let {
					moduleNameMap.computeIfAbsent(it) { moduleNameMap.size + 1 }
				}
				node.atomName?.let {
					atomNameMap.computeIfAbsent(it) { atomNameMap.size + 1 }
				}
				workStack.addAll(node.children.reversed())
			}
			// Output these strings first, so they'll be available early during
			// reconstruction.  Start with the module names.
			binaryStream.vlq(moduleNameMap.size)
			moduleNameMap.entries.sortedBy { it.value }.forEach { (string, _) ->
				binaryStream.sizedString(string.asNativeString())
			}
			// Now do the same with all the atom names.
			binaryStream.vlq(atomNameMap.size)
			atomNameMap.entries.sortedBy { it.value }.forEach { (string, _) ->
				binaryStream.sizedString(string.asNativeString())
			}
			// Now traverse it all again, producing instructions for assembling
			// the trees.  Output the nodes top-down, left-to-right.
			binaryStream.vlq(rootTrees.size)
			workStack.addAll(rootTrees.reversed())
			val tokenCursor = Mutable(0)
			while (workStack.isNotEmpty())
			{
				val node = workStack.removeLast()
				node.write(
					binaryStream, moduleNameMap, atomNameMap, tokenCursor)
				binaryStream.vlq(node.children.size)
				workStack.addAll(node.children.reversed())
			}
		}

		override fun toString(): String =
			"PhrasePathRecord (${rootTrees.size} top-level phrases)"

		/**
		 * Reconstruct a [PhrasePathRecord], having previously been written via
		 * [write].
		 *
		 * @param bytes
		 *   Where to read the [PhrasePathRecord] from.
		 * @throws IOException
		 *   If I/O fails.
		 */
		@Throws(IOException::class)
		internal constructor(bytes: ByteArray) : this()
		{
			val binaryStream = DataInputStream(ByteArrayInputStream(bytes))
			val moduleNames = List(binaryStream.unvlqInt()) {
				stringFrom(binaryStream.decodeString())
			}
			val atomNames = List(binaryStream.unvlqInt()) {
				stringFrom(binaryStream.decodeString())
			}
			val fakeRoot = PhraseNode(
				null, null, emptyList(), parent = null)
			// A stack of phrases and countdowns, indicating how many more
			// subphrases to add to the corresponding phrase before considering
			// that phrase complete.  This allows reconstruction of the forest
			// of PhraseNodes without recursion.
			val phrasesWithCountdowns =
				mutableListOf<Pair<PhraseNode, Mutable<Int>>>()
			phrasesWithCountdowns.add(
				fakeRoot to Mutable(binaryStream.unvlqInt()))
			val tokenCursor = Mutable(0)
			while (phrasesWithCountdowns.isNotEmpty())
			{
				val (parent, countdown) = phrasesWithCountdowns.last()
				if (countdown.value == 0)
				{
					phrasesWithCountdowns.removeLast()
					continue
				}
				countdown.value--
				val child = PhraseNode.read(
					binaryStream,
					moduleNames,
					atomNames,
					tokenCursor)
				child.parent = parent
				parent.children.add(child)
				phrasesWithCountdowns.add(
					child to Mutable(binaryStream.unvlqInt()))
			}
			rootTrees.addAll(fakeRoot.children)
			rootTrees.forEach { it.parent = null }
			assert(binaryStream.available() == 0)
		}
	}

	/**
	 * Look up the [ModuleArchive] with the specified name, creating one
	 * and adding it to my [moduleMap] if necessary.
	 *
	 * @param rootRelativeName
	 *   The name of the module, relative to the repository's root.
	 * @return
	 *   A [ModuleArchive] holding versioned data about this module.
	 */
	fun getArchive(rootRelativeName: String): ModuleArchive =
		lock.withLock {
			assert(!rootRelativeName.startsWith("/"))
			moduleMap.computeIfAbsent(rootRelativeName, this::ModuleArchive)
		}

	/**
	 * Clear the underlying `Repository` and discard any cached data. Set up the
	 * repository for subsequent usage.
	 *
	 * @throws IndexedFileException
	 *   If any other [exception][Exception] occurs.
	 */
	@Throws(IndexedFileException::class)
	fun clear() =
		lock.withLock {
			log(Level.INFO, "Clear: %s%n", rootName)
			moduleMap.clear()
			val repo = repository!!
			repo.close()
			repository = null
			try
			{
				fileName.delete()
				repository =
					IndexedRepositoryBuilder.openOrCreate(fileName, true)
				isOpen = true
			}
			catch (e: Exception)
			{
				throw IndexedFileException(e)
			}
		}

	/**
	 * Remove all compilations of the specified module.  If it's a package,
	 * remove all compilations of any contained modules.
	 *
	 * @param rootRelativePath The root-relative path of the module or package.
	 */
	fun cleanModulesUnder(rootRelativePath: String) =
		lock.withLock {
			for ((moduleKey, archive) in moduleMap)
			{
				if (moduleKey == rootRelativePath
					|| moduleKey.startsWith("$rootRelativePath/"))
				{
					archive.cleanCompilations()
				}
			}
		}

	/**
	 * If this repository is not already dirty, mark it as dirty as of now.
	 */
	fun markDirty()
	{
		if (dirtySince == 0L)
		{
			dirtySince = System.currentTimeMillis()
		}
	}

	/**
	 * Write all pending data and metadata to the `Repository`.
	 *
	 * @throws IndexedFileException
	 *   If anything goes wrong.
	 */
	@Throws(IndexedFileException::class)
	fun commit() =
		lock.withLock {
			try
			{
				if (dirtySince != 0L)
				{
					log(Level.FINER, "Commit: %s%n", rootName)
					val byteStream = ByteArrayOutputStream(131072)
					DataOutputStream(byteStream).use { binaryStream ->
						binaryStream.writeInt(moduleMap.size)
						for (moduleArchive in moduleMap.values)
						{
							moduleArchive.write(binaryStream)
						}
						log(
							Level.FINEST, "Commit size = %d%n",
							byteStream.size())
					}
					reopenIfNecessary()
					val repo = repository!!
					repo.metadata = byteStream.toByteArray()
					repo.commit()
					dirtySince = 0L
				}
			}
			catch (e: IndexedFileException)
			{
				throw e
			}
			catch (e: Exception)
			{
				throw IndexedFileException(e)
			}
		}

	/**
	 * Commit the pending changes if they're more than the specified number of
	 * milliseconds old.
	 *
	 * @param maximumChangeAgeMs
	 *   The maximum age in milliseconds that we should leave changes
	 *   uncommitted.
	 */
	fun commitIfStaleChanges(maximumChangeAgeMs: Long) =
		lock.withLock {
			if (dirtySince != 0L &&
				System.currentTimeMillis() - dirtySince > maximumChangeAgeMs)
			{
				commit()
			}
		}

	/**
	 * Close the underlying [IndexedFile].
	 */
	override fun close()
	{
		lock.withLock {
			log(Level.FINE, "Close: %s%n", rootName)
			isOpen = false
			repository?.close()
			moduleMap.clear()
		}
	}

	/**
	 * Open the underlying [IndexedFile] and initialize the `Repository`'s
	 * internal data structures.
	 *
	 * @throws IndexedFileException
	 *   If anything goes wrong.
	 */
	@Throws(IndexedFileException::class)
	private fun openOrCreate()
	{
		assert(!isOpen)
		try
		{
			val repo = try
			{
				IndexedRepositoryBuilder.openOrCreate(
					fileReference = fileName,
					forWriting = true,
					versionCheck = versionCheck)
			}
			catch (e: IndexedFileException)
			{
				log(
					Level.INFO,
					e,
					"Deleting obsolete repository: %s",
					fileName)
				fileName.delete()
				IndexedRepositoryBuilder.openOrCreate(
					fileReference = fileName,
					forWriting = true,
					versionCheck = versionCheck)
			}
			val metadata = repo.metadata
			if (metadata !== null)
			{
				val byteStream = ByteArrayInputStream(metadata)
				DataInputStream(byteStream).use { binaryStream ->
					var moduleCount = binaryStream.readInt()
					while (moduleCount-- > 0)
					{
						val archive = ModuleArchive(binaryStream)
						moduleMap[archive.rootRelativeName] = archive
					}
					assert(byteStream.available() == 0)
				}
			}
			repository = repo
			isOpen = true
		}
		catch (e: IOException)
		{
			throw IndexedFileException(e)
		}
	}

	/**
	 * Reopen the [IndexedFile] and reinitialize the `Repository`.
	 */
	fun reopenIfNecessary() =
		lock.withLock {
			log(
				Level.FINE,
				"Reopen if necessary %s (was open = %s)%n",
				rootName,
				isOpen)
			if (!isOpen)
			{
				openOrCreate()
			}
		}

	init
	{
		openOrCreate()
	}

	override fun toString(): String
	{
		val out = Formatter()
		out.format("""Repository "%s" with modules:""", rootName)
		for ((key, value) in moduleMap)
		{
			out.format("%n\t%s → %s", key, value)
		}
		return out.toString()
	}

	companion object
	{
		/** The [logger][Logger]. */
		private val logger = Logger.getLogger(Repository::class.java.name)

		/** The maximum number of versions to keep for each module. */
		private const val MAX_RECORDED_VERSIONS_PER_MODULE = 10

		/** The maximum number of digests to cache per module. */
		private const val MAX_RECORDED_DIGESTS_PER_MODULE = 20

		/**
		 * The maximum number of compilations to keep available for a particular
		 * module version.
		 */
		private const val MAX_HISTORICAL_VERSION_COMPILATIONS = 10

		/** Whether to log repository accesses to standard output. */
		private const val DEBUG_REPOSITORY = false

		/**
		 * Log the specified message if [debugging][DEBUG_REPOSITORY] is
		 * enabled.
		 *
		 * @param level
		 *   The [severity&#32;level][Level].
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		fun log(level: Level, format: String, vararg args: Any)
		{
			if (DEBUG_REPOSITORY)
			{
				if (logger.isLoggable(level))
				{
					logger.log(level, format, args)
				}
			}
		}

		/**
		 * Log the specified message if [debugging][DEBUG_REPOSITORY] is
		 * enabled.
		 *
		 * @param level
		 *   The [severity&#32;level][Level].
		 * @param exception
		 *   The [exception][Throwable] that motivated this log entry.
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		fun log(
			level: Level,
			exception: Throwable,
			format: String,
			vararg args: Any)
		{
			if (DEBUG_REPOSITORY)
			{
				if (logger.isLoggable(level))
				{
					logger.log(level, String.format(format, *args), exception)
				}
			}
		}

		/** The size in bytes of the digest of a source file. */
		private const val DIGEST_SIZE = 256 shr 3

		/**
		 * Produce a new int hash value from an existing int and a long.
		 *
		 * @param currentHash
		 *   The current hash value.
		 * @param newLong
		 *   The long to be mixed in.
		 * @return
		 *   A hash value combining the two inputs.
		 */
		internal fun mix(currentHash: Int, newLong: Long): Int
		{
			var h = currentHash
			h *= multiplier
			h += newLong.toInt()
			h *= multiplier
			h = h xor (newLong shr 32).toInt()
			return h
		}

		/**
		 * Used to determine if the file's version is compatible with the
		 * current version in the code.  Return `true` to indicate they're
		 * compatible, or false to cause on open attempt to fail.  The first
		 * argument is the file's version, and the second is the code's version.
		 */
		internal val versionCheck: (Int, Int) -> Boolean =
			{ fileVersion, codeVersion -> fileVersion == codeVersion }

		/**
		 * Create a `Repository` for a temporary [IndexedFile]. The indexed file
		 * will be deleted on exit.
		 *
		 * @param rootName
		 *   The name of the Avail root represented by the [IndexedFile].
		 * @param prefix
		 *   A prefix used in generation of the temporary file name.
		 * @param suffix
		 *   A suffix used in generation of the temporary file name.
		 * @return
		 *   The indexed repository manager.
		 * @throws IndexedFileException
		 *   If an [exception][Exception] occurs.
		 */
		fun createTemporary(
			rootName: String,
			prefix: String,
			suffix: String?
		): Repository =
			try
			{
				val file = File.createTempFile(prefix, suffix)
				file.deleteOnExit()
				var indexedFile: IndexedFile? = null
				try
				{
					indexedFile = IndexedRepositoryBuilder.openOrCreate(
						file, true)
				}
				finally
				{
					indexedFile?.close()
				}
				Repository(rootName, file)
			}
			catch (e: Exception)
			{
				throw IndexedFileException(e)
			}

		/**
		 * Is the specified [file][File] an [IndexedFile] of this kind?
		 *
		 * @param path
		 *   A path.
		 * @return
		 *   `true` if the path refers to a repository file, `false` otherwise.
		 * @throws IOException
		 *   If an [I/O&#32;exception][IOException] occurs.
		 */
		@Suppress("unused")
		@Throws(IOException::class)
		fun isIndexedRepositoryFile(path: File): Boolean
		{
			if (path.isFile)
			{
				RandomAccessFile(path, "r").use { file ->
					val repositoryHeader = IndexedRepositoryBuilder.headerBytes
					val buffer = ByteArray(repositoryHeader.size)
					var pos = 0
					while (true)
					{
						val bytesRead =
							file.read(buffer, pos, buffer.size - pos)
						if (bytesRead == -1)
						{
							break
						}
						pos += bytesRead
						if (pos == buffer.size)
						{
							break
						}
					}
					return pos == buffer.size &&
						repositoryHeader.contentEquals(buffer)
				}
			}
			return false
		}
	}
}

/**
 * An [int&#32;range][IntRange] and name of the style to apply to the
 * source characters of that range.
 */
typealias StyleRun = Pair<IntRange, String>
