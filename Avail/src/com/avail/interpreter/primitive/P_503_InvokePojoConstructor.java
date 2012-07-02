/**
 * P_503_InvokePojoConstructor.java
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.interpreter.Primitive.Flag.Private;
import java.lang.reflect.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 503:</strong> Given a {@linkplain RawPojoDescriptor raw
 * pojo} that references a reflected {@linkplain Constructor Java
 * constructor}, a {@linkplain TupleDescriptor tuple} of arguments, a
 * tuple of raw pojos that reference the reflected {@linkplain Class Java
 * classes} of the marshaled arguments, and an expected {@linkplain
 * TypeDescriptor type} of the new instance, invoke the constructor and
 * answer the new instance. If the constructor fails, then store the actual
 * Java {@linkplain Throwable exception} into the primitive failure
 * {@linkplain VariableDescriptor variable}.
 */
public class P_503_InvokePojoConstructor extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_503_InvokePojoConstructor().init(
		4, Private);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 4;
		final AvailObject constructorPojo = args.get(0);
		final AvailObject constructorArgs = args.get(1);
		final AvailObject marshaledTypePojos = args.get(2);
		final AvailObject expectedType = args.get(3);
		final Constructor<?> constructor =
			(Constructor<?>) constructorPojo.javaObject();
		assert constructor != null;
		final Object[] marshaledArgs =
			new Object[constructorArgs.tupleSize()];
		// Marshal the arguments.
		try
		{
			for (int i = 0; i < marshaledArgs.length; i++)
			{
				final Class<?> marshaledType = (Class<?>)
					marshaledTypePojos.tupleAt(i + 1).javaObject();
				marshaledArgs[i] = constructorArgs.tupleAt(
					i + 1).marshalToJava(marshaledType);
			}
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(
				PojoDescriptor.newPojo(
					RawPojoDescriptor.identityWrap(e),
					PojoTypeDescriptor.forClass(e.getClass())));
		}
		// Invoke the constructor.
		final Object newObject;
		try
		{
			newObject = constructor.newInstance(marshaledArgs);
		}
		catch (final InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			return interpreter.primitiveFailure(
				PojoDescriptor.newPojo(
					RawPojoDescriptor.identityWrap(cause),
					PojoTypeDescriptor.forClass(cause.getClass())));
		}
		catch (final Throwable e)
		{
			// This is an unexpected failure.
			error("reflected constructor call unexpectedly failed");
			throw new Error();
		}
		assert newObject != null;
		final AvailObject newPojo = PojoDescriptor.newPojo(
			RawPojoDescriptor.identityWrap(newObject),
			expectedType);
		return interpreter.primitiveSuccess(newPojo);
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				RAW_POJO.o(),
				TupleTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					RAW_POJO.o()),
				InstanceMetaDescriptor.on(
					PojoTypeDescriptor.mostGeneralType())),
			PojoTypeDescriptor.mostGeneralType());
	}

	@Override
	protected @NotNull AvailObject privateFailureVariableType ()
	{
		return PojoTypeDescriptor.forClass(Throwable.class);
	}
}