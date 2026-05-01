/*
 * P_TupleReplaceRangeAtNary.kt
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

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import avail.exceptions.AvailErrorCode.E_NEGATIVE_SIZE
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.exceptions.AvailException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.PrimitiveN

/**
 * **Primitive:** Replace the range of values in a tuple given a replacement
 * tuple and a tuple of values to chart the path to get to the desired range to
 * replace.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
object P_TupleReplaceRangeAtNary : PrimitiveN(5, CanInline, CanFold)
{
	override fun attemptN(
		interpreter: Interpreter,
		args: Array<AvailObject>
	): A_BasicObject?
	{
		assert(args.size == 5)
		val targetTuple = args[0]
		val pathTuple = args[1]
		val sliceStartIndex = args[2]
		val sliceEndIndex = args[3]
		val newValues = args[4]

		if (!sliceStartIndex.isInt || !sliceEndIndex.isInt)
			return interpreter.fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
		val startInt = sliceStartIndex.extractInt
		val endInt = sliceEndIndex.extractInt
		if (startInt < 1 || endInt < 0 || startInt > endInt + 1)
			return interpreter.fail(E_NEGATIVE_SIZE)
		return try
		{
			targetTuple.recursivelyUpdate(pathTuple.iterator()) {
				if (!it.isTuple)
					throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
				val size = it.tupleSize
				if (endInt > size)
					throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
				// Note: We can't destroy the targetTuple while extracting the
				// leftPart, since we still need to extract the rightPart.
				val leftPart =
					it.copyTupleFromToCanDestroy(1, startInt - 1, false)
				val rightPart =
					it.copyTupleFromToCanDestroy(endInt + 1, size, true)
				leftPart
					.concatenateWith(newValues, true)
					.concatenateWith(rightPart, true)
			}
		}
		catch (e: AvailException)
		{
			interpreter.fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				oneOrMoreOf(ANY()),
				naturalNumbers,
				wholeNumbers,
				zeroOrMoreOf(ANY())),
			mostGeneralTupleType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND,
				E_NEGATIVE_SIZE))
}
