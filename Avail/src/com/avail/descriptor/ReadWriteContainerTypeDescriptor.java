/**
 * com.avail.descriptor/ReadWriteContainerTypeDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

/**
 * A {@code ReadWriteContainerTypeDescriptor read-write container type} is
 * parametric on the types of values that may be {@linkplain
 * ObjectSlots#READ_TYPE read} from and {@linkplain ObjectSlots#WRITE_TYPE
 * written} to object instance {@linkplain ContainerDescriptor containers}.
 * Reading a container is a covariant capability, while writing a container is
 * a contravariant capability.
 *
 * <p>When the read and write capabilities are equivalent, the static factory
 * methods normalize the representation to an invariant {@linkplain
 * ContainerTypeDescriptor container type descriptor}.</p>
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 * @see ContainerTypeDescriptor
 */
public final class ReadWriteContainerTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/** The type of values that can be read from my object instances. */
		READ_TYPE,

		/** The type of values that can be written to my object instances. */
		WRITE_TYPE
	}

	@Override
	public @NotNull AvailObject o_ReadType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.READ_TYPE);
	}

	@Override
	public @NotNull AvailObject o_WriteType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.WRITE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("& : <--(");
		object.objectSlot(ObjectSlots.READ_TYPE).printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(")/(");
		object.objectSlot(ObjectSlots.WRITE_TYPE).printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(")-->");
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
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (aType.readType().equals(
				object.objectSlot(ObjectSlots.READ_TYPE))
			&& aType.writeType().equals(
				object.objectSlot(ObjectSlots.WRITE_TYPE)))
		{
			aType.becomeIndirectionTo(object);
			return true;
		}
		return false;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return
			(object.objectSlot(ObjectSlots.READ_TYPE).hash() ^ 0xF40149E
			+ object.objectSlot(ObjectSlots.WRITE_TYPE).hash() ^ 0x5469E1A);
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfContainerType(object);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		// Container types are covariant by read capability and contravariant by
		// write capability.
		return aContainerType.readType().isSubtypeOf(
				object.objectSlot(ObjectSlots.READ_TYPE))
			&& object.objectSlot(ObjectSlots.WRITE_TYPE).isSubtypeOf(
				aContainerType.writeType());
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
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
		// The intersection of two container types is container type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return ContainerTypeDescriptor.fromReadAndWriteTypes(
			object.objectSlot(ObjectSlots.READ_TYPE).typeIntersection(
				aContainerType.readType()),
			object.objectSlot(ObjectSlots.WRITE_TYPE).typeUnion(
				aContainerType.writeType()));
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
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
		// The union of two container types is a container type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return ContainerTypeDescriptor.fromReadAndWriteTypes(
			object.objectSlot(ObjectSlots.READ_TYPE).typeUnion(
				aContainerType.readType()),
			object.objectSlot(ObjectSlots.WRITE_TYPE).typeIntersection(
				aContainerType.writeType()));
	}

	/**
	 * Create a {@linkplain ContainerTypeDescriptor container type} based on the
	 * given read and write {@linkplain TypeDescriptor types}.
	 *
	 * @param readType
	 *        The read type.
	 * @param writeType
	 *        The write type.
	 * @return The new container type.
	 */
	static @NotNull AvailObject fromReadAndWriteTypes (
		final @NotNull AvailObject readType,
		final @NotNull AvailObject writeType)
	{
		if (readType.equals(writeType))
		{
			return ContainerTypeDescriptor.wrapInnerType(readType);
		}
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.READ_TYPE, readType);
		result.objectSlotPut(ObjectSlots.WRITE_TYPE, writeType);
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link ReadWriteContainerTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ReadWriteContainerTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ReadWriteContainerTypeDescriptor}.
	 */
	private final static @NotNull ReadWriteContainerTypeDescriptor mutable =
		new ReadWriteContainerTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ReadWriteContainerTypeDescriptor}.
	 *
	 * @return The mutable {@link ReadWriteContainerTypeDescriptor}.
	 */
	public static @NotNull ReadWriteContainerTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ReadWriteContainerTypeDescriptor}.
	 */
	private final static @NotNull ReadWriteContainerTypeDescriptor immutable =
		new ReadWriteContainerTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ReadWriteContainerTypeDescriptor}.
	 *
	 * @return The immutable {@link ReadWriteContainerTypeDescriptor}.
	 */
	public static @NotNull ReadWriteContainerTypeDescriptor immutable ()
	{
		return immutable;
	}
}
