/*
 * RegisterSet.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.AvailObject

/**
 * A runtime data structure used to hold all register values for an L2Simple
 * chunk while it's running.  Note that the elements *do not* correspond to the
 * slot numbering of a continuation.
 */
@JvmInline
value class RegisterSet
constructor (val values: Array<AvailObject>)
{
	/** Extract the current [A_Function], stashed in slot 0 of the [Array]. */
	var function: AvailObject
		get() = values[0]
		set(value) { values[0] = value }

	/** Read from the [RegisterSet]. */
	operator fun get(read: Read): AvailObject
	{
		assert(read.value > 0)
		return values[read.value]
	}

	/**
	 * Look up each [Read] of the [readArray] in this [RegisterSet], producing
	 * an [Array] of [AvailObject]s.
	 */
	operator fun get(readArray: ReadArray): Array<AvailObject> =
		Array(readArray.size) { get(readArray[it]) }

	/** Write to the [RegisterSet]. */
	operator fun set(write: Write, valueToWrite: AvailObject): Unit
	{
		assert(write.value > 0)
		values[write.value] = valueToWrite
	}

	/**
	 * Write to the [RegisterSet], with a weak ([A_BasicObject]) value
	 * automatically strengthened to [AvailObject].
	 */
	operator fun set(write: Write, valueToWrite: A_BasicObject): Unit
	{
		assert(write.value > 0)
		values[write.value] = valueToWrite as AvailObject
	}
}
