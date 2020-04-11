/*
 * Repository.kt
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

package com.avail.persistence

import com.avail.builder.ModuleRoot
import com.avail.builder.ResolvedModuleName
import com.avail.compiler.ModuleHeader
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.serialization.Serializer
import java.io.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableSortedMap
import java.util.Map.Entry.comparingByKey
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.bind.DatatypeConverter
import kotlin.concurrent.withLock
import kotlin.streams.toList

/**
 * An `Repository` manages a persistent [IndexedFile] of compiled
 * [modules][ModuleDescriptor].
 *
 * @property rootName
 *   The name of the [Avail root][ModuleRoot] represented by this [IndexedFile].
 * @property fileName
 *   The [filename][File] of the [IndexedFile].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
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
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	private object IndexedRepositoryBuilder : IndexedFileBuilder(
		"Avail compiled module repository\u0000")

	/**
	 * The [lock][ReentrantLock] responsible for guarding against unsafe
	 * concurrent access.
	 */
	internal val lock = ReentrantLock()

	/**
	 * The [repository][IndexedFile] that stores this
	 * [Repository]'s compiled [modules][ModuleDescriptor].
	 */
	internal var repository: IndexedFile? = null

	/**
	 * Keep track of whether changes have happened since the last commit, and
	 * when the first such change happened.
	 */
	private var dirtySince = 0L

	/**
	 * A [Map] from the [root-relative
	 * name][ResolvedModuleName.rootRelativeName] of each module that has ever
	 * been compiled within this repository to the corresponding ModuleArchive.
	 */
	private val moduleMap = HashMap<String, ModuleArchive>(100)

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
	: LinkedHashMap<K, V>(maximumSize,0.75f,true)
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
		/** The latest `N` versions of this module. */
		private val versions = LinkedHashMap<ModuleVersionKey, ModuleVersion>(
			MAX_RECORDED_VERSIONS_PER_MODULE,
			0.75f,
			true)

		/** This module's name, relative to its root. */
		internal val rootRelativeName: String

		/**
		 * A [LimitedCache] used to avoid computing digests of files when the
		 * file's timestamp has not changed.  Each key is a [Long] representing
		 * the file's  [last][File.lastModified].  The value is a byte array
		 * holding the SHA-256 digest of the file content.
		 */
		private val digestCache =
			LimitedCache<Long, ByteArray>(MAX_RECORDED_DIGESTS_PER_MODULE)

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
				val map = lock.withLock { HashMap(versions) }
				return unmodifiableSortedMap(TreeMap(map))
			}

		/**
		 * Determine the cryptographic hash of the file's current contents.
		 * Since we assume that the same filename and modification time implies
		 * the same digest, we cache the digest under that combination for
		 * performance.
		 *
		 * @param resolvedModuleName
		 *   The [resolved name][ResolvedModuleName] of the module, in case the
		 *   backing source file must be read to produce a digest.
		 * @return
		 *   The digest of the file, updating the [digestCache] if necessary.
		 */
		fun digestForFile(resolvedModuleName: ResolvedModuleName): ByteArray
		{
			assert(resolvedModuleName.rootRelativeName == rootRelativeName)
			val sourceFile = resolvedModuleName.sourceReference
			val lastModification = sourceFile.lastModified()
			var digest: ByteArray? = digestCache[lastModification]
			if (digest === null)
			{
				// Don't bother protecting against computing the digest for the
				// same file in multiple threads.  At worst it's extra work, and
				// it's not likely that maintenance on the build mechanism would
				// *ever* cause it to do that anyhow.
				val newDigest: ByteArray =
					try
					{
						RandomAccessFile(sourceFile, "r").use { reader ->
							val hasher =
								MessageDigest.getInstance(DIGEST_ALGORITHM)
							val buffer = ByteArray(4096)
							while (true)
							{
								val bufferSize = reader.read(buffer)
								if (bufferSize == -1)
								{
									break
								}
								hasher.update(buffer, 0, bufferSize)
							}
							hasher.digest()
						}
					}
					catch (e: NoSuchAlgorithmException)
					{
						throw RuntimeException(e)
					}
					catch (e: IOException)
					{
						throw RuntimeException(e)
					}

				assert(newDigest.size == DIGEST_SIZE)
				digest = newDigest
				lock.withLock {
					digestCache[lastModification] = newDigest
					markDirty()
				}
			}
			return digest
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
			binaryStream.writeUTF(rootRelativeName)
			binaryStream.writeInt(digestCache.size)
			for ((key, value) in digestCache)
			{
				binaryStream.writeLong(key)
				binaryStream.write(value)
			}
			binaryStream.writeInt(versions.size)
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
			rootRelativeName = binaryStream.readUTF()
			var digestCount = binaryStream.readInt()
			while (digestCount-- > 0)
			{
				val lastModification = binaryStream.readLong()
				val digest = ByteArray(DIGEST_SIZE)
				binaryStream.readFully(digest)
				digestCache[lastModification] = digest
			}
			var versionCount = binaryStream.readInt()
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
				assert(!versions.containsKey(versionKey))
				versions[versionKey] = version
				markDirty()
			}

		/**
		 * Record a new [compilation][ModuleCompilation] of a [module
		 * version][ModuleVersion].  The version must already exist in the
		 * repository.  The [compilation key][ModuleCompilationKey] must not yet
		 * have a [compilation][ModuleCompilation] associated with it.
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
				assert(version.getCompilation(compilationKey) === null)
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

		override fun toString(): String
		{
			return String.format(
				"VersionKey(@%s...)",
				DatatypeConverter.printHexBinary(
					sourceDigest.copyOf(3)))
		}

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
		 *   The [resolved name][ResolvedModuleName] of the module.
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
		fun shortString(): String
		{
			val prefix = sourceDigest.copyOf(3)
			return DatatypeConverter.printHexBinary(prefix)
		}

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
			var h = 0x9E5_90125
			for (predecessorCompilationTime in predecessorCompilationTimes)
			{
				h = mix(h.toInt(), predecessorCompilationTime).toLong()
			}
			return h.toInt()
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
			binaryStream.writeInt(predecessorCompilationTimes.size)
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
			val predecessorsCount = binaryStream.readInt()
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
		 * produce the same list of entry points.  Therefore the entry points
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
		 * Answer the [serialized][Serializer] [module header][ModuleHeader]
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
			get() = lock.withLock {
				unmodifiableList(ArrayList(compilations.values))
			}

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
				moduleHeaderRecordNumber = repo.size
				repo.add(bytes)
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
				stacksRecordNumber = repo.size
				repo.add(bytes)
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
			binaryStream.writeLong(moduleSize)
			binaryStream.writeInt(localImportNames.size)
			for (importName in localImportNames)
			{
				binaryStream.writeUTF(importName)
			}
			binaryStream.writeInt(entryPoints.size)
			for (entryPoint in entryPoints)
			{
				binaryStream.writeUTF(entryPoint)
			}
			binaryStream.writeInt(compilations.size)
			for ((key, value) in compilations)
			{
				key.write(binaryStream)
				value.write(binaryStream)
			}
			binaryStream.writeLong(moduleHeaderRecordNumber)
			binaryStream.writeLong(stacksRecordNumber)
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
			moduleSize = binaryStream.readLong()
			var localImportCount = binaryStream.readInt()
			localImportNames = ArrayList(localImportCount)
			while (localImportCount-- > 0)
			{
				localImportNames.add(binaryStream.readUTF())
			}
			var entryPointCount = binaryStream.readInt()
			entryPoints = ArrayList(entryPointCount)
			while (entryPointCount-- > 0)
			{
				entryPoints.add(binaryStream.readUTF())
			}
			var compilationsCount = binaryStream.readInt()
			while (compilationsCount-- > 0)
			{
				compilations[ModuleCompilationKey(binaryStream)] =
					ModuleCompilation(binaryStream)
			}
			moduleHeaderRecordNumber = binaryStream.readLong()
			stacksRecordNumber = binaryStream.readLong()
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
			this.localImportNames = ArrayList(localImportNames)
			this.entryPoints = ArrayList(entryPoints)
		}
	}

	/**
	 * Information kept in memory about a compilation of a
	 * [module][ModuleDescriptor].
	 */
	inner class ModuleCompilation
	{
		/** The time at which this module was compiled. */
		val compilationTime: Long

		/**
		 * The persistent record number of this version of the compiled
		 * [module][ModuleDescriptor].
		 */
		val recordNumber: Long

		/** The byte array containing a serialization of this compilation. */
		val bytes: ByteArray?
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
			binaryStream.writeLong(recordNumber)
		}

		override fun toString(): String =
			String.format(
				"Compilation(%tFT%<tTZ, rec=%d)",
				compilationTime,
				recordNumber)

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
		internal constructor(binaryStream: DataInputStream)
		{
			compilationTime = binaryStream.readLong()
			recordNumber = binaryStream.readLong()
		}

		/**
		 * Construct a new `ModuleCompilation`, adding the serialized compiled
		 * module bytes to the repository without committing.
		 *
		 * @param compilationTime
		 *   The compilation time of this module.
		 * @param bytes
		 *   The [serialized][Serializer] form of the compiled module.
		 */
		constructor(compilationTime: Long, bytes: ByteArray)
		{
			lock.lock()
			try
			{
				this.compilationTime = compilationTime
				val repo = repository!!
				this.recordNumber = repo.size
				repo.add(bytes)
			}
			finally
			{
				lock.unlock()
			}
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
			moduleMap.computeIfAbsent(rootRelativeName) { ModuleArchive(it) }
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
		/** The [logger][Logger].  */
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
		 *   The [severity level][Level].
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		@Suppress("ConstantConditionIf")
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
		 *   The [severity level][Level].
		 * @param exception
		 *   The [exception][Throwable] that motivated this log entry.
		 * @param format
		 *   The format string.
		 * @param args
		 *   The format arguments.
		 */
		@Suppress("ConstantConditionIf")
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

		/** The name of the [MessageDigest] used to detect file changes. */
		private const val DIGEST_ALGORITHM = "SHA-256"

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
		@JvmStatic
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
		 *   If an [I/O exception][IOException] occurs.
		 */
		@Suppress("unused")
		@JvmStatic
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
