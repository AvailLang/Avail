/**
 * descriptor/ContainerTypeDescriptor.java
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
import java.util.List;

@ObjectSlots("innerType")
public class ContainerTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	/**
	 * Setter for field innerType.
	 */
	@Override
	public void ObjectInnerType (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field innerType.
	 */
	@Override
	public AvailObject ObjectInnerType (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append("& : ");
		object.innerType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsContainerType(object);
	}

	@Override
	public boolean ObjectEqualsContainerType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Container types compare for equality by comparing their innerTypes.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.innerType().equals(object.innerType());
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.containerType.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return ((object.innerType().hash() * 17) ^ 0x613E420);
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.innerType().isHashAvailable();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.containerType.object();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfContainerType(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Container types are invariant (co- and contra-variant simultaneously).  Strange to
		//  compare for equality, but that's how invariance is defined.

		return object.innerType().equals(aContainerType.innerType());
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
		return another.typeIntersectionOfContainerType(object);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.innerType().equals(aContainerType.innerType()))
		{
			return object;
		}
		return Types.terminates.object();
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
		return another.typeUnionOfContainerType(object);
	}

	@Override
	public AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.innerType().equals(aContainerType.innerType()))
		{
			return object;
		}
		return Types.container.object();
	}





	/* Descriptor lookup */
	public static AvailObject containerTypeForInnerType (AvailObject innerType)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ContainerTypeDescriptor.mutableDescriptor());
		result.innerType(innerType.makeImmutable());
		result.makeImmutable();
		return result;
	};

	/**
	 * Construct a new {@link ContainerTypeDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContainerTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/* Descriptor lookup */
	final static ContainerTypeDescriptor mutableDescriptor = new ContainerTypeDescriptor(true);

	public static ContainerTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static ContainerTypeDescriptor immutableDescriptor = new ContainerTypeDescriptor(false);

	public static ContainerTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
