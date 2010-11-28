/**
 * descriptor/ListTypeDescriptor.java
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
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;

public class ListTypeDescriptor extends TypeDescriptor
{

	enum ObjectSlots
	{
		tupleType
	}


	// GENERATED accessors

	/**
	 * Setter for field tupleType.
	 */
	@Override
	public void ObjectTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.tupleType, value);
	}

	/**
	 * Getter for field tupleType.
	 */
	@Override
	public AvailObject ObjectTupleType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.tupleType);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append("(");
		object.tupleType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(") as listType");
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsListType(object);
	}

	@Override
	public boolean ObjectEqualsListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		if (object.sameAddressAs(aListType))
		{
			return true;
		}
		//  Compare by tupleType.
		return object.tupleType().equals(aListType.tupleType());
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.listType.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return ListTypeDescriptor.hashFromTupleTypeHash(object.tupleType().hash());
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.tupleType().isHashAvailable();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.listType.object();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfListType(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  List types are covariant by their content type.

		return aListType.tupleType().isSubtypeOf(object.tupleType());
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
		return another.typeIntersectionOfListType(object);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return ListTypeDescriptor.listTypeForTupleType(object.tupleType().typeIntersection(aListType.tupleType()));
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
		return another.typeUnionOfListType(object);
	}

	@Override
	public AvailObject ObjectTypeUnionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return ListTypeDescriptor.listTypeForTupleType(object.tupleType().typeUnion(aListType.tupleType()));
	}

	@Override
	public boolean ObjectIsListType (
			final AvailObject object)
	{
		return true;
	}





	/* Object creation */
	public static AvailObject listTypeForTupleType (AvailObject tupleTypeObject)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ListTypeDescriptor.mutableDescriptor());
		result.tupleType(tupleTypeObject);
		return result;
	};

	/* Hashing */
	static int hashFromTupleTypeHash (int tupleTypeHash)
	{
		return ((tupleTypeHash + 0x0286B787) ^ 0x1350B29C);
	};

	/**
	 * Construct a new {@link ListTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ListTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ListTypeDescriptor}.
	 */
	private final static ListTypeDescriptor mutableDescriptor = new ListTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ListTypeDescriptor}.
	 *
	 * @return The mutable {@link ListTypeDescriptor}.
	 */
	public static ListTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ListTypeDescriptor}.
	 */
	private final static ListTypeDescriptor immutableDescriptor = new ListTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ListTypeDescriptor}.
	 *
	 * @return The immutable {@link ListTypeDescriptor}.
	 */
	public static ListTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
