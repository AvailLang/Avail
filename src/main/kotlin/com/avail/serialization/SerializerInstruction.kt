/*
 * SerializerInstruction.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
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

package com.avail.serialization

import com.avail.descriptor.AvailObject
import com.avail.descriptor.representation.A_BasicObject

/**
 * A `SerializerInstruction` combines an [AvailObject] and a
 * [SerializerOperation] suitable for serializing it.
 *
 * @property operation
 *   The [SerializerOperation] that can decompose this object for serialization.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SerializerInstruction`.
 *
 * @param operation
 *   The [SerializerOperation] that will decompose the object for serialization.
 * @param obj
 *   The object to record by this instruction.
 * @param serializer
 *   The [Serializer] to which this instruction will record.
 */
internal class SerializerInstruction constructor(
	internal val operation: SerializerOperation,
	obj: A_BasicObject,
	serializer: Serializer)
{
	/**
	 * An array of subobjects resulting from decomposing the object.  These
	 * correspond to the operation's [SerializerOperation.operands].
	 */
	private val subobjects =
		operation.decompose(obj as AvailObject, serializer)

	/**
	 * The index of this instruction in the list of instructions produced by a
	 * [Serializer].
	 */
	internal var index = -1
		set (newValue)
		{
			assert(field == -1)
			field = newValue
		}

	/**
	 * Whether this instruction has been assigned an instruction index, which
	 * happens when the instruction is written.
	 */
	val hasBeenWritten: Boolean
		get() = index >= 0

	/**
	 * The number of subobjects that this instruction has.
	 */
	val subobjectsCount: Int
		get() = subobjects.size

	/**
	 * Answer the subobject at the given zero-based subscript.
	 *
	 * @param subscript
	 *   The zero-based subobject subscript.
	 * @return
	 *   The [A_BasicObject] at the given subscript.
	 */
	fun getSubobject(subscript: Int): A_BasicObject = subobjects[subscript]

	/**
	 * Write this already traced instruction to the [Serializer].
	 *
	 * @param serializer
	 *   Where to write the instruction.
	 */
	fun writeTo(serializer: Serializer) =
		operation.serializeStat.record {
			operation.writeObject(subobjects, serializer)
		}
}
