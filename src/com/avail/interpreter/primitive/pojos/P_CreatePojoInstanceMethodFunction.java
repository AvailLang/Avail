/*
 * P_CreatePojoInstanceMethodFunction.java
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
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.*;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.*;

/**
 * <strong>Primitive:</strong> Given a {@linkplain A_Type type} that can be
 * successfully marshaled to a Java type, a {@linkplain A_String string} that
 * names an instance {@linkplain Method method} of that type, a {@linkplain
 * A_Tuple tuple} of parameter {@linkplain A_Type types}, and a failure
 * {@linkplain A_Function function}, create a {@linkplain A_Function function}
 * that when applied will invoke the instance method. The instance method is
 * invoked with arguments conforming to the marshaling of the receiver type and
 * then the parameter types. If the return value has a preferred Avail surrogate
 * type, then marshal the value to the surrogate type prior to answering it.
 * Should the Java method raise an exception, invoke the supplied failure
 * function with a {@linkplain PojoDescriptor pojo} that wraps that exception.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreatePojoInstanceMethodFunction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePojoInstanceMethodFunction().init(
			4, CanInline, CanFold);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final A_Type pojoType = interpreter.argument(0);
		final A_String methodName = interpreter.argument(1);
		final A_Tuple paramTypes = interpreter.argument(2);
		final A_Function failFunction = interpreter.argument(3);

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
			if (method == null)
			{
				return interpreter.primitiveFailure(errorOut.value());
			}
			// The indices are fiddly because we need to make room for the
			// receiver type at the front.
			marshaledTypesTuple =
				generateObjectTupleFrom(
					marshaledTypes.length + 1,
					i -> i == 1
						? identityPojo(marshalDefiningType(pojoType))
						: equalityPojo(marshaledTypes[i - 2]));
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		final A_Function innerFunction = pojoInvocationWrapperFunction(
			failFunction,
			writer ->
			{
				writer.primitive(P_InvokeInstancePojoMethod.instance);
				writer.argumentTypes(
					RAW_POJO.o(),
					mostGeneralTupleType(),
					zeroOrMoreOf(RAW_POJO.o()),
					topMeta());
				writer.returnType(TOP.o());
			}
		);

		// Create a list that starts with the receiver and ends with the
		// arguments.
		final A_Map typeVars = pojoType.isPojoType()
			? pojoType.typeVariables()
			: emptyMap();
		final List<A_Type> allParamTypes =
			new ArrayList<>(paramTypes.tupleSize() + 1);
		allParamTypes.add(resolvePojoType(
			method.getDeclaringClass(), typeVars));
		for (final AvailObject paramType : paramTypes)
		{
			allParamTypes.add(paramType);
		}
		final A_Function outerFunction = pojoInvocationAdapterFunction(
			method,
			tupleFromList(allParamTypes),
			marshaledTypesTuple,
			resolvePojoType(method.getGenericReturnType(), typeVars),
			innerFunction);
		// TODO: [TLS] When functions can be made non-reflective, then make
		// both these functions non-reflective for safety.
		return interpreter.primitiveSuccess(outerFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				anyMeta(),
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
