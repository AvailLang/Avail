/**
 * P_702_ReverseTuple.java
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
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import java.util.List;

/**
 * <strong>Primitive 702</strong>: Produce a {@linkplain A_Tuple#reverseTuple()
 * reverse of the given tuple; same elements, opposite order.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public final class P_702_ReverseTuple extends Primitive
{

	/**
	 * Construct a new {@link P_702_ReverseTuple}.
	 *
	 */
	public final static Primitive instance =
		new P_702_ReverseTuple().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Tuple tuple = args.get(0);
		return interpreter.primitiveSuccess(tuple.tupleReverse());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
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
			// Variable number of <key,value> pairs.  Give up.
			return super.returnTypeGuaranteedByVM(argumentTypes);
		}
		final int tupleSize = tupleSizeLowerBound.extractInt();
		final A_Tuple elementTypes = tupleType.tupleOfTypesFromTo(1, tupleSize);
		final A_Tuple reversedElementTypes = elementTypes.tupleReverse();
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			tupleSizes, reversedElementTypes, BottomTypeDescriptor.bottom());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.mostGeneralType()),
			TupleTypeDescriptor.mostGeneralType());
	}
}
