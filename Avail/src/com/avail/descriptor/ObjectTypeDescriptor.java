/**
 * descriptor/ObjectTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.TERMINATES;
import static java.lang.Math.min;
import com.avail.annotations.NotNull;

/**
 * TODO: Document this type!
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ObjectTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * A {@linkplain MapTypeDescriptor map} from {@linkplain
		 * CyclicTypeDescriptor field names} to their declared {@linkplain
		 * TypeDescriptor types}.
		 */
		FIELD_TYPE_MAP
	}

	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		final AvailObject typeMap = object.fieldTypeMap();
		final AvailObject instMap = potentialInstance.fieldMap();
		if (instMap.mapSize() < typeMap.mapSize())
		{
			return false;
		}
		AvailObject.lock(typeMap);
		for (int i1 = 1, end = typeMap.capacity(); i1 <= end; i1++)
		{
			final AvailObject key = typeMap.keyAtIndex(i1);
			if (!key.equalsVoidOrBlank())
			{
				final AvailObject fieldType = typeMap.valueAtIndex(i1);
				if (!instMap.hasKey(key))
				{
					AvailObject.unlock(typeMap);
					return false;
				}
				if (!instMap.mapAt(key).isInstanceOfSubtypeOf(fieldType))
				{
					AvailObject.unlock(typeMap);
					return false;
				}
			}
		}
		AvailObject.unlock(typeMap);
		return true;
	}

	@Override
	public void o_FieldTypeMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.FIELD_TYPE_MAP, value);
	}

	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FIELD_TYPE_MAP);
	}

	@Override
	public @NotNull AvailObject o_ExactType (final @NotNull AvailObject object)
	{
		object.makeImmutable();
		return ObjectMetaDescriptor.fromObjectTypeAndLevel(
			object, IntegerDescriptor.one().type());
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.fieldTypeMap().hash() * 11 ^ 0xE3561F16;
	}

	/**
	 * Answer whether this object's hash value can be computed without creating
	 * new objects. This method is used by the garbage collector to decide which
	 * objects to attempt to coalesce.  The garbage collector uses the hash
	 * values to find objects that it is likely can be coalesced together.
	 */
	@Override
	public boolean o_IsHashAvailable (final @NotNull AvailObject object)
	{
		return object.fieldTypeMap().isHashAvailable();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		object.makeImmutable();
		return ObjectMetaDescriptor.fromObjectTypeAndLevel(
			object, IntegerDescriptor.one().type());
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfObjectType(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject m1 = object.fieldTypeMap();
		final AvailObject m2 = anObjectType.fieldTypeMap();
		if (m1.mapSize() > m2.mapSize())
		{
			return false;
		}
		AvailObject.lock(m1);
		for (int i1 = 1, end = m1.capacity(); i1 <= end; i1++)
		{
			final AvailObject key = m1.keyAtIndex(i1);
			if (!key.equalsVoidOrBlank())
			{
				final AvailObject v1 = m1.valueAtIndex(i1);
				if (!m2.hasKey(key))
				{
					AvailObject.unlock(m1);
					return false;
				}
				if (!m2.mapAt(key).isSubtypeOf(v1))
				{
					AvailObject.unlock(m1);
					return false;
				}
			}
		}
		AvailObject.unlock(m1);
		return true;
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
		return another.typeIntersectionOfObjectType(object);
	}

	/**
	 * Answer the most general type that is still at least as specific as these.
	 * Here we're finding the nearest common descendant of two eager object
	 * types.
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		AvailObject resultMap = MapDescriptor.newWithCapacity(
			map1.capacity() + map2.capacity());
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final AvailObject key = entry.key;
			AvailObject type = entry.value;
			if (map2.hasKey(key))
			{
				type = type.typeIntersection(map2.mapAt(key));
				if (type.equals(TERMINATES.o()))
				{
					return TERMINATES.o();
				}
			}
			resultMap = resultMap.mapAtPuttingCanDestroy(
				key,
				type,
				true);
		}
		for (final MapDescriptor.Entry entry : map2.mapIterable())
		{
			final AvailObject key = entry.key;
			AvailObject type = entry.value;
			if (!map1.hasKey(key))
			{
				resultMap = resultMap.mapAtPuttingCanDestroy(
					key,
					type,
					true);
			}
		}
		return objectTypeFromMap(resultMap);
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
		return another.typeUnionOfObjectType(object);
	}


	/**
	 * Answer the most specific type that is still at least as general as these.
	 * Here we're finding the nearest common ancestor of two eager object types.
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		AvailObject resultMap = MapDescriptor.newWithCapacity(
			min(map1.capacity(), map2.capacity()));
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final AvailObject key = entry.key;
			if (map2.hasKey(key))
			{
				final AvailObject valueType = entry.value;
				resultMap = resultMap.mapAtPuttingCanDestroy(
					key,
					valueType.typeUnion(map2.mapAt(key)),
					true);
			}
		}
		return objectTypeFromMap(resultMap);
	}

	/**
	 * Create an {@linkplain ObjectTypeDescriptor object type} using the given
	 * {@linkplain MapDescriptor map} from {@linkplain CyclicTypeDescriptor
	 * keys} to {@linkplain TypeDescriptor types}.
	 *
	 * @param map
	 *        The {@linkplain MapDescriptor map} from {@linkplain
	 *        CyclicTypeDescriptor keys} to {@linkplain TypeDescriptor types}.
	 * @return The new {@linkplain ObjectTypeDescriptor object type}.
	 */
	public static AvailObject objectTypeFromMap (
		final @NotNull AvailObject map)
	{
		AvailObject result = mutable().create();
		result.fieldTypeMap(map);
		return result;
	}

	/**
	 * Construct a new {@link ObjectTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link ObjectTypeDescriptor}. */
	private final static @NotNull ObjectTypeDescriptor mutable =
		new ObjectTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The mutable {@link ObjectTypeDescriptor}.
	 */
	public static @NotNull ObjectTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ObjectTypeDescriptor}. */
	private final static @NotNull ObjectTypeDescriptor immutable =
		new ObjectTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The immutable {@link ObjectTypeDescriptor}.
	 */
	public static @NotNull ObjectTypeDescriptor immutable ()
	{
		return immutable;
	}
}
