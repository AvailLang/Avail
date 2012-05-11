/**
 * P_007_CreateIntegerRange.java
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

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 7:</strong> Answer the {@linkplain
 * IntegerRangeTypeDescriptor integer range} constrained by the specified
 * {@linkplain ExtendedIntegerDescriptor upper and lower bounds}. The
 * provided {@linkplain EnumerationTypeDescriptor#booleanObject() booleans}
 * indicate whether their corresponding bounds are inclusive ({@code true})
 * or exclusive ({@code false}).
 */
public class P_007_CreateIntegerRange extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_007_CreateIntegerRange().init(
		4, CanFold, CannotFail);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 4;
		final AvailObject min = args.get(0);
		final AvailObject minInc = args.get(1);
		final AvailObject max = args.get(2);
		final AvailObject maxInc = args.get(3);
		return interpreter.primitiveSuccess(
			IntegerRangeTypeDescriptor.create(
				min,
				minInc.extractBoolean(),
				max,
				maxInc.extractBoolean()));
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.extendedIntegers(),
				EnumerationTypeDescriptor.booleanObject(),
				IntegerRangeTypeDescriptor.extendedIntegers(),
				EnumerationTypeDescriptor.booleanObject()),
			IntegerRangeTypeDescriptor.meta());
	}
}