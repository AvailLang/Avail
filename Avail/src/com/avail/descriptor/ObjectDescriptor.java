/**
 * descriptor/ObjectDescriptor.java
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
import com.avail.annotations.NotNull;
import com.avail.utility.*;

public class ObjectDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		FIELD_MAP
	}

	@Override
	public void o_FieldMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.FIELD_MAP, value);
	}

	@Override
	public @NotNull AvailObject o_FieldMap (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FIELD_MAP);
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsObject(object);
	}

	@Override
	public boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObject)
	{
		if (object.sameAddressAs(anObject))
		{
			return true;
		}
		return object.fieldMap().equals(anObject.fieldMap());
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ALL.o()))
		{
			return true;
		}
		return aTypeObject.hasObjectInstance(object);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		object.makeImmutable();
		final AvailObject valueMap = object.fieldMap();
		final Mutable<AvailObject> typeMap = new Mutable<AvailObject>(
				MapDescriptor.newWithCapacity(valueMap.capacity()));
		valueMap.mapDo(new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				typeMap.value = typeMap.value.mapAtPuttingCanDestroy(
					key,
					value.type(),
					true);
			}
		});
		return ObjectTypeDescriptor.objectTypeFromMap(typeMap.value);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer the object's hash value.

		return ObjectDescriptor.computeHashFromFieldMapHash(object.fieldMap().hash());
	}

	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.fieldMap().isHashAvailable();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}

	public static AvailObject objectFromMap (final AvailObject map)
	{
		AvailObject result = mutable().create();
		result.fieldMap(map);
		return result;
	}

	private static int computeHashFromFieldMapHash (final int fieldMapHash)
	{
		return fieldMapHash + 0x1099BE88 ^ 0x38547ADE;
	}

	/**
	 * Construct a new {@link ObjectDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ObjectDescriptor}.
	 */
	private final static ObjectDescriptor mutable = new ObjectDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectDescriptor}.
	 *
	 * @return The mutable {@link ObjectDescriptor}.
	 */
	public static ObjectDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ObjectDescriptor}.
	 */
	private final static ObjectDescriptor immutable = new ObjectDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectDescriptor}.
	 *
	 * @return The immutable {@link ObjectDescriptor}.
	 */
	public static ObjectDescriptor immutable ()
	{
		return immutable;
	}
}
