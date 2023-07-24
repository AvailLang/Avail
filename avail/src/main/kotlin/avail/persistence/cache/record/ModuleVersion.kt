/*
 * ModuleVersion.kt
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

import avail.compiler.ModuleHeader
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.tokens.CommentTokenDescriptor
import avail.descriptor.tuples.TupleDescriptor
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.LimitedCache
import avail.serialization.Serializer
import avail.utility.decodeString
import avail.utility.sizedString
import avail.utility.unvlqInt
import avail.utility.unvlqLong
import avail.utility.unzigzagLong
import avail.utility.vlq
import avail.utility.zigzag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Collections
import kotlin.concurrent.withLock

/**
 * Information kept in memory about a specific version of a
 * [module][ModuleDescriptor] file.
 */
class ModuleVersion
{
	/**
	 * The enclosing [Repository].
	 */
	val repository: Repository

	/**
	 * The size of the [module][ModuleDescriptor]'s source code, in bytes.
	 */
	private val moduleSize: Long

	/**
	 * The names of the modules being imported by this version of this module.
	 * The names are local names, in the order they occur in the module source.
	 */
	private val localImportNames: MutableList<String>

	/**
	 * The list of entry points declared by this version of the module. Note
	 * that because the entry point declarations are in the module header and in
	 * a fixed syntax, all valid compilations of the module would produce the
	 * same list of entry points.  Therefore, the entry points belong here in
	 * the module version, not with a compilation.
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
		get() = Collections.unmodifiableList(localImportNames)

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
			return repository.lock.withLock {
				repository[moduleHeaderRecordNumber]
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
	 * [comment][CommentTokenDescriptor] associated with this [ModuleVersion].
	 *
	 * @return
	 *   A serialized tuple of comment tokens, or `null` if the
	 *   [module][ModuleDescriptor] has not been compiled yet.
	 */
	val comments: ByteArray?
		get() = when (stacksRecordNumber)
		{
			-1L -> null
			else -> repository.lock.withLock {
				repository[stacksRecordNumber]
			}
		}

	/**
	 * An immutable [List] of compilations for this module version. There may be
	 * multiple compilations due to changes in ancestor module versions that
	 * forced this module to be recompiled.
	 */
	val allCompilations: List<ModuleCompilation>
		get() = repository.lock.withLock { compilations.values.toList() }

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
		repository.lock.withLock { compilations[compilationKey] }

	/**
	 * The list of entry point names declared by this version of the module.
	 *
	 * @return
	 *   The list of entry point names.
	 */
	fun getEntryPoints(): List<String> =
		Collections.unmodifiableList(entryPoints)

	/**
	 * Write the specified byte array (encoding a [ModuleHeader]) into the
	 * indexed file. Record the record position for subsequent retrieval.
	 *
	 * @param bytes
	 *   A [serialized][Serializer] module header.
	 */
	fun putModuleHeader(bytes: ByteArray) =
		repository.lock.withLock {
			// Write the serialized data to the end of the repository.
			moduleHeaderRecordNumber = repository.add(bytes)
			repository.markDirty()
		}

	/**
	 * Write the specified byte array (encoding a [tuple][TupleDescriptor] of
	 * [comment][CommentTokenDescriptor]) into the indexed file. Record the
	 * record position for subsequent retrieval.
	 *
	 * @param bytes
	 *   A [serialized][Serializer] tuple of comment tokens.
	 */
	fun putComments(bytes: ByteArray) =
		repository.lock.withLock {
			// Write the comment tuple to the end of the repository.
			stacksRecordNumber = repository.add(bytes)
			repository.markDirty()
		}

	/**
	 * Output this module version to the provided [DataOutputStream].  It can
	 * later be reconstructed via the constructor taking a [DataInputStream].
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

	override fun toString(): String = buildString {
		append("Version:\n\t\timports=")
		append(localImportNames)
		if (entryPoints.isNotEmpty())
		{
			append("\n\t\tentry points=")
			append(entryPoints)
		}
		append("\n\t\tcompilations=")
		append(compilations.values)
		append("\n\t\tmoduleHeaderRecordNumber=")
		append(moduleHeaderRecordNumber)
		append("\n\t\tstacksRecordNumber=")
		append(stacksRecordNumber)
	}


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
	internal constructor(
		repository: Repository,
		binaryStream: DataInputStream)
	{
		this.repository = repository
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
				ModuleCompilation(repository, binaryStream)
		}
		moduleHeaderRecordNumber = binaryStream.unzigzagLong()
		stacksRecordNumber = binaryStream.unzigzagLong()
	}

	/**
	 * Construct a new `ModuleVersion`.
	 *
	 * @param repository
	 *   The enclosing [Repository].
	 * @param moduleSize
	 *   The size of the compiled module, in bytes.
	 * @param localImportNames
	 *   The list of module names being imported.
	 * @param entryPoints
	 *   The list of entry points defined in the module.
	 */
	constructor(
		repository: Repository,
		moduleSize: Long,
		localImportNames: List<String>,
		entryPoints: List<String>)
	{
		this.repository = repository
		this.moduleSize = moduleSize
		this.localImportNames = localImportNames.toMutableList()
		this.entryPoints = entryPoints.toMutableList()
	}

	companion object
	{
		/**
		 * The maximum number of compilations to keep available for a particular
		 * module version.
		 */
		private const val MAX_HISTORICAL_VERSION_COMPILATIONS = 10
	}
}
