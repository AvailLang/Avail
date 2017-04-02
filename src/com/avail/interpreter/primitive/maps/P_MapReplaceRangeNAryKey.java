/**
 * P_MapReplaceRangeNAryKey.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.maps;

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

/**
 * <strong>Primitive:</strong> Replace the range of values in a tuple inside
 * a top level map given a replacement tuple and a tuple of values to chart the
 * path to get to the desired range to replace
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class P_MapReplaceRangeNAryKey
extends Primitive
{

	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_MapReplaceRangeNAryKey().init(
			5, CanInline, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
		final A_Map targetMap = args.get(0);
		final A_Tuple pathTuple = args.get(1);
		final A_Number headLastIndex = args.get(2);
		final A_Number tailFirstIndex = args.get(3);
		final A_Tuple newValues = args.get(4);
		if (!headLastIndex.isInt() || !tailFirstIndex.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int startInt = headLastIndex.extractInt();
		final int endInt = tailFirstIndex.extractInt();

		if (startInt < 1
			|| endInt < 0
			|| startInt > endInt + 1)
		{
			return interpreter.primitiveFailure(E_NEGATIVE_SIZE);
		}

		try
		{
			return interpreter.primitiveSuccess(recursivelyUpdateMap(
				targetMap, pathTuple, startInt, endInt, 1, newValues));
		}
		catch (final AvailException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	/**
	 * Recursively traverses the target {@linkplain TupleDescriptor tuple}
	 * ultimately updating the value range at the final index of the pathIndex.
	 *
	 * @param targetTuple
	 * 		the {@linkplain TupleDescriptor tuple} to traverse
	 * @param pathTuple
	 * 		{@linkplain TupleDescriptor tuple} containing the path of indices
	 *        to traverse to
	 * @param headLastIndex
	 * 		the last index in the head of the target tuple to be kept
	 * @param tailFirstIndex
	 * 		the first index in the tail of the target tuple to be kept
	 * @param pathIndex
	 * 		the current position of the pathTuple being accessed
	 * @param newValues
	 * 		the {@linkplain TupleDescriptor tuple} of values used to update
	 * 		the given target range
	 * @return
	 * @throws AvailException
	 */
	private A_Tuple recursivelyUpdateTuple (
			final A_Tuple targetTuple,
			final A_Tuple pathTuple,
			final int headLastIndex,
			final int tailFirstIndex,
			final int pathIndex,
			final A_Tuple newValues)
		throws AvailException
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			if (targetTuple.tupleSize() < tailFirstIndex)
			{
				throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}

			return ((targetTuple
				.copyTupleFromToCanDestroy(1, headLastIndex, true))
					.concatenateWith(newValues, true))
				.concatenateWith(
					targetTuple.copyTupleFromToCanDestroy(tailFirstIndex,
						targetTuple.tupleSize(), true), true);
		}

		final int targetIndex = pathTuple.tupleAt(pathIndex).extractInt();
		if (targetIndex > targetTuple.tupleSize())
		{
			throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}

		final AvailObject subtuple = targetTuple.tupleAt(targetIndex);
		if (subtuple.isTuple())
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				subtuple, pathTuple, headLastIndex, tailFirstIndex,
				pathIndex + 1, newValues);
			final A_Tuple resultTuple = targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newTuple, true);
			return resultTuple;
		}
		else if (subtuple.isMap())
		{
			final A_BasicObject newMap = recursivelyUpdateMap(subtuple,
				pathTuple, headLastIndex, tailFirstIndex, pathIndex + 1,
				newValues);
			final A_Tuple resultTuple = targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newMap, true);
			return resultTuple;
		}
		else
		{
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	/**
	 * Recursively traverses the target {@linkplain MapDescriptor map}
	 * ultimately to arrive at the {@linkplain TupleDescriptor tuple} that
	 * contains the range to be replaced.
	 *
	 * @param targetMap
	 * 		the {@linkplain MapDescriptor map} to traverse
	 * @param pathTuple
	 * 		{@linkplain TupleDescriptor tuple} containing the path of indices
	 *        to traverse to
	 * @param headLastIndex
	 * 		the last index in the head of the target tuple to be kept
	 * @param tailFirstIndex
	 * 		the first index in the tail of the target tuple to be kept
	 * @param pathIndex
	 * 		the current position of the pathTuple being accessed
	 * @param newValues
	 * 		the {@linkplain TupleDescriptor tuple} of values used to update
	 * 		the given target range
	 * @return
	 * @throws AvailException
	 */
	private A_Map recursivelyUpdateMap (
		final A_Map targetMap,
		final A_Tuple pathTuple,
		final int headLastIndex,
		final int tailFirstIndex,
		final int pathIndex,
		final A_Tuple newValues)
		throws AvailException
	{
		if (pathIndex == pathTuple.tupleSize() + 1)
		{
			//The final index to be accessed MUST be a tuple, if this
			//is the final location, then the pathTuple was wrong.
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
		final A_BasicObject targetIndex = pathTuple.tupleAt(pathIndex);
		if (!targetMap.hasKey(targetIndex))
		{
			throw new AvailException(E_KEY_NOT_FOUND);
		}
		final A_BasicObject targetElement = targetMap.mapAt(targetIndex);
		if (targetElement.isInstanceOf(TupleTypeDescriptor.mostGeneralType()))
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				(A_Tuple)targetElement, pathTuple, headLastIndex,
				tailFirstIndex, pathIndex + 1, newValues);
			final A_Map resultMap = targetMap.mapAtPuttingCanDestroy(
				targetIndex, newTuple, true);
			return resultMap;
		}
		else if (targetElement.isInstanceOf(
			MapTypeDescriptor.mostGeneralType()))
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				(A_Map)targetElement, pathTuple, headLastIndex, tailFirstIndex,
				pathIndex + 1, newValues);
			final A_Map resultMap = targetMap.mapAtPuttingCanDestroy(
				targetIndex, newMap, true);
			return resultMap;
		}
		else
		{
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				MapTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(1),
						true,
						InfinityDescriptor.positiveInfinity(),
						false),
					TupleDescriptor.empty(),
					ANY.o()),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(2),
						true,
						InfinityDescriptor.positiveInfinity(),
						false),
					TupleDescriptor.empty(),
					ANY.o())),
			MapTypeDescriptor.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND,
				E_NEGATIVE_SIZE));
	}
}
