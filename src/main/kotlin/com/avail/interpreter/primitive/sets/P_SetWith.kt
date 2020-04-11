/*
 * P_SetWith.kt
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
package com.avail.interpreter.primitive.sets

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.IntegerDescriptor.one
import com.avail.descriptor.numbers.IntegerDescriptor.two
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.types.SetTypeDescriptor.mostGeneralSetType
import com.avail.descriptor.types.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Answer a new [set][SetDescriptor] like the argument but
 * including the new [element][AvailObject].  If it was already present, answer
 * the original set.
 */
object P_SetWith : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val set = interpreter.argument(0)
		val newElement = interpreter.argument(1)

		return interpreter.primitiveSuccess(
			set.setWithElementCanDestroy(newElement, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralSetType(),
				ANY.o()),
			setTypeForSizesContentType(
				naturalNumbers(),
				ANY.o()))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val setType = argumentTypes[0]
		val newElementType = argumentTypes[1]

		val setContentType = setType.contentType()
		val mightBePresent =
			!setContentType.typeIntersection(newElementType).isBottom
		val sizes = setType.sizeRange()
		val unionSize = integerRangeType(
			if (mightBePresent)
				sizes.lowerBound()
			else
				sizes.lowerBound().plusCanDestroy(one(), false),
			true,
			sizes.upperBound().plusCanDestroy(two(), false),
			false)
		val unionType = setTypeForSizesContentType(
			unionSize, setType.contentType().typeUnion(newElementType))
		return unionType.makeImmutable()
	}
}
