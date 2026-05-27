/*
 * ReadArray.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwoSimple.instructions.registers

import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction

/**
 * A field of an [L2SimpleInstruction] has this type if it represents a read of
 * a value from the registers array.  This does not necessarily represent a slot
 * number in the L1 continuation.
 *
 * @constructor
 *   Build a [ReadArray] from an underlying [IntArray].
 * @property values
 *   The [IntArray] underlying this [ReadArray].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@JvmInline
value class ReadArray(val values: IntArray)
{
	/** A secondary constructor given a collection of [Read]s. */
	constructor(
		reads: Collection<Read>
	): this(reads.map { it.value }.toIntArray())

	/**
	 * Map each [Read] to another [Read], and collect them into a new
	 * [ReadArray].
	 */
	inline fun map(mapper: (Read)->Read): ReadArray
	{
		val transformed = IntArray(values.size)
		values.forEachIndexed { index, readInt ->
			transformed[index] = mapper(Read(readInt)).value
		}
		return ReadArray(transformed)
	}

	/** Iterate over each [Read], passing the index as well. */
	inline fun forEachIndexed(action: (Int, Read)->Unit)
	{
		values.forEachIndexed { index, readInt ->
			action(index, Read(readInt))
		}
	}

	/** Extract a [Read] at the specified index. */
	operator fun get(zeroIndex: Int): Read = Read(values[zeroIndex])

	/** Answer the number of [Read]s in this [ReadArray]. */
	val size: Int get() = values.size

	/** Pretty-print this [ReadArray]. */
	override fun toString(): String
	{
		return values.joinToString(", ", "R[", "]")
	}
}
