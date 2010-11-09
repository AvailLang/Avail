/**
 * descriptor/ApproximateTypeDescriptor.java
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
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;

@ObjectSlots("instance")
public class ApproximateTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectInstance (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectInstance (
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
		aStream.append("ApproximateType of ");
		object.instance().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Check if object and another are equal.  Since I'm an ApproximateType, I have two
		//  choices for how to proceed.  If my instance claims it canComputeHashOfType, then
		//  it must also support the hashOfType: and typeEquals: messages without creating
		//  any instances.  Otherwise, I must create the exact type and let it do the equality
		//  check.

		final AvailObject inst = object.instance();
		return (inst.canComputeHashOfType() ? inst.typeEquals(another) : object.becomeExactType().equals(another));
	}

	boolean ObjectHasObjectInstance (
			final AvailObject object, 
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return object.becomeExactType().hasObjectInstance(potentialInstance);
	}

	boolean ObjectIsBetterRepresentationThan (
			final AvailObject object, 
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm quick for some things, but if a more 'solid' object exists, use it.
		return false;
	}

	boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm not a very efficient alternative representation of a tuple type.
		return false;
	}

	AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		//  Transform object into an indirection to its exact equivalent.

		final AvailObject exact = object.instance().exactType();
		object.becomeIndirectionTo(exact);
		return exact;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().exactType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value that is always the same for equal objects, but
		//  statistically different for different objects.

		final AvailObject inst = object.instance();
		return (inst.canComputeHashOfType() ? inst.hashOfType() : object.becomeExactType().hash());
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.  We ask our instance
		//  if it can compute #hashOfType or not.

		return object.instance().canComputeHashOfType();
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().type();
	}



	// operations-faulting

	boolean ObjectEqualsClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aClosureType) : object.becomeExactType().equalsClosureType(aClosureType));
	}

	boolean ObjectEqualsContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aContainerType) : object.becomeExactType().equalsContainerType(aContainerType));
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
		return object.instance().closure().type().equals(aType.closureType());
	}

	boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  This is always false here, but we can leave that to the type propagating Java VM generator to deduce.

		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aGeneralizedClosureType) : object.becomeExactType().equalsGeneralizedClosureType(aGeneralizedClosureType));
	}

	boolean ObjectEqualsIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(anIntegerRangeType) : object.becomeExactType().equalsIntegerRangeType(anIntegerRangeType));
	}

	boolean ObjectEqualsListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		if (object.instance().isList())
		{
			return object.instance().tuple().type().equals(aListType.tupleType());
		}
		return false;
	}

	boolean ObjectEqualsMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aMapType) : object.becomeExactType().equalsMapType(aMapType));
	}

	boolean ObjectEqualsPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aPrimitiveType) : object.becomeExactType().equalsPrimitiveType(aPrimitiveType));
	}

	boolean ObjectEqualsSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aSetType) : object.becomeExactType().equalsSetType(aSetType));
	}

	boolean ObjectEqualsTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aTupleType) : object.becomeExactType().equalsTupleType(aTupleType));
	}

	boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfClosureType(aClosureType);
	}

	boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfContainerType(aContainerType);
	}

	boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfCyclicType(aCyclicType);
	}

	boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  Redirect it to the exact type to be more precise.

		if (object.instance().isExtendedInteger())
		{
			return (anIntegerRangeType.lowerBound().equals(object.instance()) && anIntegerRangeType.upperBound().equals(object.instance()));
		}
		return object.becomeExactType().isSupertypeOfIntegerRangeType(anIntegerRangeType);
	}

	boolean ObjectIsSupertypeOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Redirect it to the exact type to be more precise.

		if (object.instance().isList())
		{
			return object.instance().tuple().type().isSupertypeOfTupleType(aListType.tupleType());
		}
		return false;
	}

	boolean ObjectIsSupertypeOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfMapType(aMapType);
	}

	boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMeta(anObjectMeta);
	}

	boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectType(anObjectType);
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	boolean ObjectIsSupertypeOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfSetType(aSetType);
	}

	boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfTupleType(aTupleType);
	}

	AvailObject ObjectTypeIntersection (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersection(another);
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfClosureType(aClosureType);
	}

	AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfContainerType(aContainerType);
	}

	AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfCyclicType(aCyclicType);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfIntegerRangeType(anIntegerRangeType);
	}

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfListType(aListType);
	}

	AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfMapType(aMapType);
	}

	AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.

		return object.becomeExactType().typeIntersectionOfMeta(someMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMeta(anObjectMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMetaMeta(anObjectMetaMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectType(anObjectType);
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfSetType(aSetType);
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfTupleType(aTupleType);
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.  Make it exact first.

		return object.becomeExactType().typeUnion(another);
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfClosureType(aClosureType);
	}

	AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfContainerType(aContainerType);
	}

	AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfCyclicType(aCyclicType);
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfIntegerRangeType(anIntegerRangeType);
	}

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfListType(aListType);
	}

	AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfMapType(aMapType);
	}

	AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectMeta(anObjectMeta);
	}

	AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectType(anObjectType);
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfSetType(aSetType);
	}

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfTupleType(aTupleType);
	}

	AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		return object.becomeExactType().fieldTypeMap();
	}



	// operations-integer range

	AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().lowerBound();
	}

	boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().lowerInclusive();
	}

	AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().upperBound();
	}

	boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().upperInclusive();
	}



	// operations-lists

	AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  I must support this in case I represent a listType.  I simply go to my
		//  instance, get its tuple, and take its approximate type.

		return object.instance().tuple().type();
	}



	// operations-tupleType

	AvailObject ObjectTypeAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  This is only intended for a TupleType stand-in.
		//
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  terminates if the index is out of bounds.

		if (! object.isTupleType())
		{
			error("Don't send typeAtIndex: to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		if ((1 <= index && index <= object.instance().tupleSize()))
		{
			return object.instance().tupleAt(index).type();
		}
		return TypeDescriptor.terminates();
	}

	AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  Answer the union of the types that object's instances could have in the
		//  given range of indices.  Out-of-range indices are treated as terminates,
		//  which don't affect the union (unless all indices are out of range).

		assert (startIndex <= endIndex);
		assert object.isTupleType();
		if ((startIndex == endIndex))
		{
			return object.typeAtIndex(startIndex);
		}
		if ((endIndex <= 0))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject tupleObject = object.instance();
		final int upperIndex = tupleObject.tupleSize();
		if ((startIndex > upperIndex))
		{
			return TypeDescriptor.terminates();
		}
		AvailObject unionType = TypeDescriptor.terminates();
		for (int i = max (startIndex, 1), _end1 = min (endIndex, upperIndex); i <= _end1; i++)
		{
			unionType = unionType.typeUnion(tupleObject.tupleAt(i).type());
		}
		return unionType;
	}

	AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		if (! object.isTupleType())
		{
			error("Don't send defaultType to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		if ((object.instance().tupleSize() == 0))
		{
			return TypeDescriptor.terminates();
		}
		return object.instance().tupleAt(object.instance().tupleSize()).type();
	}

	AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		if (! object.isTupleType())
		{
			error("Don't send sizeRange to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		return IntegerDescriptor.objectFromInt(object.instance().tupleSize()).type();
	}

	AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  I can't keep pretending forever.  The encapsulation of tupleType has leaked enough
		//  already.  Fault in the real type.

		return object.becomeExactType().typeTuple();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return object.instance().isInstanceOfSubtypeOf(aType);
	}

	boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		return object.instance().isExtendedInteger();
	}

	boolean ObjectIsListType (
			final AvailObject object)
	{
		return object.instance().isList();
	}

	boolean ObjectIsMapType (
			final AvailObject object)
	{
		return object.instance().isMap();
	}

	boolean ObjectIsSetType (
			final AvailObject object)
	{
		return object.instance().isSet();
	}

	boolean ObjectIsTupleType (
			final AvailObject object)
	{
		return object.instance().isTuple();
	}





	/* Object creation */
	static AvailObject withInstance (AvailObject instance)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ApproximateTypeDescriptor.mutableDescriptor());
		result.instance(instance.makeImmutable());
		return result;
	};


	/* Descriptor lookup */
	public static ApproximateTypeDescriptor mutableDescriptor()
	{
		return (ApproximateTypeDescriptor) AllDescriptors [2];
	};
	public static ApproximateTypeDescriptor immutableDescriptor()
	{
		return (ApproximateTypeDescriptor) AllDescriptors [3];
	};

}
