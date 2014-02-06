/**
 * P_136_ConcatenateTuples.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive;

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 136:</strong> Concatenate a {@linkplain TupleDescriptor
 * tuple} of tuples together into a single tuple.
 */
public final class P_136_ConcatenateTuples extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_136_ConcatenateTuples().init(
		1, CanFold, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Tuple tuples = args.get(0);
		return interpreter.primitiveSuccess(
			tuples.concatenateTuplesCanDestroy(true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.zeroOrMoreOf(
					TupleTypeDescriptor.mostGeneralType())),
			TupleTypeDescriptor.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tuplesType = argumentTypes.get(0);

		final A_Type tuplesSizes = tuplesType.sizeRange();
		final A_Number lowerBound = tuplesSizes.lowerBound();
		final A_Number upperBound = tuplesSizes.upperBound();
		if (lowerBound.equals(upperBound))
		{
			// A fixed number of subtuples.  Must be finite, of course.
			if (lowerBound.greaterThan(
				IntegerDescriptor.fromUnsignedByte((short)100)))
			{
				// Too expensive to compute here.
				return super.returnTypeGuaranteedByVM(
					argumentTypes);
			}
			// A (reasonably small) collection of tuple types.
			assert lowerBound.isInt();
			final int bound = lowerBound.extractInt();
			A_Type concatenatedType =
				InstanceTypeDescriptor.on(TupleDescriptor.empty());
			for (int i = 1; i <= bound; i++)
			{
				concatenatedType =
					ConcatenatedTupleTypeDescriptor.concatenatingAnd(
						concatenatedType,
						tuplesType.typeAtIndex(i));
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
						innerSizes.lowerBound(),
						false);
				final A_Number maxSize =
					tuplesSizes.upperBound().timesCanDestroy(
						innerSizes.upperBound(),
						false);
				final A_Type newSizeRange =
					IntegerRangeTypeDescriptor.create(
						minSize,
						true,
						maxSize.plusCanDestroy(IntegerDescriptor.one(), true),
						false);
				return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					newSizeRange,
					TupleDescriptor.empty(),
					innerTupleType.defaultType());
			}
		}
		// Too tricky to bother narrowing.
		return super.returnTypeGuaranteedByVM(argumentTypes);
	}
}