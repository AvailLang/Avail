/**
 * P_268_SealMethodsAtExistingDefinitions.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 268</strong>: Seal the {@linkplan A_Atom named} {@linkplain
 * A_Method} at each existing {@linkplain A_Definition definition}. Ignore
 * macros and forward definitions.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
public final class P_268_SealMethodsAtExistingDefinitions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_268_SealMethodsAtExistingDefinitions().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Set methodNames = args.get(0);
		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final AvailRuntime runtime = interpreter.runtime();
		final A_Module module = interpreter.module();
		for (final A_Atom name : methodNames)
		{
			final A_Bundle bundle = name.bundleOrNil();
			if (!bundle.equalsNil())
			{
				// The definition tuple of a method can only be replaced in a
				// Level One safe zone. Like the vast majority of primitives,
				// this one runs in a Level One *unsafe* zone. Therefore it is
				// not necessary to lock the method while traversing its
				// definition tuple.
				final A_Method method = bundle.bundleMethod();
				final A_Tuple definitions = method.definitionsTuple();
				// Ignore macros.
				if (definitions.tupleSize() != 0
					&& !definitions.tupleAt(1).isMacroDefinition())
				{
					for (final A_Definition definition : definitions)
					{
						if (!definition.isForwardDefinition())
						{
							final A_Type function = definition.bodySignature();
							final A_Type params = function.argsTupleType();
							final A_Tuple signature =
								params.tupleOfTypesFromTo(1, method.numArgs());
							try
							{
								runtime.addSeal(name, signature);
								module.addSeal(name, signature);
							}
							catch (final MalformedMessageException e)
							{
								assert false : "This should not happen!";
								throw new AvailRuntimeException(e.errorCode());
							}
						}
					}
				}
			}
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ATOM.o())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_LOADING_IS_OVER.numericCode());
	}
}
