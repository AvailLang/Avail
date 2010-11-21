/**
 * descriptor/ObjectMetaMetaDescriptor.java
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
import com.avail.descriptor.TypeDescriptor;

@ObjectSlots("myObjectMeta")
public class ObjectMetaMetaDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	/**
	 * Setter for field myObjectMeta.
	 */
	@Override
	public void ObjectMyObjectMeta (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field myObjectMeta.
	 */
	@Override
	public AvailObject ObjectMyObjectMeta (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// operations

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer this object type's type's type's type.

		return Types.objectMetaMeta.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  The hash value is always recomputed from the objectMetaMeta's instance (an objectMeta).

		return (object.myObjectMeta().hash() + 0xAC9A6AA);
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.myObjectMeta().isHashAvailable();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer this object type's type's type's type.

		return Types.objectMetaMeta.object();
	}



	// operations-meta

	@Override
	public AvailObject ObjectInstance (
			final AvailObject object)
	{
		//  Answer this object meta meta's sole instance, an object meta.  Object metas parallel the
		//  object types precisely, so no information is lost crossing this type/meta barrier here.  Object
		//  meta metas also parallel the object metas precisely, so no information is lost even if two steps
		//  up the meta-hierarchy are needed.  An infinite regress should not be necessary because
		//  defining-style code can simply keep the meta-level in range (either an objectType or an
		//  objectMeta or an objectMetaMeta).  Hopefully there are no examples where three meta-levels
		//  are crossed simultaneously and need to be uncrossed without information loss.

		return object.myObjectMeta();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfObjectMetaMeta(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Check if I'm a supertype of the given object meta meta.  Skip comparing the underlying
		//  objectMetas and go directly to comparing the underlying objectTypes two levels down.

		return anObjectMetaMeta.instance().instance().isSubtypeOf(object.instance().instance());
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
		return another.typeIntersectionOfObjectMetaMeta(object);
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
	public AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Here we're finding
		//  the nearest common descendant of two object meta metas.  Skip the objectMeta level and
		//  work directly with the objectTypes two layers down.

		return object.instance().instance().typeIntersection(anObjectMetaMeta.instance().instance()).type().type();
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
		return another.typeUnionOfObjectMetaMeta(object);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.instance().instance().typeUnion(anObjectMetaMeta.instance().instance()).type().type();
	}

	/* Object creation */
	static AvailObject fromObjectMeta (AvailObject objectMeta)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ObjectMetaMetaDescriptor.mutableDescriptor());
		result.myObjectMeta(objectMeta);
		return result;
	};

	/**
	 * Construct a new {@link ObjectMetaMetaDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectMetaMetaDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static ObjectMetaMetaDescriptor mutableDescriptor = new ObjectMetaMetaDescriptor(true);

	public static ObjectMetaMetaDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static ObjectMetaMetaDescriptor immutableDescriptor = new ObjectMetaMetaDescriptor(false);

	public static ObjectMetaMetaDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
