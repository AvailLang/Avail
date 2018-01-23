/**
 * P_ReverseTuple.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor
	.tupleTypeForSizesTypesDefaultType;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.lang.Integer.MAX_VALUE;

/**
 * <strong>Primitive:</strong> Produce a {@linkplain A_Tuple#tupleReverse()
 * reverse} of the given tuple; same elements, opposite order.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public final class P_ReverseTuple extends Primitive
{

	/**
	 * Construct a new {@link P_ReverseTuple}.
	 *
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ReverseTuple().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final A_Tuple tuple = args.get(0);
		return interpreter.primitiveSuccess(tuple.tupleReverse());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleType = argumentTypes.get(0);
		if (tupleType.typeTuple().tupleSize() == 0)
		{
			// The tuple type is homogeneous.  Answer the same tuple type, since
			// it's its own inverse.
			return tupleType;
		}
		final A_Type tupleSizes = tupleType.sizeRange();
		final A_Number tupleSizeLowerBound = tupleSizes.lowerBound();
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound())
			|| !tupleSizeLowerBound.isInt())
		{
			// Variable number of <key,value> pairs.  In theory we could
			// still strengthen it, but a homogenous tuple type of the same size
			// should be sufficient.
			return tupleTypeForSizesTypesDefaultType(
				tupleSizes,
				emptyTuple(),
				tupleType.unionOfTypesAtThrough(1, MAX_VALUE));
		}
		final int tupleSize = tupleSizeLowerBound.extractInt();
		final A_Tuple elementTypes = tupleType.tupleOfTypesFromTo(1, tupleSize);
		final A_Tuple reversedElementTypes = elementTypes.tupleReverse();
		return tupleTypeForSizesTypesDefaultType(
			tupleSizes, reversedElementTypes, bottom());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(mostGeneralTupleType()),
			mostGeneralTupleType());
	}
}
