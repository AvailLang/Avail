/*
 * NameInModule.kt
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

import avail.utility.ifZero
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * A structure containing both a module name and the name of an atom declared in
 * that module.
 *
 */
data class NameInModule constructor(
	val moduleName: String,
	val atomName: String
): Comparable<NameInModule>
{
	override fun compareTo(other: NameInModule): Int =
		moduleName.compareTo(other.moduleName)
			.ifZero { atomName.compareTo(other.atomName) }

	fun write(
		binaryStream: DataOutputStream,
		moduleNumbering: Map<String, Int>,
		nameNumbering: Map<String, Int>)
	{
		binaryStream.vlq(moduleNumbering[moduleName]!!)
		binaryStream.vlq(nameNumbering[atomName]!!)
	}

	/**
	 * Reconstruct a [NameInModule] from a stream, using the already constructed
	 * lists of module names and atom names
	 */
	constructor(
		binaryStream: DataInputStream,
		moduleNames: List<String>,
		atomNames: List<String>
	) : this(
		moduleNames[binaryStream.unvlqInt()],
		atomNames[binaryStream.unvlqInt()])
}
