/**
 * descriptor/ContainerDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.error;
import java.util.Random;
import com.avail.annotations.NotNull;

/**
 * My {@linkplain AvailObject object instances} are containers which can hold
 * any object that agrees with my {@linkplain #forInnerType(AvailObject) inner
 * type}.  A container may also hold no value at all.  Any attempt to read the
 * {@linkplain #o_GetValue(AvailObject) current value} of a container that holds
 * no value will fail immediately.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ContainerDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain AvailObject contents} of the {@linkplain
		 * ContainerDescriptor container}.
		 */
		VALUE,

		/**
		 * The {@linkplain AvailObject type} of the {@linkplain
		 * ContainerDescriptor container}.
		 */
		TYPE
	}

	@Override
	public void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override
	public void o_Type (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.TYPE, value);
	}

	@Override
	public void o_Value (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VALUE, value);
	}

	@Override
	public int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TYPE);
	}

	@Override
	public @NotNull AvailObject o_Value (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VALUE);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.VALUE
			|| e == IntegerSlots.HASH_OR_ZERO;
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContainer(object);
	}

	@Override
	public boolean o_EqualsContainer (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainer)
	{
		//  Containers compare by address (Smalltalk object identity).  No need to traverse the
		//  objects before comparing addresses, because this message was a double-dispatch
		//  that would have skipped (and stripped) the indirection objects in either path.

		return object.sameAddressAs(aContainer);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return object.type();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = hashGenerator.nextInt();
		}
		object.hashOrZero(hash);
		return hash;
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		// If I am being frozen (a container), I don't need to freeze my current
		// value.  I do, on the other hand, have to freeze my type object.
		object.descriptor(ContainerDescriptor.immutable());
		object.type().makeImmutable();
		return object;
	}

	@Override
	public void o_InnerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject innerType)
	{
		//  Initialize my type based on the given inner type.

		object.type(ContainerTypeDescriptor.wrapInnerType(innerType));
	}

	@Override
	public void o_SetValue (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newValue)
	{
		if (!newValue.isInstanceOfSubtypeOf(object.type().innerType()))
		{
			error("container can't hold that value (wrong type)");
		}
		object.value(newValue);
	}

	@Override
	public void o_ClearValue (
		final @NotNull AvailObject object)
	{
		// Clear the container (make it have no current value).
		// Eventually, the previous contents should drop a reference.
		object.value(VoidDescriptor.voidObject());
	}

	@Override
	public @NotNull AvailObject o_GetValue (
		final @NotNull AvailObject object)
	{
		// Answer the current value of the container.  Fail if no value is
		// currently assigned.
		final AvailObject value = object.value();
		if (value.equalsVoid())
		{
			error("container has no value yet");
			return VoidDescriptor.voidObject();
		}
		return value;
	}

	@Override
	public void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object)
	{
		//  If I'm mutable, release me.  Otherwise make my contents immutable.  This
		//  is used when a variable is being read 'for the last time', but it's unknown
		//  whether the variable has already been shared.

		if (isMutable)
		{
			object.assertObjectUnreachableIfMutableExcept(object.value());
		}
		else
		{
			object.value().makeImmutable();
		}
	}

	/**
	 * Create a {@linkplain ContainerDescriptor container} which can only
	 * contain values of the specified type.  The new container initially holds
	 * no value.
	 *
	 * @param innerType
	 *            The type of objects the new container can contain.
	 * @return
	 *            A new container able to hold the specified type of objects.
	 */
	public static @NotNull AvailObject forInnerType (
		final @NotNull AvailObject innerType)
	{
		return ContainerDescriptor.forOuterType(
			ContainerTypeDescriptor.wrapInnerType(innerType));
	}

	/**
	 * Create a {@linkplain ContainerDescriptor container} of the specified
	 * {@linkplain ContainerTypeDescriptor container type}.  The new container
	 * initially holds no value.
	 *
	 * @param outerType
	 *            The container type to instantiate.
	 * @return
	 *            A new container of the given type.
	 */
	public static @NotNull AvailObject forOuterType (
		final @NotNull AvailObject outerType)
	{
		final AvailObject result = mutable().create();
		result.type(outerType);
		result.hashOrZero(0);
		result.value(VoidDescriptor.voidObject());
		return result;
	}

	/**
	 * A random generator used for creating hash values as needed.
	 */
	private static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link ContainerDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContainerDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContainerDescriptor}.
	 */
	private final static ContainerDescriptor mutable = new ContainerDescriptor(true);

	/**
	 * Answer the mutable {@link ContainerDescriptor}.
	 *
	 * @return The mutable {@link ContainerDescriptor}.
	 */
	public static ContainerDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ContainerDescriptor}.
	 */
	private final static ContainerDescriptor immutable = new ContainerDescriptor(false);

	/**
	 * Answer the immutable {@link ContainerDescriptor}.
	 *
	 * @return The immutable {@link ContainerDescriptor}.
	 */
	public static ContainerDescriptor immutable ()
	{
		return immutable;
	}
}
