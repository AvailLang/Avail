/**
 * P_251_AbstractMethodDeclaration.java
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
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.utility.evaluation.*;

/**
 * <strong>Primitive 251:</strong> Declare method as {@linkplain
 * AbstractDefinitionDescriptor abstract}. This identifies responsibility for
 * definitions that want to be concrete.
 */
public final class P_251_AbstractMethodDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_251_AbstractMethodDeclaration().init(
			2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_String string = args.get(0);
		final A_Type blockSignature = args.get(1);
		final A_Fiber fiber = interpreter.fiber();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
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
							loader.addAbstractSignature(
								loader.lookupName(string),
								blockSignature);
							Interpreter.resumeFromSuccessfulPrimitive(
								AvailRuntime.current(),
								fiber,
								NilDescriptor.nil(),
								skipReturnCheck);
						}
						catch (
							final MalformedMessageException
								| SignatureException
								| AmbiguousNameException e)
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
				FunctionTypeDescriptor.meta()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
					E_LOADING_IS_OVER.numericCode(),
					E_AMBIGUOUS_NAME.numericCode(),
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES.numericCode(),
					E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS.numericCode(),
					E_CANNOT_MIX_METHOD_AND_MACRO_DEFINITIONS.numericCode(),
					E_METHOD_IS_SEALED.numericCode()))
				.setUnionCanDestroy(MessageSplitter.possibleErrors, true));
	}
}
