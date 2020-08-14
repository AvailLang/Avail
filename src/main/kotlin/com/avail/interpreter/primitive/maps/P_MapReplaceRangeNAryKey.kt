/*
 * P_MapReplaceRangeNAryKey.kt
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

package com.avail.interpreter.primitive.maps

import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import com.avail.exceptions.AvailErrorCode.E_NEGATIVE_SIZE
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.exceptions.AvailException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Replace the range of values in a tuple inside a top level map
 * given a replacement tuple and a tuple of values to chart the path to get to
 * the desired range to replace
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
object P_MapReplaceRangeNAryKey : Primitive(5, CanInline, CanFold)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val targetMap = interpreter.argument(0)
		val pathTuple = interpreter.argument(1)
		val sliceStartIndex = interpreter.argument(2)
		val sliceEndIndex = interpreter.argument(3)
		val newValues = interpreter.argument(4)
		if (!sliceStartIndex.isInt || !sliceEndIndex.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val startInt = sliceStartIndex.extractInt()
		val endInt = sliceEndIndex.extractInt()

		if (startInt < 1 || endInt < 0 || startInt > endInt + 1)
		{
			return interpreter.primitiveFailure(E_NEGATIVE_SIZE)
		}

		return try
		{
			interpreter.primitiveSuccess(
				recursivelyUpdateMap(
					targetMap, pathTuple, startInt, endInt, 1, newValues))
		}
		catch (e: AvailException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	/**
	 * Recursively traverses the target [tuple][TupleDescriptor], ultimately
	 * updating the value range at the final index of the pathIndex.
	 *
	 * @param targetTuple
	 *   The [tuple][TupleDescriptor] to traverse.
	 * @param pathTuple
	 *   The [tuple][TupleDescriptor] containing the path of indices to
	 *   traverse.
	 * @param sliceStartIndex
	 *   The start index of the tuple slice to be replaced.
	 * @param sliceEndIndex
	 *   The end index of the tuple slice to be replaced.
	 * @param pathIndex
	 *   The current position of the pathTuple being accessed.
	 * @param newValues
	 *   The [tuple][TupleDescriptor] of values used to update the given target
	 *   range.
	 * @return
	 *   The outermost tuple with the range updated.
	 * @throws AvailException If a problem occurs.
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateTuple(
		targetTuple: A_Tuple,
		pathTuple: A_Tuple,
		sliceStartIndex: Int,
		sliceEndIndex: Int,
		pathIndex: Int,
		newValues: A_Tuple): A_Tuple
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			if (sliceEndIndex > targetTuple.tupleSize())
			{
				throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
			}
			// Note: We can't destroy the targetTuple while extracting the
			// leftPart, since we still need to extract the rightPart.
			val leftPart = targetTuple.copyTupleFromToCanDestroy(
					1, sliceStartIndex - 1, false)
			val rightPart = targetTuple.copyTupleFromToCanDestroy(
				sliceEndIndex + 1, targetTuple.tupleSize(), true)
			return leftPart
				.concatenateWith(newValues, true)
				.concatenateWith(rightPart, true)
		}

		val targetIndexNumber = pathTuple.tupleAt(pathIndex)
		if (!targetIndexNumber.isInt)
		{
			// Index is non-integral or bigger than an int.
			throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val targetIndex = targetIndexNumber.extractInt()
		if (targetIndex > targetTuple.tupleSize())
		{
			throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}

		val subtuple = targetTuple.tupleAt(targetIndex)
		when
		{
			subtuple.isTuple ->
			{
				val newTuple = recursivelyUpdateTuple(
					subtuple,
					pathTuple,
					sliceStartIndex,
					sliceEndIndex,
					pathIndex + 1,
					newValues)
				return targetTuple.tupleAtPuttingCanDestroy(
					targetIndex, newTuple, true)
			}
			subtuple.isMap ->
			{
				val newMap = recursivelyUpdateMap(
					subtuple,
					pathTuple,
					sliceStartIndex,
					sliceEndIndex,
					pathIndex + 1,
					newValues)
				return targetTuple.tupleAtPuttingCanDestroy(
					targetIndex, newMap, true)
			}
			else -> throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	/**
	 * Recursively traverses the target [map][MapDescriptor] ultimately to
	 * arrive at the [tuple][TupleDescriptor] that contains the range to be
	 * replaced.
	 *
	 * @param targetMap
	 *   The [map][MapDescriptor] to traverse.
	 * @param pathTuple
	 *   The [tuple][TupleDescriptor] containing the path of indices to
	 *   traverse.
	 * @param sliceStartIndex
	 *   The start index of the tuple slice to be replaced.
	 * @param sliceEndIndex
	 *   The end index of the tuple slice to be replaced.
	 * @param pathIndex
	 *   The current position of the pathTuple being accessed.
	 * @param newValues
	 *   The [tuple][TupleDescriptor] of values used to update the given target
	 *   range.
	 * @return The updated map.
	 * @throws AvailException
	 *   If the path cannot be used to correctly navigate the structure.
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateMap(
		targetMap: A_Map,
		pathTuple: A_Tuple,
		sliceStartIndex: Int,
		sliceEndIndex: Int,
		pathIndex: Int,
		newValues: A_Tuple): A_Map
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			// The final index to be accessed MUST be a tuple.  If this is the
			// final location, then the pathTuple was wrong.
			throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
		val targetIndex = pathTuple.tupleAt(pathIndex)
		if (!targetMap.hasKey(targetIndex))
		{
			throw AvailException(E_KEY_NOT_FOUND)
		}
		val targetElement = targetMap.mapAt(targetIndex)
		when
		{
			targetElement.isTuple ->
			{
				val newTuple = recursivelyUpdateTuple(
					targetElement,
					pathTuple,
					sliceStartIndex,
					sliceEndIndex,
					pathIndex + 1,
					newValues)
				return targetMap.mapAtPuttingCanDestroy(
					targetIndex, newTuple, true)
			}
			targetElement.isMap ->
			{
				val newMap = recursivelyUpdateMap(
					targetElement,
					pathTuple,
					sliceStartIndex,
					sliceEndIndex,
					pathIndex + 1,
					newValues)
				return targetMap.mapAtPuttingCanDestroy(
					targetIndex, newMap, true)
			}
			else -> throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralMapType(),
				oneOrMoreOf(ANY.o),
				naturalNumbers,
				wholeNumbers,
				zeroOrMoreOf(ANY.o)),
			mostGeneralMapType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND,
				E_NEGATIVE_SIZE))
}
