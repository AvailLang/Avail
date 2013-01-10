/**
 * P_508_CreatePojoStaticMethodFunction.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import java.lang.reflect.Method;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;

/**
 * <strong>Primitive 508:</strong> Given the specified {@linkplain
 * PojoTypeDescriptor pojo type}, {@linkplain StringDescriptor method name},
 * and {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
 * types}, create a {@linkplain FunctionDescriptor function} that when
 * applied with arguments will invoke the reflected Java static {@linkplain
 * Method method}. The last argument is a function that should be invoked with a
 * pojo-wrapped {@link Exception} in the event that Java raises an exception.
 */
public class P_508_CreatePojoStaticMethodFunction extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_508_CreatePojoStaticMethodFunction().init(4, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 4;
		final AvailObject pojoType = args.get(0);
		final AvailObject methodName = args.get(1);
		final AvailObject paramTypes = args.get(2);
		final AvailObject failFunction = args.get(3);
		// Marshal the argument types.
		final Class<?>[] marshaledTypes =
			new Class<?>[paramTypes.tupleSize()];
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
				(Class<?>) pojoType.javaClass().javaObject();
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
			final Set<Method> methods = new HashSet<Method>();
			final AvailObject ancestors = pojoType.javaAncestors();
			for (final AvailObject ancestor : ancestors.keysAsSet())
			{
				final Class<?> javaClass = (Class<?>) ancestor.javaObject();
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
			new ArrayList<AvailObject>(marshaledTypes.length);
		for (final Class<?> paramClass : marshaledTypes)
		{
			marshaledTypePojos.add(
				RawPojoDescriptor.equalityWrap(paramClass));
		}
		final AvailObject marshaledTypesTuple =
			TupleDescriptor.fromList(marshaledTypePojos);
		// Create a function wrapper for the pojo method invocation
		// primitive. This function will be embedded as a literal into
		// an outer function that holds the (unexposed) method pojo.
		L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.primitiveNumber(
			P_509_InvokeStaticPojoMethod.instance.primitiveNumber);
		writer.argumentTypes(
			RAW_POJO.o(),
			TupleTypeDescriptor.mostGeneralType(),
			TupleTypeDescriptor.zeroOrMoreOf(
				RAW_POJO.o()),
			InstanceMetaDescriptor.topMeta());
		writer.returnType(TOP.o());
		writer.write(new L1Instruction(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(failFunction)));
		writer.write(new L1Instruction(
			L1Operation.L1_doGetLocal,
			writer.createLocal(VariableTypeDescriptor.wrapInnerType(
				PojoTypeDescriptor.forClass(Throwable.class)))));
		writer.write(new L1Instruction(L1Operation.L1_doMakeTuple, 1));
		writer.write(new L1Instruction(
			L1Operation.L1_doCall,
			writer.addLiteral(MethodDescriptor.vmFunctionApplyMethod()),
			writer.addLiteral(BottomTypeDescriptor.bottom())));
		final AvailObject innerFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty()).makeImmutable();
		// Create the outer function that pushes the arguments expected by
		// the method invocation primitive. Various objects that we do
		// not want to expose to the Avail program are embedded in this
		// function as literals.
		writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.argumentTypesTuple(paramTypes);
		final AvailObject returnType = PojoTypeDescriptor.resolve(
			method.getGenericReturnType(),
			pojoType.typeVariables());
		writer.returnType(returnType);
		writer.write(new L1Instruction(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(innerFunction)));
		writer.write(new L1Instruction(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(RawPojoDescriptor.equalityWrap(method))));
		final int limit = paramTypes.tupleSize();
		for (int i = 1; i <= limit; i++)
		{
			writer.write(new L1Instruction(L1Operation.L1_doPushLocal, i));
		}
		writer.write(new L1Instruction(
			L1Operation.L1_doMakeTuple,
			paramTypes.tupleSize()));
		writer.write(new L1Instruction(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(marshaledTypesTuple)));
		writer.write(new L1Instruction(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(pojoType)));
		writer.write(new L1Instruction(
			L1Operation.L1_doMakeTuple,
			4));
		writer.write(new L1Instruction(
			L1Operation.L1_doCall,
			writer.addLiteral(MethodDescriptor.vmFunctionApplyMethod()),
			writer.addLiteral(returnType)));
		final AvailObject outerFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty()).makeImmutable();
		// TODO: [TLS] When functions can be made non-reflective, then make
		// both these functions non-reflective for safety.
		return interpreter.primitiveSuccess(outerFunction);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				InstanceMetaDescriptor.on(
					PojoTypeDescriptor.mostGeneralType()),
				TupleTypeDescriptor.stringTupleType(),
				TupleTypeDescriptor.zeroOrMoreOf(
					InstanceMetaDescriptor.topMeta()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						PojoTypeDescriptor.forClass(Throwable.class)),
					BottomTypeDescriptor.bottom())),
			// TODO: [TLS] Answer a function type that answers any and
			// can raise java.lang.Throwable.
			FunctionTypeDescriptor.forReturnType(TOP.o()));
	}
}