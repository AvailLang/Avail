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

import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.APPLY;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelOne.L1Operation.*;

/**
 * {@code PrimitiveHelper} aggregates utility functions for reuse by the various
 * pojo subsystem {@linkplain Primitive primitives}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PrimitiveHelper
{
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
		if (!pojoType.isPojoFusedType())
		{
			final Class<?> javaClass = pojoType.javaClass().javaObjectNotNull();
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
			for (final A_BasicObject ancestor : ancestors.keysAsSet())
			{
				final Class<?> javaClass = ancestor.javaObjectNotNull();
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
	 * Create a {@linkplain A_Function function} wrapper for a pojo constructor
	 * or method invocation {@linkplain Primitive primitive}.
	 *
	 * @param failFunction
	 *        A function that should be applied to a pojo-wrapped {@link
	 *        Exception} in the event that Java raises an exception.
	 * @param interfaceHeader
	 *        A {@linkplain Continuation1 function} that accepts an {@link
	 *        L1InstructionWriter} and sets the {@linkplain
	 *        L1InstructionWriter#primitive(Primitive) primitive}, {@link
	 *        L1InstructionWriter#argumentTypes(A_Type...) argument types}, and
	 *        {@link L1InstructionWriter#returnType(A_Type) return type}.
	 * @return A {@linkplain A_Function function} wrapper for a pojo constructor
	 *         or method. This function will be embed various literals that we
	 *         do not want to expose to the Avail program.
	 */
	static A_Function pojoInvocationWrapperFunction (
		final A_Function failFunction,
		final Continuation1NotNull<L1InstructionWriter> interfaceHeader)
	{
		L1InstructionWriter writer = new L1InstructionWriter(nil, 0, nil);
		interfaceHeader.value(writer);
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(failFunction));
		writer.write(
			0,
			L1_doGetLocal,
			writer.createLocal(variableTypeFor(
				pojoTypeForClass(Throwable.class))));
		writer.write(0, L1_doMakeTuple, 1);
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
			writer.addLiteral(bottom()));
		// TODO: [TLS] When functions can be made non-reflective, then make
		// this function non-reflective for safety.
		return createFunction(
			writer.compiledCode(), emptyTuple()).makeImmutable();
	}

	/**
	 * Create a {@linkplain A_Function function} that adapts unmarshaled Avail
	 * types to the specified inner function.
	 *
	 * @param executable
	 *        The reflected Java {@link Executable}, either a {@link
	 *        Constructor} or a {@link Method}.
	 * @param paramTypes
	 *        The unmarshaled Avail types that correspond to the formal
	 *        parameters of the desired function.
	 * @param marshaledTypes
	 *        The marshaled Avail types that correspond to the formal parameters
	 *        of the {@link Executable}, needed for invocation of the inner
	 *        function.
	 * @param returnType
	 *        The unmarshaled Avail type that corresponds to the return type of
	 *        the {@link Executable}.
	 * @param innerFunction
	 *        A {@linkplain A_Function function} produced by {@link
	 *        #pojoInvocationWrapperFunction(A_Function, Continuation1NotNull)
	 *        pojoInvocationWrapperFunction}.
	 * @return A {@linkplain A_Function function} that pushes the arguments
	 *         expected by the inner function. This function will be embed
	 *         various literals that we do not want to expose to the Avail
	 *         program.
	 */
	static A_Function pojoInvocationAdapterFunction (
		final Executable executable,
		final A_Tuple paramTypes,
		final A_Tuple marshaledTypes,
		final A_Type returnType,
		final A_Function innerFunction)
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.argumentTypesTuple(paramTypes);
		writer.returnType(returnType);
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(innerFunction));
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(
				equalityPojo(executable)));
		for (int i = 1, limit = paramTypes.tupleSize(); i <= limit; i++)
		{
			writer.write(0, L1_doPushLocal, i);
		}
		writer.write(0, L1_doMakeTuple, paramTypes.tupleSize());
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(marshaledTypes));
		writer.write(0, L1_doPushLiteral, writer.addLiteral(returnType));
		writer.write(0, L1_doMakeTuple, 4);
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
			writer.addLiteral(returnType));
		// TODO: [TLS] When functions can be made non-reflective, then make
		// this function non-reflective for safety.
		return createFunction(
			writer.compiledCode(),
			emptyTuple()
		).makeImmutable();
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
				final @Nullable Class<?> marshaledType =
					marshaledTypes.tupleAt(i + 1).javaObject();
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
