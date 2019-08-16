/*
 * P_InvokeStaticPojoMethod.java
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

import com.avail.AvailRuntime.HookType;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.MutableOrNull;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PojoDescriptor.newPojo;
import static com.avail.descriptor.PojoDescriptor.nullPojo;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.PojoTypeDescriptor.unmarshal;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.interpreter.Primitive.Flag.Private;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.marshalValues;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Invoke a static Java {@link Method}, passing
 * marshaled forms of this primitive's arguments.  Unmarshal the resulting
 * object as needed.
 *
 * <p>If an exception is thrown during evaluation, raise it as an Avail
 * exception via the {@link HookType#RAISE_JAVA_EXCEPTION_IN_AVAIL} hook.</p>
 *
 * <p>The current function was constructed via {@link
 * P_CreatePojoStaticMethodFunction}, and has two outer values: the Java {@link
 * Method} and the tuple of marshaled types.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_InvokeStaticPojoMethod
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_InvokeStaticPojoMethod().init(
			-1, Private);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		final A_Tuple methodArgs = tupleFromList(interpreter.argsBuffer);

		final A_Function primitiveFunction = stripNull(interpreter.function);
		final A_RawFunction primitiveRawFunction = primitiveFunction.code();
		assert primitiveRawFunction.primitive() == this;

		final A_BasicObject methodPojo = primitiveFunction.outerVarAt(1);
		final A_Tuple marshaledTypes = primitiveFunction.outerVarAt(2);
		// The exact return kind was captured in the function type.
		final A_Type expectedType =
			primitiveRawFunction.functionType().returnType();

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}

		// Marshal the arguments.
		final Method method = methodPojo.javaObjectNotNull();
		final MutableOrNull<AvailErrorCode> errorOut = new MutableOrNull<>();
		final @Nullable Object[] marshaledArgs = marshalValues(
			marshaledTypes, methodArgs, errorOut);
		if (errorOut.value != null)
		{
			final AvailErrorCode e = errorOut.value;
			return interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.getClass())));
		}

		// Invoke the static method.
		final Object result;
		try
		{
			result = method.invoke(null, marshaledArgs);
		}
		catch (final InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			return interpreter.primitiveFailure(newPojo(
				identityPojo(cause), pojoTypeForClass(cause.getClass())));
		}
		catch (final Throwable e)
		{
			// This is an unexpected failure in the invocation mechanism.  For
			// now, report it like an expected InvocationTargetException.
			return interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.getClass())));
		}

		if (result == null)
		{
			return interpreter.primitiveSuccess(nullPojo());
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
		// This primitive is suitable for any block signature, although really
		// the primitive could only be applied if the function returns any.
		return bottom();
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return pojoTypeForClass(Throwable.class);
	}
}
