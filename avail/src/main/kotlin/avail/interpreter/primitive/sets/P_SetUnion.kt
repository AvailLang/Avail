/*
 * P_SetUnion.kt
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
package avail.interpreter.primitive.sets

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.numericCompare
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.contentType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer the union of two [sets][SetDescriptor].
 */
@Suppress("unused")
object P_SetUnion : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val set1 = interpreter.argument(0)
		val set2 = interpreter.argument(1)

		return interpreter.primitiveSuccess(set1.setUnionCanDestroy(set2, true))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralSetType(),
				mostGeneralSetType()),
			mostGeneralSetType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (setType1, setType2) = argumentTypes

		// Technically we can compute the exact minimum bound by building a
		// graph where the edges are the mutually disjoint element types, then
		// computing the minimum coloring via a Birkhoff chromatic polynomial.
		// Even the upper bound can be strengthened beyond the sum of the upper
		// bounds of the inputs through solution of a set of linear inequalities
		// and the pigeon-hole principle.  For now, just keep it simple.
		val sizes1 = setType1.sizeRange
		val sizes2 = setType2.sizeRange
		val min1 = sizes1.lowerBound
		val min2 = sizes2.lowerBound
		// Use the *max* of the lower bounds as the new min bound.
		val minSize = if (min1.numericCompare(min2).isMore())
			min1
		else
			min2
		val maxSize = sizes1.upperBound.plusCanDestroy(
			sizes2.upperBound, false)
		val unionSize = integerRangeType(
			minSize, true, maxSize.plusCanDestroy(one, false), false)
		val unionType = setTypeForSizesContentType(
			unionSize, setType1.contentType.typeUnion(setType2.contentType))
		return unionType.makeImmutable()
	}
}
