/**
 * com.avail.descriptor/PojoDescriptor.java
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

import static com.avail.descriptor.PojoDescriptor.ObjectSlots.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;

/**
 * A {@code PojoDescriptor} describes a plain-old Java object (pojo) that is
 * accessible to an Avail programmer as an {@linkplain AvailObject Avail
 * object}. An Avail pojo comprises a {@linkplain RawPojoDescriptor raw pojo}
 * and a {@linkplain PojoTypeDescriptor pojo type} that describes the pojo
 * contextually.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class PojoDescriptor
extends Descriptor
{
	/** The {@linkplain PojoDescriptor pojo} that wraps Java's {@code null}. */
	private static AvailObject nullObject;

	/**
	 * Answer the {@linkplain PojoDescriptor pojo} that wraps Java's
	 * {@code null}.
	 *
	 * @return The {@code null} pojo.
	 */
	public static AvailObject nullObject ()
	{
		return nullObject;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		nullObject = create(
			RawPojoDescriptor.rawNullObject(),
			PojoTypeDescriptor.mostSpecificType());
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		nullObject = null;
	}

	/** The layout of the object slots. */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo}.
		 */
		RAW_POJO,

		/**
		 * The {@linkplain PojoTypeDescriptor kind} of the {@linkplain
		 * PojoDescriptor descriptor}.
		 */
		KIND
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPojo(object);
	}

	@Override
	public boolean o_EqualsPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojo)
	{
		if (!object.objectSlot(RAW_POJO).equals(aPojo.objectSlot(RAW_POJO))
			|| !object.objectSlot(KIND).equals(aPojo.objectSlot(KIND)))
		{
			return false;
		}

		if (!object.sameAddressAs(aPojo))
		{
			object.becomeIndirectionTo(aPojo);
			aPojo.makeImmutable();
		}

		return true;
	}

	@Override
	public int o_Hash (final @NotNull AvailObject object)
	{
		int hash = object.objectSlot(RAW_POJO).hash() ^ 0x749101DD;
		hash *= AvailObject.Multiplier;
		hash += object.objectSlot(KIND).hash();
		return hash;
	}

	@Override
	public @NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return object.objectSlot(KIND);
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor(PojoDescriptor.immutable());
		object.objectSlot(RAW_POJO).makeImmutable();
		object.objectSlot(KIND).makeImmutable();
		return object;
	}

	/**
	 * Construct a new {@link PojoDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain AbstractDescriptor descriptor} represent a
	 *        mutable object?
	 */
	private PojoDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link PojoDescriptor}. */
	private final static @NotNull PojoDescriptor mutable =
		new PojoDescriptor(true);

	/**
	 * Answer the mutable {@link PojoDescriptor}.
	 *
	 * @return The mutable {@code PojoDescriptor}.
	 */
	public static @NotNull PojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoDescriptor}. */
	private final static @NotNull PojoDescriptor immutable =
		new PojoDescriptor(false);

	/**
	 * Answer the immutable {@link PojoDescriptor}.
	 *
	 * @return The immutable {@code PojoDescriptor}.
	 */
	public static @NotNull PojoDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * RawPojoDescriptor raw pojo} and has the specified {@linkplain
	 * PojoTypeDescriptor pojo kind}.
	 *
	 * @param rawPojo A raw pojo.
	 * @param pojoKind A pojo kind.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject rawPojo,
		final @NotNull AvailObject pojoKind)
	{
		assert rawPojo.isRawPojo();
		assert pojoKind.isSubtypeOf(PojoTypeDescriptor.mostGeneralType());
		final AvailObject newObject = mutable.create();
		newObject.objectSlotPut(RAW_POJO, rawPojo);
		newObject.objectSlotPut(KIND, pojoKind);
		return newObject.makeImmutable();
	}
}
