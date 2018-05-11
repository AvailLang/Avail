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
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.PojoTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.APPLY;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoType;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.exceptions.AvailErrorCode.E_POJO_TYPE_IS_ABSTRACT;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelOne.L1Operation.*;

/**
 * <strong>Primitive:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type} and {@linkplain TupleDescriptor
 * tuple} of {@linkplain TypeDescriptor types}, create a {@linkplain
 * FunctionDescriptor function} that when applied will produce a new
 * instance of the pojo type by invoking a reflected Java {@linkplain
 * Constructor constructor} with arguments conforming to the specified
 * types. The last argument is a function that should be invoked with a
 * pojo-wrapped {@link Exception} in the event that Java raises an exception.
 */
public final class P_CreatePojoConstructorFunction extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePojoConstructorFunction().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Type pojoType = interpreter.argument(0);
		final A_Tuple paramTypes = interpreter.argument(1);
		final A_Function failFunction = interpreter.argument(2);
		// Do not attempt to bind a constructor to an abstract pojo type.
		if (pojoType.isAbstract())
		{
			return interpreter.primitiveFailure(E_POJO_TYPE_IS_ABSTRACT);
		}
		// Marshal the argument types and look up the appropriate
		// constructor.
		final Class<?> javaClass = pojoType.javaClass().javaObjectNotNull();
		final Class<?>[] marshaledTypes = new Class<?>[paramTypes.tupleSize()];
		try
		{
			for (int i = 0; i < marshaledTypes.length; i++)
			{
				marshaledTypes[i] =
					(Class<?>) paramTypes.tupleAt(i + 1).marshalToJava(null);
			}
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		final Constructor<?> constructor;
		try
		{
			constructor = javaClass.getConstructor(
				marshaledTypes);
		}
		catch (final Exception e)
		{
			return interpreter.primitiveFailure(E_JAVA_METHOD_NOT_AVAILABLE);
		}
		assert constructor != null;
		// Wrap each of the marshaled argument types into raw pojos. These
		// will be embedded into one of the generated functions below.
		final List<AvailObject> marshaledTypePojos =
			new ArrayList<>(marshaledTypes.length);
		for (final Class<?> paramClass : marshaledTypes)
		{
			marshaledTypePojos.add(
				equalityPojo(paramClass));
		}
		final A_Tuple marshaledTypesTuple = tupleFromList(marshaledTypePojos);
		// Create a function wrapper for the pojo constructor invocation
		// primitive. This function will be embedded as a literal into
		// an outer function that holds the (unexposed) constructor pojo.
		L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.primitive(P_InvokePojoConstructor.instance);
		writer.argumentTypes(
			RAW_POJO.o(),
			mostGeneralTupleType(),
			zeroOrMoreOf(RAW_POJO.o()),
			instanceMeta(mostGeneralPojoType()));
		writer.returnType(mostGeneralPojoType());
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
		final A_Function innerFunction =
			createFunction(writer.compiledCode(), emptyTuple()).makeImmutable();
		// Create the outer function that pushes the arguments expected by
		// the constructor invocation primitive. Various objects that we do
		// not want to expose to the Avail program are embedded in this
		// function as literals.
		writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.argumentTypesTuple(paramTypes);
		writer.returnType(pojoType);
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(innerFunction));
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(
				equalityPojo(constructor)));
		for (int i = 1; i <= paramTypes.tupleSize(); i++)
		{
			writer.write(0, L1_doPushLocal, i);
		}
		writer.write(0, L1_doMakeTuple, paramTypes.tupleSize());
		writer.write(
			0,
			L1_doPushLiteral,
			writer.addLiteral(marshaledTypesTuple));
		writer.write(0, L1_doPushLiteral, writer.addLiteral(pojoType));
		writer.write(0, L1_doMakeTuple, 4);
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
			writer.addLiteral(pojoType));
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
				zeroOrMoreOf(anyMeta()),
				functionType(
					tuple(pojoTypeForClass(Throwable.class)),
					bottom())),
			// TODO: [TLS] Answer a function type that answers pojo and
			// can raise java.lang.Throwable.
			functionTypeReturning(mostGeneralPojoType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_POJO_TYPE_IS_ABSTRACT,
				E_JAVA_METHOD_NOT_AVAILABLE));
	}
}
