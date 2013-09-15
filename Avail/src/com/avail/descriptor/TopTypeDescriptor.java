/**
 * TopTypeDescriptor.java
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

import com.avail.annotations.*;

/**
 * {@code TopTypeDescriptor} implements the type of {@linkplain
 * NilDescriptor#nil() nil}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class TopTypeDescriptor
extends PrimitiveTypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash of this primitive type, computed at construction time.
		 */
		HASH,

		/**
		 * This primitive type's (mutually) unique ordinal number.
		 */
		PRIMITIVE_TYPE_ORDINAL;

		static
		{
			assert PrimitiveTypeDescriptor.IntegerSlots.HASH.ordinal()
				== HASH.ordinal();
			assert PrimitiveTypeDescriptor.IntegerSlots.PRIMITIVE_TYPE_ORDINAL
					.ordinal()
				== PRIMITIVE_TYPE_ORDINAL.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor name} of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT;

		static
		{
			assert PrimitiveTypeDescriptor.ObjectSlots.NAME.ordinal()
				== NAME.ordinal();
			assert PrimitiveTypeDescriptor.ObjectSlots.PARENT.ordinal()
				== PARENT.ordinal();
		}
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (the type top) is a subtype of aType (may also be
		// top).
		assert aType.isType();
		return aType.traversed().sameAddressAs(object);
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		// Check if object (the type top) is a supertype of aPrimitiveType (a
		// primitive type). Always true.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsTop (final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link TopTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private TopTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link TopTypeDescriptor}. */
	@SuppressWarnings("hiding")
	final static TopTypeDescriptor mutable =
		new TopTypeDescriptor(Mutability.MUTABLE);

	@Override
	TopTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link TopTypeDescriptor}. */
	final private static TopTypeDescriptor shared =
		new TopTypeDescriptor(Mutability.SHARED);

	@Override
	TopTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	TopTypeDescriptor shared ()
	{
		return shared;
	}
}
