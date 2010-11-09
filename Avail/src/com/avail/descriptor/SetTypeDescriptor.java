/**
 * descriptor/SetTypeDescriptor.java
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
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;

@ObjectSlots({
	"sizeRange", 
	"contentType"
})
public class SetTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectContentType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectSizeRange (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectContentType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	AvailObject ObjectSizeRange (
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
		aStream.append("set");
		if (! object.sizeRange().equals(IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			aStream.append(" ");
			object.sizeRange().printOnAvoidingIndent(
				aStream,
				recursionList,
				(indent + 1));
		}
		aStream.append(" of ");
		object.contentType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsSetType(object);
	}

	boolean ObjectEqualsSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Set types are equal iff both their sizeRange and contentType match.

		if (object.sameAddressAs(aSetType))
		{
			return true;
		}
		return (object.sizeRange().equals(aSetType.sizeRange()) && object.contentType().equals(aSetType.contentType()));
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.setType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return (((object.sizeRange().hash() * 11) + (object.contentType().hash() * 5)) & HashMask);
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (! object.sizeRange().isHashAvailable())
		{
			return false;
		}
		if (! object.contentType().isHashAvailable())
		{
			return false;
		}
		return true;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.setType();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfSetType(object);
	}

	boolean ObjectIsSupertypeOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Set type A is a subtype of B if and only if their size ranges are covariant
		//  and their content types are covariant.

		return (aSetType.sizeRange().isSubtypeOf(object.sizeRange()) && aSetType.contentType().isSubtypeOf(object.contentType()));
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
		return another.typeIntersectionOfSetType(object);
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return SetTypeDescriptor.setTypeForSizesContentType(object.sizeRange().typeIntersection(aSetType.sizeRange()), object.contentType().typeIntersection(aSetType.contentType()));
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.equals(another))
		{
			return object;
		}
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfSetType(object);
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return SetTypeDescriptor.setTypeForSizesContentType(object.sizeRange().typeUnion(aSetType.sizeRange()), object.contentType().typeUnion(aSetType.contentType()));
	}

	boolean ObjectIsSetType (
			final AvailObject object)
	{
		return true;
	}





	/* Object creation */
	public static AvailObject setTypeForSizesContentType (
			AvailObject sizeRange,
			AvailObject contentType)
	{
		if (sizeRange.equals(TypeDescriptor.terminates()))
		{
			return TypeDescriptor.terminates();
		}
		assert(sizeRange.lowerBound().isFinite());
		assert(IntegerDescriptor.objectFromByte((byte)0).lessOrEqual(sizeRange.lowerBound()));
		assert(sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive());
		AvailObject result = AvailObject.newIndexedDescriptor(0, SetTypeDescriptor.mutableDescriptor());
		if (sizeRange.upperBound().equals(IntegerDescriptor.objectFromByte((byte)0)))
		{
			result.sizeRange(sizeRange);
			result.contentType(TypeDescriptor.terminates());
		}
		else if (contentType.equals(TypeDescriptor.terminates()))
		{
			if (sizeRange.lowerBound().equals(IntegerDescriptor.objectFromByte((byte)0)))
			{
				//  sizeRange includes at least 0 and 1, but the content type is terminates, so no contents exist.
				result.sizeRange(IntegerRangeTypeDescriptor.singleInteger(IntegerDescriptor.objectFromByte((byte)0)));
				result.contentType(TypeDescriptor.terminates());
			}
			else
			{
				//  sizeRange does not include 0, and terminates is not the content type, so the whole type is inconsistent.  Answer terminates
				return TypeDescriptor.terminates();
			}
		}
		else
		{
			result.sizeRange(sizeRange);
			result.contentType(contentType);
		};
		return result;
	};


	/* Descriptor lookup */
	public static SetTypeDescriptor mutableDescriptor()
	{
		return (SetTypeDescriptor) AllDescriptors [144];
	};
	public static SetTypeDescriptor immutableDescriptor()
	{
		return (SetTypeDescriptor) AllDescriptors [145];
	};

}
