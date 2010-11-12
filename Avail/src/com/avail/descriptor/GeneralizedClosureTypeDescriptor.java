/**
 * descriptor/GeneralizedClosureTypeDescriptor.java
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
import com.avail.descriptor.GeneralizedClosureTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;

@ObjectSlots("returnType")
public class GeneralizedClosureTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectReturnType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectReturnType (
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
		aStream.append("[...]->");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsGeneralizedClosureType(object);
	}

	boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Generalized closure types are equal iff they have the same return type.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (! object.returnType().equals(aType.returnType()))
		{
			return false;
		}
		object.becomeIndirectionTo(aType);
		//  They're equal but physically disjoint, so merge the objects.
		aType.makeImmutable();
		//  There are at least 2 references now.
		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.generalizedClosureType.object();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  The hash value is always recomputed from the argTypeTuple and returnType.

		return (((object.returnType().hash() * 13) + 0x359991) & HashMask);
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.generalizedClosureType.object();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfGeneralizedClosureType(object);
	}

	boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Closure types are contravariant by arguments and covariant by return type.  Since
		//  generalized closure types don't know anything about arguments, just compare the
		//  return types.

		return aClosureType.returnType().isSubtypeOf(object.returnType());
	}

	boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Generalized closure types are covariant by return type.

		if (object.equals(aGeneralizedClosureType))
		{
			return true;
		}
		return aGeneralizedClosureType.returnType().isSubtypeOf(object.returnType());
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
		return another.typeIntersectionOfGeneralizedClosureType(object);
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  The intersection
		//  of a closure type and a generalized closure type is always a closure type, so simply
		//  intersect the return types, and use the argument types verbatim.
		//
		//  Here we do something unusual - we invert the arguments again, and let aClosureType take
		//  its best shot at running the show.  It knows better how to deal with cloning aClosureType.

		return aClosureType.typeIntersectionOfGeneralizedClosureType(object);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Respect
		//  the covariance of the return types.

		return GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(object.returnType().typeIntersection(aGeneralizedClosureType.returnType()));
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
		return another.typeUnionOfGeneralizedClosureType(object);
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.  Discard the argument information, because
		//  the union of a generalized closure type and a closure type is always a generalized
		//  closure type.

		return GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(object.returnType().typeUnion(aClosureType.returnType()));
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.

		return GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(object.returnType().typeUnion(aGeneralizedClosureType.returnType()));
	}





	/* Descriptor lookup */
	public static AvailObject generalizedClosureTypeForReturnType (
			AvailObject returnType)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, GeneralizedClosureTypeDescriptor.mutableDescriptor());
		result.returnType(returnType);
		return result;
	};


	/* Descriptor lookup */
	public static GeneralizedClosureTypeDescriptor mutableDescriptor()
	{
		return (GeneralizedClosureTypeDescriptor) allDescriptors [56];
	};
	public static GeneralizedClosureTypeDescriptor immutableDescriptor()
	{
		return (GeneralizedClosureTypeDescriptor) allDescriptors [57];
	};

}
