/**
 * P_132_TupleReplaceAt.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static java.lang.Math.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 132:</strong> Answer a {@linkplain TupleDescriptor
 * tuple} like the given one, but with an element changed as indicated.
 */
public class P_132_TupleReplaceAt extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_132_TupleReplaceAt().init(
		3, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final A_Tuple tuple = args.get(0);
		final A_Number indexObject = args.get(1);
		final AvailObject value = args.get(2);
		if (!indexObject.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int index = indexObject.extractInt();
		if (index > tuple.tupleSize())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		return interpreter.primitiveSuccess(tuple.tupleAtPuttingCanDestroy(
			index,
			value,
			true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.mostGeneralType(),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				ANY.o()),
			TupleTypeDescriptor.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type originalTupleType = argumentTypes.get(0);
		final A_Type subscripts = argumentTypes.get(1);
		final A_Type newElementType = argumentTypes.get(2);
		final A_Number lowerBound = subscripts.lowerBound();
		final A_Number upperBound = subscripts.upperBound();
		final boolean singleSubscript = lowerBound.equals(upperBound);
		if (lowerBound.greaterThan(
				IntegerDescriptor.fromUnsignedByte((short)100))
			|| (upperBound.isFinite()
				&& upperBound.greaterThan(
					IntegerDescriptor.fromUnsignedByte((short)100))))
		{
			// Too expensive.  Fall back on the primitive's basic type.
			return super.returnTypeGuaranteedByVM(
				argumentTypes);
		}
		final A_Tuple originalTypeTuple = originalTupleType.typeTuple();
		final int originalTypeTupleSize = originalTypeTuple.tupleSize();
		final int minSubscript = lowerBound.isInt()
			? max(lowerBound.extractInt(), 1)
			: 1;
		final int maxSubscript = upperBound.isFinite()
			? min(upperBound.extractInt(), originalTypeTupleSize)
			: Integer.MAX_VALUE;
		final List<A_Type> typeList =
			new ArrayList<A_Type>(originalTypeTuple.tupleSize());
		for (final A_Type element : originalTypeTuple)
		{
			typeList.add(element);
		}
		for (int i = 1; i < minSubscript; i++)
		{
			typeList.add(originalTupleType.typeAtIndex(i));
		}
		final int limit = min(maxSubscript, originalTypeTupleSize);
		for (int i = minSubscript; i <= limit; i++)
		{
			if (singleSubscript)
			{
				// A single subscript is possible, so that element *must* be
				// replaced by the new value.
				assert minSubscript == limit;
				typeList.add(newElementType);
			}
			else
			{
				// A non-singular range of subscripts is eligible to be
				// overwritten, so any element in that range can have either the
				// old type or the new type.
				typeList.add(
					originalTupleType.typeAtIndex(i).typeUnion(newElementType));
			}
		}
		for (int i = limit + 1; i <= originalTypeTupleSize; i++)
		{
			typeList.add(originalTupleType.typeAtIndex(i));
		}
		final A_Type newDefaultType = upperBound.isFinite()
			? originalTupleType.defaultType()
			: originalTupleType.defaultType().typeUnion(newElementType);
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			originalTupleType.sizeRange(),
			TupleDescriptor.fromList(typeList),
			newDefaultType);
	}
}