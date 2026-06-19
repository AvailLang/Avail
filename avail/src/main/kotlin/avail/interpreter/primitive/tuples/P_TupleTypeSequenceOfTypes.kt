/*
 * P_TupleTypeSequenceOfTypes.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_NEGATIVE_SIZE
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive3

/**
 * **Primitive:** Answer a [tuple][A_Tuple] of [types][A_Type] representing the
 * types of the given range of indices within the [tuple][A_Type]. Use
 * [bottom][BottomTypeDescriptor] for indices out of range.
 */
@Suppress("unused")
object P_TupleTypeSequenceOfTypes : Primitive3(CanFold, CanInline)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val tupleType = arg1
		val startIndex = arg2
		val endIndex = arg3
		if (!startIndex.isInt || !endIndex.isInt)
		{
			return fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val startInt = startIndex.extractInt
		val endInt = endIndex.extractInt
		val tupleSize = endInt - startInt + 1
		if (tupleSize < 0)
		{
			return fail(E_NEGATIVE_SIZE)
		}
		return generateObjectTupleFrom(tupleSize) {
			tupleType.typeAtIndex(it + startInt - 1).makeImmutable()
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				tupleMeta,
				naturalNumbers,
				wholeNumbers),
			zeroOrMoreOf(anyMeta))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_NEGATIVE_SIZE))
}
