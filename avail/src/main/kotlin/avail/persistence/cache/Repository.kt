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
import avail.compiler.ModuleManifestEntry
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.AvailObject.Companion.multiplier
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*
import avail.persistence.cache.record.ManifestRecord
import avail.persistence.cache.record.ModuleArchive
import avail.persistence.cache.record.ModuleCompilation
import avail.persistence.cache.record.NamesIndex
import avail.persistence.cache.record.PhrasePathRecord
import avail.persistence.cache.record.StylingRecord
import avail.serialization.Serializer
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
import java.util.Formatter
import java.util.Map.Entry.comparingByKey
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
 *    2.a. moduleArchive
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
 * 2. recordNumber (long)   (serialized fast-load information)
 * 3. recordNumberOfBlockPhrases (long)  (serialized tuple of phrases)
 * 4. recordNumberOfManifest (long)  [ManifestRecord]
 * 5. recordNumberOfStyling (long)  [StylingRecord]
 * 6. recordNumberOfPhrasePaths (long)  [PhrasePathRecord]
 * 7. recordNumberOfNamesIndex (long)  [NamesIndex]
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
		"Avail compiled module repository V15")

	/**
	 * The [lock][ReentrantLock] responsible for guarding against unsafe
	 * concurrent access.
	 */
	internal val lock = ReentrantLock()

	/**
	 * The [repository][IndexedFile] that stores this [Repository]'s compiled
	 * [modules][ModuleDescriptor].
	 */
	private var repository: IndexedFile? = null

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
	 * @param namesIndex
	 *   The [NamesIndex] containing indexing information for names.
	 */
	fun createModuleCompilation(
		compilationTime: Long,
		serializedBody: ByteArray,
		serializedBlockPhrases: ByteArray,
		manifest: ManifestRecord,
		stylingRecord: StylingRecord,
		phrasePaths: PhrasePathRecord,
		namesIndex: NamesIndex
	) : ModuleCompilation
	{
		// No need to hold a lock during initialization.
		val manifestBytes = ByteArrayOutputStream(4096)
		manifest.write(DataOutputStream(manifestBytes))
		val innerStylingRecordBytes = ByteArrayOutputStream(4096)
		stylingRecord.write(DataOutputStream(innerStylingRecordBytes))
		val innerPhrasePathsBytes = ByteArrayOutputStream(4096)
		phrasePaths.write(
			DataOutputStream(innerPhrasePathsBytes),
			namesIndex)
		val namesIndexBytes = ByteArrayOutputStream(4096)
		namesIndex.write(DataOutputStream(namesIndexBytes))
		return lock.withLock {
			ModuleCompilation(
				repository = this@Repository,
				compilationTime = compilationTime,
				recordNumber = add(serializedBody),
				recordNumberOfBlockPhrases = add(serializedBlockPhrases),
				recordNumberOfManifest = add(manifestBytes.toByteArray()),
				recordNumberOfStyling = add(
					innerStylingRecordBytes.toByteArray()),
				recordNumberOfPhrasePaths = add(
					innerPhrasePathsBytes.toByteArray()),
				recordNumberOfNamesIndex = add(
					namesIndexBytes.toByteArray()))
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
			moduleMap.computeIfAbsent(rootRelativeName) { name ->
				ModuleArchive(this, name)
			}
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
			close()
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
					repository!!.run {
						metadata = byteStream.toByteArray()
						commit()
					}
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
						val archive = ModuleArchive(this, binaryStream)
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

	/** Forward add to the contained [IndexedFile]. */
	fun add(byteArray: ByteArray): Long = repository!!.add(byteArray)

	/** Forward get operation to the contained [IndexedFile]. */
	operator fun get(index: Long): ByteArray = repository!![index]

	init
	{
		openOrCreate()
	}

	companion object
	{
		/** The [logger][Logger]. */
		private val logger = Logger.getLogger(Repository::class.java.name)

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
		const val DIGEST_SIZE = 256 shr 3

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
