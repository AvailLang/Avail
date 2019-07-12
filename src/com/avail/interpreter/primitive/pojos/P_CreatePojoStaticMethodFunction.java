/*
 * P_CreatePojoStaticMethodFunction.java
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
import java.lang.reflect.Method;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.*;

/**
 * <strong>Primitive:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type}, {@linkplain StringDescriptor method name},
 * and {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
 * types}, create a {@linkplain FunctionDescriptor function} that when
 * applied with arguments will invoke the reflected Java static {@linkplain
 * Method method}. The last argument is a function that should be invoked with a
 * pojo-wrapped {@link Exception} in the event that Java raises an exception.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("unused")
public final class P_CreatePojoStaticMethodFunction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePojoStaticMethodFunction().init(
			4, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final AvailObject pojoType = interpreter.argument(0);
		final A_String methodName = interpreter.argument(1);
		final A_Tuple paramTypes = interpreter.argument(2);
		final AvailObject failFunction = interpreter.argument(3);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}

		// Marshal the argument types.
		final @Nullable Method method;
		final A_Tuple marshaledTypesTuple;
		try
		{
			final Class<?>[] marshaledTypes = marshalTypes(paramTypes);
			final MutableOrNull<AvailErrorCode> errorOut =
				new MutableOrNull<>();
			method = lookupMethod(
				pojoType, methodName, marshaledTypes, errorOut);
			if (errorOut.value != null)
			{
				return interpreter.primitiveFailure(errorOut.value);
			}
			marshaledTypesTuple =
				generateObjectTupleFrom(
					marshaledTypes.length,
					i -> equalityPojo(marshaledTypes[i - 1]));
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		assert method != null;
		final A_Function innerFunction = pojoInvocationWrapperFunction(
			failFunction,
			writer ->
			{
				writer.primitive(P_InvokeStaticPojoMethod.instance);
				writer.argumentTypes(
					RAW_POJO.o(),
					mostGeneralTupleType(),
					zeroOrMoreOf(RAW_POJO.o()),
					topMeta());
				writer.returnType(TOP.o());
			}
		);
		final A_Function outerFunction = pojoInvocationAdapterFunction(
			method,
			paramTypes,
			marshaledTypesTuple,
			resolvePojoType(
				method.getGenericReturnType(),
				pojoType.typeVariables()),
			innerFunction);
		return interpreter.primitiveSuccess(outerFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				instanceMeta(mostGeneralPojoType()),
				stringType(),
				zeroOrMoreOf(anyMeta()),
				functionType(
					tuple(pojoTypeForClass(Throwable.class)),
					bottom())),
			functionTypeReturning(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_JAVA_METHOD_NOT_AVAILABLE,
			E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS));
	}
}
