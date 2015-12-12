/**
 * P_DeclarePrefixFunctionForAtom.java
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
package com.avail.interpreter.primitive.methods;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.PARSE_NODE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.avail.*;
import com.avail.compiler.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.utility.evaluation.*;

/**
 * <strong>Primitive:</strong> Declare a prefix function.  The first argument is
 * the atom indicating which message bundle is affected.  The second argument is
 * the 1-based prefix function index, which refers to the order of occurrences
 * of a {@linkplain StringDescriptor#sectionSign() section sign} (§) in the
 * bundle's name.  The third argument is the actual function to invoke when a
 * parse of a potential call site reaches a point corresponding to the section
 * sign.
 */
public final class P_DeclarePrefixFunctionForAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_DeclarePrefixFunctionForAtom().init(
			3, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Atom atom = args.get(0);
		final A_Number prefixFunctionInteger = args.get(1);
		final A_Function prefixFunction = args.get(2);

		final A_Fiber fiber = interpreter.fiber();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Bundle bundle;
		try
		{
			bundle = atom.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e.numericCode());
		}
		final MessageSplitter splitter = bundle.messageSplitter();
		final int numSectionCheckpoints = splitter.numberOfSectionCheckpoints();
		final int prefixFunctionIndex;
		if (!prefixFunctionInteger.isInt()
			|| (prefixFunctionIndex = prefixFunctionInteger.extractInt()) < 1
			|| prefixFunctionIndex > numSectionCheckpoints)
		{
			return interpreter.primitiveFailure(
				E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS);
		}
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
		if (!kind.returnType().isTop())
		{
			return interpreter.primitiveFailure(
				E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP);
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
							prefixFunction.code().setMethodName(
								StringDescriptor.format(
									"Macro prefix #%d of %s",
									prefixFunctionIndex,
									atom.atomName()));
							loader.addPrefixFunction(
								atom, prefixFunctionIndex, prefixFunction);
							Interpreter.resumeFromSuccessfulPrimitive(
								AvailRuntime.current(),
								fiber,
								NilDescriptor.nil(),
								skipReturnCheck);
						}
						catch (
							final MalformedMessageException e)
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
				Types.ATOM.o(),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				FunctionTypeDescriptor.mostGeneralType()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
					E_LOADING_IS_OVER.numericCode(),
					E_AMBIGUOUS_NAME.numericCode(),
					E_INCORRECT_NUMBER_OF_ARGUMENTS.numericCode(),
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES.numericCode(),
					E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE
						.numericCode(),
					E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP.numericCode()))
				.setUnionCanDestroy(MessageSplitter.possibleErrors, true));
	}
}