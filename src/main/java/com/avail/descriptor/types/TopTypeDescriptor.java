/*
 * TopTypeDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.utility.json.JSONWriter;

/**
 * {@code TopTypeDescriptor} implements the type of {@linkplain
 * NilDescriptor#nil nil}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class TopTypeDescriptor
extends PrimitiveTypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, populated during construction.
		 */
		static final BitField
			HASH = AbstractDescriptor.bitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert PrimitiveTypeDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== IntegerSlots.HASH_AND_MORE.ordinal();
			assert PrimitiveTypeDescriptor.IntegerSlots.HASH
				.isSamePlaceAs(HASH);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
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
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (the type top) is a subtype of aType (may also be
		// top).
		assert aType.isType();
		return aType.traversed().sameAddressAs(object);
	}

	@Override
	@AvailMethod
	@ThreadSafe
	protected boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		// Check if object (the type top) is a supertype of aPrimitiveType (a
		// primitive type). Always true.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	protected boolean o_IsTop (final AvailObject object)
	{
		return true;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("top type");
		writer.endObject();
	}

	/**
	 * Construct a new {@link Mutability#SHARED shared} {@link
	 * PrimitiveTypeDescriptor}.
	 *
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param primitiveType
	 *        The {@link Types primitive type} represented by this descriptor.
	 */
	TopTypeDescriptor (
		final TypeTag typeTag,
		final Types primitiveType)
	{
		super(typeTag, primitiveType, ObjectSlots.class, IntegerSlots.class);
	}
}
