/*
 * P_TupleAppend.kt
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

package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.ConcatenatedTupleTypeDescriptor.Companion.concatenatingAnd
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer a new [tuple][TupleDescriptor] like the argument but
 * with the [element][AvailObject] appended to its right.
 */
@Suppress("unused")
object P_TupleAppend : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val tuple = interpreter.argument(0)
		val newElement = interpreter.argument(1)

		return interpreter.primitiveSuccess(
			tuple.appendCanDestroy(newElement, true))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val aTupleType = argumentTypes[0]
		val anElementType = argumentTypes[1]

		val anElementTupleType = tupleTypeForSizesTypesDefaultType(
			singleInt(1), emptyTuple, anElementType)
		return concatenatingAnd(aTupleType, anElementTupleType)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType(),
				ANY.o
			),
			mostGeneralTupleType())
}
