/*
 * P_SealMethodsAtExistingDefinitions.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode
	.E_CANNOT_DEFINE_DURING_COMPILATION;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Seal the {@linkplain A_Atom named} {@linkplain
 * A_Method} at each existing {@linkplain A_Definition definition}. Ignore
 * macros and forward definitions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SealMethodsAtExistingDefinitions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SealMethodsAtExistingDefinitions().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Set methodNames = interpreter.argument(0);
		final @Nullable AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (!loader.phase().isExecuting())
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
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
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(tuple(setTypeForSizesContentType(
				wholeNumbers(),
				ATOM.o())), TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION));
	}
}
