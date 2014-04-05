/**
 * P_504_BindPojoInstanceField.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import java.lang.reflect.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 504:</strong> Bind the instance {@linkplain Field Java
 * field} specified by the {@linkplain PojoDescriptor pojo} and {@linkplain
 * StringDescriptor field name} to a {@linkplain PojoFieldDescriptor
 * variable}. Reads/writes of this variable pass through to the field.
 * Answer this variable.
 */
public final class P_504_BindPojoInstanceField extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_504_BindPojoInstanceField().init(
		2, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_BasicObject pojo = args.get(0);
		final A_String fieldName = args.get(1);
		// Use the actual Java runtime type of the pojo to perform the
		// reflective field lookup.
		final Object object = pojo.rawPojo().javaObject();
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
		final A_Type fieldType = PojoTypeDescriptor.resolve(
			field.getGenericType(),
			pojo.kind().typeVariables());
		final AvailObject var = PojoFieldDescriptor.forInnerType(
			RawPojoDescriptor.equalityWrap(field),
			pojo.rawPojo(),
			fieldType);
		return interpreter.primitiveSuccess(var);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				PojoTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.stringType()),
			VariableTypeDescriptor.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_JAVA_FIELD_NOT_AVAILABLE.numericCode());
	}
}