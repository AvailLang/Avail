/**
 * TopTypeDescriptor.java
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

import com.avail.annotations.*;

/**
 * {@code TopTypeDescriptor} implements the type of the {@linkplain
 * NullDescriptor#nullObject() null object}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TopTypeDescriptor
extends PrimitiveTypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash of this primitive type, computed at construction time.
		 */
		HASH;

		static
		{
			assert PrimitiveTypeDescriptor.IntegerSlots.HASH.ordinal()
				== HASH.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor name} of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT,

		/**
		 * The type (i.e., a metatype) of this primitive type.
		 */
		MY_TYPE;

		static
		{
			assert PrimitiveTypeDescriptor.ObjectSlots.NAME.ordinal()
				== NAME.ordinal();
			assert PrimitiveTypeDescriptor.ObjectSlots.PARENT.ordinal()
				== PARENT.ordinal();
			assert PrimitiveTypeDescriptor.ObjectSlots.MY_TYPE.ordinal()
				== MY_TYPE.ordinal();
		}
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (the type top) is a subtype of aType (may also be
		// top).
		assert aType.isType();
		return aType.traversed().sameAddressAs(object);
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		// Check if object (the type top) is a supertype of aPrimitiveType (a
		// primitive type). Always true.
		return true;
	}

	/**
	 * Construct a new {@link TopTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TopTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TopTypeDescriptor}.
	 */
	private final static TopTypeDescriptor mutable =
		new TopTypeDescriptor(true);

	/**
	 * Answer a mutable {@link TopTypeDescriptor}.
	 *
	 * @return A mutable {@link TopTypeDescriptor}.
	 */
	@ThreadSafe
	/**
	 * Answer the mutable {@link VoidTypeDescriptor}.
	 *
	 * @return The mutable {@link VoidTypeDescriptor}.
	 */
	public static TopTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TopTypeDescriptor}.
	 */
	private final static TopTypeDescriptor immutable =
		new TopTypeDescriptor(false);

	/**
	 * Answer an immutable {@link TopTypeDescriptor}.
	 *
	 * @return An immutable {@link TopTypeDescriptor}.
	 */
	@ThreadSafe
	/**
	 * Answer the immutable {@link VoidTypeDescriptor}.
	 *
	 * @return The immutable {@link VoidTypeDescriptor}.
	 */
	public static TopTypeDescriptor immutable ()
	{
		return immutable;
	}
}
