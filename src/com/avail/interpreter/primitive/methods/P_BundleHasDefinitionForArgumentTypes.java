/*
 * P_MethodHasDefinitionForArgumentTypes.java
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

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Does the {@linkplain MethodDescriptor method}
 * have a unique definition for the specified {@linkplain TupleDescriptor
 * tuple} of parameter {@linkplain TypeDescriptor types}?
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_BundleHasDefinitionForArgumentTypes
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BundleHasDefinitionForArgumentTypes().init(
			2, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Atom methodName = interpreter.argument(0);
		final A_Tuple argTypes = interpreter.argument(1);
		final A_Bundle bundle = methodName.bundleOrNil();
		if (bundle.equalsNil())
		{
			return interpreter.primitiveFailure(E_NO_METHOD);
		}
		final A_Method method = bundle.bundleMethod();
		if (argTypes.tupleSize() != method.numArgs())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		try
		{
			final A_BasicObject definition =
				method.lookupByTypesFromTuple(argTypes);
			assert !definition.equalsNil();
			return interpreter.primitiveSuccess(trueObject());
		}
		catch (final MethodDefinitionException e)
		{
			return interpreter.primitiveSuccess(falseObject());
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				zeroOrMoreOf(anyMeta())),
			booleanType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_AMBIGUOUS_METHOD_DEFINITION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD,
				E_NO_METHOD_DEFINITION));
	}
}
