/*
 * SerializerOperand.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.representation.AvailObject

/**
 * A `SerializerOperand` is part of a [SerializerOperation].  It indicates how
 * to serialize part of an object already provided in an appropriate form, and
 * it knows how to describe the relationship between the parent object and this
 * part of it.
 *
 * @property operandEncoding
 *   The [encoding][SerializerOperandEncoding] to use.
 * @property roleName
 *   The role this occupies in its containing [SerializerOperation].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SerializerOperand`.
 *
 * @param operandEncoding
 *   The [encoding][SerializerOperandEncoding] to use.
 * @param roleName
 *   The role this occupies in its containing [SerializerOperation].
 */
internal class SerializerOperand constructor(
	private val operandEncoding: SerializerOperandEncoding,
	private val roleName: String)
{
	/**
	 * Trace the [AvailObject], visiting any subobjects to ensure they will get
	 * a chance to be serialized before this object.  The object is potentially
	 * synthetic, perhaps representing just one aspect of the real object being
	 * traced.  For example, a [CompiledCodeDescriptor] object may produce a
	 * tuple of its literals for use by the operand responsible for literals,
	 * even though the actual representation does not use a separate tuple.
	 *
	 * @param obj
	 *   The object to trace.
	 * @param serializer
	 *   The [Serializer] onto which to record the object's parts.
	 */
	fun trace(obj: AvailObject, serializer: Serializer) =
		operandEncoding.trace(obj, serializer)

	/**
	 * Write the [AvailObject]'s subobjects as described by the
	 * [operandEncoding].  As with [trace], the object may be synthetic,
	 * produced solely for pre-processing information for serialization of a
	 * single operand.
	 *
	 * @param obj
	 *   The object to deconstruct and write.
	 * @param serializer
	 *   The serializer to which to write the object.
	 */
	fun write(obj: AvailObject, serializer: Serializer) =
		operandEncoding.write(obj, serializer)

	/**
	 * Read an [AvailObject] from the [AbstractDeserializer] using
	 * the receiver's [operandEncoding].
	 *
	 * @param deserializer
	 *   The deserializer from which to read an object.
	 * @return
	 *   The newly decoded object.
	 */
	fun read(deserializer: AbstractDeserializer) =
		operandEncoding.read(deserializer)

	/**
	 * Describe the operand that would be deserialized, using the receiver's
	 * [operandEncoding].
	 *
	 * @param describer
	 *   The [DeserializerDescriber] from which to construct an object
	 *   description.
	 */
	fun describe(describer: DeserializerDescriber)
	{
		describer.append(roleName)
		describer.append(" = ")
		operandEncoding.describe(describer)
	}

	override fun toString(): String = "$operandEncoding($roleName)"
}
