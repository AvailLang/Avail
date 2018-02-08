/*
 * P_ConcatenateTuples.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.ConcatenatedTupleTypeDescriptor.concatenatingAnd;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Concatenate a {@linkplain TupleDescriptor
 * tuple} of tuples together into a single tuple.
 */
public final class P_ConcatenateTuples extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ConcatenateTuples().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Tuple tuples = interpreter.argument(0);
		return interpreter.primitiveSuccess(
			tuples.concatenateTuplesCanDestroy(true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(zeroOrMoreOf(mostGeneralTupleType())),
			mostGeneralTupleType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tuplesType = argumentTypes.get(0);

		final A_Type tuplesSizes = tuplesType.sizeRange();
		final A_Number lowerBound = tuplesSizes.lowerBound();
		final A_Number upperBound = tuplesSizes.upperBound();
		if (lowerBound.equals(upperBound))
		{
			// A fixed number of subtuples.  Must be finite, of course.
			if (lowerBound.greaterThan(fromInt(20)))
			{
				// Too expensive to compute here.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes);
			}
			// A (reasonably small) collection of tuple types.
			assert lowerBound.isInt();
			final int bound = lowerBound.extractInt();
			if (bound == 0)
			{
				return instanceType(emptyTuple());
			}
			A_Type concatenatedType = tuplesType.typeAtIndex(1);
			for (int i = 2; i <= bound; i++)
			{
				concatenatedType = concatenatingAnd(
					concatenatedType, tuplesType.typeAtIndex(i));
			}
			return concatenatedType;
		}
		// A variable number of subtuples.  See if it's homogeneous.
		if (tuplesType.typeTuple().tupleSize() == 0)
		{
			// The outer tuple type is homogeneous.
			final A_Type innerTupleType = tuplesType.defaultType();
			if (innerTupleType.typeTuple().tupleSize() == 0)
			{
				// The inner tuple type is also homogeneous.
				final A_Type innerSizes = innerTupleType.sizeRange();
				final A_Number minSize =
					tuplesSizes.lowerBound().timesCanDestroy(
						innerSizes.lowerBound(), false);
				final A_Number maxSize =
					tuplesSizes.upperBound().timesCanDestroy(
						innerSizes.upperBound(), false);
				final A_Type newSizeRange = integerRangeType(
					minSize,
					true,
					maxSize.plusCanDestroy(one(), true),
					false);
				return tupleTypeForSizesTypesDefaultType(
					newSizeRange,
					emptyTuple(),
					innerTupleType.defaultType());
			}
		}
		// Too tricky to bother narrowing.
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
	}
}
