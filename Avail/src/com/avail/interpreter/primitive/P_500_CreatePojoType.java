/**
 * P_500_CreatePojoType.java
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import java.lang.reflect.TypeVariable;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 500:</strong> Create a {@linkplain
 * PojoTypeDescriptor pojo type} for the specified {@linkplain Class
 * Java class}, specified by fully-qualified name, and type parameters.
 */
public class P_500_CreatePojoType extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_500_CreatePojoType().init(
		2, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final A_String className = args.get(0);
		final A_Tuple classParameters = args.get(1);
		// Forbid access to the Avail implementation's packages.
		final String nativeClassName = className.asNativeString();
		if (nativeClassName.startsWith("com.avail"))
		{
			return interpreter.primitiveFailure(E_JAVA_CLASS_NOT_AVAILABLE);
		}
		// Look up the raw Java class using the interpreter's runtime's
		// class loader.
		final Class<?> rawClass;
		try
		{
			rawClass = Class.forName(
				className.asNativeString(),
				true,
				AvailRuntime.current().classLoader());
		}
		catch (final ClassNotFoundException e)
		{
			return interpreter.primitiveFailure(E_JAVA_CLASS_NOT_AVAILABLE);
		}
		// Check that the correct number of type parameters have been
		// supplied. Don't bother to check the bounds of the type
		// parameters. Incorrect bounds will cause some method and
		// constructor lookups to fail, but that's fine.
		final TypeVariable<?>[] typeVars = rawClass.getTypeParameters();
		if (typeVars.length != classParameters.tupleSize())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// Replace all occurrences of the pojo self type atom with actual
		// pojo self types.
		A_Tuple realParameters = classParameters.copyAsMutableObjectTuple();
		for (int i = 1; i <= classParameters.tupleSize(); i++)
		{
			final A_BasicObject originalParameter = classParameters.tupleAt(i);
			final AvailObject realParameter;
			if (originalParameter.equals(PojoTypeDescriptor.selfType()))
			{
				realParameter = PojoTypeDescriptor.selfTypeForClass(rawClass);
			}
			else
			{
				realParameter = originalParameter.makeImmutable();
			}
			realParameters = realParameters.tupleAtPuttingCanDestroy(
				i, realParameter, true);
		}
		// Construct and answer the pojo type.
		final A_Type newPojoType =
			PojoTypeDescriptor.forClassWithTypeArguments(
				rawClass, realParameters);
		return interpreter.primitiveSuccess(newPojoType);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringTupleType(),
				TupleTypeDescriptor.zeroOrMoreOf(
					InstanceMetaDescriptor.anyMeta())),
			InstanceMetaDescriptor.on(
				PojoTypeDescriptor.mostGeneralType()));
	}
}
