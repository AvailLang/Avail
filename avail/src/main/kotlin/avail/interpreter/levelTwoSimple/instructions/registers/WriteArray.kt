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
 * A field of an [L2SimpleInstruction] has this type if it represents a write to
 * multiple values of the registers array.  This does not necessarily represent
 * slot numbers in the L1 continuation.
 *
 * @constructor
 *   Build the [WriteArray] from an [IntArray].
 * @property values
 *   The underlying [IntArray].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@JvmInline
value class WriteArray(val values: IntArray)
{
	/** Secondary constructor, given a collection of [Write]s. */
	constructor(
		writes: Collection<Write>
	): this(writes.map { it.value }.toIntArray())

	/**
	 * Transform each [Write] into another [Write], collecting them into a new
	 * [WriteArray].
	 */
	fun map(mapper: (Write)->Write): WriteArray
	{
		val transformed = IntArray(values.size)
		values.forEachIndexed { index, writeInt ->
			transformed[index] = mapper(Write(writeInt)).value
		}
		return WriteArray(transformed)
	}

	/** Iterate over each [Write], passing the zero-based index as well. */
	inline fun forEachIndexed(action: (Int, Write)->Unit)
	{
		values.forEachIndexed { index, writeInt ->
			action(index, Write(writeInt))
		}
	}

	/** Extract the [Write] at the indicated position. */
	operator fun get(zeroIndex: Int): Write = Write(values[zeroIndex])

	/** Answer the size of the [IntArray]. */
	val size: Int get() = values.size

	/** Pretty-print this [WriteArray]. */
	override fun toString(): String
	{
		return values.joinToString(", ", "W[", "]")
	}
}
