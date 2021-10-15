/*
 * P_ReverseTuple.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import java.lang.Integer.MAX_VALUE

/**
 * **Primitive:** Produce a [reverse][A_Tuple.tupleReverse] of the given tuple;
 * same elements, opposite order.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
object P_ReverseTuple : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val tuple = interpreter.argument(0)
		return interpreter.primitiveSuccess(tuple.tupleReverse())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		if (tupleType.typeTuple.tupleSize == 0)
		{
			// The tuple type is homogeneous.  Answer the same tuple type, since
			// it's its own inverse.
			return tupleType
		}
		val tupleSizes = tupleType.sizeRange
		val tupleSizeLowerBound = tupleSizes.lowerBound
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound)
			|| !tupleSizeLowerBound.isInt)
		{
			// Variable number of <key,value> pairs.  In theory we could
			// still strengthen it, but a homogenous tuple type of the same size
			// should be sufficient.
			return tupleTypeForSizesTypesDefaultType(
				tupleSizes,
				emptyTuple,
				tupleType.unionOfTypesAtThrough(1, MAX_VALUE))
		}
		val tupleSize = tupleSizeLowerBound.extractInt
		val elementTypes = tupleType.tupleOfTypesFromTo(1, tupleSize)
		val reversedElementTypes = elementTypes.tupleReverse()
		return tupleTypeForSizesTypesDefaultType(
			tupleSizes, reversedElementTypes, bottom)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralTupleType),
			mostGeneralTupleType)
}
