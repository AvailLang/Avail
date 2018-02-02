/*
 * P_PojoMethodReturnType.java
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
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.*;
import static com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoType;
import static com.avail.descriptor.PojoTypeDescriptor.resolvePojoType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type}, {@linkplain StringDescriptor method name},
 * and {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
 * types}, answer the fully parameterized type of the Java method's result.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_PojoMethodReturnType
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_PojoMethodReturnType().init(
			3, CanInline, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final A_Type pojoType = args.get(0);
		final A_String methodName = args.get(1);
		final A_Tuple paramTypes = args.get(2);
		// Marshal the argument types.
		final Class<?>[] marshaledTypes = new Class<?>[paramTypes.tupleSize()];
		try
		{
			for (int i = 0; i < marshaledTypes.length; i++)
			{
				marshaledTypes[i] = (Class<?>) paramTypes.tupleAt(
					i + 1).marshalToJava(null);
			}
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		// Search for the method.
		final Method method;
		// If pojoType is not a fused type, then it has an immediate class
		// that should be used to recursively look up the method.
		if (!pojoType.isPojoFusedType())
		{
			final Class<?> javaClass = pojoType.javaClass().javaObjectNotNull();
			try
			{
				method = javaClass.getMethod(
					methodName.asNativeString(), marshaledTypes);
			}
			catch (final NoSuchMethodException e)
			{
				return interpreter.primitiveFailure(
					E_JAVA_METHOD_NOT_AVAILABLE);
			}
		}
		// If pojoType is a fused type, then iterate through its ancestry in
		// an attempt to uniquely resolve the method.
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
				return interpreter.primitiveFailure(
					E_JAVA_METHOD_NOT_AVAILABLE);
			}
			if (methods.size() > 1)
			{
				return interpreter.primitiveFailure(
					E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS);
			}
			method = methods.iterator().next();
		}
		assert method != null;
		final A_Type returnType = resolvePojoType(
			method.getGenericReturnType(), pojoType.typeVariables());
		return interpreter.primitiveSuccess(returnType);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				instanceMeta(mostGeneralPojoType()),
				stringType(),
				zeroOrMoreOf(anyMeta())),
			topMeta());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_JAVA_METHOD_NOT_AVAILABLE,
			E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS));
	}
}
