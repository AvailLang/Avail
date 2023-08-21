/*
 * ModuleCompilationKey.kt
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

import avail.persistence.cache.Repository
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * An immutable key which specifies a version of a module and its context at the
 * time of compilation.  It does not explicitly contain the [ModuleVersionKey],
 * but it includes the compilation times of the module's predecessors.
 */
class ModuleCompilationKey
{
	/**
	 * The times at which this module's predecessors were compiled, in the order
	 * specified by the Uses/Extends declarations.
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
			h = Repository.mix(h, predecessorCompilationTime)
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
	 * Output this module compilation key to the provided [DataOutputStream].
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
		binaryStream.vlq(predecessorCompilationTimes.size)
		for (predecessorCompilationTime in predecessorCompilationTimes)
		{
			binaryStream.writeLong(predecessorCompilationTime)
		}
	}

	/**
	 * Reconstruct a [ModuleCompilationKey], having previously been written via
	 * [write].
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
	 * Construct a new [ModuleCompilationKey].
	 *
	 * @param predecessorCompilationTimes
	 *   The compilation times of this module's predecessors, in the order of
	 *   import declaration.
	 */
	constructor(predecessorCompilationTimes: LongArray)
	{
		this.predecessorCompilationTimes =
			predecessorCompilationTimes.clone()
		hash = computeHash()
	}
}
