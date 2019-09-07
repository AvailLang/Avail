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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_String;
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
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import static com.avail.descriptor.FunctionDescriptor.createWithOuters2;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.enumerationWith;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PojoTypeDescriptor.marshalTypes;
import static com.avail.descriptor.PojoTypeDescriptor.resolvePojoType;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.lookupMethod;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType;
import static com.avail.utility.Casts.cast;
import static java.util.Collections.synchronizedMap;

/**
 * <strong>Primitive:</strong> Given a {@linkplain A_Type type} that can be
 * successfully marshaled to a Java type, a {@linkplain A_String string} that
 * names a {@code static} {@linkplain Method method} of that type, and a
 * {@linkplain A_Tuple tuple} of parameter {@linkplain A_Type types}, create a
 * {@linkplain A_Function function} that when applied will invoke the {@code
 * static} method. The {@code static} method is invoked with arguments
 * conforming to the marshaling of the parameter types. If the return value has
 * a preferred Avail surrogate type, then marshal the value to the surrogate
 * type prior to answering it.
 *
 * <p>Should the Java method raise an exception, re-raise it within Avail as a
 * pojo exception.</p>
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
			3, CanInline, CanFold);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final AvailObject pojoType = interpreter.argument(0);
		final A_String methodName = interpreter.argument(1);
		final A_Tuple paramTypes = interpreter.argument(2);

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
			marshaledTypesTuple =
				generateObjectTupleFrom(
					marshaledTypes.length,
					i -> equalityPojo(marshaledTypes[i - 1]));
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		final A_Type returnType = resolvePojoType(
			method.getGenericReturnType(),
			pojoType.isPojoType() ? pojoType.typeVariables() : emptyMap());
		final A_Type functionType = functionType(paramTypes, returnType);

		final A_RawFunction rawFunction = rawFunctionCache.computeIfAbsent(
			functionType,
			fType -> rawPojoInvokerFunctionFromFunctionType(
				P_InvokeStaticPojoMethod.instance,
				fType,
				// Outer#1 = Static method to invoke.
				RAW_POJO.o(),
				// Outer#2 = Marshaled type parameters.
				zeroOrMoreOf(RAW_POJO.o())));
		final A_Function function =
			createWithOuters2(
				rawFunction,
				// Outer#1 = Static method to invoke.
				equalityPojo(method),
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
				stringType(),
				zeroOrMoreOf(anyMeta())),
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
