/*
 * ManifestRecord.kt
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

import avail.compiler.ModuleManifestEntry
import avail.descriptor.module.A_Module
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Manifest information that was collected during compilation of a
 * [module][A_Module].  This keeps track of where certain declarations and
 * definitions occurred within a module during compilation, each summarized as a
 * [ModuleManifestEntry].
 */
class ManifestRecord
{
	/**
	 * The [List] of each [ModuleManifestEntry] present in this module.
	 */
	val manifestEntries: List<ModuleManifestEntry>

	/**
	 * Output this styling record to the provided [DataOutputStream]. It can
	 * later be reconstructed via the constructor taking a [DataInputStream].
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
