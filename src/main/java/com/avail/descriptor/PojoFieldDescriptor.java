/*
 * PojoFieldDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MarshalingException;
import com.avail.exceptions.VariableGetException;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

import static com.avail.descriptor.IntegerDescriptor.zero;
import static com.avail.descriptor.PojoFieldDescriptor.ObjectSlots.*;
import static com.avail.descriptor.PojoFinalFieldDescriptor.pojoFinalFieldForInnerType;
import static com.avail.descriptor.PojoTypeDescriptor.unmarshal;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static java.lang.reflect.Modifier.STATIC;

/**
 * A {@code PojoFieldDescriptor} is an Avail {@linkplain VariableDescriptor
 * variable} that facilitates access to the instance {@linkplain Field Java
 * field} of a particular {@linkplain PojoDescriptor pojo} or the static field
 * of a particular {@linkplain PojoTypeDescriptor pojo type}. It supports the
 * same protocol as any other variable, but reads and writes are of the pojo's
 * field.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PojoFieldDescriptor
extends Descriptor
{
	/** The layout of the object slots. */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that wraps a {@linkplain
		 * Field reflected Java field}.
		 */
		FIELD,

		/**
		 * The {@linkplain RawPojoDescriptor raw pojo} to which the {@linkplain
		 * Field reflected Java field} is bound.
		 */
		RECEIVER,

		/**
		 * The {@linkplain VariableTypeDescriptor kind} of the {@linkplain
		 * VariableDescriptor variable}.
		 */
		KIND
	}

	@Override
	protected void o_ClearValue (final AvailObject object)
	{
		final Object receiver = object.slot(RECEIVER).javaObjectNotNull();
		final Field field = object.slot(FIELD).javaObjectNotNull();
		final Class<?> fieldType = field.getType();
		final @Nullable Object defaultValue;
		// Sadly Java does not offer reflective access to the default values of
		// its primitive types ...
		if (fieldType.isPrimitive())
		{
			if (fieldType.equals(boolean.class))
			{
				defaultValue = Boolean.FALSE;
			}
			else if (fieldType.equals(float.class))
			{
				defaultValue = 0.0f;
			}
			else if (fieldType.equals(double.class))
			{
				defaultValue = 0.0d;
			}
			else if (fieldType.equals(char.class))
			{
				defaultValue = (char) 0;
			}
			else
			{
				defaultValue = zero().marshalToJava(fieldType);
			}
		}
		// Reference types have a default value of null.
		else
		{
			defaultValue = null;
		}
		// Clear the variable by writing the appropriate default value.
		try
		{
			synchronized (receiver)
			{
				field.set(receiver, defaultValue);
			}
		}
		catch (final Exception e)
		{
			throw new MarshalingException(e);
		}
	}

	@Override @AvailMethod
	protected boolean o_Equals (
		final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPojoField(
			object.slot(FIELD), object.slot(RECEIVER));
	}

	@Override @AvailMethod
	protected boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return object.slot(FIELD).equals(field)
			&& object.slot(RECEIVER).equals(receiver);
	}

	@Override @AvailMethod
	protected AvailObject o_GetValue (final AvailObject object)
		throws VariableGetException
	{
		final Object receiver = object.slot(RECEIVER).javaObjectNotNull();
		final Field field = object.slot(FIELD).javaObjectNotNull();
		final A_Type expectedType = object.slot(KIND).readType();
		try
		{
			synchronized (receiver)
			{
				return unmarshal(field.get(receiver), expectedType);
			}
		}
		catch (final Exception e)
		{
			throw new AvailRuntimeException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return object.slot(FIELD).hash()
			* object.slot(RECEIVER).hash() ^ 0x2199C0C3;
	}

	@Override
	protected boolean o_HasValue (final AvailObject object)
	{
		// A pojo field has a value by definition, since we consider Java null
		// as unequal to nil.
		return true;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		final Field field = object.slot(FIELD).javaObjectNotNull();
		if ((field.getModifiers() & STATIC) != 0)
		{
			return SerializerOperation.STATIC_POJO_FIELD;
		}
		throw unsupportedOperationException();
	}


	@Override @AvailMethod
	protected void o_SetValue (
		final AvailObject object, final A_BasicObject newValue)
	{
		final Object receiver = object.slot(RECEIVER).javaObjectNotNull();
		final Field field = object.slot(FIELD).javaObjectNotNull();
		final Class<?> classHint = field.getType();
		try
		{
			synchronized (receiver)
			{
				field.set(receiver, newValue.marshalToJava(classHint));
			}
		}
		catch (final Exception e)
		{
			throw new AvailRuntimeException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override
	protected void o_SetValueNoCheck (
		final AvailObject object,
		final A_BasicObject newValue)
	{
		// Actually check this write anyhow. Just in case.
		final Object receiver = object.slot(RECEIVER).javaObjectNotNull();
		final Field field = object.slot(FIELD).javaObjectNotNull();
		final Class<?> classHint = field.getType();
		try
		{
			synchronized (receiver)
			{
				field.set(receiver, newValue.marshalToJava(classHint));
			}
		}
		catch (final Exception e)
		{
			throw new AvailRuntimeException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override @AvailMethod
	protected AvailObject o_Value (final AvailObject object)
	{
		final Object receiver = object.slot(RECEIVER).javaObjectNotNull();
		final Field field = object.slot(FIELD).javaObjectNotNull();
		final A_Type expectedType = object.slot(KIND).readType();
		try
		{
			synchronized (receiver)
			{
				return unmarshal(field.get(receiver), expectedType);
			}
		}
		catch (final Exception e)
		{
			throw new AvailRuntimeException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.kind().writeTo(writer);
		writer.write("value");
		object.value().writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (
		final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.kind().writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	protected void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final Field field = object.slot(FIELD).javaObjectNotNull();
		if (!Modifier.isStatic(field.getModifiers()))
		{
			builder.append('(');
			object.slot(RECEIVER).printOnAvoidingIndent(
				builder, recursionMap, indent + 1);
			builder.append(")'s ");
		}
		builder.append(field);
		builder.append(" = ");
		object.value().printOnAvoidingIndent(builder, recursionMap, indent + 1);
	}

	/**
	 * Construct a new {@code PojoFieldDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private PojoFieldDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.VARIABLE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link PojoFieldDescriptor}. */
	private static final PojoFieldDescriptor mutable =
		new PojoFieldDescriptor(Mutability.MUTABLE);

	@Override
	protected PojoFieldDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoFieldDescriptor}. */
	private static final PojoFieldDescriptor immutable =
		new PojoFieldDescriptor(Mutability.IMMUTABLE);

	@Override
	protected PojoFieldDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link PojoFieldDescriptor}. */
	private static final PojoFieldDescriptor shared =
		new PojoFieldDescriptor(Mutability.SHARED);

	@Override
	protected PojoFieldDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a {@code PojoFieldDescriptor variable} that reads/writes
	 * through to the specified {@linkplain Field field} and has the specified
	 * {@linkplain VariableTypeDescriptor variable type}.
	 *
	 * @param field
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps a reflected
	 *        Java field.
	 * @param receiver
	 *        The raw pojo to which the reflected Java field is bound.
	 * @param outerType
	 *        The variable type.
	 * @return A new variable of the specified type.
	 */
	private static AvailObject forOuterType (
		final AvailObject field,
		final AvailObject receiver,
		final A_Type outerType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(FIELD, field);
		newObject.setSlot(RECEIVER, receiver);
		newObject.setSlot(KIND, outerType);
		return newObject;
	}

	/**
	 * Create a {@code PojoFieldDescriptor variable} that can read/write
	 * through to the specified {@linkplain Field field} values of the specified
	 * {@linkplain TypeDescriptor type}.
	 *
	 * @param field
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps a reflected
	 *        Java field.
	 * @param receiver
	 *        The {@linkplain PojoDescriptor pojo} to which the reflected Java
	 *        field is bound.
	 * @param innerType
	 *        The types of values that can be read/written.
	 * @return A new variable able to read/write values of the specified types.
	 */
	public static AvailObject pojoFieldVariableForInnerType (
		final AvailObject field,
		final AvailObject receiver,
		final A_Type innerType)
	{
		final Field javaField = field.javaObjectNotNull();
		if (Modifier.isFinal(javaField.getModifiers()))
		{
			return pojoFinalFieldForInnerType(field, receiver, innerType);
		}
		return forOuterType(field, receiver, variableTypeFor(innerType));
	}
}
