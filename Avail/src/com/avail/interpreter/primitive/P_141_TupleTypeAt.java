/**
 * P_141_TupleTypeAt.java
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
package com.avail.interpreter.primitive;

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 141:</strong> Answer the {@linkplain TypeDescriptor
 * type} for the given element of {@linkplain TupleDescriptor instances} of
 * the given {@linkplain TupleTypeDescriptor tuple type}. Answer
 * {@linkplain BottomTypeDescriptor bottom} if out of range.
 */
public final class P_141_TupleTypeAt extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_141_TupleTypeAt().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Type tupleType = args.get(0);
		final A_Number index = args.get(1);
		if (!index.isInt())
		{
			return interpreter.primitiveSuccess(
				BottomTypeDescriptor.bottom());
		}
		return interpreter.primitiveSuccess(
			tupleType.typeAtIndex(index.extractInt()));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleMeta = argumentTypes.get(0);
		final A_Type indexType = argumentTypes.get(1);

		final A_Type tupleType = tupleMeta.instance();
		final A_Number minIndex = indexType.lowerBound();
		final A_Number maxIndex = indexType.upperBound();
		final int maxIndexInt = maxIndex.isInt()
			? maxIndex.extractInt()
			: Integer.MAX_VALUE;
		if (minIndex.isInt())
		{
			return tupleType.unionOfTypesAtThrough(
				minIndex.extractInt(), maxIndexInt);
		}
		return super.returnTypeGuaranteedByVM(
			argumentTypes);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.meta(),
				IntegerRangeTypeDescriptor.naturalNumbers()),
			InstanceMetaDescriptor.anyMeta());
	}
}