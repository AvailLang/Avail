/*
 * ModuleVersionKey.kt
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
import avail.descriptor.representation.AvailObject
import avail.persistence.cache.Repository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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
			h = h * AvailObject.multiplier + digestByte
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
	 * Output this module version key to the provided [DataOutputStream]. An
	 * equal key can later be rebuilt via the constructor taking a
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
	 * Reconstruct a [ModuleVersionKey], having previously been written via
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
		sourceDigest = ByteArray(Repository.DIGEST_SIZE)
		binaryStream.readFully(sourceDigest)
		hash = computeHash()
	}

	/**
	 * Construct a new [ModuleVersionKey].
	 *
	 * @param moduleName
	 *   The [resolved&#32;name][ResolvedModuleName] of the module.
	 * @param sourceDigest
	 *   The digest of the module, which (cryptographically) uniquely identifies
	 *   which source code is present within this version.
	 */
	constructor(
		moduleName: ResolvedModuleName,
		sourceDigest: ByteArray)
	{
		assert(sourceDigest.size == Repository.DIGEST_SIZE)
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
			val d = sourceDigest[i].toUByte().compareTo(
				other.sourceDigest[i].toUByte())
			if (d != 0)
			{
				return d
			}
			i++
		}
		return sourceDigest.size - other.sourceDigest.size
	}
}
