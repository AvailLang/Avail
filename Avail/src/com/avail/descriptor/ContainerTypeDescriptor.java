/**
 * descriptor/ContainerTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.NotNull;

public class ContainerTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		INNER_TYPE
	}

	@Override
	public void o_InnerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.INNER_TYPE, value);
	}

	@Override
	public @NotNull AvailObject o_InnerType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INNER_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("& : ");
		object.innerType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContainerType(object);
	}

	@Override
	public boolean o_EqualsContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Container types compare for equality by comparing their innerTypes.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.innerType().equals(object.innerType());
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return CONTAINER_TYPE.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer the object's hash value.

		return object.innerType().hash() * 17 ^ 0x613E420;
	}

	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.innerType().isHashAvailable();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return CONTAINER_TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfContainerType(object);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Container types are invariant (co- and contra-variant simultaneously).  Strange to
		//  compare for equality, but that's how invariance is defined.

		return object.innerType().equals(aContainerType.innerType());
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfContainerType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.innerType().equals(aContainerType.innerType()))
		{
			return object;
		}
		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfContainerType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.innerType().equals(aContainerType.innerType()))
		{
			return object;
		}
		return CONTAINER.o();
	}

	public static @NotNull AvailObject wrapInnerType (
		final @NotNull AvailObject innerType)
	{
		AvailObject result = mutable().create();
		result.innerType(innerType.makeImmutable());
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link ContainerTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContainerTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContainerTypeDescriptor}.
	 */
	private final static ContainerTypeDescriptor mutable =
		new ContainerTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ContainerTypeDescriptor}.
	 *
	 * @return The mutable {@link ContainerTypeDescriptor}.
	 */
	public static ContainerTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ContainerTypeDescriptor}.
	 */
	private final static ContainerTypeDescriptor immutable =
		new ContainerTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ContainerTypeDescriptor}.
	 *
	 * @return The immutable {@link ContainerTypeDescriptor}.
	 */
	public static ContainerTypeDescriptor immutable ()
	{
		return immutable;
	}
}
