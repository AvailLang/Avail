/**
 * descriptor/ObjectMetaDescriptor.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ObjectMetaMetaDescriptor;
import com.avail.descriptor.TypeDescriptor;

@ObjectSlots("myObjectType")
public class ObjectMetaDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	/**
	 * Setter for field !M!yObjectType.
	 */
	@Override
	public void ObjectMyObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field !M!yObjectType.
	 */
	@Override
	public AvailObject ObjectMyObjectType (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// operations

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer this object type's type's type.

		object.makeImmutable();
		return ObjectMetaMetaDescriptor.fromObjectMeta(object);
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  The hash value is always recomputed from the objectMeta's instance (an objectType).

		return object.myObjectType().hash() ^ (0x1317C873);
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.myObjectType().isHashAvailable();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer this object type's type's type.

		object.makeImmutable();
		return ObjectMetaMetaDescriptor.fromObjectMeta(object);
	}



	// operations-meta

	@Override
	public AvailObject ObjectInstance (
			final AvailObject object)
	{
		//  Answer this object meta's sole instance, an object type.  Object metas parallel the
		//  object types precisely, so no information is lost crossing this type/meta barrier here.  Object
		//  meta metas also parallel the object metas precisely, so no information is lost even if two steps
		//  up the meta-hierarchy are needed.  An infinite regress should not be necessary because
		//  defining-style code can simply keep the meta-level in range (either an objectType or an
		//  objectMeta or an objectMetaMeta).  Hopefully there are no examples where three meta-levels
		//  are crossed simultaneously and need to be uncrossed without information loss.

		return object.myObjectType();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfObjectMeta(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Check if I'm a supertype of the given object meta.

		return anObjectMeta.instance().isSubtypeOf(object.instance());
	}

	@Override
	public AvailObject ObjectTypeIntersection (
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
		return another.typeIntersectionOfObjectMeta(object);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.  Note that the cases of the types
		//  being equal or one being a subtype of the other have already been dealt
		//  with (in Object:typeIntersection:), so don't test for them here.

		return Types.terminatesType.object();
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Here we're finding
		//  the nearest common descendant of two eager object metas.

		return object.instance().typeIntersection(anObjectMeta.instance()).type();
	}

	@Override
	public AvailObject ObjectTypeUnion (
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
		return another.typeUnionOfObjectMeta(object);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.instance().typeUnion(anObjectMeta.instance()).type();
	}





	/* Object creation */
	static AvailObject fromObjectType (AvailObject objectType)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ObjectMetaDescriptor.mutableDescriptor());
		result.myObjectType(objectType);
		return result;
	};

	/**
	 * Construct a new {@link ObjectMetaDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected ObjectMetaDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	public static ObjectMetaDescriptor mutableDescriptor()
	{
		return (ObjectMetaDescriptor) allDescriptors [130];
	}

	public static ObjectMetaDescriptor immutableDescriptor()
	{
		return (ObjectMetaDescriptor) allDescriptors [131];
	}
}
