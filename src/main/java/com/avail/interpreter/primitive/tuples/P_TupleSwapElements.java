/*
 * P_TupleSwapElements.java
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
package com.avail.interpreter.primitive.tuples;

import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * <strong>Primitive:</strong> Answer a {@linkplain TupleDescriptor
 * tuple} like the given one, but with the elements at the specified indices
 * swapped.
 */
public final class P_TupleSwapElements extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_TupleSwapElements().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Tuple tuple = interpreter.argument(0);
		final A_Number indexObject1 = interpreter.argument(1);
		final A_Number indexObject2 = interpreter.argument(2);

		if (!indexObject1.isInt() || !indexObject2.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int index1 = indexObject1.extractInt();
		final int index2 = indexObject2.extractInt();
		if (index1 == index2)
		{
			return interpreter.primitiveSuccess(tuple);
		}
		final AvailObject temp1 = tuple.tupleAt(index1);
		final AvailObject temp2 = tuple.tupleAt(index2);
		A_Tuple newTuple = tuple.tupleAtPuttingCanDestroy(index1, temp2, true);
		newTuple = newTuple.tupleAtPuttingCanDestroy(index2, temp1, true);
		return interpreter.primitiveSuccess(newTuple);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers(),
				naturalNumbers()),
			mostGeneralTupleType());
	}

	/**
	 * A measure of complexity beyond which we don't bother computing a precise
	 * guarantee about the resulting type, since the cost of computing it might
	 * be higher than the potential savings.
	 */
	private static final int maximumComplexity = 100;

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type originalTupleType = argumentTypes.get(0);
		final A_Type indexRange1 = argumentTypes.get(1);
		final A_Type indexRange2 = argumentTypes.get(2);

		final A_Type tupleSizeRange = originalTupleType.sizeRange();
		final A_Number lowerBound1 = indexRange1.lowerBound();
		final A_Number lowerBound2 = indexRange2.lowerBound();
		if (!lowerBound1.isInt() || !lowerBound2.isInt())
		{
			// At least one subscript is always out of range of the primitive.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
		}
		final int maxLowerBound =
			max(lowerBound1.extractInt(), lowerBound2.extractInt());
		final A_Number maxTupleSize = tupleSizeRange.upperBound();
		if (maxTupleSize.isInt() && maxLowerBound > maxTupleSize.extractInt())
		{
			// One of the indices is always out of range of the tuple.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
		}
		final int minLowerBound =
			min(lowerBound1.extractInt(), lowerBound2.extractInt());

		final int startOfSmear = min(minLowerBound, maximumComplexity);
		// Keep the leading N types the same, but smear the rest.
		final A_Tuple newLeadingTypes = generateObjectTupleFrom(
			startOfSmear - 1, originalTupleType::typeAtIndex);
		return tupleTypeForSizesTypesDefaultType(
			tupleSizeRange,
			newLeadingTypes,
			originalTupleType.unionOfTypesAtThrough(
				startOfSmear, Integer.MAX_VALUE));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS));
	}
}
