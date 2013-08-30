/**
 * P_249_SimpleMacroDeclaration.java
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.PARSE_NODE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.*;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.utility.Continuation0;

/**
 * <strong>Primitive 249:</strong> Simple macro definition.  The first argument
 * is the macro name, and the second argument is a {@linkplain TupleDescriptor
 * tuple} of {@linkplain FunctionDescriptor functions} returning ⊤, one for each
 * occurrence of a {@linkplain StringDescriptor#sectionSign() section sign} (§)
 * in the macro name.  The third argument is the function to invoke for the
 * complete macro.  It is constrained to answer a {@linkplain
 * ParseNodeDescriptor parse node}.
 */
public final class P_249_SimpleMacroDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_249_SimpleMacroDeclaration().init(3, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_String string = args.get(0);
		final A_Tuple prefixFunctions = args.get(1);
		final A_Function function = args.get(2);
		final A_Fiber fiber = interpreter.fiber();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		for (final A_Function prefixFunction : prefixFunctions)
		{
			final int numArgs = prefixFunction.code().numArgs();
			final A_Type kind = prefixFunction.kind();
			final A_Type argsKind = kind.argsTupleType();
			for (int argIndex = 1; argIndex <= numArgs; argIndex++)
			{
				if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
					PARSE_NODE.mostGeneralType()))
				{
					return interpreter.primitiveFailure(
						E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE);
				}
			}
			if (!kind.returnType().equals(TOP.o()))
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP);
			}
		}
		final int numArgs = function.code().numArgs();
		final A_Type kind = function.kind();
		final A_Type argsKind = kind.argsTupleType();
		for (int argIndex = 1; argIndex <= numArgs; argIndex++)
		{
			if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
				PARSE_NODE.mostGeneralType()))
			{
				return interpreter.primitiveFailure(
					E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE);
			}
		}
		if (!kind.returnType().isSubtypeOf(PARSE_NODE.mostGeneralType()))
		{
			return interpreter.primitiveFailure(
				AvailErrorCode.E_MACRO_MUST_RETURN_A_PARSE_NODE);
		}
		final A_Function failureFunction =
			interpreter.primitiveFunctionBeingAttempted();
		final List<AvailObject> copiedArgs = new ArrayList<>(args);
		assert failureFunction.code().primitiveNumber() == primitiveNumber;
		interpreter.primitiveSuspend();
		AvailRuntime.current().whenLevelOneSafeDo(
			AvailTask.forUnboundFiber(
				fiber,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						try
						{
							loader.addMacroBody(
								loader.lookupName(string),
								prefixFunctions,
								function);
							int counter = 1;
							for (final A_Function prefixFunction
								: prefixFunctions)
							{
								prefixFunction.code().setMethodName(
									StringDescriptor.from(
										String.format(
											"Macro prefix #%d of %s",
											counter++,
											string)));
							}
							function.code().setMethodName(
								StringDescriptor.from(
									String.format("Macro body of %s", string)));
							function.code().setMethodName(
								StringDescriptor.from(
									String.format("Macro body of %s", string)));
							Interpreter.resumeFromSuccessfulPrimitive(
								AvailRuntime.current(),
								fiber,
								NilDescriptor.nil(),
								skipReturnCheck);
						}
						catch (
							final AmbiguousNameException|SignatureException e)
						{
							Interpreter.resumeFromFailedPrimitive(
								AvailRuntime.current(),
								fiber,
								e.numericCode(),
								failureFunction,
								copiedArgs,
								skipReturnCheck);
						}
					}
				}));
		return FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				TupleTypeDescriptor.zeroOrMoreOf(
					FunctionTypeDescriptor.mostGeneralType()),
				FunctionTypeDescriptor.forReturnType(
					PARSE_NODE.mostGeneralType())),
			TOP.o());
	}

//	@Override
//	protected A_Type privateFailureVariableType ()
//	{
//		return AbstractEnumerationTypeDescriptor.withInstances(
//			TupleDescriptor.from(
//				E_LOADING_IS_OVER.numericCode(),
//				E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE.numericCode(),
//				E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP.numericCode(),
//				E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE.numericCode(),
//				E_MACRO_MUST_RETURN_A_PARSE_NODE.numericCode()
//			).asSet());
//	}

}
