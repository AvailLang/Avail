/**
 * P_200_CatchException.java
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

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 200:</strong> Always fail. The Avail failure code
 * invokes the {@linkplain FunctionDescriptor body block}. A handler block is
 * only invoked when an exception is raised.
 */
public class P_200_CatchException extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_200_CatchException().init(
		3, CatchException, PreserveFailureVariable, PreserveArguments, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		@SuppressWarnings("unused")
		final A_BasicObject bodyBlock = args.get(0);
		final A_Tuple handlerBlocks = args.get(1);
		@SuppressWarnings("unused")
		final A_BasicObject optionalEnsureBlock = args.get(2);
		for (final A_BasicObject block : handlerBlocks)
		{
			if (!block.kind().argsTupleType().typeAtIndex(1).isSubtypeOf(
				ObjectTypeDescriptor.exceptionType()))
			{
				return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
			}
		}
		return interpreter.primitiveFailure(E_REQUIRED_FAILURE);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(),
					TOP.o()),
				TupleTypeDescriptor.zeroOrMoreOf(
					FunctionTypeDescriptor.create(
						TupleDescriptor.from(
							BottomTypeDescriptor.bottom()),
						TOP.o())),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(0),
						true,
						IntegerDescriptor.fromInt(1),
						true),
					TupleDescriptor.from(),
					FunctionTypeDescriptor.create(
						TupleDescriptor.from(),
						TOP.o()))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				IntegerDescriptor.zero(),
				E_INCORRECT_ARGUMENT_TYPE.numericCode(),
				E_HANDLER_SENTINEL.numericCode(),
				E_UNWIND_SENTINEL.numericCode()
			).asSet());
	}
}
