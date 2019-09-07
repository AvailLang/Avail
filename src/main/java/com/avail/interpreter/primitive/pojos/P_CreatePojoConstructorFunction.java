/*
 * P_CreatePojoConstructorFunction.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.RawPojoDescriptor;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionDescriptor.createWithOuters2;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PojoTypeDescriptor.marshalDefiningType;
import static com.avail.descriptor.PojoTypeDescriptor.marshalTypes;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_POJO_TYPE_IS_ABSTRACT;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType;
import static com.avail.utility.Casts.cast;
import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Primitive:</strong> Given a {@linkplain A_Type type} that can be
 * successfully marshaled to a Java type, and a {@linkplain A_Tuple tuple} of
 * parameter {@linkplain A_Type types}, create a {@linkplain A_Function
 * function} that when applied will produce a new instance of the defining Java
 * type. The instance is created by invoking a reflected Java {@linkplain
 * Constructor constructor} with arguments conforming to the marshaling of the
 * parameter types. If the new instance has a preferred Avail surrogate type,
 * then marshal the value to the surrogate type prior to answering it.
 *
 * <p>Should the Java method raise an exception, re-raise it within Avail as a
 * pojo exception.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreatePojoConstructorFunction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePojoConstructorFunction().init(
			2, CanInline, CanFold);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Type pojoType = interpreter.argument(0);
		final A_Tuple paramTypes = interpreter.argument(1);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}

		// Marshal the argument types and look up the appropriate
		// constructor.
		final A_Tuple marshaledTypesTuple;
		final Constructor<?> constructor;
		try
		{
			final Class<?> javaClass = marshalDefiningType(pojoType);
			if ((javaClass.getModifiers() & Modifier.ABSTRACT) != 0)
			{
				return interpreter.primitiveFailure(E_POJO_TYPE_IS_ABSTRACT);
			}

			final Class<?>[] marshaledTypes = marshalTypes(paramTypes);
			constructor = javaClass.getConstructor(marshaledTypes);
			marshaledTypesTuple = tupleFromList(
				Arrays.stream(marshaledTypes)
					.map(RawPojoDescriptor::equalityPojo)
					.collect(toList()));
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		catch (final Exception e)
		{
			return interpreter.primitiveFailure(E_JAVA_METHOD_NOT_AVAILABLE);
		}
		final A_Type functionType = functionType(paramTypes, pojoType);

		final A_RawFunction rawFunction = rawFunctionCache.computeIfAbsent(
			functionType,
			fType -> rawPojoInvokerFunctionFromFunctionType(
				P_InvokePojoConstructor.instance,
				fType,
				// Outer#1 = Constructor to invoke.
				RAW_POJO.o(),
				// Outer#2 = Marshaled type parameters.
				zeroOrMoreOf(RAW_POJO.o())));
		final A_Function function =
			createWithOuters2(
				rawFunction,
				// Outer#1 = Constructor to invoke.
				equalityPojo(constructor),
				// Outer#2 = Marshaled type parameters.
				cast(marshaledTypesTuple));
		return interpreter.primitiveSuccess(function);
	}

	/**
	 * Cache of {@link A_RawFunction}s, keyed by the function {@link A_Type}.
	 */
	private static final Map<A_Type, A_RawFunction> rawFunctionCache =
		synchronizedMap(new WeakHashMap<>());

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				anyMeta(),
				zeroOrMoreOf(anyMeta())),
			functionTypeReturning(ANY.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_POJO_TYPE_IS_ABSTRACT,
				E_JAVA_MARSHALING_FAILED,
				E_JAVA_METHOD_NOT_AVAILABLE));
	}
}
