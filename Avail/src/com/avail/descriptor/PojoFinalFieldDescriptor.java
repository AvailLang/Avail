/**
 * PojoFinalFieldDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.PojoFinalFieldDescriptor.ObjectSlots.*;
import static com.avail.descriptor.PojoTypeDescriptor.unmarshal;
import java.lang.reflect.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.exceptions.*;

/**
 * A {@code PojoFinalFieldDescriptor} is an Avail {@linkplain VariableDescriptor
 * variable} that facilitates access to the instance {@linkplain Field Java
 * field} of a particular {@linkplain PojoDescriptor pojo} or the static field
 * of a particular {@linkplain PojoTypeDescriptor pojo type}. It supports the
 * same protocol as any other variable, but reads and writes are of the pojo's
 * field.
 *
 * <p>It leverages the fact that the field is {@link Modifier#isFinal(int)
 * final} by caching the value and not retaining the reflected field directly.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PojoFinalFieldDescriptor
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
		 * The cached value of the reflected {@linkplain Field Java field}.
		 */
		CACHED_VALUE,

		/**
		 * The {@linkplain VariableTypeDescriptor kind} of the {@linkplain
		 * VariableDescriptor variable}.
		 */
		KIND
	}

	@Override
	void o_ClearValue (final AvailObject object)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPojoField(
			object.slot(FIELD), object.slot(RECEIVER));
	}

	@Override @AvailMethod
	boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return object.slot(FIELD).equals(field)
			&& object.slot(RECEIVER).equals(receiver);
	}

	@Override @AvailMethod
	AvailObject o_GetValue (final AvailObject object)
	{
		return object.slot(CACHED_VALUE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(FIELD).hash()
			* object.slot(RECEIVER).hash() ^ 0x2199C0C3;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD);
	}

	@Override
	void o_SetValueNoCheck (
		final AvailObject object,
		final AvailObject newValue)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD);
	}

	@Override @AvailMethod
	AvailObject o_Value (final AvailObject object)
	{
		return object.slot(CACHED_VALUE);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
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
		object.slot(CACHED_VALUE).printOnAvoidingIndent(
			builder, recursionList, indent + 1);
	}

	/**
	 * Construct a new {@link PojoFinalFieldDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public PojoFinalFieldDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link PojoFinalFieldDescriptor}. */
	private static final PojoFinalFieldDescriptor mutable =
		new PojoFinalFieldDescriptor(Mutability.MUTABLE);

	@Override
	PojoFinalFieldDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoFinalFieldDescriptor}. */
	private static final PojoFinalFieldDescriptor immutable =
		new PojoFinalFieldDescriptor(Mutability.IMMUTABLE);

	@Override
	PojoFinalFieldDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link PojoFinalFieldDescriptor}. */
	private static final PojoFinalFieldDescriptor shared =
		new PojoFinalFieldDescriptor(Mutability.SHARED);

	@Override
	PojoFinalFieldDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a {@linkplain PojoFinalFieldDescriptor variable} that reads
	 * through to the specified {@link Modifier#isFinal(int) final} {@linkplain
	 * Field field} and has the specified {@linkplain VariableTypeDescriptor
	 * variable type}.
	 *
	 * @param field
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps a reflected
	 *        Java field.
	 * @param receiver
	 *        The raw pojo to which the reflected Java field is bound.
	 * @param cachedValue
	 *        The value of the final field, already {@linkplain
	 *        AvailObject#marshalToJava(Class) marshaled}.
	 * @param outerType
	 *        The variable type.
	 * @return A new variable of the specified type.
	 */
	private static AvailObject forOuterType (
		final AvailObject field,
		final AvailObject receiver,
		final AvailObject cachedValue,
		final AvailObject outerType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(FIELD, field);
		newObject.setSlot(RECEIVER, receiver);
		newObject.setSlot(CACHED_VALUE, cachedValue);
		newObject.setSlot(KIND, outerType);
		return newObject;
	}

	/**
	 * Create a {@linkplain PojoFinalFieldDescriptor variable} that can read
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
	 *        The types of values that can be read.
	 * @return A new variable able to read values of the specified types.
	 */
	static AvailObject forInnerType (
		final AvailObject field,
		final AvailObject receiver,
		final A_Type innerType)
	{
		final Field javaField = (Field) field.javaObject();
		assert Modifier.isFinal(javaField.getModifiers());
		final Object javaReceiver = receiver.javaObject();
		final AvailObject value;
		try
		{
			value = unmarshal(javaField.get(javaReceiver), innerType);
		}
		catch (final Exception e)
		{
			throw new VariableGetException(
				AvailErrorCode.E_JAVA_MARSHALING_FAILED,
				e);
		}
		return forOuterType(
			field,
			receiver,
			value,
			VariableTypeDescriptor.wrapInnerType(innerType));
	}
}
