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
import com.avail.descriptor.AtomDescriptor.IntegerSlots;

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
		 * The {@linkplain AvailObject kind} of the {@linkplain
		 * ContainerDescriptor container}.  Note that this is always a
		 * {@linkplain ContainerTypeDescriptor container type}.
		 */
		KIND
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.VALUE
			|| e == IntegerSlots.HASH_OR_ZERO;
	}

	@Override
	public @NotNull AvailObject o_Value (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VALUE);
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.KIND);
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContainer(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Containers compare by address (Java object identity).  There's no need to
	 * traverse the objects before comparing addresses, because this message was
	 * a double-dispatch that would have skipped (and stripped) the indirection
	 * objects in either path.
	 * </p>
	 */
	@Override
	public boolean o_EqualsContainer (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainer)
	{
		return object.sameAddressAs(aContainer);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.integerSlot(IntegerSlots.HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = hashGenerator.nextInt();
			}
			while (hash == 0);
			object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		// If I am being frozen (a container), I don't need to freeze my current
		// value.  I do, on the other hand, have to freeze my kind object.
		object.descriptor(ContainerDescriptor.immutable());
		object.objectSlot(ObjectSlots.KIND).makeImmutable();
		return object;
	}

	@Override
	public void o_SetValue (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newValue)
	{
		final AvailObject outerKind = object.objectSlot(ObjectSlots.KIND);
		if (!newValue.isInstanceOf(outerKind.innerType()))
		{
			error("container can't hold that value (wrong type)");
		}
		object.objectSlotPut(ObjectSlots.VALUE, newValue);
	}

	@Override
	public void o_ClearValue (
		final @NotNull AvailObject object)
	{
		// Clear the container (make it have no current value).
		// Eventually, the previous contents should drop a reference.
		object.objectSlotPut(ObjectSlots.VALUE, NullDescriptor.nullObject());
	}

	@Override
	public @NotNull AvailObject o_GetValue (
		final @NotNull AvailObject object)
	{
		// Answer the current value of the container.  Fail if no value is
		// currently assigned.
		final AvailObject value = object.objectSlot(ObjectSlots.VALUE);
		if (value.equalsVoid())
		{
			error("container has no value yet");
			return NullDescriptor.nullObject();
		}
		return value;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * If I'm mutable, release me.  Otherwise make my contents immutable.  This
	 * is used when a variable is being read 'for the last time', but it's
	 * unknown whether the variable has already been shared.
	 * </p>
	 */
	@Override
	public void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object)
	{
		final AvailObject value = object.objectSlot(ObjectSlots.VALUE);
		if (isMutable)
		{
			object.assertObjectUnreachableIfMutableExcept(value);
		}
		else
		{
			value.makeImmutable();
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
		result.objectSlotPut(ObjectSlots.KIND, outerType);
		result.integerSlotPut(IntegerSlots.HASH_OR_ZERO, 0);
		result.objectSlotPut(ObjectSlots.VALUE, NullDescriptor.nullObject());
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
	private final static ContainerDescriptor mutable =
		new ContainerDescriptor(true);

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
	private final static ContainerDescriptor immutable =
		new ContainerDescriptor(false);

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
