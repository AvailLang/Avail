/*
 * P_TupleTypeAtThrough.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.interpreter.primitive.tuples

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.BottomTypeDescriptor
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.TupleTypeDescriptor
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.descriptor.types.TypeDescriptor
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer the [type][TypeDescriptor] that is the union of the
 * types within the given range of indices of the given
 * [tuple&#32;type][TupleTypeDescriptor]. Answer [bottom][BottomTypeDescriptor]
 * if all the indices are out of range.
 */
@Suppress("unused")
object P_TupleTypeAtThrough : Primitive(3, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val (tupleType, startIndex, endIndex) = interpreter.argsBuffer
		val startInt = when {
			startIndex.isInt -> startIndex.extractInt
			else -> Integer.MAX_VALUE
		}
		val endInt = when {
			endIndex.isInt -> endIndex.extractInt
			else -> Integer.MAX_VALUE
		}
		return interpreter.primitiveSuccess(
			tupleType.unionOfTypesAtThrough(startInt, endInt))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				tupleMeta,
				naturalNumbers,
				inclusive(
					zero,
					positiveInfinity)),
			anyMeta())
}
