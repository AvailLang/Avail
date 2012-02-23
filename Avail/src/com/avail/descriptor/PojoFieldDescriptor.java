/**
 * PojoFieldDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.PojoFieldDescriptor.IntegerSlots.*;
import static com.avail.descriptor.PojoFieldDescriptor.ObjectSlots.*;
import static com.avail.descriptor.PojoTypeDescriptor.unmarshal;
import java.lang.reflect.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.exceptions.*;

/**
 * A {@code PojoFieldDescriptor} is an Avail {@linkplain VariableDescriptor
 * variable} that facilitates access to the instance {@linkplain Field Java
 * field} of a particular {@linkplain PojoDescriptor pojo} or the static field
 * of a particular {@linkplain PojoTypeDescriptor pojo type}. It supports the
 * same protocol as any other variable, but reads and writes are of the pojo's
 * field.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class PojoFieldDescriptor
extends Descriptor
{
	/** The layout of the integer slots. */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain AvailObject#hash() hash}, or zero ({@code 0}) if the
		 * hash should be computed.
		 */
		HASH_OR_ZERO
	}

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
	boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO;
	}

	@Override
	void o_ClearValue (final @NotNull AvailObject object)
	{
		final Object receiver = object.slot(RECEIVER).javaObject();
		final Field field = (Field) object.slot(FIELD).javaObject();
		final Class<?> fieldType = field.getType();
		final Object defaultValue;
		// Sadly Java does not offer reflective access to the default values of
		// its primitive types ...
		if (fieldType.isPrimitive())
		{
			if (fieldType.equals(Boolean.TYPE))
			{
				defaultValue = Boolean.FALSE;
			}
			else if (fieldType.equals(Float.TYPE))
			{
				defaultValue = Float.valueOf(0.0f);
			}
			else if (fieldType.equals(Double.TYPE))
			{
				defaultValue = Double.valueOf(0.0d);
			}
			else if (fieldType.equals(Character.TYPE))
			{
				defaultValue = Character.valueOf((char) 0);
			}
			else
			{
				defaultValue =
					IntegerDescriptor.zero().marshalToJava(fieldType);
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
			field.set(receiver, defaultValue);
		}
		catch (final Exception e)
		{
			throw new MarshalingException(e);
		}
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPojoField(
			object.slot(FIELD), object.slot(RECEIVER));
	}

	@Override @AvailMethod
	boolean o_EqualsPojoField (
		final @NotNull AvailObject object,
		final @NotNull AvailObject field,
		final @NotNull AvailObject receiver)
	{
		return object.slot(FIELD).equals(field)
			&& object.slot(RECEIVER).equals(receiver);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_GetValue (final @NotNull AvailObject object)
	{
		final Object receiver = object.slot(RECEIVER).javaObject();
		final Field field = (Field) object.slot(FIELD).javaObject();
		final AvailObject expectedType = object.slot(KIND).readType();
		try
		{
			return unmarshal(field.get(receiver), expectedType);
		}
		catch (final Exception e)
		{
			throw new VariableGetException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return object.slot(FIELD).hash()
			* object.slot(RECEIVER).hash() ^ 0x2199C0C3;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		object.slot(FIELD).makeImmutable();
		object.slot(RECEIVER).makeImmutable();
		object.slot(KIND).makeImmutable();
		return object;
	}

	@Override
	void o_SetValue (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newValue)
	{
		final Object receiver = object.slot(RECEIVER).javaObject();
		final Field field = (Field) object.slot(FIELD).javaObject();
		final Class<?> classHint = field.getType();
		try
		{
			field.set(receiver, newValue.marshalToJava(classHint));
		}
		catch (final Exception e)
		{
			throw new VariableSetException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Value (final @NotNull AvailObject object)
	{
		final Object receiver = object.slot(RECEIVER).javaObject();
		final Field field = (Field) object.slot(FIELD).javaObject();
		final AvailObject expectedType = object.slot(KIND).readType();
		try
		{
			return unmarshal(field.get(receiver), expectedType);
		}
		catch (final Exception e)
		{
			throw new VariableGetException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final Field field = (Field) object.slot(FIELD).javaObject();
		if (!Modifier.isStatic(field.getModifiers()))
		{
			builder.append('(');
			object.slot(RECEIVER).printOnAvoidingIndent(
				builder, recursionList, indent + 1);
			builder.append(")'s ");
		}
		builder.append(field);
		builder.append(" = ");
		object.value().printOnAvoidingIndent(
			builder, recursionList, indent + 1);
	}

	/**
	 * Construct a new {@link PojoFieldDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain PojoFieldDescriptor descriptor} represent a
	 *        mutable object?
	 */
	public PojoFieldDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link PojoFieldDescriptor}. */
	private static final PojoFieldDescriptor mutable =
		new PojoFieldDescriptor(true);

	/**
	 * Answer the mutable {@link PojoFieldDescriptor}.
	 *
	 * @return The mutable {@link PojoFieldDescriptor}.
	 */
	public static PojoFieldDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoFieldDescriptor}. */
	private static final PojoFieldDescriptor immutable =
		new PojoFieldDescriptor(false);

	/**
	 * Create a {@linkplain PojoFieldDescriptor variable} that reads/writes
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
	private static @NotNull AvailObject forOuterType (
		final @NotNull AvailObject field,
		final @NotNull AvailObject receiver,
		final @NotNull AvailObject outerType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(FIELD, field);
		newObject.setSlot(RECEIVER, receiver);
		newObject.setSlot(KIND, outerType);
		return newObject;
	}

	/**
	 * Create a {@linkplain PojoFieldDescriptor variable} that can read/write
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
	public static @NotNull AvailObject forInnerType (
		final @NotNull AvailObject field,
		final @NotNull AvailObject receiver,
		final @NotNull AvailObject innerType)
	{
		final Field javaField = (Field) field.javaObject();
		if (Modifier.isFinal(javaField.getModifiers()))
		{
			return PojoFinalFieldDescriptor.forInnerType(
				field, receiver, innerType);
		}
		return forOuterType(
			field, receiver, VariableTypeDescriptor.wrapInnerType(innerType));
	}
}
