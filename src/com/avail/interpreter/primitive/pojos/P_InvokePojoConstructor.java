/*
 * P_InvokePojoConstructor.java
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
package com.avail.interpreter.primitive.pojos;

import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.MutableOrNull;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PojoDescriptor.newPojo;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.interpreter.Primitive.Flag.Private;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.marshalValues;

/**
 * <strong>Primitive:</strong> Given a {@linkplain RawPojoDescriptor raw
 * pojo} that references a reflected {@linkplain Constructor Java
 * constructor}, a {@linkplain TupleDescriptor tuple} of arguments, a
 * tuple of raw pojos that reference the reflected {@linkplain Class Java
 * classes} of the marshaled arguments, and an expected {@linkplain
 * TypeDescriptor type} of the new instance, invoke the constructor and
 * answer the new instance. If the constructor fails, then store the actual
 * Java {@linkplain Throwable exception} into the primitive failure
 * {@linkplain VariableDescriptor variable}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_InvokePojoConstructor
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_InvokePojoConstructor().init(
			4, Private);

	@SuppressWarnings("Duplicates")
	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final A_BasicObject constructorPojo = interpreter.argument(0);
		final A_Tuple constructorArgs = interpreter.argument(1);
		final A_Tuple marshaledTypes = interpreter.argument(2);
		final A_Type expectedType = interpreter.argument(3);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}

		final Constructor<?> constructor = constructorPojo.javaObjectNotNull();
		final MutableOrNull<AvailErrorCode> errorOut = new MutableOrNull<>();
		final @Nullable Object[] marshaledArgs = marshalValues(
			marshaledTypes,
			constructorArgs,
			errorOut);
		if (errorOut.value != null)
		{
			final AvailErrorCode e = errorOut.value;
			return interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.getClass())));
		}

		// Invoke the constructor.
		final Object result;
		try
		{
			result = constructor.newInstance(marshaledArgs);
		}
		catch (final InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			return interpreter.primitiveFailure(newPojo(
				identityPojo(cause), pojoTypeForClass(cause.getClass())));
		}
		catch (final Throwable e)
		{
			// This is an unexpected failure.
			error("reflected constructor call unexpectedly failed");
			throw new Error();
		}
		try
		{
			final AvailObject unmarshaled = unmarshal(result, expectedType);
			return interpreter.primitiveSuccess(unmarshaled);
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(
				newPojo(
					identityPojo(e),
					pojoTypeForClass(e.getClass())));
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tuple(
					RAW_POJO.o(),
					mostGeneralTupleType(),
					zeroOrMoreOf(RAW_POJO.o()),
					anyMeta()),
				mostGeneralPojoType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return pojoTypeForClass(Throwable.class);
	}
}
