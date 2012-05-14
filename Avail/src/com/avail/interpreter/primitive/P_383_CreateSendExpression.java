/**
 * P_383_CreateSendExpression.java
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * <strong>Primitive 383</strong>: Create a {@linkplain SendNodeDescriptor send
 * expression} from the specified {@linkplain MethodDescriptor method},
 * {@linkplain TupleDescriptor tuple} of {@linkplain
 * ParseNodeKind#EXPRESSION_NODE argument expressions}. The return type is
 * determined by running all appropriate semantic restrictions.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class P_383_CreateSendExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final @NotNull static Primitive instance =
		new P_383_CreateSendExpression().init(2, CanFold);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject method = args.get(0);
		final AvailObject sendArgs = args.get(1);
		try
		{
			final MessageSplitter splitter =
				new MessageSplitter(method.name().name());
			if (splitter.numberOfArguments() != sendArgs.tupleSize())
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS);
			}
		}
		catch (final SignatureException e)
		{
			assert false : "The method name was extracted from a real method!";
		}
		final List<AvailObject> argTypes = new ArrayList<AvailObject>(
			sendArgs.tupleSize());
		for (final AvailObject sendArg : sendArgs)
		{
			argTypes.add(sendArg.expressionType());
		}
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final AvailObject returnType =
			method.validateArgumentTypesInterpreterIfFail(
				argTypes,
				new L2Interpreter(AvailRuntime.current()),
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (
						final @NotNull Generator<String> errorGenerator)
					{
						valid.value = false;
					}
				});
		if (!valid.value)
		{
			return interpreter.primitiveFailure(E_INVALID_ARGUMENTS_FOR_SEND);
		}
		return interpreter.primitiveSuccess(
			SendNodeDescriptor.from(method, sendArgs, returnType));
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
					EXPRESSION_NODE.create(ANY.o()))),
			SEND_NODE.mostGeneralType());
	}
}
