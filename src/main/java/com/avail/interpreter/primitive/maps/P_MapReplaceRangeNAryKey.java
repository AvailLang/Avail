/*
 * P_MapReplaceRangeNAryKey.java
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

package com.avail.interpreter.primitive.maps;

import com.avail.descriptor.*;
import com.avail.exceptions.AvailException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.MapTypeDescriptor.mostGeneralMapType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_MapReplaceRangeNAryKey().init(
			5, CanInline, CanFold);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(5);
		final A_Map targetMap = interpreter.argument(0);
		final A_Tuple pathTuple = interpreter.argument(1);
		final A_Number headLastIndex = interpreter.argument(2);
		final A_Number tailFirstIndex = interpreter.argument(3);
		final A_Tuple newValues = interpreter.argument(4);
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
			return interpreter.primitiveSuccess(
				recursivelyUpdateMap(
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
	 *        The {@linkplain TupleDescriptor tuple} to traverse.
	 * @param pathTuple
	 *        The {@linkplain TupleDescriptor tuple} containing the path of
	 *        indices to traverse.
	 * @param headLastIndex
	 *        The last index in the head of the target tuple to be kept.
	 * @param tailFirstIndex
	 *        The first index in the tail of the target tuple to be kept.
	 * @param pathIndex
	 *        The current position of the pathTuple being accessed.
	 * @param newValues
	 *        The {@linkplain TupleDescriptor tuple} of values used to update
	 *        the given target range.
	 * @return The updated tuple.
	 * @throws AvailException
	 *         If the path cannot be used to correctly navigate the structure.
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
			if (tailFirstIndex > targetTuple.tupleSize() + 1)
			{
				throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			// Note: We can't destroy the targetTuple while extracting the
			// leftPart, since we still need to extract the rightPart.
			final A_Tuple leftPart =
				targetTuple.copyTupleFromToCanDestroy(1, headLastIndex, false);
			final A_Tuple rightPart = targetTuple.copyTupleFromToCanDestroy(
				tailFirstIndex, targetTuple.tupleSize(), true);
			return
				leftPart
					.concatenateWith(newValues, true)
					.concatenateWith(rightPart, true);
		}

		final A_Number targetIndexNumber = pathTuple.tupleAt(pathIndex);
		if (!targetIndexNumber.isInt())
		{
			// Index is non-integral or bigger than an int.
			throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int targetIndex = targetIndexNumber.extractInt();
		if (targetIndex < 1 || targetIndex > targetTuple.tupleSize())
		{
			throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final AvailObject subtuple = targetTuple.tupleAt(targetIndex);
		if (subtuple.isTuple())
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				subtuple,
				pathTuple,
				headLastIndex,
				tailFirstIndex,
				pathIndex + 1,
				newValues);
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newTuple, true);
		}
		else if (subtuple.isMap())
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				subtuple,
				pathTuple,
				headLastIndex,
				tailFirstIndex,
				pathIndex + 1,
				newValues);
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newMap, true);
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
	 *
	 * @param targetMap
	 *        The {@linkplain MapDescriptor map} to traverse.
	 * @param pathTuple
	 *        The {@linkplain TupleDescriptor tuple} containing the path of
	 *        indices to traverse.
	 * @param headLastIndex
	 *        The last index in the head of the target tuple to be kept.
	 * @param tailFirstIndex
	 *        The first index in the tail of the target tuple to be kept.
	 * @param pathIndex
	 *        The current position of the pathTuple being accessed.
	 * @param newValues
	 *        The {@linkplain TupleDescriptor tuple} of values used to update
	 *        the given target range.
	 * @return The updated map.
	 * @throws AvailException
	 *         If the path cannot be used to correctly navigate the structure.
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
			// The final index to be accessed MUST be a tuple.  If this is the
			// final location, then the pathTuple was wrong.
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
		final A_Number targetIndex = pathTuple.tupleAt(pathIndex);
		if (!targetMap.hasKey(targetIndex))
		{
			throw new AvailException(E_KEY_NOT_FOUND);
		}
		final AvailObject targetElement = targetMap.mapAt(targetIndex);
		if (targetElement.isTuple())
		{
			final A_Tuple newTuple = recursivelyUpdateTuple(
				targetElement,
				pathTuple,
				headLastIndex,
				tailFirstIndex,
				pathIndex + 1,
				newValues);
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newTuple, true);
		}
		else if (targetElement.isMap())
		{
			final A_Map newMap = recursivelyUpdateMap(
				targetElement,
				pathTuple,
				headLastIndex,
				tailFirstIndex,
				pathIndex + 1,
				newValues);
			return targetMap.mapAtPuttingCanDestroy(targetIndex, newMap, true);
		}
		else
		{
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralMapType(),
				oneOrMoreOf(ANY.o()),
				wholeNumbers(),
				naturalNumbers(),
				tupleTypeForSizesTypesDefaultType(
					integerRangeType(
						fromInt(2), true, positiveInfinity(), false),
					emptyTuple(),
					ANY.o())),
			mostGeneralMapType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND,
				E_NEGATIVE_SIZE));
	}
}
