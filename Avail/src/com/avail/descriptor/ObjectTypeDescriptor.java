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

import com.avail.compiler.Continuation2;
import com.avail.compiler.Mutable;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.ObjectMetaDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import static java.lang.Math.*;

public class ObjectTypeDescriptor extends TypeDescriptor
{

	enum ObjectSlots
	{
		FIELD_TYPE_MAP
	}


	// As yet unclassified

	@Override
	public boolean o_HasObjectInstance (
			final AvailObject object,
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		final AvailObject typeMap = object.fieldTypeMap();
		final AvailObject instMap = potentialInstance.fieldMap();
		if ((instMap.mapSize() < typeMap.mapSize()))
		{
			return false;
		}
		AvailObject.lock(typeMap);
		for (int i1 = 1, _end1 = typeMap.capacity(); i1 <= _end1; i1++)
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



	// GENERATED accessors

	/**
	 * Setter for field fieldTypeMap.
	 */
	@Override
	public void o_FieldTypeMap (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.FIELD_TYPE_MAP, value);
	}

	/**
	 * Getter for field fieldTypeMap.
	 */
	@Override
	public AvailObject o_FieldTypeMap (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FIELD_TYPE_MAP);
	}



	// operations

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer this object type's type.

		object.makeImmutable();
		return ObjectMetaDescriptor.fromObjectType(object);
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Use the hash of the map (of field keys and field types), multiplied by 11.

		return (object.fieldTypeMap().hash() * 11);
	}

	@Override
	public boolean o_IsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.fieldTypeMap().isHashAvailable();
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer this object type's type.

		object.makeImmutable();
		return ObjectMetaDescriptor.fromObjectType(object);
	}



	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfObjectType(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Check if I'm a supertype of the given eager object type.

		final AvailObject m1 = object.fieldTypeMap();
		final AvailObject m2 = anObjectType.fieldTypeMap();
		if (m1.mapSize() > m2.mapSize())
		{
			return false;
		}
		AvailObject.lock(m1);
		for (int i1 = 1, _end1 = m1.capacity(); i1 <= _end1; i1++)
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
	public AvailObject o_TypeIntersection (
			final AvailObject object,
			final AvailObject another)
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
		return another.typeIntersectionOfObjectType(object);
	}

	/**
	 * Answer the most general type that is still at least as specific as these.
	 * Here we're finding the nearest common descendant of two eager object
	 * types.
	 */
	@Override
	public AvailObject o_TypeIntersectionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		final Mutable<AvailObject> resultMap = new Mutable<AvailObject>(
			MapDescriptor.newWithCapacity(map1.capacity() + map2.capacity()));
		map1.mapDo(new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (AvailObject key, AvailObject type)
			{
				if (map2.hasKey(key))
				{
					type = type.typeIntersection(map2.mapAt(key));
				}
				resultMap.value = resultMap.value.mapAtPuttingCanDestroy(
					key,
					type,
					true);
			}
		});
		map2.mapDo(new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (AvailObject key, AvailObject type)
			{
				if (!map1.hasKey(key))
				{
					resultMap.value = resultMap.value.mapAtPuttingCanDestroy(
						key,
						type,
						true);
				}
			}
		});
		return ObjectTypeDescriptor.objectTypeFromMap(resultMap.value);
	}

	@Override
	public AvailObject o_TypeUnion (
			final AvailObject object,
			final AvailObject another)
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
		return another.typeUnionOfObjectType(object);
	}

	/**
	 * Answer the most specific type that is still at least as general as these.
	 * Here we're finding the nearest common ancestor of two eager object types.
	 */
	@Override
	public AvailObject o_TypeUnionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		final Mutable<AvailObject> resultMap = new Mutable<AvailObject>(
			MapDescriptor.newWithCapacity(
				min(map1.capacity(), map2.capacity())));
		map1.mapDo(new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (AvailObject key, AvailObject valueType)
			{
				if (map2.hasKey(key))
				{
					resultMap.value = resultMap.value.mapAtPuttingCanDestroy(
						key,
						valueType.typeUnion(map2.mapAt(key)),
						true);
				}
			}
		});
		return ObjectTypeDescriptor.objectTypeFromMap(resultMap.value);
	}



	// private-computation

	int computeHashForObject (
			final AvailObject object)
	{
		//  Compute the hash value from the object's data.  The result should be
		//  a Smalltalk Integer between 16r00000001 and 16rFFFFFFFF inclusive.
		//  Hash the map (of field keys and field types) and multiply it by 11.

		return (object.fieldTypeMap().hash() * 11);
	}





	/* Object creation */
	public static AvailObject objectTypeFromMap (AvailObject map)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ObjectTypeDescriptor.mutableDescriptor());
		result.fieldTypeMap(map);
		return result;
	};

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

	/**
	 * The mutable {@link ObjectTypeDescriptor}.
	 */
	private final static ObjectTypeDescriptor mutableDescriptor = new ObjectTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The mutable {@link ObjectTypeDescriptor}.
	 */
	public static ObjectTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ObjectTypeDescriptor}.
	 */
	private final static ObjectTypeDescriptor immutableDescriptor = new ObjectTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The immutable {@link ObjectTypeDescriptor}.
	 */
	public static ObjectTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
