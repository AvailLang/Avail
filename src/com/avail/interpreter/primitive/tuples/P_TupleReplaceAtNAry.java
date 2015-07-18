/**
 * P_TupleReplaceAtNAry.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.tuples;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Replace the value with a new value in the
 * {@linkplain TupleDescriptor tuple} at the location indicated by the path
 * tuple.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class P_TupleReplaceAtNAry
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_TupleReplaceAtNAry().init(
			3, CanInline, CanFold);
	/**
	 * Recursively traverses the target {@linkplain TupleDescriptor tuple}
	 * ultimately updating the value at the final index of the pathIndex.
	 *
	 * @param targetTuple
	 *        the {@linkplain TupleDescriptor tuple} to traverse
	 * @param pathTuple
	 *        {@linkplain TupleDescriptor tuple} containing the path of indices
	 *        to traverse to
	 * @param pathIndex
	 *        the current position of pathTuple being accessed
	 * @param newValue
	 *        the updating value
	 * @return
	 * @throws AvailException
	 */
	private A_Tuple recursivelyUpdateTuple (
			final A_Tuple targetTuple,
			final A_Tuple pathTuple,
			final int pathIndex,
			final A_BasicObject newValue)
		throws AvailException
	{
		final int targetIndex = pathTuple.tupleAt(pathIndex).extractInt();
		if (targetIndex > targetTuple.tupleSize())
		{
			throw new AvailException(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		if (pathIndex == pathTuple.tupleSize())
		{
			final A_Tuple newTuple = targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newValue, true);
			return newTuple;
		}

		final AvailObject subtuple = targetTuple.tupleAt(targetIndex);
		if (subtuple.isTuple())
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				subtuple, pathTuple, pathIndex + 1,
				newValue);
			final A_Tuple resultTuple = targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newTuple, true);
			return resultTuple;
		}
		else if (subtuple.isMap())
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				subtuple, pathTuple, pathIndex + 1,
				newValue);
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
	 * ultimately updating the value at the final index of the pathIndex.
	 *
	 * @param targetMap
	 *        the {@linkplain MapDescriptor map} to traverse
	 * @param pathTuple
	 *        {@linkplain TupleDescriptor tuple} containing the path of indices
	 *        to traverse to
	 * @param pathIndex
	 *        the current position of pathTuple being accessed
	 * @param newValue
	 *        the updating value
	 * @return
	 * @throws AvailException E_INCORRECT_ARGUMENT_TYPE
	 * @throws AvailException E_KEY_NOT_FOUND
	 */
	private A_Map recursivelyUpdateMap (
			final A_Map targetMap,
			final A_Tuple pathTuple,
			final int pathIndex,
			final A_BasicObject newValue)
		throws AvailException
	{
		final A_BasicObject targetIndex = pathTuple.tupleAt(pathIndex);
		if (!targetMap.hasKey(targetIndex))
		{
			throw new AvailException(E_KEY_NOT_FOUND);
		}
		if (pathIndex == pathTuple.tupleSize())
		{
			final A_Map newMap = targetMap.mapAtPuttingCanDestroy(
				targetIndex, newValue, true);
			return newMap;
		}
		final A_BasicObject targetElement = targetMap.mapAt(targetIndex);
		if (targetElement.isInstanceOf(TupleTypeDescriptor.mostGeneralType()))
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				(A_Tuple)targetElement, pathTuple, pathIndex + 1, newValue);
			final A_Map resultMap = targetMap.mapAtPuttingCanDestroy(
				targetIndex, newTuple, true);
			return resultMap;
		}
		else if (targetElement.isInstanceOf(
			MapTypeDescriptor.mostGeneralType()))
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				(A_Map)targetElement, pathTuple, pathIndex + 1, newValue);
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
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Tuple tuple = args.get(0);
		final A_Tuple pathTuple = args.get(1);
		final A_BasicObject newValue = args.get(2);
		try
		{
			return interpreter.primitiveSuccess(recursivelyUpdateTuple(
				tuple, pathTuple, 1, newValue));
		}
		catch (final AvailException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(2),
						true,
						InfinityDescriptor.positiveInfinity(),
						false),
					TupleDescriptor.empty(),
					ANY.o()),
				ANY.o()),
			TupleTypeDescriptor.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_SUBSCRIPT_OUT_OF_BOUNDS.numericCode(),
				E_INCORRECT_ARGUMENT_TYPE.numericCode(),
				E_KEY_NOT_FOUND.numericCode())));
	}
}
