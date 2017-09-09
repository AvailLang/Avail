/**
 * P_CreatePojoStaticMethodFunction.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode
	.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type}, {@linkplain StringDescriptor method name},
 * and {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
 * types}, create a {@linkplain FunctionDescriptor function} that when
 * applied with arguments will invoke the reflected Java static {@linkplain
 * Method method}. The last argument is a function that should be invoked with a
 * pojo-wrapped {@link Exception} in the event that Java raises an exception.
 */
public final class P_CreatePojoStaticMethodFunction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CreatePojoStaticMethodFunction().init(
			4, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		final AvailObject pojoType = args.get(0);
		final A_String methodName = args.get(1);
		final A_Tuple paramTypes = args.get(2);
		final AvailObject failFunction = args.get(3);
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
			final Class<?> javaClass =
				(Class<?>) pojoType.javaClass().javaObjectNotNull();
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
				final Class<?> javaClass =
					(Class<?>) ancestor.javaObjectNotNull();
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
		// Wrap each of the marshaled argument types into raw pojos. These
		// will be embedded into one of the generated functions below.
		final List<AvailObject> marshaledTypePojos =
			new ArrayList<>(marshaledTypes.length);
		for (final Class<?> paramClass : marshaledTypes)
		{
			marshaledTypePojos.add(equalityPojo(paramClass));
		}
		final A_Tuple marshaledTypesTuple =
			tupleFromList(marshaledTypePojos);
		// Create a function wrapper for the pojo method invocation
		// primitive. This function will be embedded as a literal into
		// an outer function that holds the (unexposed) method pojo.
		L1InstructionWriter writer = new L1InstructionWriter(
			nil(), 0, nil());
		writer.primitive(P_InvokeStaticPojoMethod.instance);
		writer.argumentTypes(
			RAW_POJO.o(),
			mostGeneralTupleType(),
			zeroOrMoreOf(RAW_POJO.o()),
			topMeta());
		writer.returnType(TOP.o());
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(failFunction));
		writer.write(
			L1Operation.L1_doGetLocal,
			writer.createLocal(
				variableTypeFor(pojoTypeForClass(Throwable.class))));
		writer.write(L1Operation.L1_doMakeTuple, 1);
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.APPLY.bundle),
			writer.addLiteral(bottom()));
		final A_Function innerFunction = createFunction(
			writer.compiledCode(), emptyTuple()).makeImmutable();
		// Create the outer function that pushes the arguments expected by
		// the method invocation primitive. Various objects that we do
		// not want to expose to the Avail program are embedded in this
		// function as literals.
		writer = new L1InstructionWriter(
			nil(), 0, nil());
		writer.argumentTypesTuple(paramTypes);
		final A_Type returnType = resolvePojoType(
			method.getGenericReturnType(), pojoType.typeVariables());
		writer.returnType(returnType);
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(innerFunction));
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(equalityPojo(method)));
		final int limit = paramTypes.tupleSize();
		for (int i = 1; i <= limit; i++)
		{
			writer.write(L1Operation.L1_doPushLocal, i);
		}
		writer.write(L1Operation.L1_doMakeTuple, paramTypes.tupleSize());
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(marshaledTypesTuple));
		writer.write(L1Operation.L1_doPushLiteral, writer.addLiteral(pojoType));
		writer.write(L1Operation.L1_doMakeTuple, 4);
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.APPLY.bundle),
			writer.addLiteral(returnType));
		final A_Function outerFunction =
			createFunction(writer.compiledCode(), emptyTuple()).makeImmutable();
		// TODO: [TLS] When functions can be made non-reflective, then make
		// both these functions non-reflective for safety.
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
