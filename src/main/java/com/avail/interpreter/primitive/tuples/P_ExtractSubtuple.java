/*
 * P_ExtractSubtuple.java
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

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerDescriptor.zero;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static java.lang.Math.min;

/**
 * <strong>Primitive:</strong> Extract a {@linkplain TupleDescriptor
 * subtuple} with the given range of elements.
 */
public final class P_ExtractSubtuple extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ExtractSubtuple().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Tuple tuple = interpreter.argument(0);
		final A_Number start = interpreter.argument(1);
		final A_Number end = interpreter.argument(2);
		if (!start.isInt() || !end.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int startInt = start.extractInt();
		final int endInt = end.extractInt();
		if (startInt < 1
			|| startInt > endInt + 1
			|| endInt > tuple.tupleSize())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		return interpreter.primitiveSuccess(
			tuple.copyTupleFromToCanDestroy(startInt, endInt, true));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleType = argumentTypes.get(0);
		final A_Type startType = argumentTypes.get(1);
		final A_Type endType = argumentTypes.get(2);

		// If the start type is a fixed index (and it's an int) then strengthen
		// the result easily.  Otherwise it's too tricky for now.
		final A_Number startInteger = startType.lowerBound();
		if (startInteger.equals(startType.upperBound()) && startInteger.isInt())
		{
			final int startInt = startInteger.extractInt();
			final A_Number adjustment =
				startInteger.minusCanDestroy(one(), false).makeImmutable();
			final A_Type oldSizes = tupleType.sizeRange();
			final A_Number oldEnd1 = oldSizes.upperBound();
			final A_Number oldEnd2 = endType.upperBound();
			final A_Number oldEnd = oldEnd1.greaterOrEqual(oldEnd2)
				? oldEnd2 : oldEnd1;
			final A_Number newLower =
				endType.lowerBound().minusCanDestroy(adjustment, false);
			final A_Number realLower =
				newLower.lessThan(zero()) ? zero() : newLower;
			final A_Number newEnd = oldEnd.minusCanDestroy(adjustment, false);
			final A_Type newSizes = integerRangeType(
				realLower, true, newEnd.plusCanDestroy(one(), true), false);
			final A_Tuple originalLeading = tupleType.typeTuple();
			final A_Tuple newLeading =
				originalLeading.copyTupleFromToCanDestroy(
					min(startInt, originalLeading.tupleSize() + 1),
					originalLeading.tupleSize(),
					false);
			return tupleTypeForSizesTypesDefaultType(
				newSizes, newLeading, tupleType.defaultType());
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers(),
				wholeNumbers()),
			mostGeneralTupleType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS));
	}
}
