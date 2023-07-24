/*
 * ModuleCompilation.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.module.ModuleDescriptor
import avail.persistence.cache.Repository
import avail.utility.unzigzagLong
import avail.utility.zigzag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.concurrent.withLock

/**
 * Information kept in memory about a compilation of a
 * [module][ModuleDescriptor].
 */
class ModuleCompilation
{
	/** The enclosing [Repository]. */
	val repository: Repository

	/** The time at which this module was compiled. */
	val compilationTime: Long

	/**
	 * The persistent record number of this version of the compiled
	 * [module][ModuleDescriptor].
	 */
	val recordNumber: Long

	/**
	 * The record number at which a tuple of block phrases for this compilation
	 * is stored.  This can be fetched on demand, separately from the
	 * [A_RawFunction]s needed to load the module.
	 */
	val recordNumberOfBlockPhrases: Long

	/**
	 * The record number at which a [ByteArray] was recorded for this module.
	 * That record should be fetched as needed and decoded into a
	 * [ManifestRecord].
	 */
	val recordNumberOfManifest: Long

	/**
	 * The record number at which a [ByteArray] was recorded for this module.
	 * That record should be fetched as needed and decoded into a
	 * [StylingRecord].
	 */
	val recordNumberOfStyling: Long

	/**
	 * The record number at which a [ByteArray] was recorded for this module, in
	 * which is an encoding that can be used to convert from a file position to
	 * a series of phrases (of descending extent) which ultimately contain the
	 * token at that file position.
	 */
	val recordNumberOfPhrasePaths: Long

	/**
	 * The record number at which a [ByteArray] was recorded for this module, in
	 * which is an encoding of declarations, aliases, definitions, and usages
	 * (within method/macro sends) of atoms.  This information is used by Anvil
	 * to navigate between occurrences of these categories of entries.
	 */
	val recordNumberOfNamesIndex: Long

	/** The byte array containing a serialization of this compilation. */
	val bytes: ByteArray
		get() = repository.lock.withLock {
			repository[recordNumber]
		}

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
		binaryStream.zigzag(recordNumberOfNamesIndex)
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
	 * @param repository
	 *   The enclosing [Repository].
	 * @param binaryStream
	 *   Where to read the key from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(
		repository: Repository,
		binaryStream: DataInputStream
	) : this(
		repository = repository,
		compilationTime = binaryStream.readLong(),
		recordNumber = binaryStream.unzigzagLong(),
		recordNumberOfBlockPhrases = binaryStream.unzigzagLong(),
		recordNumberOfManifest = binaryStream.unzigzagLong(),
		recordNumberOfStyling = binaryStream.unzigzagLong(),
		recordNumberOfPhrasePaths = binaryStream.unzigzagLong(),
		recordNumberOfNamesIndex = binaryStream.unzigzagLong())

	/**
	 * Construct a [ModuleCompilation] from its parts.
	 *
	 * @param repository
	 *   The enclosing [Repository].
	 * @param compilationTime
	 *   The time at which this module was compiled.
	 * @param recordNumber
	 *   The persistent record number of this version of the compiled
	 *   [module][ModuleDescriptor].
	 * @param recordNumberOfBlockPhrases
	 *   The record number at which a tuple of block phrases for this
	 *   compilation is stored.  This can be fetched on demand, separately from
	 *   the [A_RawFunction]s needed to load the module.
	 * @param recordNumberOfManifest
	 *   The record number at which a [ByteArray] was recorded for this module.
	 *   That record should be fetched as needed and decoded into a
	 *   [ManifestRecord].
	 * @param recordNumberOfStyling
	 *   The record number at which a [ByteArray] was recorded for this module.
	 *   That record should be fetched as needed and decoded into a
	 *   [StylingRecord].
	 * @param recordNumberOfPhrasePaths
	 *   The record number at which a [ByteArray] was recorded for this module,
	 *   in which is an encoding that can be used to convert from a file
	 *   position to a series of phrases (of descending extent) which ultimately
	 *   contain the token at that file position.
	 * @param recordNumberOfNamesIndex
	 *   The record number at which a [ByteArray] was recorded for this module,
	 *   in which is an encoding of declarations, aliases, definitions, and
	 *   usages (within method/macro sends) of atoms.  This information is used
	 *   by Anvil to navigate between occurrences of these categories of
	 *   entries.
	 *
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(
		repository: Repository,
		compilationTime: Long,
		recordNumber: Long,
		recordNumberOfBlockPhrases: Long,
		recordNumberOfManifest: Long,
		recordNumberOfStyling: Long,
		recordNumberOfPhrasePaths: Long,
		recordNumberOfNamesIndex: Long)
	{
		this.repository = repository
		this.compilationTime = compilationTime
		this.recordNumber = recordNumber
		this.recordNumberOfBlockPhrases = recordNumberOfBlockPhrases
		this.recordNumberOfManifest = recordNumberOfManifest
		this.recordNumberOfStyling = recordNumberOfStyling
		this.recordNumberOfPhrasePaths = recordNumberOfPhrasePaths
		this.recordNumberOfNamesIndex = recordNumberOfNamesIndex
	}
}
