/*
 * P_Deserialize.kt
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

package com.avail.interpreter.primitive.general

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.MODULE
import com.avail.exceptions.AvailErrorCode.E_DESERIALIZATION_FAILED
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.serialization.Deserializer
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * **Primitive:** Answer a [tuple][A_Tuple] comprising the objects encoded in
 * the specified [byte][IntegerRangeTypeDescriptor.bytes] tuple, preserving
 * their order.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_Deserialize : Primitive(2, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val bytes = interpreter.argument(0)
		val module = interpreter.argument(1)

		val byteArray: ByteArray
		if (bytes.isByteArrayTuple)
		{
			byteArray = bytes.byteArray()
		}
		else if (bytes.isByteBufferTuple)
		{
			val buffer = bytes.byteBuffer().slice()
			if (buffer.hasArray())
			{
				byteArray = buffer.array()
			}
			else
			{
				val limit = buffer.limit()
				byteArray = ByteArray(limit)
				buffer.get(byteArray)
			}
		}
		else
		{
			val limit = bytes.tupleSize()
			val buffer = ByteBuffer.allocate(limit)
			bytes.transferIntoByteBuffer(1, limit, buffer)
			byteArray = buffer.array()
		}

		val input = ByteArrayInputStream(byteArray)
		val deserializer = Deserializer(
			input, interpreter.runtime())
		deserializer.currentModule = module
		val values = ArrayList<A_BasicObject>()
		try
		{
			var value: A_BasicObject? = deserializer.deserialize()
			while (value !== null)
			{
				values.add(value)
				value = deserializer.deserialize()
			}
		}
		catch (e: Exception)
		{
			return interpreter.primitiveFailure(E_DESERIALIZATION_FAILED)
		}

		return interpreter.primitiveSuccess(tupleFromList(values))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(zeroOrMoreOf(bytes()), MODULE.o()), zeroOrMoreOf(ANY.o()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_DESERIALIZATION_FAILED))
}
