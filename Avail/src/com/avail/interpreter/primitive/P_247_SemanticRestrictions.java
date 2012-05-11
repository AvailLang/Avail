/**
 * P_247_SemanticRestrictions.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 247:</strong> Answer a {@linkplain TupleDescriptor
 * tuple} of restriction {@linkplain FunctionDescriptor functions} that
 * would run for a call site for the specified {@linkplain
 * MethodDescriptor method} and tuple of argument types.
 */
public class P_247_SemanticRestrictions extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_247_SemanticRestrictions().init(
		2, CanInline, CannotFail);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject impSet = args.get(0);
		final AvailObject argTypes = args.get(1);
		final AvailObject restrictions = impSet.typeRestrictions();
		final List<AvailObject> applicable = new ArrayList<AvailObject>();
		for (int i = restrictions.tupleSize(); i >= 1; i--)
		{
			final AvailObject restriction = restrictions.tupleAt(i);
			if (restriction.kind().acceptsTupleOfArguments(argTypes))
			{
				applicable.add(restriction);
			}
		}
		return interpreter.primitiveSuccess(
			TupleDescriptor.fromCollection(applicable));
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				METHOD.o(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					InstanceTypeDescriptor.on(ANY.o()))),
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				FunctionTypeDescriptor.forReturnType(
					TYPE.o())));
	}
}