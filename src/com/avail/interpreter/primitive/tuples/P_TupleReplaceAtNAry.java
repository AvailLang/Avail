/**
 * P_TupleReplaceAtNAry.java
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

package com.avail.interpreter.primitive.tuples;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.exceptions.AvailException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor
	.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
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
	 *         The outermost tuple with the update applied.
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
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newValue, true);
		}

		final AvailObject subtuple = targetTuple.tupleAt(targetIndex);
		if (subtuple.isTuple())
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				subtuple, pathTuple, pathIndex + 1,
				newValue);
			return targetTuple.tupleAtPuttingCanDestroy(
				targetIndex, newTuple, true);
		}
		else if (subtuple.isMap())
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				subtuple, pathTuple, pathIndex + 1,
				newValue);
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
	 *         The outermost {@link A_Map map} with the update applied.
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
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newValue, true);
		}
		final A_BasicObject targetElement = targetMap.mapAt(targetIndex);
		if (targetElement.isTuple())
		{
			final A_BasicObject newTuple = recursivelyUpdateTuple(
				(A_Tuple)targetElement, pathTuple, pathIndex + 1, newValue);
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newTuple, true);
		}
		else if (targetElement.isMap())
		{
			final A_BasicObject newMap = recursivelyUpdateMap(
				(A_Map)targetElement, pathTuple, pathIndex + 1, newValue);
			return targetMap.mapAtPuttingCanDestroy(
				targetIndex, newMap, true);
		}
		else
		{
			throw new AvailException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
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
		return functionType(tuple(mostGeneralTupleType(),
			tupleTypeForSizesTypesDefaultType(
				integerRangeType(
					fromInt(2),
					true,
					positiveInfinity(),
					false),
				emptyTuple(),
				ANY.o()), ANY.o()), mostGeneralTupleType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(E_SUBSCRIPT_OUT_OF_BOUNDS, E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND));
	}
}
