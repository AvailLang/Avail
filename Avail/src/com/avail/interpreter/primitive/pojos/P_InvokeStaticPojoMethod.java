/**
 * P_InvokeStaticPojoMethod.java
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
package com.avail.interpreter.primitive.pojos;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.Private;
import java.lang.reflect.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Given a {@linkplain RawPojoDescriptor raw
 * pojo} that references a reflected static {@linkplain Method Java method},
 * a {@linkplain TupleDescriptor tuple} of arguments, and a tuple of raw
 * pojos that reference the reflected {@linkplain Class Java classes} of the
 * marshaled arguments, invoke the method and answer the result. If the
 * method fails, then store the actual Java {@linkplain Throwable exception}
 * into the primitive failure {@linkplain VariableDescriptor variable}.
 */
public final class P_InvokeStaticPojoMethod extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_InvokeStaticPojoMethod().init(
			4, Private);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		final A_BasicObject methodPojo = args.get(0);
		final A_Tuple methodArgs = args.get(1);
		final A_Tuple marshaledTypePojos = args.get(2);
		final A_Type expectedType = args.get(3);
		// Marshal the arguments and invoke the method.
		final Method method = (Method) methodPojo.javaObject();
		assert method != null;
		final Object[] marshaledArgs = new Object[methodArgs.tupleSize()];
		try
		{
			for (int i = 0; i < marshaledArgs.length; i++)
			{
				final Class<?> marshaledType = (Class<?>)
					marshaledTypePojos.tupleAt(i + 1).javaObject();
				marshaledArgs[i] = methodArgs.tupleAt(
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
		final Object result;
		try
		{
			result = method.invoke(null, marshaledArgs);
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
			error("reflected method call unexpectedly failed");
			throw new Error();
		}
		if (result == null)
		{
			return interpreter.primitiveSuccess(
				PojoDescriptor.nullObject());
		}
		final AvailObject unmarshaled = PojoTypeDescriptor.unmarshal(
			result, expectedType);
		return interpreter.primitiveSuccess(unmarshaled);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				RAW_POJO.o(),
				TupleTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.zeroOrMoreOf(
					RAW_POJO.o()),
				InstanceMetaDescriptor.topMeta()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return PojoTypeDescriptor.forClass(Throwable.class);
	}
}