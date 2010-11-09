/**
 * descriptor/ContinuationTypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;

@ObjectSlots("closureType")
public class ContinuationTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectClosureType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectClosureType (
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
		aStream.append('$');
		object.closureType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsContinuationType(object);
	}

	boolean ObjectEqualsContinuationType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Continuation types compare for equality by comparing their closureTypes.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.closureType().equals(object.closureType());
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.continuationType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return ((((object.closureType().hash() * 11) & HashMask) ^ 0x3E20409) & HashMask);
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.closureType().isHashAvailable();
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.continuationType();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfContinuationType(object);
	}

	boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  Since the only things that can be done with continuations are to restart them or to exit them,
		//  continuation subtypes must accept any values that could be passed as arguments or as the
		//  return value to the supertype.  Therefore, continuation types must be contravariant with respect
		//  to the contained closureType's arguments, and also contravariant with respect to the contained
		//  closureType's result type.

		final AvailObject subClosureType = aContinuationType.closureType();
		final AvailObject superClosureType = object.closureType();
		if (! (subClosureType.numArgs() == superClosureType.numArgs()))
		{
			return false;
		}
		if (! superClosureType.returnType().isSubtypeOf(subClosureType.returnType()))
		{
			return false;
		}
		for (int i = 1, _end1 = subClosureType.numArgs(); i <= _end1; i++)
		{
			if (! superClosureType.argTypeAt(i).isSubtypeOf(subClosureType.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
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
		return another.typeIntersectionOfContinuationType(object);
	}

	AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  Answer the most general type that is still at least as specific as these.

		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aContinuationType.closureType();
		if (closType1.equals(closType2))
		{
			return object;
		}
		if (! (closType1.numArgs() == closType2.numArgs()))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject intersection = AvailObject.newIndexedDescriptor(closType1.numArgs(), ClosureTypeDescriptor.mutableDescriptor());
		AvailObject.lock(intersection);
		intersection.returnType(closType1.returnType().typeUnion(closType2.returnType()));
		for (int i = 1, _end1 = closType1.numArgs(); i <= _end1; i++)
		{
			intersection.argTypeAtPut(i, closType1.argTypeAt(i).typeUnion(closType2.argTypeAt(i)));
		}
		intersection.hashOrZero(0);
		AvailObject.unlock(intersection);
		return ContinuationTypeDescriptor.continuationTypeForClosureType(intersection);
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
		return another.typeUnionOfContinuationType(object);
	}

	AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  Answer the most specific type that is still at least as general as these.

		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aContinuationType.closureType();
		if (closType1.equals(closType2))
		{
			return object;
		}
		if (! (closType1.numArgs() == closType2.numArgs()))
		{
			return TypeDescriptor.continuation();
		}
		final AvailObject closureUnion = AvailObject.newIndexedDescriptor(closType1.numArgs(), ClosureTypeDescriptor.mutableDescriptor());
		AvailObject.lock(closureUnion);
		closureUnion.returnType(closType1.returnType().typeIntersection(closType2.returnType()));
		for (int i = 1, _end1 = closType1.numArgs(); i <= _end1; i++)
		{
			closureUnion.argTypeAtPut(i, closType1.argTypeAt(i).typeIntersection(closType2.argTypeAt(i)));
		}
		closureUnion.hashOrZero(0);
		AvailObject.unlock(closureUnion);
		return ContinuationTypeDescriptor.continuationTypeForClosureType(closureUnion);
	}





	/* Descriptor lookup */
	public static AvailObject continuationTypeForClosureType (AvailObject closureType)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ContinuationTypeDescriptor.mutableDescriptor());
		result.closureType(closureType.makeImmutable());
		result.makeImmutable();
		return result;
	};


	/* Descriptor lookup */
	public static ContinuationTypeDescriptor mutableDescriptor()
	{
		return (ContinuationTypeDescriptor) AllDescriptors [40];
	};
	public static ContinuationTypeDescriptor immutableDescriptor()
	{
		return (ContinuationTypeDescriptor) AllDescriptors [41];
	};

}
