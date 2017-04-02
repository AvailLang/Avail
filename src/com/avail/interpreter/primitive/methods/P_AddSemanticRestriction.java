/**
 * P_AddSemanticRestriction.java
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
package com.avail.interpreter.primitive.methods;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.Unknown;

import java.util.List;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.interpreter.AvailLoader.Phase;

/**
 * <strong>Primitive:</strong> Add a type restriction function.
 */
public final class P_AddSemanticRestriction
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_AddSemanticRestriction().init(
			2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_String string = args.get(0);
		final A_Function function = args.get(1);
		final A_Type functionType = function.kind();
		final A_Type tupleType = functionType.argsTupleType();
		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (loader.phase() != Phase.EXECUTING)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
		}
		for (int i = function.code().numArgs(); i >= 1; i--)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				InstanceMetaDescriptor.on(InstanceMetaDescriptor.topMeta())))
			{
				return interpreter.primitiveFailure(
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES);
			}
		}
		try
		{
			final A_Atom atom = loader.lookupName(string);
			final A_Method method = atom.bundleOrCreate().bundleMethod();
			final A_SemanticRestriction restriction =
				SemanticRestrictionDescriptor.create(
					function,
					method,
					interpreter.module());
			loader.addSemanticRestriction(restriction);
		}
		catch (
			final MalformedMessageException
				| AmbiguousNameException
				| SignatureException e)
		{
			return interpreter.primitiveFailure(e);
		}
		function.code().setMethodName(
			StringDescriptor.format("Semantic restriction of %s", string));
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				FunctionTypeDescriptor.forReturnType(
					InstanceMetaDescriptor.topMeta())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_AMBIGUOUS_NAME,
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES,
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
				.setUnionCanDestroy(MessageSplitter.possibleErrors, true));
	}
}
