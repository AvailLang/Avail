/*
 * P_TupleReplaceRangeAtNary.kt
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.IntegerRangeTypeDescriptor.*
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.AvailException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Replace the range of values in a tuple given
 * a replacement tuple and a tuple of values to chart the path to get to the
 * desired range to replace.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object P_TupleReplaceRangeAtNary : Primitive(5, CanInline, CanFold)
{

	/**
	 * Recursively traverses the target [tuple][TupleDescriptor]
	 * ultimately updating the value range at the final index of the pathIndex.
	 *
	 * @param targetTuple
	 * The [tuple][TupleDescriptor] to traverse.
	 * @param pathTuple
	 * The [tuple][TupleDescriptor] containing the path of
	 * indices to traverse.
	 * @param headLastIndex
	 * The last index in the head of the target tuple to be kept.
	 * @param tailFirstIndex
	 * The first index in the tail of the target tuple to be kept.
	 * @param pathIndex
	 * The current position of the pathTuple being accessed.
	 * @param newValues
	 * The [tuple][TupleDescriptor] of values used to update
	 * the given target range.
	 * @return
	 * The outermost tuple with the range updated.
	 * @throws AvailException If a problem occurs.
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateTuple(
		targetTuple: A_Tuple,
		pathTuple: A_Tuple,
		headLastIndex: Int,
		tailFirstIndex: Int,
		pathIndex: Int,
		newValues: A_Tuple): A_Tuple
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			if (targetTuple.tupleSize() < tailFirstIndex)
			{
				throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
			}
			val head = targetTuple.copyTupleFromToCanDestroy(
				1, headLastIndex, true)
			var newTuple = head.concatenateWith(newValues, true)
			val tail = targetTuple.copyTupleFromToCanDestroy(
				tailFirstIndex, targetTuple.tupleSize(), true)
			newTuple = newTuple.concatenateWith(tail, true)
			return newTuple

		}

		val targetIndex = pathTuple.tupleAt(pathIndex).extractInt()
		if (targetIndex > targetTuple.tupleSize())
		{
			throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}

		val subtuple = targetTuple.tupleAt(targetIndex)
		if (subtuple.isTuple)
		{
			val newTuple = recursivelyUpdateTuple(
				subtuple, pathTuple, headLastIndex, tailFirstIndex,
				pathIndex + 1, newValues)
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newTuple, true)
		}
		else if (subtuple.isMap)
		{
			val newMap = recursivelyUpdateMap(subtuple,
			                                  pathTuple, headLastIndex, tailFirstIndex, pathIndex + 1,
			                                  newValues)
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newMap, true)
		}
		else
		{
			throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	/**
	 * Recursively traverses the target [map][MapDescriptor]
	 * ultimately to arrive at the [tuple][TupleDescriptor] that
	 * contains the range to be replaced.
	 *
	 * @param targetMap
	 * The [map][MapDescriptor] to traverse.
	 * @param pathTuple
	 * The [tuple][TupleDescriptor] containing the path of
	 * indices to traverse.
	 * @param headLastIndex
	 * The last index in the head of the target tuple to be kept.
	 * @param tailFirstIndex
	 * The first index in the tail of the target tuple to be kept.
	 * @param pathIndex
	 * The current position of the pathTuple being accessed.
	 * @param newValues
	 * The [tuple][TupleDescriptor] of values used to update
	 * the given target range.
	 * @return
	 * The outermost [map][A_Map], but with the inner tuple range
	 * updated.
	 * @throws AvailException If a problem occurs.
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateMap(
		targetMap: A_Map,
		pathTuple: A_Tuple,
		headLastIndex: Int,
		tailFirstIndex: Int,
		pathIndex: Int,
		newValues: A_Tuple): A_Map
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			//The final index to be accessed MUST be a tuple, if this
			//is the final location, then the pathTuple was wrong.
			throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
		val targetIndex = pathTuple.tupleAt(pathIndex)
		if (!targetMap.hasKey(targetIndex))
		{
			throw AvailException(E_KEY_NOT_FOUND)
		}
		val targetElement = targetMap.mapAt(targetIndex)
		if (targetElement.isTuple)
		{
			val newTuple = recursivelyUpdateTuple(
				targetElement, pathTuple, headLastIndex,
				tailFirstIndex, pathIndex + 1, newValues)
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newTuple, true)
		}
		else if (targetElement.isMap)
		{
			val newMap = recursivelyUpdateMap(
				targetElement, pathTuple, headLastIndex, tailFirstIndex,
				pathIndex + 1, newValues)
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newMap, true)
		}
		else
		{
			throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val targetTuple = interpreter.argument(0)
		val pathTuple = interpreter.argument(1)
		val headLastIndex = interpreter.argument(2)
		val tailFirstIndex = interpreter.argument(3)
		val newValues = interpreter.argument(4)
		if (!headLastIndex.isInt || !tailFirstIndex.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}

		val startInt = headLastIndex.extractInt()
		val endInt = tailFirstIndex.extractInt()

		if (startInt < 1
		    || endInt < 0
		    || startInt > endInt + 1)
		{
			return interpreter.primitiveFailure(E_NEGATIVE_SIZE)
		}

		try
		{
			val result = recursivelyUpdateTuple(
				targetTuple, pathTuple, startInt, endInt, 1, newValues)

			return interpreter.primitiveSuccess(result)
		}
		catch (e: AvailException)
		{
			return interpreter.primitiveFailure(e)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(mostGeneralTupleType(),
		                          tupleTypeForSizesTypesDefaultType(
			                          integerRangeType(
				                          fromInt(1),
				                          true,
				                          positiveInfinity(),
				                          false),
			                          emptyTuple(),
			                          ANY.o()), naturalNumbers(),
		                          wholeNumbers(),
		                          tupleTypeForSizesTypesDefaultType(
			                          integerRangeType(
				                          fromInt(2),
				                          true,
				                          positiveInfinity(),
				                          false),
			                          emptyTuple(),
			                          ANY.o())), mostGeneralTupleType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_SUBSCRIPT_OUT_OF_BOUNDS, E_INCORRECT_ARGUMENT_TYPE,
			    E_KEY_NOT_FOUND, E_NEGATIVE_SIZE))
	}

}