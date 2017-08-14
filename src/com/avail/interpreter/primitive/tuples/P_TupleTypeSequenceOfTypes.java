/**
 * P_TupleTypeSequenceOfTypes.java
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;

import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Generator;

/**
 * <strong>Primitive:</strong> Answer a {@linkplain TupleDescriptor
 * tuple} of {@linkplain TypeDescriptor types} representing the types of the
 * given range of indices within the {@linkplain TupleTypeDescriptor tuple
 * type}. Use {@linkplain BottomTypeDescriptor bottom} for indices out of
 * range.
 */
public final class P_TupleTypeSequenceOfTypes extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_TupleTypeSequenceOfTypes().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Type tupleType = args.get(0);
		final A_Number startIndex = args.get(1);
		final A_Number endIndex = args.get(2);
		if (!startIndex.isInt() || !endIndex.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int startInt = startIndex.extractInt();
		final int endInt = endIndex.extractInt();
		final int tupleSize = endInt - startInt + 1;
		if (tupleSize < 0)
		{
			return interpreter.primitiveFailure(E_NEGATIVE_SIZE);
		}
		final A_Tuple tupleObject = ObjectTupleDescriptor.generateFrom(
			tupleSize,
			new Generator<A_BasicObject>()
			{
				private int index = startInt;

				@Override
				public A_BasicObject value ()
				{
					return tupleType.typeAtIndex(index++).makeImmutable();
				}
			});
		return interpreter.primitiveSuccess(tupleObject);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.meta(),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				IntegerRangeTypeDescriptor.wholeNumbers()),
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.anyMeta()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_NEGATIVE_SIZE));
	}
}
