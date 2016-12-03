/**
 * P_CreatePojoConstructorFunction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.lang.reflect.Constructor;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;

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
	public final static Primitive instance =
		new P_CreatePojoConstructorFunction().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Type pojoType = args.get(0);
		final A_Tuple paramTypes = args.get(1);
		final A_Function failFunction = args.get(2);
		// Do not attempt to bind a constructor to an abstract pojo type.
		if (pojoType.isAbstract())
		{
			return interpreter.primitiveFailure(E_POJO_TYPE_IS_ABSTRACT);
		}
		// Marshal the argument types and look up the appropriate
		// constructor.
		final Class<?> javaClass = (Class<?>) pojoType.javaClass().javaObject();
		assert javaClass != null;
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
				RawPojoDescriptor.equalityWrap(paramClass));
		}
		final A_Tuple marshaledTypesTuple =
			TupleDescriptor.fromList(marshaledTypePojos);
		// Create a function wrapper for the pojo constructor invocation
		// primitive. This function will be embedded as a literal into
		// an outer function that holds the (unexposed) constructor pojo.
		L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.primitive(P_InvokePojoConstructor.instance);
		writer.argumentTypes(
			RAW_POJO.o(),
			TupleTypeDescriptor.mostGeneralType(),
			TupleTypeDescriptor.zeroOrMoreOf(RAW_POJO.o()),
			InstanceMetaDescriptor.on(PojoTypeDescriptor.mostGeneralType()));
		writer.returnType(PojoTypeDescriptor.mostGeneralType());
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(failFunction));
		writer.write(
			L1Operation.L1_doGetLocal,
			writer.createLocal(VariableTypeDescriptor.wrapInnerType(
				PojoTypeDescriptor.forClass(Throwable.class))));
		writer.write(L1Operation.L1_doMakeTuple, 1);
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.APPLY.bundle),
			writer.addLiteral(BottomTypeDescriptor.bottom()));
		final A_Function innerFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty()).makeImmutable();
		// Create the outer function that pushes the arguments expected by
		// the constructor invocation primitive. Various objects that we do
		// not want to expose to the Avail program are embedded in this
		// function as literals.
		writer = new L1InstructionWriter(NilDescriptor.nil(), 0);
		writer.argumentTypesTuple(paramTypes);
		writer.returnType(pojoType);
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(innerFunction));
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(
				RawPojoDescriptor.equalityWrap(constructor)));
		for (int i = 1; i <= paramTypes.tupleSize(); i++)
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
			writer.addLiteral(SpecialAtom.APPLY.bundle),
			writer.addLiteral(pojoType));
		final A_Function outerFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty()).makeImmutable();
		// TODO: [TLS] When functions can be made non-reflective, then make
		// both these functions non-reflective for safety.
		return interpreter.primitiveSuccess(outerFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				InstanceMetaDescriptor.on(
					PojoTypeDescriptor.mostGeneralType()),
				TupleTypeDescriptor.zeroOrMoreOf(
					InstanceMetaDescriptor.anyMeta()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						PojoTypeDescriptor.forClass(Throwable.class)),
					BottomTypeDescriptor.bottom())),
			// TODO: [TLS] Answer a function type that answers pojo and
			// can raise java.lang.Throwable.
			FunctionTypeDescriptor.forReturnType(
				PojoTypeDescriptor.mostGeneralType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_POJO_TYPE_IS_ABSTRACT,
				E_JAVA_METHOD_NOT_AVAILABLE));
	}
}
