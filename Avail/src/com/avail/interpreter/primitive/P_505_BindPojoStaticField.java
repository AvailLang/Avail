/**
 * P_505_BindPojoStaticField.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 505:</strong> Bind the static {@linkplain Field Java
 * field} specified by the {@linkplain PojoTypeDescriptor pojo type} and
 * {@linkplain StringDescriptor field name} to a {@linkplain
 * PojoFieldDescriptor variable}. Reads/writes of this variable pass through
 * to the field. Answer this variable.
 */
public class P_505_BindPojoStaticField extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_505_BindPojoStaticField().init(
		2, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject pojoType = args.get(0);
		final AvailObject fieldName = args.get(1);
		final Field field;
		// If pojoType is not a fused type, then it has an immediate class
		// that should be used to recursively look up the field.
		if (!pojoType.isPojoFusedType())
		{
			final Class<?> javaClass =
				(Class<?>) pojoType.javaClass().javaObject();
			try
			{
				field = javaClass.getField(fieldName.asNativeString());
			}
			catch (final NoSuchFieldException e)
			{
				return interpreter.primitiveFailure(
					E_JAVA_FIELD_NOT_AVAILABLE);
			}
		}
		// If pojoType is a fused type, then iterate through its ancestry in
		// an attempt to uniquely resolve the field.
		else
		{
			final Set<Field> fields = new HashSet<Field>();
			final AvailObject ancestors = pojoType.javaAncestors();
			for (final AvailObject ancestor : ancestors.keysAsSet())
			{
				final Class<?> javaClass = (Class<?>) ancestor.javaObject();
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
				return interpreter.primitiveFailure(
					E_JAVA_FIELD_NOT_AVAILABLE);
			}
			if (fields.size() > 1)
			{
				return interpreter.primitiveFailure(
					E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS);
			}
			field = fields.iterator().next();
		}
		// This is not the right primitive to bind instance fields.
		if (!Modifier.isStatic(field.getModifiers()))
		{
			return interpreter.primitiveFailure(
				E_JAVA_FIELD_NOT_AVAILABLE);
		}
		// A static field cannot have a type parametric on type variables
		// of the declaring class, so pass an empty map where the type
		// variables are expected.
		final AvailObject fieldType = PojoTypeDescriptor.resolve(
			field.getGenericType(),
			MapDescriptor.empty());
		final AvailObject var = PojoFieldDescriptor.forInnerType(
			RawPojoDescriptor.equalityWrap(field),
			RawPojoDescriptor.rawNullObject(),
			fieldType);
		return interpreter.primitiveSuccess(var);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				InstanceMetaDescriptor.on(
					PojoTypeDescriptor.mostGeneralType()),
				TupleTypeDescriptor.stringTupleType()),
			VariableTypeDescriptor.mostGeneralType());
	}
}