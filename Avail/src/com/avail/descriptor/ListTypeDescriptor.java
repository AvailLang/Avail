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

@ObjectSlots("tupleType")
public class ListTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// java printing

	void printObjectOnAvoidingIndent (
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

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsListType(object);
	}

	boolean ObjectEqualsListType (
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

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.listType.object();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return ListTypeDescriptor.hashFromTupleTypeHash(object.tupleType().hash());
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.tupleType().isHashAvailable();
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.listType.object();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfListType(object);
	}

	boolean ObjectIsSupertypeOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  List types are covariant by their content type.

		return aListType.tupleType().isSubtypeOf(object.tupleType());
	}

	AvailObject ObjectTypeIntersection (
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

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return ListTypeDescriptor.listTypeForTupleType(object.tupleType().typeIntersection(aListType.tupleType()));
	}

	AvailObject ObjectTypeUnion (
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

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return ListTypeDescriptor.listTypeForTupleType(object.tupleType().typeUnion(aListType.tupleType()));
	}

	boolean ObjectIsListType (
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
		return ((tupleTypeHash + 0x0286B787) ^ 0x1350B29C) & HashMask;
	};


	/* Descriptor lookup */
	public static ListTypeDescriptor mutableDescriptor()
	{
		return (ListTypeDescriptor) allDescriptors [102];
	};
	public static ListTypeDescriptor immutableDescriptor()
	{
		return (ListTypeDescriptor) allDescriptors [103];
	};

}
