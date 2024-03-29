/*
 * P_MapReplacingNAryKey.kt
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

package avail.interpreter.primitive.maps

import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.exceptions.AvailException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Replace the value at the location indicated by the path
 * [tuple][TupleDescriptor] of the target [map][MapDescriptor] with a new value.
 *
 * @author Rich &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
object P_MapReplacingNAryKey : Primitive(3, CanInline, CanFold)
{
	/**
	 * Recursively traverses the target [tuple][TupleDescriptor] ultimately
	 * updating the value at the final index of the pathIndex.
	 * @param targetTuple
	 *   The [tuple][TupleDescriptor] to traverse
	 * @param pathTuple
	 *   [Tuple][TupleDescriptor] containing the path of indices to traverse to
	 * @param pathIndex
	 *   The current position of pathTuple being accessed
	 * @param newValue
	 *   The updating value
	 * @return The updated tuple.
	 * @throws AvailException E_INCORRECT_ARGUMENT_TYPE
	 * @throws AvailException E_SUBSCRIPT_OUT_OF_BOUNDS
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateTuple(
		targetTuple: A_Tuple,
		pathTuple: A_Tuple,
		pathIndex: Int,
		newValue: A_BasicObject): A_Tuple
	{
		val targetIndex = pathTuple.tupleAt(pathIndex).extractInt
		if (targetIndex > targetTuple.tupleSize)
		{
			throw AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		if (pathIndex == pathTuple.tupleSize)
		{
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newValue, true)
		}

		val subtuple = targetTuple.tupleAt(targetIndex)
		when
		{
			subtuple.isTuple ->
			{
				val newTuple = recursivelyUpdateTuple(
					subtuple, pathTuple, pathIndex + 1, newValue)
				return targetTuple.tupleAtPuttingCanDestroy(
					targetIndex, newTuple, true)
			}
			subtuple.isMap ->
			{
				val newMap = recursivelyUpdateMap(
					subtuple, pathTuple, pathIndex + 1, newValue)
				return targetTuple.tupleAtPuttingCanDestroy(
					targetIndex, newMap, true)
			}
			else -> throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	/**
	 * Recursively traverses the target [map][MapDescriptor] ultimately updating
	 * the value at the final index of the pathIndex.
	 *
	 * @param targetMap
	 *   The [map][MapDescriptor] to traverse
	 * @param pathTuple
	 *   [Tuple][TupleDescriptor] containing the path of indices to traverse to
	 * @param pathIndex
	 *   The current position of pathTuple being accessed
	 * @param newValue
	 *   The updating value
	 * @return The updated map.
	 * @throws AvailException E_INCORRECT_ARGUMENT_TYPE
	 * @throws AvailException E_KEY_NOT_FOUND
	 */
	@Throws(AvailException::class)
	private fun recursivelyUpdateMap(
		targetMap: A_Map,
		pathTuple: A_Tuple,
		pathIndex: Int,
		newValue: A_BasicObject): A_Map
	{
		val targetIndex = pathTuple.tupleAt(pathIndex)
		val targetElement = targetMap.mapAtOrNull(targetIndex) ?:
			throw AvailException(E_KEY_NOT_FOUND)
		if (pathIndex == pathTuple.tupleSize)
		{
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newValue, true)
		}
		when
		{
			targetElement.isTuple ->
			{
				val newTuple = recursivelyUpdateTuple(
					targetElement, pathTuple, pathIndex + 1, newValue)
				return targetMap.mapAtPuttingCanDestroy(
					targetIndex, newTuple, true)
			}
			targetElement.isMap ->
			{
				val newMap = recursivelyUpdateMap(
					targetElement, pathTuple, pathIndex + 1, newValue)
				return targetMap.mapAtPuttingCanDestroy(
					targetIndex, newMap, true)
			}
			else -> throw AvailException(E_INCORRECT_ARGUMENT_TYPE)
		}
	}

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val map = interpreter.argument(0)
		val pathTuple = interpreter.argument(1)
		val newValue = interpreter.argument(2)
		return try
		{
			interpreter.primitiveSuccess(recursivelyUpdateMap(
				map, pathTuple, 1, newValue))
		}
		catch (e: AvailException)
		{
			interpreter.primitiveFailure(e)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralMapType(), oneOrMoreOf(ANY.o), ANY.o),
			mostGeneralMapType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND))
}
