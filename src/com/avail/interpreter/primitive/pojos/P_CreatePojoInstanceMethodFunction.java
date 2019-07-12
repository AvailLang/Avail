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
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.MutableOrNull;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.APPLY;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelOne.L1Operation.*;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.lookupMethod;
import static com.avail.interpreter.primitive.pojos.PrimitiveHelper.pojoInvocationWrapperFunction;

/**
 * <strong>Primitive:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type}, {@linkplain StringDescriptor method name},
 * and {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
 * types}, create a {@linkplain FunctionDescriptor function} that when
 * applied with a {@linkplain PojoDescriptor receiver} and arguments will
 * invoke the reflected Java instance {@linkplain Method method}. The last
 * argument is a function that should be invoked with a pojo-wrapped {@link
 * Exception} in the event that Java raises an exception.
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
		final Class<?>[] marshaledTypes;
		try
		{
			marshaledTypes = marshalTypes(paramTypes);
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		// Search for the method.
		final MutableOrNull<AvailErrorCode> errorCode = new MutableOrNull<>();
		final @Nullable Method method =
			lookupMethod(pojoType, methodName, marshaledTypes, errorCode);
		assert method != null;
		assert errorCode.value == null;
		// Wrap each of the marshaled argument types into raw pojos. These
		// will be embedded into one of the generated functions below.
		final A_Tuple marshaledTypesTuple =
			generateObjectTupleFrom(
				marshaledTypes.length,
				i -> equalityPojo(marshaledTypes[i - 1]));
		// Create a function wrapper for the pojo method invocation
		// primitive. This function will be embedded as a literal into
		// an outer function that holds the (unexposed) method pojo.
		final A_Function innerFunction = pojoInvocationWrapperFunction(
			failFunction,
			writer ->
			{
				writer.primitive(P_InvokeInstancePojoMethod.instance);
				writer.argumentTypes(
					RAW_POJO.o(),
					ANY.o(),
					mostGeneralTupleType(),
					zeroOrMoreOf(RAW_POJO.o()));
				writer.returnType(TOP.o());
			}
		);
		// Create the outer function that pushes the arguments expected by
		// the method invocation primitive. Various objects that we do
		// not want to expose to the Avail program are embedded in this
		// function as literals.
		final L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		final List<A_Type> allParamTypes =
			new ArrayList<>(paramTypes.tupleSize() + 1);
		allParamTypes.add(resolvePojoType(
			method.getDeclaringClass(), pojoType.typeVariables()));
		for (final AvailObject paramType : paramTypes)
		{
			allParamTypes.add(paramType);
		}
		writer.argumentTypesTuple(tupleFromList(allParamTypes));
		final A_Type returnType = resolvePojoType(
			method.getGenericReturnType(), pojoType.typeVariables());
		writer.returnType(returnType);
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(innerFunction));
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(equalityPojo(method)));
		for (int i = 1, limit = allParamTypes.size(); i <= limit; i++)
		{
			writer.write(0, L1_doPushLocal, i);
		}
		writer.write(
			0,
			L1_doMakeTuple,
			allParamTypes.size() - 1);
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(marshaledTypesTuple));
		writer.write(0, L1_doMakeTuple, 4);
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
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
