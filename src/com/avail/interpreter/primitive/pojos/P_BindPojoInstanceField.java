/*
 * P_BindPojoInstanceField.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.PojoDescriptor;
import com.avail.descriptor.PojoFieldDescriptor;
import com.avail.descriptor.StringDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PojoFieldDescriptor.pojoFieldVariableForInnerType;
import static com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoType;
import static com.avail.descriptor.PojoTypeDescriptor.resolvePojoType;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_FIELD_NOT_AVAILABLE;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Bind the instance {@linkplain Field Java
 * field} specified by the {@linkplain PojoDescriptor pojo} and {@linkplain
 * StringDescriptor field name} to a {@linkplain PojoFieldDescriptor
 * variable}. Reads/writes of this variable pass through to the field.
 * Answer this variable.
 */
public final class P_BindPojoInstanceField extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BindPojoInstanceField().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_BasicObject pojo = interpreter.argument(0);
		final A_String fieldName = interpreter.argument(1);
		// Use the actual Java runtime type of the pojo to perform the
		// reflective field lookup.
		final Object object = pojo.rawPojo().javaObjectNotNull();
		final Class<?> javaClass = object.getClass();
		final Field field;
		try
		{
			field = javaClass.getField(fieldName.asNativeString());
		}
		catch (final NoSuchFieldException e)
		{
			return interpreter.primitiveFailure(
				E_JAVA_FIELD_NOT_AVAILABLE);
		}
		// This is not the right primitive to bind static fields.
		if (Modifier.isStatic(field.getModifiers()))
		{
			return interpreter.primitiveFailure(
				E_JAVA_FIELD_NOT_AVAILABLE);
		}
		final A_Type fieldType = resolvePojoType(
			field.getGenericType(), pojo.kind().typeVariables());
		final AvailObject var = pojoFieldVariableForInnerType(
			equalityPojo(field), pojo.rawPojo(), fieldType);
		return interpreter.primitiveSuccess(var);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralPojoType(),
				stringType()),
			mostGeneralVariableType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_JAVA_FIELD_NOT_AVAILABLE));
	}
}
