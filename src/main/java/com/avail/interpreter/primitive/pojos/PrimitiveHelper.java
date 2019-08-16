/*
 * PrimitiveHelper.java
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
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.utility.MutableOrNull;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static com.avail.AvailRuntime.HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.APPLY;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PojoTypeDescriptor.marshalDefiningType;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelOne.L1Operation.*;

/**
 * {@code PrimitiveHelper} aggregates utility functions for reuse by the various
 * pojo subsystem {@linkplain Primitive primitives}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PrimitiveHelper
{
	/** Prevent instantiation. */
	private PrimitiveHelper () { }

	/**
	 * Search for the requested Java {@link Method}.
	 *
	 * @param pojoType
	 *        The pojo type (an {@link A_Type}) where the method is defined. The
	 *        method may also be in a superclass of this class.
	 * @param methodName
	 *        The name of the method.
	 * @param marshaledTypes
	 *        The array of {@link Class}es for the arguments, used to
	 *        disambiguate overloaded methods.
	 * @param errorOut
	 *        A {@link MutableOrNull} into which an {@link AvailErrorCode} can
	 *        be written in the event that a unique {@link Method} is not found.
	 * @return Either the successfully looked up {@link Method} or {@code null}.
	 *         Note that either the return is non-null or the errorOut will
	 *         have a non-null value written to it.
	 */
	static @Nullable Method lookupMethod (
		final A_Type pojoType,
		final A_String methodName,
		final Class<?>[] marshaledTypes,
		final MutableOrNull<AvailErrorCode> errorOut)
	{
		// If pojoType is not a fused type, then it has an immediate class that
		// should be used to recursively look up the method.
		if (!pojoType.isPojoType() || !pojoType.isPojoFusedType())
		{
			final Class<?> javaClass = marshalDefiningType(pojoType);
			try
			{
				return javaClass.getMethod(
					methodName.asNativeString(), marshaledTypes);
			}
			catch (final NoSuchMethodException e)
			{
				errorOut.value = E_JAVA_METHOD_NOT_AVAILABLE;
				return null;
			}
		}
		// If pojoType is a fused type, then iterate through its ancestry in an
		// attempt to uniquely resolve the method.
		else
		{
			final Set<Method> methods = new HashSet<>();
			final A_Map ancestors = pojoType.javaAncestors();
			for (final A_Type ancestor : ancestors.keysAsSet())
			{
				final Class<?> javaClass = marshalDefiningType(ancestor);
				try
				{
					methods.add(javaClass.getMethod(
						methodName.asNativeString(), marshaledTypes));
				}
				catch (final NoSuchMethodException e)
				{
					// Ignore -- this is not unexpected.
				}
			}
			if (methods.isEmpty())
			{
				errorOut.value = E_JAVA_METHOD_NOT_AVAILABLE;
				return null;
			}
			if (methods.size() > 1)
			{
				errorOut.value = E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
				return null;
			}
			return methods.iterator().next();
		}
	}

	/**
	 * Search for the requested Java {@link Field}.
	 *
	 * @param pojoType
	 *        The pojo type (an {@link A_Type}) where the field is defined. The
	 *        field may also be in a superclass of this class.
	 * @param fieldName
	 *        The name of the field.
	 * @param errorOut
	 *        A {@link MutableOrNull} into which an {@link AvailErrorCode} can
	 *        be written in the event that a unique {@link Field} is not found.
	 * @return Either the successfully looked up {@link Field} or {@code null}.
	 *         Note that either the return is non-null or the errorOut will
	 *         have a non-null value written to it.
	 */
	static @Nullable Field lookupField (
		final A_Type pojoType,
		final A_String fieldName,
		final MutableOrNull<AvailErrorCode> errorOut)
	{
		// If pojoType is not a fused type, then it has an immediate class
		// that should be used to recursively look up the field.
		if (!pojoType.isPojoType() || !pojoType.isPojoFusedType())
		{
			final Class<?> javaClass = marshalDefiningType(pojoType);
			try
			{
				return javaClass.getField(fieldName.asNativeString());
			}
			catch (final NoSuchFieldException e)
			{
				errorOut.value = E_JAVA_FIELD_NOT_AVAILABLE;
				return null;
			}
		}
		// If pojoType is a fused type, then iterate through its ancestry in
		// an attempt to uniquely resolve the field.
		else
		{
			final Set<Field> fields = new HashSet<>();
			final A_Map ancestors = pojoType.javaAncestors();
			for (final A_Type ancestor : ancestors.keysAsSet())
			{
				final Class<?> javaClass = marshalDefiningType(ancestor);
				try
				{
					fields.add(javaClass.getField(
						fieldName.asNativeString()));
				}
				catch (final NoSuchFieldException e)
				{
					// Ignore -- this is not unexpected.
				}
			}
			if (fields.isEmpty())
			{
				errorOut.value = E_JAVA_FIELD_NOT_AVAILABLE;
				return null;
			}
			if (fields.size() > 1)
			{
				errorOut.value = E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS;
				return null;
			}
			return fields.iterator().next();
		}
	}

	/**
	 * Synthesize an {@link A_RawFunction}.  It should have the given function
	 * type, and expect to be instantiated as an {@link A_Function} with the
	 * given types of outers.  It should also be {@link Primitive}, with failure
	 * code to invoke the {@link HookType#RAISE_JAVA_EXCEPTION_IN_AVAIL} if a
	 * Java {@link Throwable} is caught and made available in the failure
	 * variable.
	 *
	 * @param primitive
	 *        The {@link Primitive} to invoke.
	 * @param functionType
	 *        The {@link A_Type} of the {@link A_Function}s that will be created
	 *        from the {@link A_RawFunction} that is produced here.
	 * @param outerTypes
	 *        The {@link A_Type}s of the outers that will be supplied later to
	 *        the raw function to make an {@link A_Function}.
	 * @return An {@link A_RawFunction} with the exact given signature.
	 */
	public static A_RawFunction rawPojoInvokerFunctionFromFunctionType (
		final Primitive primitive,
		final A_Type functionType,
		final A_Type... outerTypes)
	{
		final A_Type argTypes = functionType.argsTupleType();
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final A_Type[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final A_Type returnType = functionType.returnType();
		final L1InstructionWriter writer = new L1InstructionWriter(nil, 0, nil);
		writer.primitive(primitive);
		writer.argumentTypes(argTypesArray);
		writer.returnType(returnType);
		// Produce failure code.  First declare the local that holds primitive
		// failure information.
		final int failureLocal = writer.createLocal(
			variableTypeFor(pojoTypeForClass(Throwable.class)));
		assert failureLocal == numArgs + 1;
		for (final A_Type outerType : outerTypes)
		{
			writer.createOuter(outerType);
		}
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(
				SpecialMethodAtom.GET_RETHROW_JAVA_EXCEPTION.bundle),
			writer.addLiteral(RAISE_JAVA_EXCEPTION_IN_AVAIL.functionType));
		writer.write(0, L1_doPushLocal, failureLocal);
		writer.write(0, L1_doMakeTuple, 1);
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
			writer.addLiteral(bottom())
		);
		// TODO: [TLS] When functions can be made non-reflective, then make
		// this raw function non-reflective for safety.
		return writer.compiledCode();
	}

	/**
	 * Marshal the specified values using the provided {@linkplain A_Type
	 * marshaling types}.
	 *
	 * @param marshaledTypes
	 *        The marshaled types.
	 * @param args
	 *        The values to marshal using the corresponding types.
	 * @param errorOut
	 *        A {@link MutableOrNull} into which an {@link AvailErrorCode} can
	 *        be written in the event that marshaling fails for some value.
	 * @return The marshaled values.
	 */
	static @Nullable Object[] marshalValues (
		final A_Tuple marshaledTypes,
		final A_Tuple args,
		final MutableOrNull<AvailErrorCode> errorOut)
	{
		assert marshaledTypes.tupleSize() == args.tupleSize();
		final Object[] marshaled = new Object[args.tupleSize()];
		// Marshal the arguments.
		try
		{
			for (int i = 0, limit = marshaled.length; i < limit; i++)
			{
				final Class<?> marshaledType =
					marshaledTypes.tupleAt(i + 1).javaObjectNotNull();
				marshaled[i] =
					args.tupleAt(i + 1).marshalToJava(marshaledType);
			}
		}
		catch (final MarshalingException e)
		{
			errorOut.value = E_JAVA_MARSHALING_FAILED;
			return null;
		}
		return marshaled;
	}
}
